package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.SyncApi
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val syncApi = SyncApi()
    private val karaokeApi = NeuroKaraokeApi()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadFavorites()
    }

    private fun loadFavorites() {
        val json = prefs.getString(KEY_FAVORITES, null)
        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                val songs = mutableListOf<Song>()
                for (i in 0 until jsonArray.length()) {
                    songs.add(parseSong(jsonArray.getJSONObject(i)))
                }
                _favorites.value = songs
            } catch (e: Exception) {
                e.printStackTrace()
                _favorites.value = emptyList()
            }
        }
    }

    private fun saveFavorites() {
        val jsonArray = JSONArray()
        for (song in _favorites.value) {
            jsonArray.put(songToJson(song))
        }
        prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
    }

    fun toggleFavorite(song: Song, accessToken: String? = null) {
        val current = _favorites.value.toMutableList()
        val existing = current.indexOfFirst { it.id == song.id || it.matchesByAudioUrl(song) }
        val isRemoving = existing >= 0
        if (isRemoving) {
            current.removeAt(existing)
        } else {
            current.add(0, song)
        }
        _favorites.value = current
        saveFavorites()

        // Sync to server in background if logged in
        if (accessToken != null && song.id.isNotBlank()) {
            syncScope.launch {
                // Song ID should be a server UUID now; if it's still a hash fallback, resolve it
                val serverSongId = if (song.id.contains("-")) {
                    song.id // Already a UUID
                } else if (song.audioUrl.isNotBlank()) {
                    karaokeApi.findSongIdByAudioUrl(song.audioUrl)
                } else null

                if (serverSongId.isNullOrBlank()) {
                    Log.w("FavoritesRepo", "Could not resolve server song ID for: ${song.title}")
                    return@launch
                }

                Log.d("FavoritesRepo", "Syncing favorite: ${song.title} (serverId=$serverSongId, removing=$isRemoving)")
                if (isRemoving) {
                    syncApi.removeFavorite(accessToken, serverSongId)
                } else {
                    syncApi.addFavorite(accessToken, serverSongId)
                }
            }
        }
    }

    fun isFavorite(songId: String, audioUrl: String = ""): Boolean {
        return _favorites.value.any {
            it.id == songId || (audioUrl.isNotBlank() && it.matchesByAudioUrl(audioUrl))
        }
    }

    /** Match by normalized audioUrl as fallback for ID mismatches */
    private fun Song.matchesByAudioUrl(other: Song): Boolean {
        return audioUrl.isNotBlank() && other.audioUrl.isNotBlank() &&
                normalizeAudioUrl(audioUrl) == normalizeAudioUrl(other.audioUrl)
    }

    private fun Song.matchesByAudioUrl(otherUrl: String): Boolean {
        return audioUrl.isNotBlank() && otherUrl.isNotBlank() &&
                normalizeAudioUrl(audioUrl) == normalizeAudioUrl(otherUrl)
    }

    private fun normalizeAudioUrl(url: String): String {
        return url
            .removePrefix("https://storage.neurokaraoke.com/")
            .removePrefix("http://storage.neurokaraoke.com/")
    }

    /**
     * Sync favorites from the server. Merges server data with local data.
     * Server favorites take precedence for songs that exist on server.
     */
    suspend fun syncFromServer(accessToken: String) {
        _isSyncing.value = true
        try {
            syncApi.fetchFavorites(accessToken).onSuccess { serverSongs ->
                if (serverSongs.isNotEmpty()) {
                    // Merge: keep server songs + any local-only songs (deduplicate by audioUrl)
                    val serverAudioUrls = serverSongs.map { normalizeAudioUrl(it.audioUrl) }.filter { it.isNotBlank() }.toSet()
                    val localOnly = _favorites.value.filter { local ->
                        val localUrl = normalizeAudioUrl(local.audioUrl)
                        localUrl.isBlank() || localUrl !in serverAudioUrls
                    }
                    _favorites.value = serverSongs + localOnly
                    saveFavorites()
                    Log.d("FavoritesRepo", "Synced ${serverSongs.size} favorites from server")
                } else {
                    Log.d("FavoritesRepo", "Server returned empty favorites")
                }
            }.onFailure { e ->
                Log.e("FavoritesRepo", "Sync favorites failed: ${e.message}")
            }
        } finally {
            _isSyncing.value = false
        }
    }

    private fun parseSong(json: JSONObject): Song {
        return Song(
            id = json.getString("id"),
            title = json.getString("title"),
            artist = json.getString("artist"),
            coverUrl = json.optString("coverUrl", ""),
            audioUrl = json.optString("audioUrl", ""),
            duration = json.optLong("duration", 0L),
            singer = try {
                Singer.valueOf(json.optString("singer", "NEURO"))
            } catch (e: Exception) {
                Singer.NEURO
            }
        )
    }

    private fun songToJson(song: Song): JSONObject {
        val json = JSONObject()
        json.put("id", song.id)
        json.put("title", song.title)
        json.put("artist", song.artist)
        json.put("coverUrl", song.coverUrl)
        json.put("audioUrl", song.audioUrl)
        json.put("duration", song.duration)
        json.put("singer", song.singer.name)
        return json
    }

    companion object {
        private const val PREFS_NAME = "neurokaraoke_favorites"
        private const val KEY_FAVORITES = "favorites"
    }
}
