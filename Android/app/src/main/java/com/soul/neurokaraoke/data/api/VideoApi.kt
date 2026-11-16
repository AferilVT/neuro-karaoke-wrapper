package com.soul.neurokaraoke.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

/**
 * A video from the NeuroKaraoke gallery (`GET /api/videos`). Playback uses the Bunny
 * HLS stream derived from [cloudflareId]; the API's own `url` field is a Bunny embed
 * (WebView) which we ignore in favour of native ExoPlayer.
 */
data class Video(
    val id: String,
    val name: String,
    val description: String,
    val cloudflareId: String,
    val thumbnailUrl: String,
    val category: Int,
    val views: Int,
    val upvotes: Int,
    val songId: String?,
    val songTitle: String?,
    val createdBy: String?,
    val creatorAvatarUrl: String?,
) {
    /**
     * Bunny HLS stream. Derived from [thumbnailUrl] (which every video has —
     * `https://<host>/<guid>/thumbnail*.jpg`) rather than [cloudflareId], because
     * cloudflareId is null for Karaoke videos. `playlist.m3u8` serves both categories;
     * the API's own `play.mp4`/embed `url` is unreliable (stale/404).
     */
    val hlsUrl: String
        get() = if (thumbnailUrl.isNotBlank()) {
            thumbnailUrl.substringBeforeLast('/') + "/playlist.m3u8"
        } else {
            "https://vz-26de8a11-dde.b-cdn.net/$cloudflareId/playlist.m3u8"
        }
}

data class VideoPage(val items: List<Video>, val totalCount: Int)

/** Client-side category names/order — the API only exposes numeric ids. */
object VideoCategories {
    // Display order: Watchalongs (2) first, then Karaoke Videos (0). Category 1 is empty.
    val ORDER = listOf(2, 0)

    fun label(id: Int): String = when (id) {
        2 -> "Watchalongs"
        0 -> "Karaoke Videos"
        else -> "Videos"
    }
}

class VideoApi {
    companion object {
        private const val API_URL = "https://api.neurokaraoke.com"
    }

    fun parseVideoPage(json: String): VideoPage {
        val root = JSONObject(json)
        val totalCount = root.optInt("totalCount", 0)
        val arr = root.optJSONArray("items")
        val items = mutableListOf<Video>()
        if (arr != null) {
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                items.add(
                    Video(
                        id = o.optString("id", ""),
                        name = o.optString("name", ""),
                        description = o.optString("description", ""),
                        cloudflareId = o.optString("cloudflareId", ""),
                        thumbnailUrl = o.optString("thumbnailUrl", ""),
                        category = o.optInt("category", 0),
                        views = o.optInt("views", 0),
                        upvotes = o.optInt("upvotes", 0),
                        songId = o.nullableString("songId"),
                        songTitle = o.nullableString("songTitle"),
                        createdBy = o.nullableString("createdBy"),
                        creatorAvatarUrl = o.nullableString("creatorAvatarUrl"),
                    )
                )
            }
        }
        return VideoPage(items, totalCount)
    }

    suspend fun fetchVideos(
        category: Int,
        startIndex: Int,
        pageSize: Int,
    ): Result<VideoPage> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$API_URL/api/videos?category=$category&startIndex=$startIndex&pageSize=$pageSize")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 15000

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                Result.success(parseVideoPage(response))
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }
}

/** JSON `null` (or missing) → Kotlin null; otherwise the string value. */
private fun JSONObject.nullableString(key: String): String? =
    if (isNull(key)) null else optString(key, "").ifEmpty { null }
