package com.soul.neurokaraoke.data.api

import com.soul.neurokaraoke.data.model.Artist
import com.soul.neurokaraoke.data.model.Playlist
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CoverDistribution(
    val totalSongs: Int,
    val neuroCount: Int,
    val evilCount: Int,
    val duetCount: Int,
    val otherCount: Int
)

data class ApiPlaylistInfo(
    val id: String,
    val name: String,
    val coverUrl: String,
    val previewCovers: List<String> = emptyList(),
    val songCount: Int = 0
)

data class ApiSong(
    val playlistName: String?,
    val title: String,
    val originalArtists: String?,
    val coverArtists: String?,
    val coverArt: String?,
    val audioUrl: String?,
    val artCredit: String? = null
) {
    /**
     * Derive cover art URL from audio URL
     * Example: https://storage.neurokaraoke.com/audio/FEX%20-%20Subways.mp3
     * Becomes: https://storage.neurokaraoke.com/images/FEX%20-%20Subways.jpg
     */
    fun getCoverArtUrl(): String? {
        // Use explicit coverArt if available
        if (!coverArt.isNullOrBlank()) return coverArt

        // Derive from audio URL
        return audioUrl?.replace("/audio/", "/images/")
            ?.replace(Regex("\\.v\\d+\\)?\\.mp3$"), ".jpg")
            ?.replace(".mp3", ".jpg")
    }
}

data class ApiPublicPlaylist(
    val id: String,
    val name: String,
    val description: String?,
    val coverUrl: String?,
    val mosaicCovers: List<String>,
    val songCount: Int,
    val createdBy: String?
)

class NeuroKaraokeApi {
    companion object {
        private const val BASE_URL = "https://idk.neurokaraoke.com"
        private const val API_URL = "https://api.neurokaraoke.com"
        private const val MAX_CACHE_SIZE = 20 // Limit cache to 20 playlists
    }

    // LRU cache with size limit to prevent memory issues
    private val cache = object : LinkedHashMap<String, List<ApiSong>>(MAX_CACHE_SIZE, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<ApiSong>>): Boolean {
            return size > MAX_CACHE_SIZE
        }
    }

    // Lazy-loaded map of audio path → backend song ID for lyrics lookup
    private var songIdMap: Map<String, String>? = null

    /**
     * Fetch playlist songs from API
     */
    suspend fun fetchPlaylist(playlistId: String): Result<List<ApiSong>> = withContext(Dispatchers.IO) {
        // Check cache first
        cache[playlistId]?.let { return@withContext Result.success(it) }

        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/public/playlist/$playlistId")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val songs = parsePlaylistResponse(response)
                cache[playlistId] = songs
                Result.success(songs)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parsePlaylistResponse(json: String): List<ApiSong> {
        val songs = mutableListOf<ApiSong>()
        try {
            // API returns an object with "songs" array and metadata
            val rootObject = JSONObject(json)
            val playlistName = rootObject.optString("name").takeIf { it.isNotEmpty() }
            val jsonArray = rootObject.optJSONArray("songs") ?: JSONArray()

            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                songs.add(
                    ApiSong(
                        playlistName = playlistName,
                        title = obj.optString("title", "Unknown"),
                        originalArtists = obj.optString("originalArtists"),
                        coverArtists = obj.optString("coverArtists"),
                        coverArt = obj.optString("coverArt"),
                        audioUrl = obj.optString("audioUrl"),
                        artCredit = obj.optString("artCredit")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return songs
    }

    /**
     * Fetch playlist info (name, cover) from API
     */
    suspend fun fetchPlaylistInfo(playlistId: String): Result<ApiPlaylistInfo> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/public/playlist/$playlistId")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val rootObject = JSONObject(response)

                // Handle cover URL - prepend base URL if it's a relative path
                val rawCoverUrl = rootObject.optString("cover", "")
                val coverUrl = when {
                    rawCoverUrl.isEmpty() -> ""
                    rawCoverUrl.startsWith("http") -> rawCoverUrl
                    rawCoverUrl.startsWith("/") -> "https://storage.neurokaraoke.com$rawCoverUrl"
                    else -> "https://storage.neurokaraoke.com/$rawCoverUrl"
                }

                // Extract first 4 unique song covers for preview grid
                val previewCovers = mutableListOf<String>()
                val songsArray = rootObject.optJSONArray("songs")
                if (songsArray != null) {
                    for (i in 0 until minOf(songsArray.length(), 20)) {
                        if (previewCovers.size >= 4) break
                        val songObj = songsArray.getJSONObject(i)

                        // Try coverArt first, then derive from audioUrl
                        var coverArtUrl = songObj.optString("coverArt", "")
                        if (coverArtUrl.isBlank()) {
                            val audioUrl = songObj.optString("audioUrl", "")
                            if (audioUrl.isNotBlank()) {
                                coverArtUrl = audioUrl
                                    .replace("/audio/", "/images/")
                                    .replace(Regex("\\.v\\d+\\)?\\.mp3$"), ".jpg")
                                    .replace(".mp3", ".jpg")
                            }
                        }

                        if (coverArtUrl.isNotBlank() && coverArtUrl !in previewCovers) {
                            previewCovers.add(coverArtUrl)
                        }
                    }
                }

                val info = ApiPlaylistInfo(
                    id = playlistId,
                    name = rootObject.optString("name", "Unknown Playlist"),
                    coverUrl = coverUrl,
                    previewCovers = previewCovers,
                    songCount = songsArray?.length() ?: 0
                )
                Result.success(info)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fetch all official setlists in a single API call.
     * Returns playlists sorted by setListDate (newest first).
     */
    suspend fun fetchOfficialSetlists(): Result<List<Playlist>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/playlists?startIndex=0&pageSize=200&isSetlist=True&year=0")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val playlists = parseOfficialSetlistsResponse(response)
                Result.success(playlists)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseOfficialSetlistsResponse(json: String): List<Playlist> {
        val entries = mutableListOf<Pair<String, Playlist>>() // setListDate to Playlist
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // Parse mosaic covers from mosaicMedia array
                val previewCovers = mutableListOf<String>()
                val mosaicArray = obj.optJSONArray("mosaicMedia")
                if (mosaicArray != null) {
                    for (j in 0 until minOf(mosaicArray.length(), 4)) {
                        val mosaicObj = mosaicArray.getJSONObject(j)
                        val cloudflareId = mosaicObj.optString("cloudflareId", "")
                        val absolutePath = mosaicObj.optString("absolutePath", "")
                        val mosaicUrl = when {
                            cloudflareId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
                            absolutePath.isNotBlank() -> absolutePath
                            else -> null
                        }
                        mosaicUrl?.let { previewCovers.add(it) }
                    }
                }

                // Parse cover URL from media object
                val mediaObj = obj.optJSONObject("media")
                val coverUrl = mediaObj?.let { media ->
                    val cloudflareId = media.optString("cloudflareId", "")
                    val absolutePath = media.optString("absolutePath", "")
                    when {
                        cloudflareId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
                        absolutePath.isNotBlank() -> absolutePath
                        else -> ""
                    }
                } ?: ""

                val setListDate = obj.optString("setListDate", "")

                entries.add(
                    setListDate to Playlist(
                        id = obj.getString("id"),
                        title = obj.optString("name", "Unknown Playlist"),
                        coverUrl = coverUrl,
                        previewCovers = previewCovers,
                        songCount = obj.optInt("songCount", 0)
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        // Sort by setListDate descending (newest first)
        return entries.sortedByDescending { it.first }.map { it.second }
    }

    /**
     * Fetch all public playlists from API
     */
    suspend fun fetchPublicPlaylists(): Result<List<ApiPublicPlaylist>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/playlist/public")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val playlists = parsePublicPlaylistsResponse(response)
                Result.success(playlists)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parsePublicPlaylistsResponse(json: String): List<ApiPublicPlaylist> {
        val playlists = mutableListOf<ApiPublicPlaylist>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                // Parse cover URL from media object
                val mediaObj = obj.optJSONObject("media")
                val coverUrl = mediaObj?.let { media ->
                    val cloudflareId = media.optString("cloudflareId", "")
                    val absolutePath = media.optString("absolutePath", "")
                    when {
                        cloudflareId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
                        absolutePath.isNotBlank() -> absolutePath
                        else -> null
                    }
                }

                // Parse mosaic covers from mosaicMedia array
                val mosaicCovers = mutableListOf<String>()
                val mosaicArray = obj.optJSONArray("mosaicMedia")
                if (mosaicArray != null) {
                    for (j in 0 until minOf(mosaicArray.length(), 4)) {
                        val mosaicObj = mosaicArray.getJSONObject(j)
                        val cloudflareId = mosaicObj.optString("cloudflareId", "")
                        val absolutePath = mosaicObj.optString("absolutePath", "")
                        val mosaicUrl = when {
                            cloudflareId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
                            absolutePath.isNotBlank() -> absolutePath
                            else -> null
                        }
                        mosaicUrl?.let { mosaicCovers.add(it) }
                    }
                }

                playlists.add(
                    ApiPublicPlaylist(
                        id = obj.getString("id"),
                        name = obj.optString("name", "Unknown Playlist"),
                        description = obj.optString("description").takeIf { it.isNotEmpty() },
                        coverUrl = coverUrl,
                        mosaicCovers = mosaicCovers,
                        songCount = obj.optInt("songCount", 0),
                        createdBy = obj.optString("createdBy").takeIf { it.isNotEmpty() }
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return playlists
    }

    /**
     * Fetch cover distribution stats from API
     */
    suspend fun fetchCoverDistribution(): Result<CoverDistribution> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/stats/cover-distribution")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val obj = JSONObject(response)
                Result.success(
                    CoverDistribution(
                        totalSongs = obj.getInt("totalSongs"),
                        neuroCount = obj.getInt("neuroCount"),
                        evilCount = obj.getInt("evilCount"),
                        duetCount = obj.getInt("duetCount"),
                        otherCount = obj.getInt("otherCount")
                    )
                )
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fetch all artists from API
     */
    suspend fun fetchArtists(): Result<List<Artist>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/artists")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val artists = parseArtistsResponse(response)
                Result.success(artists)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseArtistsResponse(json: String): List<Artist> {
        val artists = mutableListOf<Artist>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)

                val imagePath = obj.optString("imagePath", "")
                val imageUrl = when {
                    imagePath.isBlank() -> ""
                    imagePath.startsWith("http") -> imagePath
                    imagePath.startsWith("/") -> "https://storage.neurokaraoke.com$imagePath"
                    else -> "https://storage.neurokaraoke.com/$imagePath"
                }

                artists.add(
                    Artist(
                        id = obj.optString("id", ""),
                        name = obj.optString("name", "Unknown"),
                        imageUrl = imageUrl,
                        songCount = obj.optInt("songCount", 0),
                        summary = obj.optString("summary", "")
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return artists.sortedByDescending { it.songCount }
    }

    /**
     * Fetch trending songs from the explore API.
     * Returns songs sorted by play count (most played first).
     */
    suspend fun fetchTrendingSongs(days: Int = 7): Result<List<ApiSong>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/explore/trendings?days=$days")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val songs = mutableListOf<ApiSong>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)

                    // originalArtists and coverArtists are arrays
                    val originalArtists = buildList {
                        val arr = obj.optJSONArray("originalArtists")
                        if (arr != null) {
                            for (j in 0 until arr.length()) add(arr.optString(j, ""))
                        }
                    }.filter { it.isNotBlank() }.joinToString(", ")

                    val coverArtists = buildList {
                        val arr = obj.optJSONArray("coverArtists")
                        if (arr != null) {
                            for (j in 0 until arr.length()) add(arr.optString(j, ""))
                        }
                    }.filter { it.isNotBlank() }.joinToString(", ")

                    // Cover art image from nested coverArt object
                    val coverArtObj = obj.optJSONObject("coverArt")
                    val coverArtUrl = coverArtObj?.let { art ->
                        val cloudflareId = art.optString("cloudflareId", "")
                        val artAbsPath = art.optString("absolutePath", "")
                        when {
                            cloudflareId.isNotBlank() -> "https://images.neurokaraoke.com/WxURxyML82UkE7gY-PiBKw/$cloudflareId/public"
                            artAbsPath.isNotBlank() -> artAbsPath
                            else -> null
                        }
                    }

                    val artCredit = coverArtObj?.optString("credit", "")

                    // Audio URL from absolutePath
                    val audioPath = obj.optString("absolutePath", "")
                    val audioUrl = if (audioPath.isNotBlank()) {
                        "https://storage.neurokaraoke.com/$audioPath"
                    } else ""

                    songs.add(
                        ApiSong(
                            playlistName = null,
                            title = obj.optString("title", "Unknown"),
                            originalArtists = originalArtists.ifEmpty { "Unknown Artist" },
                            coverArtists = coverArtists.ifEmpty { null },
                            coverArt = coverArtUrl,
                            audioUrl = audioUrl,
                            artCredit = artCredit
                        )
                    )
                }

                Result.success(songs)
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Find a song in cached playlist by title
     */
    fun findSong(playlistId: String, title: String, artist: String? = null): ApiSong? {
        val songs = cache[playlistId] ?: return null

        val normalize = { value: String? ->
            value?.lowercase()
                ?.replace(Regex("[\\u0300-\\u036f]"), "")
                ?.replace(Regex("['\".,!?()\\[\\]{}:;/-]"), " ")
                ?.replace(Regex("\\s+"), " ")
                ?.trim()
                ?: ""
        }

        val titleNorm = normalize(title)
        val artistNorm = normalize(artist)

        var bestSong: ApiSong? = null
        var bestScore = 0

        for (song in songs) {
            val songTitleNorm = normalize(song.title)
            val coverArtistsNorm = normalize(song.coverArtists)
            val originalArtistsNorm = normalize(song.originalArtists)

            var score = 0
            if (song.title.equals(title, ignoreCase = true)) score += 3
            if (songTitleNorm == titleNorm) score += 2
            if (songTitleNorm.contains(titleNorm) || titleNorm.contains(songTitleNorm)) score += 1

            if (artistNorm.isNotEmpty()) {
                if (coverArtistsNorm.contains(artistNorm)) score += 2
                if (originalArtistsNorm.contains(artistNorm)) score += 2
            } else {
                score += 1
            }

            if (score > bestScore) {
                bestScore = score
                bestSong = song
            }
        }

        return if (bestScore > 0) bestSong else null
    }

    /**
     * Ensure the song ID map is built. Safe to call multiple times.
     */
    suspend fun ensureSongIdMap() {
        if (songIdMap == null) {
            buildSongIdMap()
        }
    }

    /**
     * Find the backend song ID for a given audio URL.
     * Builds the lookup map lazily from setlists on first call.
     */
    suspend fun findSongIdByAudioUrl(audioUrl: String): String? {
        ensureSongIdMap()
        // Extract the path portion from the audioUrl
        // audioUrl: "https://storage.neurokaraoke.com/audio/X.mp3"
        // absolutePath in API: "audio/X.mp3"
        val path = audioUrl
            .removePrefix("https://storage.neurokaraoke.com/")
            .removePrefix("http://storage.neurokaraoke.com/")
        return songIdMap?.get(path)
    }

    /**
     * Build the audioPath → songId lookup map from setlists.
     */
    private suspend fun buildSongIdMap() = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/playlists?startIndex=0&pageSize=200&isSetlist=True&year=0")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            if (connection.responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val map = mutableMapOf<String, String>()

                for (i in 0 until jsonArray.length()) {
                    val playlist = jsonArray.getJSONObject(i)
                    val songs = playlist.optJSONArray("songListDTOs") ?: continue
                    for (j in 0 until songs.length()) {
                        val song = songs.getJSONObject(j)
                        val songId = song.optString("id", "")
                        val absolutePath = song.optString("absolutePath", "")
                        if (songId.isNotBlank() && absolutePath.isNotBlank()) {
                            map[absolutePath] = songId
                        }
                    }
                }
                songIdMap = map
            } else {
                songIdMap = emptyMap()
            }
        } catch (e: Exception) {
            e.printStackTrace()
            songIdMap = emptyMap()
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Fetch lyrics from the NeuroKaraoke API for a given song ID.
     * Returns synced lyrics as a list of LyricLine objects.
     * The API returns: [{"time":"00:00:05.3700000","text":"lyric line"}, ...]
     */
    suspend fun fetchSongLyrics(songId: String): Result<List<LyricLine>> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/songs/$songId/lyrics")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val jsonArray = JSONArray(response)
                val lines = mutableListOf<LyricLine>()

                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    val timeStr = obj.optString("time", "")
                    val text = obj.optString("text", "")
                    val timestamp = parseTimeSpan(timeStr)
                    lines.add(LyricLine(timestamp, text.trim()))
                }

                Result.success(lines.sortedBy { it.timestamp })
            } else {
                Result.failure(Exception("HTTP $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Parse a .NET TimeSpan string (HH:mm:ss.fffffff) into milliseconds.
     * Examples: "00:00:05.3700000" → 5370, "00:01:30" → 90000
     */
    private fun parseTimeSpan(timeSpan: String): Long {
        try {
            val parts = timeSpan.split(":")
            if (parts.size < 3) return 0L

            val hours = parts[0].toLongOrNull() ?: 0L
            val minutes = parts[1].toLongOrNull() ?: 0L
            val secParts = parts[2].split(".")
            val seconds = secParts[0].toLongOrNull() ?: 0L
            val fractional = if (secParts.size > 1) {
                // Pad or truncate to 3 digits for milliseconds
                val frac = secParts[1].take(3).padEnd(3, '0')
                frac.toLongOrNull() ?: 0L
            } else 0L

            return hours * 3600000 + minutes * 60000 + seconds * 1000 + fractional
        } catch (_: Exception) {
            return 0L
        }
    }
}
