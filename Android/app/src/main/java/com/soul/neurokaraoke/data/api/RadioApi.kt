package com.soul.neurokaraoke.data.api

import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class RadioSong(
    val id: String,
    val title: String,
    val originalArtists: List<String>,
    val coverArtists: List<String>,
    val duration: Int, // seconds
    val coverUrl: String,
    val artCredit: String?
) {
    fun toSong(): Song {
        val singer = when {
            coverArtists.any { it.contains("Evil", ignoreCase = true) } &&
            coverArtists.any { it.contains("Neuro", ignoreCase = true) } -> Singer.DUET
            coverArtists.any { it.contains("Evil", ignoreCase = true) } -> Singer.EVIL
            coverArtists.any { it.contains("Neuro", ignoreCase = true) &&
                it.contains("Evil", ignoreCase = true) } -> Singer.DUET
            else -> Singer.NEURO
        }

        return Song(
            id = "radio_$id",
            title = title,
            artist = originalArtists.joinToString(", ").ifEmpty { "Unknown" },
            coverUrl = coverUrl,
            audioUrl = RadioApi.STREAM_URL,
            duration = duration.toLong() * 1000,
            singer = singer,
            artCredit = artCredit
        )
    }

    val coverArtistDisplay: String
        get() = when {
            coverArtists.any { it.contains("Evil", ignoreCase = true) } &&
            coverArtists.any { it.contains("Neuro", ignoreCase = true) } -> "Neuro & Evil"
            coverArtists.any { it.contains("Neuro", ignoreCase = true) &&
                it.contains("Evil", ignoreCase = true) } -> "Neuro & Evil"
            coverArtists.any { it.contains("Evil", ignoreCase = true) } -> "Evil Neuro"
            else -> "Neuro-sama"
        }
}

data class RadioState(
    val current: RadioSong?,
    val upcoming: List<RadioSong>,
    val history: List<RadioSong>,
    val listenerCount: Int,
    val offline: Boolean
)

class RadioApi {
    companion object {
        const val STREAM_URL = "https://radio.twinskaraoke.com/listen/neuro_21/radio.mp3"
        private const val STATE_URL = "https://socket.neurokaraoke.com/api/radio/current-state"
        private const val IMAGE_BASE = "https://images.neurokaraoke.com"
        private const val IMAGE_ACCOUNT = "WxURxyML82UkE7gY-PiBKw"
    }

    suspend fun fetchCurrentState(): Result<RadioState> = withContext(Dispatchers.IO) {
        try {
            val url = URL(STATE_URL)
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()
            val json = JSONObject(response)

            Result.success(parseRadioState(json))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun parseRadioState(json: JSONObject): RadioState {
        return RadioState(
            current = json.optJSONObject("current")?.let { parseSong(it) },
            upcoming = parseSongList(json.optJSONArray("upcoming")),
            history = parseSongList(json.optJSONArray("history")),
            listenerCount = json.optInt("listenerCount", 0),
            offline = json.optBoolean("offline", false)
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

        val artCredit = coverArtObj?.optString("credit", "")
            ?.takeIf { it.isNotBlank() }

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
            artCredit = artCredit
        )
    }
}
