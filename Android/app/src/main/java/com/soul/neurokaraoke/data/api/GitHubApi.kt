package com.soul.neurokaraoke.data.api

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class GitHubAsset(
    val name: String,
    val downloadUrl: String,
    val size: Long,
    val contentType: String
)

data class GitHubRelease(
    val tagName: String,
    val name: String,
    val body: String,
    val htmlUrl: String,
    val publishedAt: String,
    val isPrerelease: Boolean,
    val assets: List<GitHubAsset>
) {
    /**
     * Get the APK download URL if available, otherwise return the release page URL
     */
    fun getDownloadUrl(): String {
        val apkAsset = assets.find { it.name.endsWith(".apk") }
        return apkAsset?.downloadUrl ?: htmlUrl
    }

    /**
     * Get version string without 'v' prefix for comparison
     */
    fun getVersionString(): String {
        return tagName.removePrefix("v").removePrefix("V")
    }
}

class GitHubApi(
    private val owner: String,
    private val repo: String
) {
    companion object {
        private const val BASE_URL = "https://api.github.com"
    }

    /**
     * Fetch the latest non-prerelease from GitHub
     */
    suspend fun fetchLatestRelease(): Result<GitHubRelease> = withContext(Dispatchers.IO) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL("$BASE_URL/repos/$owner/$repo/releases")
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/vnd.github+json")
            connection.setRequestProperty("User-Agent", "NeuroKaraoke-Android")

            val responseCode = connection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = connection.inputStream.bufferedReader().use { it.readText() }
                val releases = parseReleasesResponse(response)

                // Find the first (newest) release, including prereleases
                val latest = releases.firstOrNull()

                if (latest != null) {
                    Result.success(latest)
                } else {
                    Result.failure(Exception("No releases found"))
                }
            } else {
                Result.failure(Exception("HTTP error: $responseCode"))
            }
        } catch (e: Exception) {
            Result.failure(e)
        } finally {
            connection?.disconnect()
        }
    }

    private fun parseReleasesResponse(json: String): List<GitHubRelease> {
        val releases = mutableListOf<GitHubRelease>()
        try {
            val jsonArray = JSONArray(json)
            for (i in 0 until jsonArray.length()) {
                val obj = jsonArray.getJSONObject(i)
                releases.add(parseRelease(obj))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return releases
    }

    private fun parseRelease(obj: JSONObject): GitHubRelease {
        val assets = mutableListOf<GitHubAsset>()
        val assetsArray = obj.optJSONArray("assets")
        if (assetsArray != null) {
            for (i in 0 until assetsArray.length()) {
                val assetObj = assetsArray.getJSONObject(i)
                assets.add(
                    GitHubAsset(
                        name = assetObj.optString("name", ""),
                        downloadUrl = assetObj.optString("browser_download_url", ""),
                        size = assetObj.optLong("size", 0),
                        contentType = assetObj.optString("content_type", "")
                    )
                )
            }
        }

        return GitHubRelease(
            tagName = obj.optString("tag_name", ""),
            name = obj.optString("name", ""),
            body = obj.optString("body", ""),
            htmlUrl = obj.optString("html_url", ""),
            publishedAt = obj.optString("published_at", ""),
            isPrerelease = obj.optBoolean("prerelease", false),
            assets = assets
        )
    }
}
