package com.soul.neurokaraoke.data.api

import com.soul.neurokaraoke.data.model.Badge
import com.soul.neurokaraoke.data.model.Profile
import com.soul.neurokaraoke.data.model.ProfileResponse
import com.soul.neurokaraoke.data.model.UploadLimits
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val API_BASE = "https://api.neurokaraoke.com"

object ProfileApi {

    suspend fun fetchBadgeProfile(token: String): Result<ProfileResponse> =
        withContext(Dispatchers.IO) {
            fetch("/api/badge/profile", token) { body ->
                val json = JSONObject(body)
                val profileJson = json.getJSONObject("profile")
                val profile = Profile(
                    displayName = profileJson.optString("displayName", ""),
                    avatarUrl = profileJson.optString("avatarUrl").takeIf { it.isNotEmpty() },
                    level = profileJson.optInt("level").takeIf { it > 0 },
                    levelTitle = if (!profileJson.isNull("levelTitle")) profileJson.getString("levelTitle").takeIf { it.isNotEmpty() } else null,
                    totalXP = profileJson.optInt("totalXP").takeIf { it > 0 },
                    totalBadges = profileJson.optInt("totalBadges").takeIf { profileJson.has("totalBadges") },
                    unlockedBadges = profileJson.optInt("unlockedBadges").takeIf { profileJson.has("unlockedBadges") },
                    levelProgress = if (profileJson.has("levelProgress")) profileJson.getDouble("levelProgress") else null,
                    xpToNextLevel = profileJson.optInt("xpToNextLevel").takeIf { profileJson.has("xpToNextLevel") }
                )
                val badgesArray = json.optJSONArray("badges")
                val badges = buildList {
                    if (badgesArray != null) {
                        for (i in 0 until badgesArray.length()) {
                            val b = badgesArray.getJSONObject(i)
                            val media = b.optJSONObject("media")
                            add(
                                Badge(
                                    id = b.optString("id", "$i"),
                                    name = b.optString("name", ""),
                                    description = b.optString("description").takeIf { it.isNotEmpty() },
                                    rarity = b.optInt("rarity", 0),
                                    unlocked = b.optBoolean("unlocked", false),
                                    currentProgress = b.optInt("currentProgress", 0),
                                    conditionValue = b.optInt("conditionValue", 0),
                                    absolutePath = media?.optString("absolutePath")?.takeIf { it.isNotEmpty() && it != "null" }
                                )
                            )
                        }
                    }
                }
                ProfileResponse(profile, badges)
            }
        }

    suspend fun fetchUploadLimits(token: String): Result<UploadLimits> =
        withContext(Dispatchers.IO) {
            fetch("/api/user/upload-limits", token) { body ->
                val j = JSONObject(body)
                UploadLimits(
                    maxSongs = j.optInt("maxSongs", 0),
                    maxStorageBytes = j.optLong("maxStorageBytes", 0),
                    usedStorageBytes = j.optLong("usedStorageBytes", 0),
                    currentSongCount = j.optInt("currentSongCount", 0),
                    currentPlaylistCount = j.optInt("currentPlaylistCount", 0),
                    playlistLimit = j.optInt("playlistLimit", 0),
                    songPerPlaylistLimit = j.optInt("songPerPlaylistLimit", 0)
                )
            }
        }

    private fun <T> fetch(path: String, token: String, parse: (String) -> T): Result<T> {
        return try {
            val conn = URL("$API_BASE$path").openConnection() as HttpURLConnection
            conn.connectTimeout = 15_000
            conn.readTimeout = 15_000
            conn.setRequestProperty("Authorization", "Bearer $token")
            val code = conn.responseCode
            if (code != HttpURLConnection.HTTP_OK) {
                conn.disconnect()
                return Result.failure(Exception("HTTP $code"))
            }
            val body = conn.inputStream.bufferedReader().readText()
            conn.disconnect()
            Result.success(parse(body))
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}
