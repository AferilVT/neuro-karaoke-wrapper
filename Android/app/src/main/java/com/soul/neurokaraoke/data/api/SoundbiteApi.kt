package com.soul.neurokaraoke.data.api

import com.soul.neurokaraoke.data.model.Soundbite
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

data class SoundbiteResponse(
    val items: List<Soundbite>,
    val totalCount: Int,
    val page: Int,
    val pageSize: Int
)

class SoundbiteApi {
    companion object {
        private const val BASE_URL = "https://api.neurokaraoke.com/api/soundbites"
    }

    suspend fun fetchSoundbites(
        page: Int = 1,
        pageSize: Int = 30,
        search: String? = null
    ): Result<SoundbiteResponse> = withContext(Dispatchers.IO) {
        try {
            val urlBuilder = StringBuilder("$BASE_URL?page=$page&pageSize=$pageSize")
            if (!search.isNullOrBlank()) {
                urlBuilder.append("&search=${URLEncoder.encode(search, "UTF-8")}")
            }

            val url = URL(urlBuilder.toString())
            val connection = url.openConnection() as HttpURLConnection
            connection.connectTimeout = 10_000
            connection.readTimeout = 10_000

            val response = connection.inputStream.bufferedReader().readText()
            connection.disconnect()

            val json = JSONObject(response)
            val items = mutableListOf<Soundbite>()
            val arr = json.getJSONArray("items")

            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                items.add(
                    Soundbite(
                        id = obj.getString("id"),
                        title = obj.optString("title", "Unknown"),
                        comments = if (obj.isNull("comments")) null else obj.optString("comments"),
                        duration = obj.optInt("duration", 0),
                        absolutePath = obj.optString("absolutePath", "").takeIf { it.isNotBlank() },
                        tag = obj.optInt("tag", 3),
                        audioUrl = obj.optString("audioUrl", ""),
                        uploadedAt = obj.optString("uploadedAt", "").takeIf { it.isNotBlank() },
                        uploadedBy = obj.optString("uploadedBy", "").takeIf { it.isNotBlank() },
                        imageUrl = obj.optString("imageUrl", "").takeIf { it.isNotBlank() },
                        embeddable = obj.optBoolean("embeddable", true),
                        playCount = obj.optInt("playCount", 0)
                    )
                )
            }

            Result.success(
                SoundbiteResponse(
                    items = items,
                    totalCount = json.optInt("totalCount", 0),
                    page = json.optInt("page", 1),
                    pageSize = json.optInt("pageSize", 30)
                )
            )
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
