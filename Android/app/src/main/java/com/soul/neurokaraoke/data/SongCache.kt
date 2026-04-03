package com.soul.neurokaraoke.data

import android.content.Context
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Caches all songs locally for faster loading.
 * Songs are stored as JSON in internal storage.
 */
class SongCache(private val context: Context) {

    private val fileName = "songs_cache.json"
    private val prefsName = "setup_prefs"
    private val setupCompleteKey = "setup_complete"
    private val cacheVersionKey = "cache_version"
    private val playlistCountKey = "cached_playlist_count"
    private val currentCacheVersion = 3

    private val file: File
        get() = File(context.filesDir, fileName)

    private val prefs by lazy {
        context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
    }

    /**
     * Check if first-time setup is needed
     */
    fun isSetupComplete(): Boolean {
        val isComplete = prefs.getBoolean(setupCompleteKey, false)
        val cachedVersion = prefs.getInt(cacheVersionKey, 0)
        return isComplete && cachedVersion == currentCacheVersion && file.exists()
    }

    /**
     * Mark setup as complete
     */
    fun markSetupComplete() {
        prefs.edit()
            .putBoolean(setupCompleteKey, true)
            .putInt(cacheVersionKey, currentCacheVersion)
            .apply()
    }

    /**
     * Save all songs to cache
     */
    suspend fun cacheSongs(songs: List<Song>, playlistCount: Int = 0) = withContext(Dispatchers.IO) {
        try {
            val jsonArray = JSONArray()

            songs.forEach { song ->
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("coverUrl", song.coverUrl)
                    put("audioUrl", song.audioUrl)
                    put("duration", song.duration)
                    put("singer", song.singer.name)
                    put("artCredit", song.artCredit ?: "")
                    put("titleRomaji", song.titleRomaji)
                    put("titleEnglish", song.titleEnglish ?: "")
                    put("artistRomaji", song.artistRomaji)
                }
                jsonArray.put(obj)
            }

            val root = JSONObject().apply {
                put("songs", jsonArray)
                put("cachedAt", System.currentTimeMillis())
                put("count", songs.size)
            }

            file.writeText(root.toString())

            // Save playlist count so we can detect new setlists
            if (playlistCount > 0) {
                prefs.edit().putInt(playlistCountKey, playlistCount).apply()
            }

            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if cache is stale.
     * Stale when: new playlists added, or cache older than 6 hours.
     */
    fun isCacheStale(currentPlaylistCount: Int): Boolean {
        if (currentPlaylistCount <= 0) return false
        val cachedCount = prefs.getInt(playlistCountKey, 0)
        if (cachedCount in 1 until currentPlaylistCount) return true

        // Time-based expiry: refresh every 6 hours
        if (!file.exists()) return true
        try {
            val json = file.readText()
            val root = JSONObject(json)
            val cachedAt = root.optLong("cachedAt", 0)
            if (cachedAt > 0) {
                val ageMs = System.currentTimeMillis() - cachedAt
                if (ageMs > 6 * 60 * 60 * 1000) return true
            }
        } catch (_: Exception) {
            return true
        }

        return false
    }

    /**
     * Load all songs from cache
     */
    suspend fun getCachedSongs(): List<Song> = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext emptyList()

        try {
            val json = file.readText()
            val root = JSONObject(json)
            val array = root.optJSONArray("songs") ?: return@withContext emptyList()

            val songs = mutableListOf<Song>()
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val singer = try {
                    Singer.valueOf(obj.optString("singer", "NEURO"))
                } catch (e: Exception) {
                    Singer.NEURO
                }

                val title = obj.optString("title", "Unknown")
                val artist = obj.optString("artist", "Unknown")

                songs.add(
                    Song(
                        id = obj.getString("id"),
                        title = title,
                        artist = artist,
                        coverUrl = obj.optString("coverUrl", ""),
                        audioUrl = obj.optString("audioUrl", ""),
                        duration = obj.optLong("duration", 0L),
                        singer = singer,
                        artCredit = obj.optString("artCredit", "").takeIf { it.isNotBlank() },
                        titleRomaji = obj.optString("titleRomaji", ""),
                        titleEnglish = obj.optString("titleEnglish", "").takeIf { it.isNotBlank() },
                        artistRomaji = obj.optString("artistRomaji", "")
                    )
                )
            }
            songs
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    /**
     * Get cache info
     */
    suspend fun getCacheInfo(): CacheInfo = withContext(Dispatchers.IO) {
        if (!file.exists()) return@withContext CacheInfo(0, 0, 0)

        try {
            val json = file.readText()
            val root = JSONObject(json)
            CacheInfo(
                songCount = root.optInt("count", 0),
                cachedAt = root.optLong("cachedAt", 0),
                fileSizeBytes = file.length()
            )
        } catch (e: Exception) {
            CacheInfo(0, 0, 0)
        }
    }

    /**
     * Clear cache and reset setup
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        file.delete()
        prefs.edit()
            .putBoolean(setupCompleteKey, false)
            .apply()
    }

    data class CacheInfo(
        val songCount: Int,
        val cachedAt: Long,
        val fileSizeBytes: Long
    )
}
