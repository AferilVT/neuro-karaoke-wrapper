package com.soul.neurokaraoke.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File

/**
 * Caches lyrics locally for offline access and faster loading.
 * Lyrics are cached on-demand as users view them.
 */
class LyricsCache(private val context: Context) {

    private val cacheDir: File
        get() = File(context.filesDir, "lyrics_cache").also { it.mkdirs() }

    /**
     * Get cached lyrics for a song
     * Returns null if not cached
     */
    suspend fun getCachedLyrics(songTitle: String, artistName: String): CachedLyrics? = withContext(Dispatchers.IO) {
        val file = getLyricsFile(songTitle, artistName)
        if (!file.exists()) return@withContext null

        try {
            val json = file.readText()
            val obj = JSONObject(json)
            CachedLyrics(
                syncedLyrics = obj.optString("syncedLyrics", "").takeIf { it.isNotBlank() },
                plainLyrics = obj.optString("plainLyrics", "").takeIf { it.isNotBlank() },
                cachedAt = obj.optLong("cachedAt", 0),
                source = obj.optString("source", "lrclib")
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Cache lyrics for a song
     */
    suspend fun cacheLyrics(
        songTitle: String,
        artistName: String,
        syncedLyrics: String?,
        plainLyrics: String?,
        source: String = "lrclib"
    ) = withContext(Dispatchers.IO) {
        try {
            val file = getLyricsFile(songTitle, artistName)
            val obj = JSONObject().apply {
                put("syncedLyrics", syncedLyrics ?: "")
                put("plainLyrics", plainLyrics ?: "")
                put("cachedAt", System.currentTimeMillis())
                put("songTitle", songTitle)
                put("artistName", artistName)
                put("source", source)
            }
            file.writeText(obj.toString())
            true
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    /**
     * Check if lyrics are cached
     */
    suspend fun hasCache(songTitle: String, artistName: String): Boolean = withContext(Dispatchers.IO) {
        getLyricsFile(songTitle, artistName).exists()
    }

    /**
     * Get cache statistics
     */
    suspend fun getCacheStats(): CacheStats = withContext(Dispatchers.IO) {
        val files = cacheDir.listFiles() ?: emptyArray()
        val totalSize = files.sumOf { it.length() }
        CacheStats(
            cachedSongs = files.size,
            totalSizeBytes = totalSize
        )
    }

    /**
     * Clear all cached lyrics
     */
    suspend fun clearCache() = withContext(Dispatchers.IO) {
        cacheDir.listFiles()?.forEach { it.delete() }
    }

    private fun getLyricsFile(songTitle: String, artistName: String): File {
        // Create a safe filename from song title and artist
        val safeName = "${songTitle}_${artistName}"
            .replace(Regex("[^a-zA-Z0-9]"), "_")
            .take(100) // Limit filename length
        return File(cacheDir, "$safeName.json")
    }

    data class CachedLyrics(
        val syncedLyrics: String?,
        val plainLyrics: String?,
        val cachedAt: Long,
        val source: String = "lrclib" // "neurokaraoke" or "lrclib"
    )

    data class CacheStats(
        val cachedSongs: Int,
        val totalSizeBytes: Long
    )
}
