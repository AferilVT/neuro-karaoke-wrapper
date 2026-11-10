package com.soul.neurokaraoke.data.api

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/** One entry from `GET /api/user/songs`. */
data class UploadedSong(
    val id: String,
    val title: String,
    val dateAdded: String?
)

/** HTTP failure with its status code, so callers can tell auth/quota/size errors apart. */
class UploadHttpException(val code: Int, message: String) : Exception(message)

/**
 * The signed-in user's own uploaded songs (docs.neurokaraoke.com, "Your uploads").
 * Upload goes to the upload host; list/update go to the main API.
 */
object UserSongsApi {
    private const val TAG = "UserSongsApi"
    private const val API_BASE = "https://api.neurokaraoke.com"
    private const val UPLOAD_BASE = "https://idk.neurokaraoke.com"

    /** `GET /api/user/songs` */
    suspend fun listUploads(token: String): Result<List<UploadedSong>> = withContext(Dispatchers.IO) {
        runCatching {
            val conn = URL("$API_BASE/api/user/songs").openConnection() as HttpURLConnection
            try {
                conn.connectTimeout = 15_000
                conn.readTimeout = 30_000
                conn.setRequestProperty("Authorization", "Bearer $token")
                val code = conn.responseCode
                if (code != HttpURLConnection.HTTP_OK) throw UploadHttpException(code, "HTTP $code")
                parseUploads(conn.inputStream.bufferedReader().readText())
            } finally {
                conn.disconnect()
            }
        }
    }

    /**
     * `POST /api/user/song/upload` as multipart/form-data (`File`), with `Title`/`Artist`
     * as query parameters. Returns the new song's id if the response body carries one
     * (the API does not document a body), else null — use [findNewUpload] in that case.
     */
    suspend fun upload(token: String, file: File, title: String, artist: String): Result<String?> =
        withContext(Dispatchers.IO) {
            runCatching {
                val query = buildList {
                    if (title.isNotBlank()) add("Title=" + enc(title))
                    if (artist.isNotBlank()) add("Artist=" + enc(artist))
                }.joinToString("&")
                val url = "$UPLOAD_BASE/api/user/song/upload" + if (query.isNotEmpty()) "?$query" else ""

                val boundary = "----NeuroKaraoke" + System.currentTimeMillis()
                val safeName = file.name.replace("\"", "_")
                val head = ("--$boundary\r\n" +
                    "Content-Disposition: form-data; name=\"File\"; filename=\"$safeName\"\r\n" +
                    "Content-Type: audio/mpeg\r\n\r\n").toByteArray(Charsets.UTF_8)
                val tail = "\r\n--$boundary--\r\n".toByteArray(Charsets.UTF_8)

                val conn = URL(url).openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 180_000
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Content-Type", "multipart/form-data; boundary=$boundary")
                    // Stream the file instead of buffering it all in memory.
                    conn.setFixedLengthStreamingMode(head.size + file.length() + tail.size)
                    conn.outputStream.use { out ->
                        out.write(head)
                        file.inputStream().use { it.copyTo(out, 64 * 1024) }
                        out.write(tail)
                    }
                    val code = conn.responseCode
                    if (code !in 200..299) {
                        val err = conn.errorStream?.bufferedReader()?.readText().orEmpty()
                        Log.e(TAG, "Upload failed ($code): ${err.take(300)}")
                        throw UploadHttpException(code, err.take(200).ifBlank { "HTTP $code" })
                    }
                    parseIdFromBody(conn.inputStream.bufferedReader().readText())
                } finally {
                    conn.disconnect()
                }
            }
        }

    /** `POST /api/user/song/{songId}` with `{title, artist}`. */
    suspend fun update(token: String, songId: String, title: String, artist: String): Result<Unit> =
        withContext(Dispatchers.IO) {
            runCatching {
                val conn = URL("$API_BASE/api/user/song/${enc(songId)}").openConnection() as HttpURLConnection
                try {
                    conn.requestMethod = "POST"
                    conn.doOutput = true
                    conn.connectTimeout = 15_000
                    conn.readTimeout = 30_000
                    conn.setRequestProperty("Authorization", "Bearer $token")
                    conn.setRequestProperty("Content-Type", "application/json")
                    val body = JSONObject().put("title", title).put("artist", artist).toString()
                    conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                    val code = conn.responseCode
                    if (code !in 200..299) throw UploadHttpException(code, "HTTP $code")
                }  finally {
                    conn.disconnect()
                }
            }
        }

    private fun enc(s: String) = URLEncoder.encode(s, "UTF-8").replace("+", "%20")

    internal fun parseUploads(body: String): List<UploadedSong> {
        val arr = JSONArray(body)
        return (0 until arr.length()).mapNotNull { i ->
            val o = arr.optJSONObject(i) ?: return@mapNotNull null
            val id = o.optString("id").takeIf { it.isNotBlank() && it != "null" } ?: return@mapNotNull null
            UploadedSong(
                id = id,
                title = o.optString("title").takeIf { it != "null" }.orEmpty(),
                dateAdded = o.optString("dateAdded").takeIf { it.isNotBlank() && it != "null" }
            )
        }
    }

    /** Accepts a bare id string, or a JSON object with `id` / `songId` (possibly under `song`). */
    internal fun parseIdFromBody(body: String): String? {
        val t = body.trim()
        if (t.isEmpty()) return null
        return try {
            when {
                t.startsWith("{") -> {
                    val o = JSONObject(t)
                    val src = o.optJSONObject("song") ?: o
                    (src.optString("id").ifBlank { src.optString("songId") })
                        .takeIf { it.isNotBlank() && it != "null" }
                }
                t.startsWith("\"") -> t.trim('"').takeIf { it.isNotBlank() }
                ID_REGEX.matches(t) -> t
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Picks the song an upload just created by diffing the upload list before and after.
     * Prefers a title match among the new entries, then the most recently added.
     */
    fun findNewUpload(beforeIds: Set<String>, after: List<UploadedSong>, title: String): UploadedSong? {
        val fresh = after.filter { it.id !in beforeIds }
        if (fresh.isEmpty()) return null
        if (fresh.size == 1) return fresh.single()
        return fresh.firstOrNull { it.title.equals(title.trim(), ignoreCase = true) }
            ?: fresh.maxByOrNull { it.dateAdded.orEmpty() }
    }

    private val ID_REGEX = Regex("""[0-9a-fA-F-]{16,}""")
}
