package com.soul.neurokaraoke.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class LyricsResult(
    val id: Int,
    val trackName: String,
    val artistName: String,
    val albumName: String?,
    val syncedLyrics: String?, // LRC format with timestamps
    val plainLyrics: String?
)

data class LyricLine(
    val timestamp: Long, // in milliseconds
    val text: String,
    val translatedText: String? = null
)

class LyricsApi {
    companion object {
        private const val BASE_URL = "https://lrclib.net/api"
        private const val USER_AGENT = "NeuroKaraoke Android App (https://github.com/user/neurokaraoke)"
        const val NK_API_URL = "https://api.neurokaraoke.com"

        /**
         * Pure zip of lyric lines with their translations. Only applies when the
         * counts match (the server contract); otherwise returns lines unchanged.
         * Android-free so it can be unit tested on the JVM.
         */
        /**
         * The NeuroKaraoke song UUID embedded in a local [Song.id], or null if the id
         * isn't a server id. Radio songs carry it as "radio_<uuid>"; catalog/favourite
         * songs carry the bare UUID. Lets lyrics resolve by id without an audioUrl match
         * (radio's audioUrl is the shared stream URL, which never matches a song).
         */
        fun neuroSongIdFromLocalId(localId: String): String? {
            val id = localId.removePrefix("radio_")
            return id.takeIf { it.length == 36 && it.count { c -> c == '-' } == 4 }
        }

        fun mergeTranslations(lines: List<LyricLine>, translations: List<String>): List<LyricLine> {
            if (translations.size != lines.size) return lines
            return lines.mapIndexed { i, line ->
                val t = translations[i]
                if (t.isBlank() || t == line.text) line else line.copy(translatedText = t)
            }
        }
    }

    /**
     * Search for lyrics by track name and artist
     */
    suspend fun searchLyrics(trackName: String, artistName: String): Result<LyricsResult?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val encodedTrack = URLEncoder.encode(trackName, "UTF-8")
            val encodedArtist = URLEncoder.encode(artistName, "UTF-8")
            val url = URL("$BASE_URL/get?track_name=$encodedTrack&artist_name=$encodedArtist")

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            when (val responseCode = connection.responseCode) {
                HttpURLConnection.HTTP_OK -> {
                    val response = connection.inputStream.bufferedReader().use { it.readText() }
                    val json = JSONObject(response)
                    Result.success(parseLyricsResult(json))
                }
                HttpURLConnection.HTTP_NOT_FOUND -> {
                    // No lyrics found, try search endpoint
                    searchLyricsFallback(trackName, artistName)
                }
                else -> {
                    Result.failure(Exception("HTTP $responseCode"))
                }
            }
        } catch (e: Exception) {
            if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fallback search using the search endpoint
     */
    private suspend fun searchLyricsFallback(trackName: String, artistName: String): Result<LyricsResult?> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            // Try with just track name first
            val query = "$trackName $artistName"
            val encodedQuery = URLEncoder.encode(query, "UTF-8")
            val url = URL("$BASE_URL/search?q=$encodedQuery")

            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.setRequestProperty("User-Agent", USER_AGENT)
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)

                if (jsonArray.length() > 0) {
                    // Return first result
                    val json = jsonArray.getJSONObject(0)
                    Result.success(parseLyricsResult(json))
                } else {
                    Result.success(null)
                }
            } else {
                Result.success(null)
            }
        } catch (e: Exception) {
            if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseLyricsResult(json: JSONObject): LyricsResult {
        return LyricsResult(
            id = json.optInt("id", 0),
            trackName = json.optString("trackName", ""),
            artistName = json.optString("artistName", ""),
            albumName = json.optString("albumName", "").takeIf { it.isNotBlank() },
            syncedLyrics = json.optString("syncedLyrics", "").takeIf { it.isNotBlank() && it != "null" },
            plainLyrics = json.optString("plainLyrics", "").takeIf { it.isNotBlank() && it != "null" }
        )
    }

    /**
     * Parse LRC format lyrics into list of timed lines
     * LRC format: [mm:ss.xx]lyrics text
     */
    fun parseSyncedLyrics(lrcContent: String): List<LyricLine> {
        val lines = mutableListOf<LyricLine>()
        val regex = Regex("""\[(\d{2}):(\d{2})\.(\d{2,3})\](.*)""")

        for (line in lrcContent.lines()) {
            val match = regex.find(line)
            if (match != null) {
                val (minutes, seconds, millis, text) = match.destructured
                val millisPart = if (millis.length == 2) millis.toInt() * 10 else millis.toInt()
                val timestamp = minutes.toLong() * 60 * 1000 +
                               seconds.toLong() * 1000 +
                               millisPart
                lines.add(LyricLine(timestamp, text.trim()))
            }
        }

        return lines.sortedBy { it.timestamp }
    }

    /**
     * Parse plain lyrics into lines (no timestamps)
     */
    fun parsePlainLyrics(plainContent: String): List<LyricLine> {
        return plainContent.lines()
            .filter { it.isNotBlank() }
            .mapIndexed { index, text ->
                // Assign fake timestamps for display (won't be used for syncing)
                LyricLine(index * 3000L, text.trim())
            }
    }

    /**
     * Translate synced lyrics via the NeuroKaraoke first-party endpoint.
     *   POST /api/lyrics/translate  (Bearer)
     *   body: {"songID": id, "lyrics":[{"time": <seconds>, "text": "..."}]}
     * Response accepted in any of these shapes (mirrors the iOS client):
     *   {"translations":["..."]} | {"lines":[{"text":"..."}]} | {"items":[{"text":"..."}]}
     * Returns the input lines with [LyricLine.translatedText] filled in.
     */
    suspend fun translateLyrics(
        songId: String,
        lines: List<LyricLine>,
        accessToken: String
    ): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        if (lines.isEmpty()) return@withContext Result.success(lines)
        var conn: HttpURLConnection? = null
        try {
            val body = JSONObject().apply {
                put("songID", songId)
                put("lyrics", JSONArray().apply {
                    lines.forEach { line ->
                        put(JSONObject().apply {
                            put("time", line.timestamp / 1000.0) // ms -> seconds (TimeInterval)
                            put("text", line.text)
                        })
                    }
                })
            }
            conn = (URL("$NK_API_URL/api/lyrics/translate").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $accessToken")
                setRequestProperty("Content-Type", "application/json")
                connectTimeout = 15000
                readTimeout = 15000
                doOutput = true
            }
            conn.outputStream.use { it.write(body.toString().toByteArray()) }
            val code = conn.responseCode
            if (code !in 200..299) {
                if (com.soul.neurokaraoke.BuildConfig.DEBUG) {
                    android.util.Log.e("LyricsApi", "translate failed ($code)")
                }
                return@withContext Result.failure(Exception("HTTP $code"))
            }
            val response = conn.inputStream.bufferedReader().use { it.readText() }
            val translations = parseTranslations(response)
            Result.success(mergeTranslations(lines, translations))
        } catch (e: Exception) {
            if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /** Extract the translated strings from any of the accepted response shapes. */
    private fun parseTranslations(response: String): List<String> {
        val json = JSONObject(response)
        json.optJSONArray("translations")?.let { arr ->
            return (0 until arr.length()).map { arr.getString(it) }
        }
        json.optJSONArray("lines")?.let { arr ->
            return (0 until arr.length()).map { arr.getJSONObject(it).optString("text", "") }
        }
        json.optJSONArray("items")?.let { arr ->
            return (0 until arr.length()).map { arr.getJSONObject(it).optString("text", "") }
        }
        return emptyList()
    }
}
