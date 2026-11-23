package com.soul.neurokaraoke.data.api

import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject

data class RadioSong(
    val id: String,
    val title: String,
    val originalArtists: List<String>,
    val coverArtists: List<String>,
    val duration: Int, // seconds
    val coverUrl: String,
    val artCredit: String?,
    val audioPath: String = ""
) {
    /**
     * Now-playing display entry. The id is prefixed and the audio is the live stream,
     * so it must never be stored as a real song — use [toCatalogSong] for that.
     */
    fun toSong(): Song {
        val coverArtistText = coverArtists.joinToString(", ")

        return Song(
            id = "radio_$id",
            title = title,
            artist = originalArtists.joinToString(", ").ifEmpty { "Unknown" },
            coverUrl = coverUrl,
            audioUrl = RadioApi.STREAM_URL,
            duration = duration.toLong() * 1000,
            singer = Singer.fromCoverArtists(coverArtistText),
            coverArtists = coverArtistText,
            artCredit = artCredit
        )
    }

    /** The actual catalog song (real id + its own audio file) for favorites, downloads, playlists. */
    fun toCatalogSong(): Song = toSong().copy(
        id = id,
        // The radio sends a raw path ("audio/LADY BUG.mp3"); catalog URLs are percent-encoded
        audioUrl = NeuroKaraokeApi.resolveAudioUrl(encodePath(audioPath)) ?: ""
    )

    private fun encodePath(path: String): String =
        if (path.startsWith("http")) path
        else path.split("/").joinToString("/") { segment ->
            java.net.URLEncoder.encode(segment, "UTF-8").replace("+", "%20")
        }

    val coverArtistDisplay: String
        get() = coverArtists.joinToString(", ").ifBlank { "Neuro-sama" }
}

data class RadioState(
    val current: RadioSong?,
    val upcoming: List<RadioSong>,
    val history: List<RadioSong>,
    val listenerCount: Int,
    val offline: Boolean,
    val currentProgress: Int = 0,
    val totalDuration: Int = 0
)

class RadioApi {
    companion object {
        const val STREAM_URL = "https://radio.twinskaraoke.com/listen/neuro_21/radio.mp3"
        private const val STATE_URL = "https://socket.neurokaraoke.com/api/radio/current-state"
        private const val STATIC_NOWPLAYING_URL = "https://radio.twinskaraoke.com/api/nowplaying_static/neuro_21.json"
        private const val IMAGE_BASE = "https://images.neurokaraoke.com"
        private const val IMAGE_ACCOUNT = "WxURxyML82UkE7gY-PiBKw"
        private const val CACHE_TTL_MS = 5_000L

        private val fetchMutex = Mutex()
        @Volatile
        private var cachedState: RadioState? = null
        @Volatile
        private var cachedAtMs: Long = 0L
    }

    suspend fun fetchCurrentState(forceRefresh: Boolean = false): Result<RadioState> = withContext(Dispatchers.IO) {
        val now = System.currentTimeMillis()
        if (!forceRefresh) {
            val cached = cachedState
            if (cached != null && now - cachedAtMs < CACHE_TTL_MS) {
                return@withContext Result.success(cached)
            }
        }

        fetchMutex.withLock {
            val recheckNow = System.currentTimeMillis()
            if (!forceRefresh) {
                val cached = cachedState
                if (cached != null && recheckNow - cachedAtMs < CACHE_TTL_MS) {
                    return@withLock Result.success(cached)
                }
            }

            try {
                val merged = coroutineScope {
                    val stateDeferred = async {
                        val req = Request.Builder().url(STATE_URL).build()
                        HttpClient.shared.newCall(req).execute().use { resp ->
                            if (!resp.isSuccessful) throw Exception("HTTP ${resp.code}")
                            val body = resp.body?.string() ?: throw Exception("Empty response")
                            parseRadioState(JSONObject(body))
                        }
                    }
                    val staticDeferred = async {
                        try {
                            val req = Request.Builder().url(STATIC_NOWPLAYING_URL).build()
                            HttpClient.shared.newCall(req).execute().use { resp ->
                                if (!resp.isSuccessful) return@use 0 to 0
                                val body = resp.body?.string() ?: return@use 0 to 0
                                val nowPlaying = JSONObject(body).optJSONObject("now_playing")
                                if (nowPlaying != null) {
                                    nowPlaying.optInt("elapsed", 0) to nowPlaying.optInt("duration", 0)
                                } else {
                                    0 to 0
                                }
                            }
                        } catch (_: Exception) {
                            0 to 0
                        }
                    }

                    val state = stateDeferred.await()
                    val (progress, duration) = staticDeferred.await()
                    state.copy(
                        currentProgress = progress,
                        totalDuration = duration
                    )
                }

                cachedState = merged
                cachedAtMs = System.currentTimeMillis()
                Result.success(merged)
            } catch (e: Exception) {
                Result.failure(e)
            }
        }
    }

    private fun parseRadioState(json: JSONObject): RadioState {
        return RadioState(
            current = json.optJSONObject("current")?.let { parseSong(it) },
            upcoming = parseSongList(json.optJSONArray("upcoming")),
            history = parseSongList(json.optJSONArray("history")),
            listenerCount = json.optInt("listenerCount", 0),
            offline = json.optBoolean("offline", false),
            currentProgress = json.optInt("currentProgress", 0)
        )
    }

    private fun parseSongList(arr: JSONArray?): List<RadioSong> {
        if (arr == null) return emptyList()
        return (0 until arr.length()).mapNotNull { i ->
            try {
                parseSong(arr.getJSONObject(i))
            } catch (e: Exception) {
                null
            }
        }
    }

    private fun parseSong(obj: JSONObject): RadioSong {
        val coverArtObj = obj.optJSONObject("coverArt")
        val cloudflareId = coverArtObj?.optString("cloudflareId", "") ?: ""
        val coverUrl = if (cloudflareId.isNotBlank()) {
            "$IMAGE_BASE/$IMAGE_ACCOUNT/$cloudflareId/public"
        } else ""

        val artCredit = coverArtObj?.let(::parseArtCredit)

        val originalArtists = buildList {
            val arr = obj.optJSONArray("originalArtists")
            if (arr != null) {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }

        val coverArtists = buildList {
            val arr = obj.optJSONArray("coverArtists")
            if (arr != null) {
                for (i in 0 until arr.length()) add(arr.getString(i))
            }
        }

        return RadioSong(
            id = obj.optString("id", ""),
            title = obj.optString("title", "Unknown"),
            originalArtists = originalArtists,
            coverArtists = coverArtists,
            duration = obj.optInt("duration", 0),
            coverUrl = coverUrl,
            artCredit = artCredit,
            audioPath = obj.optString("absolutePath", "")
        )
    }

    private fun parseArtCredit(coverArt: JSONObject): String? {
        coverArt.optString("credit", "")
            .cleanApiText()
            ?.let { return it }

        val artist = coverArt.optJSONObject("artist") ?: return null
        val name = artist.optString("name", "").cleanApiText() ?: return null
        val socialLink = artist.optString("socialLink", "").cleanApiText()
        return if (socialLink != null) "Art by $name - $socialLink" else "Art by $name"
    }

    private fun String.cleanApiText(): String? =
        trim().takeIf { it.isNotEmpty() && !it.equals("null", ignoreCase = true) }
}
