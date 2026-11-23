package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.RadioApi
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
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject

/**
 * Favorites, kept in sync with the user's account (issue #25).
 *
 * Signed in, the server's list is the source of truth: each sync replaces the local list
 * with the server's, so removals and reorders made on the website or another device show
 * up here. Changes made on this device are queued ([PendingFavorite], persisted) and sent
 * before each sync, so a failed or offline toggle is retried instead of lost.
 */
class FavoritesRepository(context: Context) {

    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    private val syncApi = SyncApi()
    private val karaokeApi = NeuroKaraokeApi()
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /** Serializes server round-trips so a toggle's push and a full sync don't interleave. */
    private val syncMutex = Mutex()
    private val pendingLock = Any()
    private var pending: List<PendingFavorite> = emptyList()

    private val _favorites = MutableStateFlow<List<Song>>(emptyList())
    val favorites: StateFlow<List<Song>> = _favorites.asStateFlow()

    private val _isSyncing = MutableStateFlow(false)
    val isSyncing: StateFlow<Boolean> = _isSyncing.asStateFlow()

    init {
        syncScope.launch {
            loadFavorites()
            loadPending()
        }
    }

    private fun loadFavorites() {
        val json = prefs.getString(KEY_FAVORITES, null)
        if (json != null) {
            try {
                val jsonArray = JSONArray(json)
                val songs = mutableListOf<Song>()
                for (i in 0 until jsonArray.length()) {
                    songs.add(parseSong(jsonArray.getJSONObject(i)).withoutRadioStream())
                }
                _favorites.value = songs.deduped()
            } catch (e: Exception) {
                if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
                _favorites.value = emptyList()
            }
        }
    }

    private fun saveFavorites() {
        val snapshot = _favorites.value
        syncScope.launch {
            val jsonArray = JSONArray()
            for (song in snapshot) {
                jsonArray.put(songToJson(song))
            }
            prefs.edit().putString(KEY_FAVORITES, jsonArray.toString()).apply()
        }
    }

    private fun loadPending() {
        val json = prefs.getString(KEY_PENDING, null) ?: return
        try {
            val arr = JSONArray(json)
            val list = mutableListOf<PendingFavorite>()
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                list += PendingFavorite(
                    song = parseSong(o.getJSONObject("song")),
                    add = o.getBoolean("add"),
                    attempts = o.optInt("attempts", 0)
                )
            }
            synchronized(pendingLock) { pending = list }
        } catch (e: Exception) {
            Log.e(TAG, "Could not read pending favorite changes", e)
        }
    }

    /** Must be called with [pendingLock] held. */
    private fun savePendingLocked() {
        val arr = JSONArray()
        for (p in pending) {
            arr.put(JSONObject().apply {
                put("song", songToJson(p.song))
                put("add", p.add)
                put("attempts", p.attempts)
            })
        }
        prefs.edit().putString(KEY_PENDING, arr.toString()).apply()
    }

    private fun updatePending(transform: (List<PendingFavorite>) -> List<PendingFavorite>) {
        synchronized(pendingLock) {
            pending = transform(pending)
            savePendingLocked()
        }
    }

    private fun pendingSnapshot(): List<PendingFavorite> = synchronized(pendingLock) { pending }

    fun toggleFavorite(song: Song, accessToken: String? = null) {
        val current = _favorites.value.toMutableList()
        val existing = current.indexOfFirst { it.id == song.id || it.matchesByAudioUrl(song) }
        val isRemoving = existing >= 0
        if (isRemoving) {
            current.removeAt(existing)
        } else {
            // New favorites go to the bottom, like on the website
            current.add(song)
        }
        _favorites.value = current.deduped()
        saveFavorites()

        // Queue the change even when signed out, so it reaches the account after sign-in
        if (song.id.isNotBlank()) {
            updatePending { FavoritesSync.recordToggle(it, song, add = !isRemoving) }
        }
        if (accessToken != null) {
            syncScope.launch { syncMutex.withLock { pushPending(accessToken) } }
        }
    }

    /**
     * Send queued changes to the server. Changes that fail for network/server reasons stay
     * queued for the next sync. Must be called with [syncMutex] held.
     */
    private suspend fun pushPending(accessToken: String) {
        for (change in pendingSnapshot()) {
            val song = change.song
            val serverSongId = resolveServerId(song)
            if (serverSongId.isNullOrBlank()) {
                val attempts = change.attempts + 1
                Log.w(TAG, "Could not resolve server song ID for: ${song.title} (attempt $attempts)")
                updatePending { list ->
                    list.mapNotNull {
                        if (it !== change) it
                        else if (attempts >= FavoritesSync.MAX_RESOLVE_ATTEMPTS) null
                        else it.copy(attempts = attempts)
                    }
                }
                continue
            }

            Log.d(TAG, "Syncing favorite: ${song.title} (serverId=$serverSongId, add=${change.add})")
            val result = if (change.add) {
                syncApi.addFavorite(accessToken, serverSongId)
            } else {
                syncApi.removeFavorite(accessToken, serverSongId)
            }
            val code = result.exceptionOrNull()?.message
                ?.removePrefix("HTTP ")?.trim()?.toIntOrNull()
            when {
                result.isSuccess -> updatePending { list -> list.filterNot { it === change } }
                code == 401 || code == 403 -> return // Session expired: keep everything for later
                code != null && code in 400..499 && code != 408 && code != 429 -> {
                    // The server rejected this change for good (e.g. 404 removing a song that
                    // isn't favorited). Drop it; the next fetch shows the real state.
                    Log.w(TAG, "Dropping favorite change for ${song.title}: HTTP $code")
                    updatePending { list -> list.filterNot { it === change } }
                }
                else -> Unit // Network or server error: retry on the next sync
            }
        }
    }

    private suspend fun resolveServerId(song: Song): String? = when {
        song.id.contains("-") -> song.id // Already a server UUID
        song.audioUrl.isNotBlank() -> karaokeApi.findSongIdByAudioUrl(song.audioUrl)
        else -> null
    }

    /**
     * Older builds saved radio favorites as "radio_<id>" entries pointing at the live
     * stream. Strip that back to the real song id; [repairFromCatalog] restores the audio.
     */
    private fun Song.withoutRadioStream(): Song =
        if (id.startsWith("radio_") || audioUrl == RadioApi.STREAM_URL) {
            copy(id = id.removePrefix("radio_"), audioUrl = "")
        } else this

    /** Fill in favorites that lost their audio (repaired radio entries) from the song catalog. */
    fun repairFromCatalog(catalog: List<Song>) {
        val current = _favorites.value
        if (catalog.isEmpty() || current.none { it.audioUrl.isBlank() }) return
        val byId = catalog.associateBy { it.id }
        val repaired = current.map { song ->
            if (song.audioUrl.isBlank()) byId[song.id] ?: song else song
        }
        if (repaired != current) {
            _favorites.value = repaired.deduped()
            saveFavorites()
        }
    }

    fun isFavorite(songId: String, audioUrl: String = ""): Boolean {
        return _favorites.value.any {
            it.id == songId || (audioUrl.isNotBlank() && it.matchesByAudioUrl(audioUrl))
        }
    }

    /**
     * Drop repeated songs (same id, or same audio file under different ids). The server can
     * return the same song more than once, and the favorites list uses song.id as its lazy-list
     * key — a duplicate key crashes Compose as soon as the Library tab opens.
     */
    private fun List<Song>.deduped(): List<Song> = FavoritesSync.dedupe(this)

    /** Match by normalized audioUrl as fallback for ID mismatches */
    private fun Song.matchesByAudioUrl(other: Song): Boolean {
        return audioUrl.isNotBlank() && other.audioUrl.isNotBlank() &&
                normalizeAudioUrl(audioUrl) == normalizeAudioUrl(other.audioUrl)
    }

    private fun Song.matchesByAudioUrl(otherUrl: String): Boolean {
        return audioUrl.isNotBlank() && otherUrl.isNotBlank() &&
                normalizeAudioUrl(audioUrl) == normalizeAudioUrl(otherUrl)
    }

    private fun normalizeAudioUrl(url: String): String = FavoritesSync.normalizeAudioUrl(url)

    /**
     * Sync with the server: send queued changes, then replace the local list with the
     * server's (keeping changes still queued and device-only songs on top).
     */
    suspend fun syncFromServer(accessToken: String) {
        _isSyncing.value = true
        try {
            syncMutex.withLock {
                if (!prefs.getBoolean(KEY_MIGRATED, false)) {
                    // Older builds kept favorites the server never got; queue them once so
                    // the first server-wins sync uploads them instead of dropping them.
                    val server = syncApi.fetchFavorites(accessToken).getOrElse { e ->
                        Log.e(TAG, "Sync favorites failed: ${e.message}")
                        return
                    }
                    val local = _favorites.value
                    updatePending { FavoritesSync.localOnlyAsPending(server, local, it) }
                    prefs.edit().putBoolean(KEY_MIGRATED, true).apply()
                }

                pushPending(accessToken)

                syncApi.fetchFavorites(accessToken).onSuccess { serverSongs ->
                    _favorites.value = FavoritesSync.merge(
                        server = serverSongs.map { it.withoutRadioStream() },
                        local = _favorites.value,
                        pending = pendingSnapshot()
                    )
                    saveFavorites()
                    Log.d(TAG, "Synced ${serverSongs.size} favorites from server, ${pendingSnapshot().size} pending")
                }.onFailure { e ->
                    Log.e(TAG, "Sync favorites failed: ${e.message}")
                }
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
            },
            coverArtists = json.optString("coverArtists", ""),
            artCredit = json.optString("artCredit", "").takeIf { it.isNotBlank() }
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
        json.put("coverArtists", song.coverArtists)
        json.put("artCredit", song.artCredit ?: "")
        return json
    }

    companion object {
        private const val TAG = "FavoritesRepo"
        private const val PREFS_NAME = "neurokaraoke_favorites"
        private const val KEY_FAVORITES = "favorites"
        private const val KEY_PENDING = "pending_changes"
        /** Set once the pre-#25 local-only favorites have been queued for upload. */
        private const val KEY_MIGRATED = "server_truth_migrated"
    }
}
