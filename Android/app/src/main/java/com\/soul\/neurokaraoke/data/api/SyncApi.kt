package com.soul.neurokaraoke.data.api

import android.util.Log
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * API client for syncing favorites and playlists with the NeuroKaraoke server.
 * Uses the Discord access token directly as Bearer token.
 */
class SyncApi {

    companion object {
        private const val TAG = "SyncApi"
        private const val API_URL = "https://api.neurokaraoke.com"
        private const val IDK_URL = "https://idk.neurokaraoke.com"
    }

    /**
     * Phone: ask server for a 6-char pairing code bound to the current user's JWT.
     * Code expires in 5 min, single-use.
     */
    suspend fun createPairingCode(jwt: String): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$IDK_URL/api/auth/pairing-code").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Authorization", "Bearer $jwt")
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                outputStream.use { it.write("{}".toByteArray()) }
            }
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val body = conn.inputStream.bufferedReader().readText()
            val code = JSONObject(body).optString("code", "")
            if (code.isBlank()) Result.failure(Exception("Empty code"))
            else Result.success(code)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * AAOS: redeem a pairing code for a JWT. Single-use; server deletes on success.
     */
    suspend fun redeemPairingCode(code: String): Result<String> = withContext(Dispatchers.IO) {
        var conn: HttpURLConnection? = null
        try {
            conn = (URL("$IDK_URL/api/auth/redeem-code").openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                setRequestProperty("Content-Type", "application/json")
                doOutput = true
                connectTimeout = 10000
                readTimeout = 10000
                outputStream.use { it.write(JSONObject().put("code", code).toString().toByteArray()) }
            }
            if (conn.responseCode !in 200..299) {
                return@withContext Result.failure(Exception("HTTP ${conn.responseCode}"))
            }
            val body = conn.inputStream.bufferedReader().readText()
            val token = JSONObject(body).optString("token", "")
            if (token.isBlank()) Result.failure(Exception("Empty token"))
            else Result.success(token)
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            conn?.disconnect()
        }
    }

    /**
     * Fetch user's favorites from the server.
     */
    suspend fun fetchFavorites(accessToken: String): Result<List<Song>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/favorites/type?type=0").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                Log.d(TAG, "Favorites response: ${response.take(500)}")
                val songs = parseSongsResponse(response)
                Result.success(songs)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                conn.disconnect()
                Log.e(TAG, "Fetch favorites failed ($responseCode): $errorBody")
                Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch favorites error", e)
            Result.failure(e)
        }
    }

    /**
     * Add a song to favorites on the server.
     */
    suspend fun addFavorite(accessToken: String, songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/user/favorites/$songId").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Length", "0")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Add favorite failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add favorite error", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a song from favorites on the server.
     */
    suspend fun removeFavorite(accessToken: String, songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/user/favorites/$songId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Remove favorite failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remove favorite error", e)
            Result.failure(e)
        }
    }

    /**
     * Fetch user's playlists from the server.
     * Response is a JSON array of playlist objects with fields:
     * id, name, description, media, mosaicMedia, songCount, songListDTOs, isPublic, etc.
     */
    suspend fun fetchUserPlaylists(accessToken: String): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/user/playlists").openConnection() as HttpURLConnection
            conn.requestMethod = "GET"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = conn.inputStream.bufferedReader().readText()
                conn.disconnect()
                Log.d(TAG, "Playlists response: ${response.take(500)}")
                val playlists = parseUserPlaylistsResponse(response)
                Result.success(playlists)
            } else {
                val errorBody = conn.errorStream?.bufferedReader()?.readText() ?: "Unknown error"
                conn.disconnect()
                Log.e(TAG, "Fetch playlists failed ($responseCode): $errorBody")
                Result.failure(Exception("HTTP $responseCode: $errorBody"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Fetch playlists error", e)
            Result.failure(e)
        }
    }

    /**
     * Create a new user playlist on the server.
     * Returns the server-assigned UUID as plain text.
     */
    suspend fun createPlaylist(accessToken: String, name: String, isPublic: Boolean): Result<String> = withContext(Dispatchers.IO) {
        try {
            val body = JSONObject()
                .put("Name", name)
                .put("IsPublic", isPublic)
                .put("IsSetList", false)
                .toString()
                .toByteArray()
            val conn = URL("$API_URL/api/playlist/save").openConnection() as HttpURLConnection
            conn.requestMethod = "POST"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Type", "application/json")
            conn.doOutput = true
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            conn.outputStream.use { it.write(body) }
            val responseCode = conn.responseCode
            val responseBody = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            if (responseCode in 200..299 && responseBody.isNotBlank()) {
                Result.success(responseBody.trim().removeSurrounding("\""))
            } else {
                Log.e(TAG, "Create playlist failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Create playlist error", e)
            Result.failure(e)
        }
    }

    /**
     * Add a song to a user playlist on the server.
     */
    suspend fun addSongToPlaylist(accessToken: String, playlistId: String, songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/user/playlists/$playlistId?songId=$songId").openConnection() as HttpURLConnection
            conn.requestMethod = "PUT"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.setRequestProperty("Content-Length", "0")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val responseCode = conn.responseCode
            conn.disconnect()
            if (responseCode in 200..299) Result.success(Unit)
            else {
                Log.e(TAG, "Add song to playlist failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Add song to playlist error", e)
            Result.failure(e)
        }
    }

    /**
     * Remove a song from a user playlist on the server.
     */
    suspend fun removeSongFromPlaylist(accessToken: String, playlistId: String, songId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/playlist/$playlistId?songId=$songId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000
            val responseCode = conn.responseCode
            conn.disconnect()
            if (responseCode in 200..299) Result.success(Unit)
            else {
                Log.e(TAG, "Remove song from playlist failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Remove song from playlist error", e)
            Result.failure(e)
        }
    }

    /**
     * Delete a user playlist on the server.
     */
    suspend fun deletePlaylist(accessToken: String, playlistId: String): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            val conn = URL("$API_URL/api/playlist/$playlistId").openConnection() as HttpURLConnection
            conn.requestMethod = "DELETE"
            conn.setRequestProperty("Authorization", "Bearer $accessToken")
            conn.connectTimeout = 10000
            conn.readTimeout = 10000

            val responseCode = conn.responseCode
            conn.disconnect()

            if (responseCode in 200..299) {
                Result.success(Unit)
            } else {
                Log.e(TAG, "Delete playlist failed ($responseCode)")
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Log.e(TAG, "Delete playlist error", e)
            Result.failure(e)
        }
    }

    /**
     * Resolve a media object's image URL from cloudflareId or absolutePath.
     * Handles Cloudflare Images paths (starting with /WxURxyML82UkE7gY-PiBKw/).
     */
    private fun resolveMediaUrl(mediaObj: JSONObject): String {
        val cloudflareId = mediaObj.optString("cloudflareId", "")
        val absolutePath = mediaObj.optString("absolutePath", "")
        return when {
            cloudflareId.isNotBlank() ->
                "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
            absolutePath.startsWith("/WxURxyML82UkE7gY-PiBKw/") ->
                "https://images.neurokaraoke.com$absolutePath/public"
            absolutePath.isNotBlank() && absolutePath.startsWith("http") ->
                absolutePath
            absolutePath.isNotBlank() ->
                "https://storage.neurokaraoke.com$absolutePath"
            else -> ""
        }
    }

    /**
     * Parse user playlists response. Format matches official setlists:
     * [{id, name, description, media:{cloudflareId, absolutePath}, mosaicMedia:[...], songCount, songListDTOs:[...]}]
     */
    private fun parseUserPlaylistsResponse(response: String): List<Playlist> {
        val playlists = mutableListOf<Playlist>()
        try {
            val jsonArray = JSONArray(response)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // Parse cover URL from media object
                val mediaObj = obj.optJSONObject("media")
                val coverUrl = mediaObj?.let { resolveMediaUrl(it) } ?: ""

                // Parse mosaic covers from mosaicMedia array
                val previewCovers = mutableListOf<String>()
                val mosaicArray = obj.optJSONArray("mosaicMedia")
                if (mosaicArray != null) {
                    for (j in 0 until minOf(mosaicArray.length(), 4)) {
                        val mosaicObj = mosaicArray.getJSONObject(j)
                        val mosaicUrl = resolveMediaUrl(mosaicObj)
                        if (mosaicUrl.isNotBlank()) previewCovers.add(mosaicUrl)
                    }
                }

                // Parse songs from songListDTOs if present
                val songs = mutableListOf<Song>()
                val songsArray = obj.optJSONArray("songListDTOs")
                if (songsArray != null) {
                    for (j in 0 until songsArray.length()) {
                        val songObj = songsArray.getJSONObject(j)
                        val song = parseSongObject(songObj)
                        if (song != null) songs.add(song)
                    }
                }

                playlists.add(
                    Playlist(
                        id = obj.getString("id"),
                        title = obj.optString("name", "Unknown Playlist"),
                        description = obj.optString("description", ""),
                        coverUrl = coverUrl,
                        previewCovers = previewCovers,
                        songs = songs,
                        songCount = obj.optInt("songCount", songs.size),
                        isPublic = obj.optBoolean("isPublic", false)
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse user playlists error", e)
        }
        return playlists
    }

    /**
     * Parse songs from API response.
     * Handles both array format and object-with-array format.
     * Adapts to the song format used by trending/setlist endpoints.
     */
    private fun parseSongsResponse(response: String): List<Song> {
        val songs = mutableListOf<Song>()
        try {
            val jsonArray = when {
                response.trimStart().startsWith("[") -> JSONArray(response)
                response.trimStart().startsWith("{") -> {
                    val obj = JSONObject(response)
                    // Try common wrapper keys
                    obj.optJSONArray("songs")
                        ?: obj.optJSONArray("items")
                        ?: obj.optJSONArray("favorites")
                        ?: obj.optJSONArray("data")
                        ?: JSONArray()
                }
                else -> JSONArray()
            }

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                // Favorites API wraps songs: {"id", "order", "type", "song": {...}}
                val songObj = obj.optJSONObject("song") ?: obj
                val song = parseSongObject(songObj)
                if (song != null) songs.add(song)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Parse songs error", e)
        }
        return songs
    }

    /**
     * Parse a single song JSON object.
     * Handles multiple API formats:
     * - Setlist format: title, originalArtists (string), coverArtists, audioUrl, coverArt
     * - Trending format: title, originalArtists (array), absolutePath, coverArt (object)
     * - Simple format: id, title, artist, audioUrl, coverUrl
     */
    private fun parseSongObject(obj: JSONObject): Song? {
        try {
            val id = obj.optString("id", "")

            // Title
            val title = obj.optString("title", "").ifBlank { return null }

            // Artist - try multiple field names
            val artist = when {
                obj.has("originalArtists") -> {
                    val oa = obj.opt("originalArtists")
                    when (oa) {
                        is JSONArray -> buildList {
                            for (j in 0 until oa.length()) add(oa.optString(j, ""))
                        }.filter { it.isNotBlank() }.joinToString(", ")
                        is String -> oa
                        else -> ""
                    }
                }
                obj.has("artist") -> obj.optString("artist", "")
                else -> ""
            }.ifBlank { "Unknown Artist" }

            // Audio URL
            val audioUrl = when {
                obj.has("audioUrl") && obj.optString("audioUrl", "").isNotBlank() ->
                    obj.optString("audioUrl", "")
                obj.has("absolutePath") && obj.optString("absolutePath", "").isNotBlank() -> {
                    val path = obj.optString("absolutePath", "")
                    if (path.startsWith("http")) path
                    else "https://storage.neurokaraoke.com/$path"
                }
                else -> ""
            }

            // Cover art URL
            val coverUrl = when {
                obj.has("coverUrl") && obj.optString("coverUrl", "").isNotBlank() ->
                    obj.optString("coverUrl", "")
                obj.has("coverArt") -> {
                    val ca = obj.opt("coverArt")
                    when (ca) {
                        is JSONObject -> {
                            val cfId = ca.optString("cloudflareId", "")
                            val absPath = ca.optString("absolutePath", "")
                            when {
                                cfId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cfId/public"
                                absPath.isNotBlank() -> absPath
                                else -> ""
                            }
                        }
                        is String -> ca
                        else -> ""
                    }
                }
                audioUrl.isNotBlank() -> {
                    // Derive from audio URL
                    audioUrl.replace("/audio/", "/images/")
                        .replace(Regex("\\.v\\d+\\)?\\.mp3$"), ".jpg")
                        .replace(".mp3", ".jpg")
                }
                else -> ""
            }

            // Keep the API performer text; Singer is only the theme/filter category.
            val coverArtists = when (val ca = obj.opt("coverArtists")) {
                is JSONArray -> buildList {
                    for (j in 0 until ca.length()) add(ca.optString(j, ""))
                }.filter { it.isNotBlank() }.joinToString(", ")
                is String -> ca
                else -> ""
            }
            val singer = if (coverArtists.isNotBlank()) {
                Singer.fromCoverArtists(coverArtists)
            } else {
                try { Singer.valueOf(obj.optString("singer", "NEURO")) }
                catch (_: Exception) { Singer.NEURO }
            }

            val duration = obj.optLong("duration", 0L)
            val coverArtObj = obj.optJSONObject("coverArt")
            val artCredit = obj.optString("artCredit", "").cleanApiText()
                ?: coverArtObj?.optString("credit", "")?.cleanApiText()
                ?: coverArtObj?.optJSONObject("artist")?.let { artistObj ->
                    val name = artistObj.optString("name", "").cleanApiText()
                    val socialLink = artistObj.optString("socialLink", "").cleanApiText()
                    name?.let {
                        if (socialLink != null) "Art by $it - $socialLink" else "Art by $it"
                    }
                }

            return Song(
                id = id,
                title = title,
                artist = artist,
                coverUrl = coverUrl,
                audioUrl = audioUrl,
                duration = duration,
                singer = singer,
                coverArtists = coverArtists,
                artCredit = artCredit
            )
        } catch (e: Exception) {
            Log.e(TAG, "Parse song error: ${obj.toString().take(200)}", e)
            return null
        }
    }

    private fun String.cleanApiText(): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
