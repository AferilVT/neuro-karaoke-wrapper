package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.util.Log
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.SyncApi
import com.soul.neurokaraoke.data.model.Playlist
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
import java.util.UUID

class UserPlaylistRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val syncApi = SyncApi()
    private val karaokeApi = NeuroKaraokeApi()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    private val _playlists = MutableStateFlow<List<Playlist>>(emptyList())
    val playlists: StateFlow<List<Playlist>> = _playlists.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        loadPlaylists()
    }

    private fun loadPlaylists() {
        val json = prefs.getString(KEY_PLAYLISTS, null)
        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                val playlistList = mutableListOf<Playlist>()
                for (i in 0 until jsonArray.length()) {
                    val obj = jsonArray.getJSONObject(i)
                    playlistList.add(parsePlaylist(obj))
                }
                _playlists.value = playlistList
            } catch (e: Exception) {
                e.printStackTrace()
                _playlists.value = emptyList()
            }
        }
    }

    private fun savePlaylists() {
        val jsonArray = JSONArray()
        for (playlist in _playlists.value) {
            jsonArray.put(playlistToJson(playlist))
        }
        prefs.edit().putString(KEY_PLAYLISTS, jsonArray.toString()).apply()
    }

    private fun parsePlaylist(json: JSONObject): Playlist {
        val songsArray = json.optJSONArray("songs")
        val songs = mutableListOf<Song>()
        if (songsArray != null) {
            for (i in 0 until songsArray.length()) {
                val songObj = songsArray.getJSONObject(i)
                songs.add(parseSong(songObj))
            }
        }

        val previewCoversArray = json.optJSONArray("previewCovers")
        val previewCovers = mutableListOf<String>()
        if (previewCoversArray != null) {
            for (i in 0 until previewCoversArray.length()) {
                previewCovers.add(previewCoversArray.getString(i))
            }
        }

        return Playlist(
            id = json.getString("id"),
            title = json.getString("title"),
            description = json.optString("description", ""),
            coverUrl = json.optString("coverUrl", ""),
            previewCovers = previewCovers,
            songs = songs,
            isPublic = json.optBoolean("isPublic", false),
            isNew = false
        )
    }

    private fun parseSong(json: JSONObject): Song {
        return Song(
            id = json.getString("id"),
            title = json.getString("title"),
            artist = json.getString("artist"),
            coverUrl = json.optString("coverUrl", ""),
            audioUrl = json.optString("audioUrl", ""),
            duration = json.optLong("duration", 0L)
        )
    }

    private fun playlistToJson(playlist: Playlist): JSONObject {
        val json = JSONObject()
        json.put("id", playlist.id)
        json.put("title", playlist.title)
        json.put("description", playlist.description)
        json.put("coverUrl", playlist.coverUrl)
        json.put("isPublic", playlist.isPublic)

        val previewCoversArray = JSONArray()
        for (cover in playlist.previewCovers) {
            previewCoversArray.put(cover)
        }
        json.put("previewCovers", previewCoversArray)

        val songsArray = JSONArray()
        for (song in playlist.songs) {
            songsArray.put(songToJson(song))
        }
        json.put("songs", songsArray)

        return json
    }

    private fun songToJson(song: Song): JSONObject {
        val json = JSONObject()
        json.put("id", song.id)
        json.put("title", song.title)
        json.put("artist", song.artist)
        json.put("coverUrl", song.coverUrl)
        json.put("audioUrl", song.audioUrl)
        json.put("duration", song.duration)
        return json
    }

    /**
     * Create a new playlist
     */
    fun createPlaylist(
        name: String,
        description: String = "",
        coverUri: String? = null,
        isPublic: Boolean = false
    ): Playlist {
        val playlist = Playlist(
            id = "user_${UUID.randomUUID()}",
            title = name,
            description = description,
            coverUrl = coverUri ?: "",
            previewCovers = emptyList(),
            songs = emptyList(),
            isPublic = isPublic,
            isNew = false
        )

        _playlists.value = _playlists.value + playlist
        savePlaylists()
        return playlist
    }

    /**
     * Delete a playlist (local and optionally server-side)
     */
    fun deletePlaylist(playlistId: String, accessToken: String? = null) {
        _playlists.value = _playlists.value.filter { it.id != playlistId }
        savePlaylists()

        // Delete from server if it's a server playlist (not local "user_" prefix)
        if (accessToken != null && !playlistId.startsWith("user_")) {
            syncScope.launch {
                syncApi.deletePlaylist(accessToken, playlistId).onFailure { e ->
                    Log.e(TAG, "Server delete failed for $playlistId: ${e.message}")
                }
            }
        }
    }

    /**
     * Update a playlist
     */
    fun updatePlaylist(playlist: Playlist) {
        _playlists.value = _playlists.value.map {
            if (it.id == playlist.id) playlist else it
        }
        savePlaylists()
    }

    /**
     * Add a song to a playlist
     */
    fun addSongToPlaylist(playlistId: String, song: Song) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                // Avoid duplicates
                if (playlist.songs.none { it.id == song.id }) {
                    val updatedSongs = playlist.songs + song
                    // Update preview covers (max 4)
                    val newPreviewCovers = updatedSongs
                        .filter { it.coverUrl.isNotBlank() }
                        .take(4)
                        .map { it.coverUrl }
                    playlist.copy(
                        songs = updatedSongs,
                        previewCovers = newPreviewCovers
                    )
                } else {
                    playlist
                }
            } else {
                playlist
            }
        }
        savePlaylists()
    }

    /**
     * Remove a song from a playlist
     */
    fun removeSongFromPlaylist(playlistId: String, songId: String) {
        _playlists.value = _playlists.value.map { playlist ->
            if (playlist.id == playlistId) {
                val updatedSongs = playlist.songs.filter { it.id != songId }
                val newPreviewCovers = updatedSongs
                    .filter { it.coverUrl.isNotBlank() }
                    .take(4)
                    .map { it.coverUrl }
                playlist.copy(
                    songs = updatedSongs,
                    previewCovers = newPreviewCovers
                )
            } else {
                playlist
            }
        }
        savePlaylists()
    }

    /**
     * Get a playlist by ID
     */
    fun getPlaylist(playlistId: String): Playlist? {
        return _playlists.value.find { it.id == playlistId }
    }

    /**
     * Sync playlists from the server. Merges server playlists with local-only playlists.
     * Server playlists use server UUIDs, local ones use "user_" prefix.
     */
    suspend fun syncFromServer(accessToken: String) {
        _isSyncing.value = true
        try {
            syncApi.fetchUserPlaylists(accessToken).onSuccess { serverPlaylists ->
                // Keep local-only playlists (those with "user_" prefix)
                val localOnly = _playlists.value.filter { it.id.startsWith("user_") }
                // Server playlists replace any previously synced server playlists
                _playlists.value = serverPlaylists + localOnly
                savePlaylists()
                Log.d(TAG, "Synced ${serverPlaylists.size} playlists from server, ${localOnly.size} local")
            }.onFailure { e ->
                Log.e(TAG, "Sync playlists failed: ${e.message}")
            }
        } finally {
            _isSyncing.value = false
        }
    }

    /**
     * Check if a playlist is a server playlist (vs local-only)
     */
    fun isServerPlaylist(playlistId: String): Boolean = !playlistId.startsWith("user_")

    /**
     * Load songs for a server playlist via the public playlist endpoint.
     * Also updates the cover URL and preview covers from the API response.
     */
    suspend fun loadPlaylistSongs(playlistId: String) {
        if (!isServerPlaylist(playlistId)) return

        // Already have songs? Skip.
        val existing = _playlists.value.find { it.id == playlistId }
        if (existing != null && existing.songs.isNotEmpty()) return

        _isSyncing.value = true
        try {
            // Fetch playlist info (cover URL, preview covers) and songs
            val infoResult = karaokeApi.fetchPlaylistInfo(playlistId)
            val songsResult = karaokeApi.fetchPlaylist(playlistId)

            songsResult.onSuccess { apiSongs ->
                val songs = apiSongs.map { apiSong ->
                    val coverArtists = apiSong.coverArtists?.lowercase() ?: ""
                    val singer = when {
                        "evil" in coverArtists -> Singer.EVIL
                        "duet" in coverArtists || ("neuro" in coverArtists && "evil" in coverArtists) -> Singer.DUET
                        else -> Singer.NEURO
                    }
                    Song(
                        id = apiSong.audioUrl?.hashCode()?.toString() ?: "",
                        title = apiSong.title,
                        artist = apiSong.originalArtists ?: "Unknown Artist",
                        coverUrl = apiSong.getCoverArtUrl() ?: "",
                        audioUrl = apiSong.audioUrl ?: "",
                        singer = singer,
                        artCredit = apiSong.artCredit
                    )
                }

                val info = infoResult.getOrNull()

                // Update the playlist with loaded songs and proper cover URL from API
                _playlists.value = _playlists.value.map { playlist ->
                    if (playlist.id == playlistId) {
                        val songPreviewCovers = songs
                            .filter { it.coverUrl.isNotBlank() }
                            .take(4)
                            .map { it.coverUrl }
                        playlist.copy(
                            songs = songs,
                            coverUrl = info?.coverUrl?.takeIf { it.isNotBlank() } ?: playlist.coverUrl,
                            previewCovers = info?.previewCovers?.takeIf { it.isNotEmpty() }
                                ?: songPreviewCovers.takeIf { it.isNotEmpty() }
                                ?: playlist.previewCovers
                        )
                    } else playlist
                }
                savePlaylists()
                Log.d(TAG, "Loaded ${songs.size} songs for playlist $playlistId (cover=${info?.coverUrl?.take(50)})")
            }.onFailure { e ->
                Log.e(TAG, "Failed to load songs for playlist $playlistId: ${e.message}")
            }
        } finally {
            _isSyncing.value = false
        }
    }

    companion object {
        private const val TAG = "UserPlaylistRepo"
        private const val PREFS_NAME = "neurokaraoke_user_playlists"
        private const val KEY_PLAYLISTS = "playlists"
    }
}
