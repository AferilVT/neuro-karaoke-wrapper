package com.soul.neurokaraoke.viewmodel

import android.app.Application
import android.content.ComponentName
import android.content.Context
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soul.neurokaraoke.data.PlaylistCatalog
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.model.Artist
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.SongRepository
import com.soul.neurokaraoke.service.MediaPlaybackService
import com.google.common.util.concurrent.ListenableFuture
import com.google.common.util.concurrent.MoreExecutors
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

data class PlayerUiState(
    val songs: List<Song> = emptyList(),
    val allSongs: List<Song> = emptyList(),
    val isLoadingAllSongs: Boolean = false,
    val allSongsLoaded: Boolean = false,
    val currentSong: Song? = null,
    val isPlaying: Boolean = false,
    val progress: Float = 0f,
    val currentPosition: Long = 0L,
    val duration: Long = 0L,
    val isLoading: Boolean = false,
    val error: String? = null,
    val currentPlaylistId: String? = null,
    val isShuffleEnabled: Boolean = false,
    val repeatMode: RepeatMode = RepeatMode.OFF,
    val availablePlaylists: List<Playlist> = emptyList(),
    val currentPlaylist: Playlist? = null,
    val queue: List<Song> = emptyList(),
    val sleepTimerEndTimeMs: Long? = null,
    val sleepTimerRemainingMs: Long = 0L,
    val sleepTimerEndOfSong: Boolean = false,
    val apiArtists: List<Artist> = emptyList(),
    val isLoadingArtists: Boolean = false,
    val isRadioMode: Boolean = false,
    val radioListenerCount: Int = 0
)

enum class RepeatMode {
    OFF, ONE, ALL
}

class PlayerViewModel(
    application: Application
) : AndroidViewModel(application) {

    private val repository: SongRepository = SongRepository()
    private val playlistCatalog: PlaylistCatalog = PlaylistCatalog(application)
    private val songCache: SongCache = SongCache(application)
    private val api: NeuroKaraokeApi = NeuroKaraokeApi()
    private val radioApi: RadioApi = RadioApi()
    private val prefs = application.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var progressJob: Job? = null
    private var sleepTimerJob: Job? = null
    private var radioPollingJob: Job? = null
    private var controllerFuture: ListenableFuture<MediaController>? = null
    private var mediaController: MediaController? = null
    private var playerListener: Player.Listener? = null
    private var hasRestoredState = false
    private var isRestoringState = false
    private var savedResumePosition: Long = 0L  // Position to seek to when resuming after restore
    @Volatile
    private var isCleared = false

    init {
        // Restore last played song immediately (before anything else)
        // so the mini player shows the correct song instantly
        restorePlaybackState()
        // Load available playlists on startup
        loadAvailablePlaylists()
        // Try to load cached songs for instant access
        loadCachedSongs()
        // Initialize media controller connection
        initializeMediaController()
    }

    /**
     * Load songs from local cache for instant access
     */
    fun loadCachedSongs() {
        viewModelScope.launch {
            if (songCache.isSetupComplete()) {
                val cachedSongs = songCache.getCachedSongs()
                if (cachedSongs.isNotEmpty()) {
                    // Pre-populate with cached songs for instant display,
                    // but don't mark as fully loaded — loadAllSongs() will
                    // check for staleness and refresh if new setlists exist
                    _uiState.value = _uiState.value.copy(
                        allSongs = cachedSongs
                    )
                }
            }
        }
    }

    /**
     * Public method for Activity lifecycle to force-save playback state
     */
    fun savePlaybackStateNow() {
        isRestoringState = false
        savePlaybackState()
    }

    /**
     * Save playback state to SharedPreferences
     * Saves full song details for instant restoration without API call
     */
    private fun savePlaybackState() {
        // Don't save during restore phase or radio mode
        if (isRestoringState || _uiState.value.isRadioMode) return

        val song = _uiState.value.currentSong ?: return
        val position = mediaController?.currentPosition ?: 0L
        val duration = mediaController?.duration?.takeIf { it > 0 } ?: _uiState.value.duration
        // Use commit() instead of apply() to ensure synchronous save before app killed
        prefs.edit()
            .putString("last_song_id", song.id)
            .putString("last_song_title", song.title)
            .putString("last_song_artist", song.artist)
            .putString("last_song_cover_url", song.coverUrl)
            .putString("last_song_audio_url", song.audioUrl)
            .putString("last_song_singer", song.singer.name)
            .putString("last_playlist_id", _uiState.value.currentPlaylistId)
            .putLong("last_position", position)
            .putLong("last_duration", duration)
            .commit()
    }

    /**
     * Restore playback state from SharedPreferences
     * Instantly shows last played song without waiting for API
     */
    private fun restorePlaybackState() {
        if (hasRestoredState) return
        hasRestoredState = true
        isRestoringState = true  // Prevent saving during restore

        val songId = prefs.getString("last_song_id", null) ?: run {
            isRestoringState = false
            return
        }
        val title = prefs.getString("last_song_title", null) ?: run {
            isRestoringState = false
            return
        }
        val artist = prefs.getString("last_song_artist", "") ?: ""
        val coverUrl = prefs.getString("last_song_cover_url", "") ?: ""
        val audioUrl = prefs.getString("last_song_audio_url", "") ?: ""
        val singerName = prefs.getString("last_song_singer", "NEURO") ?: "NEURO"
        val playlistId = prefs.getString("last_playlist_id", null)

        // Restore song immediately from saved data
        val singer = try {
            Singer.valueOf(singerName)
        } catch (e: Exception) {
            Singer.NEURO
        }

        val restoredSong = Song(
            id = songId,
            title = title,
            artist = artist,
            coverUrl = coverUrl,
            audioUrl = audioUrl,
            singer = singer
        )

        val savedPosition = prefs.getLong("last_position", 0L)
        val savedDuration = prefs.getLong("last_duration", 0L)
        savedResumePosition = savedPosition
        val restoredProgress = if (savedDuration > 0) {
            (savedPosition.toFloat() / savedDuration.toFloat()).coerceIn(0f, 1f)
        } else 0f

        // Restore repeat mode and shuffle state
        val savedRepeatName = prefs.getString("repeat_mode", null)
        val repeatMode = if (savedRepeatName != null) {
            try { RepeatMode.valueOf(savedRepeatName) } catch (e: Exception) { RepeatMode.OFF }
        } else RepeatMode.OFF
        val savedShuffle = prefs.getBoolean("shuffle_enabled", false)

        _uiState.value = _uiState.value.copy(
            currentSong = restoredSong,
            currentPlaylistId = playlistId,
            currentPosition = savedPosition,
            duration = savedDuration,
            progress = restoredProgress,
            repeatMode = repeatMode,
            isShuffleEnabled = savedShuffle
        )

        // Allow saving again after service has settled (5 seconds)
        viewModelScope.launch {
            delay(5000)
            isRestoringState = false
        }
    }

    private fun initializeMediaController() {
        val context = getApplication<Application>()
        val sessionToken = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )

        controllerFuture = MediaController.Builder(context, sessionToken).buildAsync()
        controllerFuture?.addListener({
            if (isCleared) return@addListener
            mediaController = controllerFuture?.get()
            setupPlayerListener()
        }, MoreExecutors.directExecutor())
    }

    private fun setupPlayerListener() {
        // Remove any previously added listener to avoid duplicates
        playerListener?.let { mediaController?.removeListener(it) }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                _uiState.value = _uiState.value.copy(isPlaying = isPlaying)
                if (isPlaying) {
                    startProgressUpdates()
                    savePlaybackState()
                } else {
                    stopProgressUpdates()
                    savePlaybackState()
                }
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_ENDED -> handleSongEnded()
                    Player.STATE_READY -> {
                        val duration = mediaController?.duration?.coerceAtLeast(0L) ?: 0L
                        _uiState.value = _uiState.value.copy(
                            duration = duration,
                            currentSong = _uiState.value.currentSong?.copy(duration = duration)
                        )
                    }
                    Player.STATE_BUFFERING -> { }
                    Player.STATE_IDLE -> { }
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                // Don't let stale service data overwrite restored song or radio metadata
                if (isRestoringState || _uiState.value.isRadioMode) return

                mediaItem?.mediaId?.let { mediaId ->
                    val song = _uiState.value.songs.find { it.id == mediaId }
                        ?: _uiState.value.allSongs.find { it.id == mediaId }
                    if (song != null && song.id != _uiState.value.currentSong?.id) {
                        _uiState.value = _uiState.value.copy(
                            currentSong = song,
                            progress = 0f,
                            currentPosition = 0L
                        )
                        savePlaybackState()
                    }
                }
            }

            override fun onShuffleModeEnabledChanged(shuffleModeEnabled: Boolean) {
                val savedShuffle = _uiState.value.isShuffleEnabled
                if (shuffleModeEnabled != savedShuffle) {
                    // Player's shuffle was reset (e.g. service restart) — re-apply saved mode
                    mediaController?.shuffleModeEnabled = savedShuffle
                    return
                }
                _uiState.value = _uiState.value.copy(isShuffleEnabled = shuffleModeEnabled)
            }

            override fun onRepeatModeChanged(repeatMode: Int) {
                val mode = when (repeatMode) {
                    Player.REPEAT_MODE_ONE -> RepeatMode.ONE
                    Player.REPEAT_MODE_ALL -> RepeatMode.ALL
                    else -> RepeatMode.OFF
                }
                val savedMode = _uiState.value.repeatMode
                if (mode != savedMode) {
                    // Player's repeat mode was reset (e.g. service restart) — re-apply saved mode
                    mediaController?.repeatMode = when (savedMode) {
                        RepeatMode.OFF -> Player.REPEAT_MODE_OFF
                        RepeatMode.ONE -> Player.REPEAT_MODE_ONE
                        RepeatMode.ALL -> Player.REPEAT_MODE_ALL
                    }
                    return
                }
                _uiState.value = _uiState.value.copy(repeatMode = mode)
            }
        }
        playerListener = listener
        mediaController?.addListener(listener)

        // Re-sync repeat mode and shuffle to the player
        // (handles service restart where the new player has default settings)
        mediaController?.repeatMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
        mediaController?.shuffleModeEnabled = _uiState.value.isShuffleEnabled
    }

    private var lastPeriodicSaveTime = 0L

    private fun startProgressUpdates() {
        progressJob?.cancel()
        progressJob = viewModelScope.launch {
            while (isActive) {
                val controller = mediaController ?: continue
                val position = controller.currentPosition.coerceAtLeast(0L)
                val duration = controller.duration.coerceAtLeast(1L)
                val progress = if (duration > 0) position.toFloat() / duration else 0f

                _uiState.value = _uiState.value.copy(
                    progress = progress.coerceIn(0f, 1f),
                    currentPosition = position,
                    duration = duration
                )

                // Periodically save state every 30 seconds for position persistence
                val now = System.currentTimeMillis()
                if (now - lastPeriodicSaveTime >= 30_000L) {
                    lastPeriodicSaveTime = now
                    savePlaybackState()
                }

                delay(100L) // Faster updates for smoother lyrics sync
            }
        }
    }

    private fun stopProgressUpdates() {
        progressJob?.cancel()
        progressJob = null
    }

    private fun handleSongEnded() {
        // Check sleep timer "end of song" mode
        if (_uiState.value.sleepTimerEndOfSong) {
            _uiState.value = _uiState.value.copy(sleepTimerEndOfSong = false)
            mediaController?.pause()
            return
        }

        when (_uiState.value.repeatMode) {
            RepeatMode.ONE -> {
                mediaController?.seekTo(0)
                mediaController?.play()
            }
            RepeatMode.ALL -> {
                playNext(wrapAround = true)
            }
            RepeatMode.OFF -> {
                playNext(wrapAround = false)
            }
        }
    }

    /**
     * Load songs from a playlist
     */
    fun loadPlaylist(playlistId: String) {
        if (_uiState.value.currentPlaylistId == playlistId && _uiState.value.songs.isNotEmpty()) {
            return // Already loaded
        }

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoading = true, error = null)

            val playlist = _uiState.value.availablePlaylists.find { it.id == playlistId }

            repository.getPlaylistSongs(playlistId).fold(
                onSuccess = { songs ->
                    _uiState.value = _uiState.value.copy(
                        songs = songs,
                        queue = songs,
                        isLoading = false,
                        currentPlaylistId = playlistId,
                        currentPlaylist = playlist,
                        currentSong = _uiState.value.currentSong ?: songs.firstOrNull()
                    )

                    // Update playlist metadata if missing (fallback from song data)
                    if (playlist != null && (playlist.previewCovers.isEmpty() || playlist.songCount == 0)) {
                        val previewCovers = songs.take(4).map { it.coverUrl }.filter { it.isNotBlank() }
                        val updatedPlaylist = playlist.copy(
                            previewCovers = if (playlist.previewCovers.isEmpty()) previewCovers else playlist.previewCovers,
                            songCount = if (playlist.songCount == 0) songs.size else playlist.songCount
                        )
                        viewModelScope.launch {
                            playlistCatalog.updatePlaylist(updatedPlaylist)
                            loadAvailablePlaylists()
                        }
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = error.message ?: "Failed to load playlist"
                    )
                }
            )
        }
    }

    /**
     * Play a specific song
     */
    fun playSong(song: Song, startPosition: Long = 0L) {
        // Stop radio mode if active
        if (_uiState.value.isRadioMode) {
            radioPollingJob?.cancel()
            _uiState.value = _uiState.value.copy(isRadioMode = false, radioListenerCount = 0)
        }

        if (song.audioUrl.isBlank()) {
            _uiState.value = _uiState.value.copy(
                error = "No audio URL available for this song"
            )
            return
        }

        val resumePos = if (startPosition > 0) startPosition else 0L
        _uiState.value = _uiState.value.copy(
            currentSong = song,
            progress = if (resumePos > 0 && _uiState.value.duration > 0) {
                (resumePos.toFloat() / _uiState.value.duration.toFloat()).coerceIn(0f, 1f)
            } else 0f,
            currentPosition = resumePos,
            error = null
        )

        // User explicitly played a song, allow saving and save immediately
        isRestoringState = false
        savePlaybackState()

        val controller = mediaController ?: return
        var queueSongs = _uiState.value.queue.ifEmpty { _uiState.value.songs }
        var songIndex = queueSongs.indexOfFirst { it.id == song.id }

        // Song not in current queue — try allSongs
        if (songIndex < 0 && _uiState.value.allSongs.isNotEmpty()) {
            queueSongs = _uiState.value.allSongs
            songIndex = queueSongs.indexOfFirst { it.id == song.id }
            if (songIndex >= 0) {
                _uiState.value = _uiState.value.copy(queue = queueSongs, currentPlaylistId = null)
            }
        }

        // Still not found — play just this song as a single-item queue
        if (songIndex < 0) {
            queueSongs = listOf(song)
            songIndex = 0
            _uiState.value = _uiState.value.copy(queue = queueSongs)
        }

        // Build media items for the queue
        val mediaItems = queueSongs.map { s ->
            MediaItem.Builder()
                .setUri(s.audioUrl)
                .setMediaId(s.id)
                .setMediaMetadata(
                    MediaMetadata.Builder()
                        .setTitle(s.title)
                        .setArtist("${s.artist} • ${s.coverArtist}")
                        .setArtworkUri(android.net.Uri.parse(s.coverUrl))
                        .build()
                )
                .build()
        }

        // Set the queue and start at the selected song, resuming from saved position
        controller.setMediaItems(mediaItems, songIndex, resumePos)
        controller.prepare()
        controller.play()
    }

    /**
     * Play song by ID from current playlist
     */
    fun playSongById(songId: String) {
        val song = _uiState.value.songs.find { it.id == songId }
        song?.let { playSong(it) }
    }

    /**
     * Toggle play/pause
     */
    fun togglePlayPause() {
        val controller = mediaController ?: return
        if (controller.isPlaying) {
            controller.pause()
        } else {
            // In radio mode, just resume or restart the stream
            if (_uiState.value.isRadioMode) {
                if (controller.playbackState == Player.STATE_IDLE || controller.mediaItemCount == 0) {
                    playRadio()
                } else {
                    controller.play()
                }
                return
            }

            val currentSong = _uiState.value.currentSong
            // Check if player has the correct song loaded
            val playerMediaId = controller.currentMediaItem?.mediaId
            val needsReload = currentSong != null && (
                controller.playbackState == Player.STATE_IDLE ||
                controller.mediaItemCount == 0 ||
                playerMediaId != currentSong.id
            )

            if (needsReload && currentSong != null) {
                // Resume from saved position if available
                val resumePos = savedResumePosition
                savedResumePosition = 0L  // Consume the saved position
                playSong(currentSong, startPosition = resumePos)
            } else {
                controller.play()
            }
        }
    }

    /**
     * Play previous song
     */
    fun playPrevious() {
        val controller = mediaController ?: return

        // Queue lost (service restarted) — reload the playlist and navigate
        if (controller.mediaItemCount == 0) {
            reloadQueueThenNavigate(forward = false)
            return
        }

        // If more than 3 seconds into the song, restart it
        if (controller.currentPosition > 3000) {
            controller.seekTo(0)
            return
        }

        // Use the player's built-in previous functionality
        if (controller.hasPreviousMediaItem()) {
            controller.seekToPreviousMediaItem()
        } else if (_uiState.value.repeatMode == RepeatMode.ALL && controller.mediaItemCount > 0) {
            controller.seekTo(controller.mediaItemCount - 1, 0L)
        }
    }

    /**
     * Play next song
     */
    fun playNext(wrapAround: Boolean = false) {
        val controller = mediaController ?: return

        // Queue lost (service restarted) — reload the playlist and navigate
        if (controller.mediaItemCount == 0) {
            reloadQueueThenNavigate(forward = true)
            return
        }

        // Use the player's built-in next functionality
        if (controller.hasNextMediaItem()) {
            controller.seekToNextMediaItem()
        } else if (wrapAround && controller.mediaItemCount > 0) {
            controller.seekTo(0, 0L)
        } else {
            // Queue ended and not looping - play random songs from across all playlists
            playRandomSongFromAllPlaylists()
        }
    }

    /**
     * Reload the correct song list into the controller, then skip forward or backward.
     * Called when mediaItemCount == 0 (service was restarted and queue is gone).
     *
     * Two contexts are possible:
     *  - Setlist mode:  last_playlist_id is a real ID → reload that playlist
     *  - Search mode:   last_playlist_id is null (cleared by playSongFromAllSongs) → reload allSongs
     */
    private fun reloadQueueThenNavigate(forward: Boolean) {
        viewModelScope.launch {
            val currentSong = _uiState.value.currentSong ?: return@launch

            // Fast path: cached songs already contain the current song
            val cachedSongs = _uiState.value.queue.ifEmpty { _uiState.value.songs }
            val songs: List<Song> = if (cachedSongs.any { it.id == currentSong.id }) {
                cachedSongs
            } else {
                // Prefs are only written during real playback so they're always reliable
                val savedPlaylistId = prefs.getString("last_playlist_id", null)
                    ?.takeIf { it.isNotEmpty() }
                    ?: _uiState.value.currentPlaylistId?.takeIf { it.isNotEmpty() }

                if (savedPlaylistId != null) {
                    // Setlist mode — reload that specific playlist
                    val freshSongs = repository.getPlaylistSongs(savedPlaylistId).getOrNull()
                        ?: return@launch
                    _uiState.value = _uiState.value.copy(songs = freshSongs, queue = freshSongs)
                    freshSongs
                } else {
                    // Search/allSongs mode — reload all songs
                    val loaded = if (_uiState.value.allSongsLoaded && _uiState.value.allSongs.isNotEmpty()) {
                        _uiState.value.allSongs
                    } else {
                        coroutineScope {
                            _uiState.value.availablePlaylists
                                .map { async { repository.getPlaylistSongs(it.id).getOrNull() ?: emptyList() } }
                                .awaitAll()
                        }.flatten().distinctBy { it.id }.also { all ->
                            _uiState.value = _uiState.value.copy(allSongs = all, allSongsLoaded = true, queue = all)
                        }
                    }
                    loaded
                }
            }

            val currentIndex = songs.indexOfFirst { it.id == currentSong.id }
            if (currentIndex == -1) return@launch  // Song genuinely not found — don't play the wrong thing

            val targetIndex = when {
                forward -> if (currentIndex + 1 < songs.size) currentIndex + 1 else 0
                currentIndex > 0 -> currentIndex - 1
                else -> 0
            }
            playSong(songs[targetIndex])
        }
    }

    /**
     * Play a random song from all playlists with shuffle enabled
     */
    fun playRandomSong() {
        // Enable shuffle mode
        mediaController?.let { controller ->
            if (!controller.shuffleModeEnabled) {
                controller.shuffleModeEnabled = true
            }
        }
        playRandomSongFromAllPlaylists()
    }

    /**
     * Play a random song from all available playlists (auto-play when queue ends)
     */
    private fun playRandomSongFromAllPlaylists() {
        viewModelScope.launch {
            // Ensure all songs are loaded first
            if (!_uiState.value.allSongsLoaded && !_uiState.value.isLoadingAllSongs) {
                loadAllSongsAndPlayRandom()
            } else if (_uiState.value.allSongsLoaded) {
                pickAndPlayRandomSong()
            } else {
                // Songs are currently loading, wait for them
                waitForSongsAndPlayRandom()
            }
        }
    }

    private suspend fun loadAllSongsAndPlayRandom() {
        _uiState.value = _uiState.value.copy(isLoadingAllSongs = true)

        // Wait for playlists to be available
        var waited = 0
        while (_uiState.value.availablePlaylists.isEmpty() && waited < 5000) {
            delay(200)
            waited += 200
        }

        val playlists = _uiState.value.availablePlaylists
        if (playlists.isEmpty()) {
            _uiState.value = _uiState.value.copy(isLoadingAllSongs = false)
            return
        }

        // Load all playlists in parallel
        val results = coroutineScope {
            playlists.map { playlist ->
                async {
                    repository.getPlaylistSongs(playlist.id).getOrNull() ?: emptyList()
                }
            }.awaitAll()
        }

        val allSongs = results.flatten().distinctBy { it.id }

        songCache.cacheSongs(allSongs, playlists.size)

        _uiState.value = _uiState.value.copy(
            allSongs = allSongs,
            isLoadingAllSongs = false,
            allSongsLoaded = true
        )

        pickAndPlayRandomSong()
    }

    private suspend fun waitForSongsAndPlayRandom() {
        // Wait for songs to finish loading (check every 100ms, max 10 seconds)
        var attempts = 0
        while (_uiState.value.isLoadingAllSongs && attempts < 100) {
            delay(100)
            attempts++
        }
        if (_uiState.value.allSongsLoaded) {
            pickAndPlayRandomSong()
        }
    }

    private fun pickAndPlayRandomSong() {
        val allSongs = _uiState.value.allSongs
        if (allSongs.isEmpty()) return

        // Exclude the current song to avoid immediate repeat
        val currentSongId = _uiState.value.currentSong?.id
        val availableSongs = if (currentSongId != null) {
            allSongs.filter { it.id != currentSongId }
        } else {
            allSongs
        }

        if (availableSongs.isEmpty()) return

        val randomSong = availableSongs.random()
        // Set queue to allSongs so playSong() can find the song and build the full queue
        _uiState.value = _uiState.value.copy(queue = allSongs, currentPlaylistId = null)
        playSong(randomSong)
    }

    /**
     * Seek to position (0.0 to 1.0)
     */
    fun seekTo(progress: Float) {
        val controller = mediaController ?: return
        val duration = controller.duration
        if (duration > 0) {
            val position = (progress * duration).toLong()
            controller.seekTo(position)
            _uiState.value = _uiState.value.copy(
                progress = progress,
                currentPosition = position
            )
        }
    }

    /**
     * Toggle shuffle mode
     */
    fun toggleShuffle() {
        val controller = mediaController ?: return
        val newShuffle = !controller.shuffleModeEnabled
        controller.shuffleModeEnabled = newShuffle
        prefs.edit().putBoolean("shuffle_enabled", newShuffle).apply()
    }

    /**
     * Cycle through repeat modes: OFF -> ALL -> ONE -> OFF
     */
    fun cycleRepeatMode() {
        val controller = mediaController ?: return
        val newMode = when (_uiState.value.repeatMode) {
            RepeatMode.OFF -> RepeatMode.ALL
            RepeatMode.ALL -> RepeatMode.ONE
            RepeatMode.ONE -> RepeatMode.OFF
        }
        _uiState.value = _uiState.value.copy(repeatMode = newMode)

        // Persist so repeat mode survives service/app restarts
        prefs.edit().putString("repeat_mode", newMode.name).apply()

        controller.repeatMode = when (newMode) {
            RepeatMode.OFF -> Player.REPEAT_MODE_OFF
            RepeatMode.ONE -> Player.REPEAT_MODE_ONE
            RepeatMode.ALL -> Player.REPEAT_MODE_ALL
        }
    }

    /**
     * Clear error
     */
    fun clearError() {
        _uiState.value = _uiState.value.copy(error = null)
    }

    /**
     * Load available playlists.
     * Merges the official setlists API results with the local catalog
     * so older playlists (not returned by API) are preserved.
     */
    fun loadAvailablePlaylists() {
        viewModelScope.launch {
            try {
                // Always start from the local catalog (has all 78+ playlists)
                val local = playlistCatalog.getPlaylists()

                val result = api.fetchOfficialSetlists()
                result.fold(
                    onSuccess = { apiPlaylists ->
                        // Merge: API playlists take priority, then local ones not in API
                        val apiIds = apiPlaylists.map { it.id }.toSet()
                        val merged = apiPlaylists + local.filter { it.id !in apiIds }
                        playlistCatalog.replacePlaylists(merged)
                        _uiState.value = _uiState.value.copy(availablePlaylists = merged)
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(availablePlaylists = local)
                    }
                )
            } catch (e: Exception) {
                val cached = playlistCatalog.getPlaylists()
                _uiState.value = _uiState.value.copy(availablePlaylists = cached)
            }
        }
    }

    /**
     * Load artists from the API
     */
    fun loadArtists() {
        if (_uiState.value.apiArtists.isNotEmpty() || _uiState.value.isLoadingArtists) return

        viewModelScope.launch {
            _uiState.value = _uiState.value.copy(isLoadingArtists = true)
            try {
                api.fetchArtists().fold(
                    onSuccess = { artists ->
                        _uiState.value = _uiState.value.copy(
                            apiArtists = artists,
                            isLoadingArtists = false
                        )
                    },
                    onFailure = {
                        _uiState.value = _uiState.value.copy(isLoadingArtists = false)
                    }
                )
            } catch (e: Exception) {
                _uiState.value = _uiState.value.copy(isLoadingArtists = false)
            }
        }
    }

    /**
     * Force refresh a specific playlist's data from the API
     */
    fun forceRefreshPlaylist(playlistId: String) {
        viewModelScope.launch {
            val playlist = _uiState.value.availablePlaylists.find { it.id == playlistId } ?: return@launch

            api.fetchPlaylistInfo(playlistId).fold(
                onSuccess = { info ->
                    val updatedPlaylist = playlist.copy(
                        title = info.name.ifEmpty { playlist.title },
                        coverUrl = info.coverUrl.ifEmpty { playlist.coverUrl },
                        previewCovers = info.previewCovers.ifEmpty { playlist.previewCovers },
                        songCount = if (info.songCount > 0) info.songCount else playlist.songCount
                    )
                    playlistCatalog.updatePlaylist(updatedPlaylist)

                    // Refresh UI
                    val refreshedPlaylists = playlistCatalog.getPlaylists()
                    _uiState.value = _uiState.value.copy(availablePlaylists = refreshedPlaylists)
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to refresh playlist: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Add a new playlist to the catalog by ID (fetches name from API)
     */
    fun addPlaylistById(id: String) {
        viewModelScope.launch {
            if (playlistCatalog.hasPlaylist(id)) {
                _uiState.value = _uiState.value.copy(error = "Playlist already exists")
                return@launch
            }

            api.fetchPlaylistInfo(id).fold(
                onSuccess = { info ->
                    val playlist = Playlist(
                        id = info.id,
                        title = info.name,
                        description = "",
                        coverUrl = info.coverUrl,
                        previewCovers = info.previewCovers,
                        songCount = info.songCount
                    )
                    if (playlistCatalog.addPlaylist(playlist)) {
                        loadAvailablePlaylists()
                    }
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to fetch playlist: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Add a new playlist to the catalog with manual details
     */
    fun addPlaylist(id: String, name: String, description: String = "", coverUrl: String = "") {
        viewModelScope.launch {
            val playlist = Playlist(
                id = id,
                title = name,
                description = description,
                coverUrl = coverUrl
            )
            if (playlistCatalog.addPlaylist(playlist)) {
                loadAvailablePlaylists()
            }
        }
    }

    /**
     * Remove a playlist from the catalog
     */
    fun removePlaylist(playlistId: String) {
        viewModelScope.launch {
            if (playlistCatalog.removePlaylist(playlistId)) {
                loadAvailablePlaylists()
            }
        }
    }

    /**
     * Select and load a playlist
     */
    fun selectPlaylist(playlist: Playlist) {
        _uiState.value = _uiState.value.copy(currentPlaylist = playlist)
        loadPlaylist(playlist.id)
    }

    /**
     * Load all songs from all playlists for search functionality
     * Uses cache if available, otherwise parallel API loading
     */
    fun loadAllSongs() {
        if (_uiState.value.isLoadingAllSongs) {
            return
        }

        viewModelScope.launch {
            val playlists = _uiState.value.availablePlaylists

            // Try cache first (only if playlists are loaded so we can check staleness)
            if (_uiState.value.allSongsLoaded && !songCache.isCacheStale(playlists.size)) {
                return@launch
            }

            _uiState.value = _uiState.value.copy(isLoadingAllSongs = true)

            if (songCache.isSetupComplete() && !songCache.isCacheStale(playlists.size)) {
                val cachedSongs = songCache.getCachedSongs()
                if (cachedSongs.isNotEmpty()) {
                    _uiState.value = _uiState.value.copy(
                        allSongs = cachedSongs,
                        isLoadingAllSongs = false,
                        allSongsLoaded = true
                    )
                    return@launch
                }
            }

            // Wait for playlists to be available before fetching songs
            if (playlists.isEmpty()) {
                // Playlists haven't loaded yet — wait briefly for them
                var waited = 0
                while (_uiState.value.availablePlaylists.isEmpty() && waited < 5000) {
                    delay(200)
                    waited += 200
                }
            }

            val finalPlaylists = _uiState.value.availablePlaylists
            if (finalPlaylists.isEmpty()) {
                _uiState.value = _uiState.value.copy(isLoadingAllSongs = false)
                return@launch
            }

            // Load all playlists in parallel
            val results = coroutineScope {
                finalPlaylists.map { playlist ->
                    async {
                        repository.getPlaylistSongs(playlist.id).getOrNull() ?: emptyList()
                    }
                }.awaitAll()
            }

            // Combine all results
            val allSongs = results.flatten().distinctBy { it.id }

            // Cache for next time (include playlist count for staleness detection)
            songCache.cacheSongs(allSongs, finalPlaylists.size)

            _uiState.value = _uiState.value.copy(
                allSongs = allSongs,
                isLoadingAllSongs = false,
                allSongsLoaded = true
            )
        }
    }

    /**
     * Play a song from the all songs list (for search results)
     */
    fun playSongFromAllSongs(songId: String) {
        val allSongs = _uiState.value.allSongs
        val song = allSongs.find { it.id == songId }
            ?: _uiState.value.songs.find { it.id == songId }

        song?.let {
            if (allSongs.isNotEmpty() && allSongs.any { s -> s.id == songId }) {
                // Set queue to allSongs and clear currentPlaylistId so savePlaybackState()
                // records a null playlist ID — reloadQueueThenNavigate uses this as a signal
                // to reload allSongs on the next restart rather than a single wrong playlist.
                _uiState.value = _uiState.value.copy(queue = allSongs, currentPlaylistId = null)
            }
            playSong(it)
        }
    }

    /**
     * Play a song with a custom queue (for external playlists like Explore)
     */
    fun playSongWithQueue(song: Song, queue: List<Song>) {
        _uiState.value = _uiState.value.copy(queue = queue)
        playSong(song)
    }

    /**
     * Get the catalog file path for manual editing
     */
    fun getPlaylistCatalogPath(): String = playlistCatalog.getFilePath()

    /**
     * Set a sleep timer for the given number of minutes.
     * Playback will pause when the timer expires.
     */
    fun setSleepTimer(minutes: Int) {
        cancelSleepTimer()
        val endTime = System.currentTimeMillis() + minutes * 60_000L
        _uiState.value = _uiState.value.copy(
            sleepTimerEndTimeMs = endTime,
            sleepTimerRemainingMs = minutes * 60_000L,
            sleepTimerEndOfSong = false
        )
        sleepTimerJob = viewModelScope.launch {
            while (isActive) {
                val remaining = endTime - System.currentTimeMillis()
                if (remaining <= 0) {
                    mediaController?.pause()
                    _uiState.value = _uiState.value.copy(
                        sleepTimerEndTimeMs = null,
                        sleepTimerRemainingMs = 0L
                    )
                    break
                }
                _uiState.value = _uiState.value.copy(sleepTimerRemainingMs = remaining)
                delay(1000L)
            }
        }
    }

    /**
     * Cancel the active sleep timer.
     */
    fun cancelSleepTimer() {
        sleepTimerJob?.cancel()
        sleepTimerJob = null
        _uiState.value = _uiState.value.copy(
            sleepTimerEndTimeMs = null,
            sleepTimerRemainingMs = 0L,
            sleepTimerEndOfSong = false
        )
    }

    /**
     * Set the sleep timer to pause at the end of the current song.
     */
    fun setSleepTimerEndOfSong() {
        cancelSleepTimer()
        _uiState.value = _uiState.value.copy(
            sleepTimerEndOfSong = true
        )
    }

    /**
     * Start playing the live radio stream.
     * Fetches current state from the API and begins streaming.
     */
    fun playRadio() {
        viewModelScope.launch {
            // Fetch current radio state first
            radioApi.fetchCurrentState().fold(
                onSuccess = { state ->
                    val currentRadioSong = state.current?.toSong()

                    _uiState.value = _uiState.value.copy(
                        isRadioMode = true,
                        radioListenerCount = state.listenerCount,
                        currentSong = currentRadioSong ?: _uiState.value.currentSong,
                        queue = emptyList(),
                        currentPlaylistId = null,
                        progress = 0f,
                        currentPosition = 0L,
                        duration = 0L
                    )

                    // Play the stream
                    val controller = mediaController ?: return@fold
                    val metadata = MediaMetadata.Builder()
                        .setTitle(currentRadioSong?.title ?: "Neuro Radio")
                        .setArtist(currentRadioSong?.let { "${it.artist} \u2022 ${it.coverArtist}" } ?: "Live")
                        .apply {
                            currentRadioSong?.coverUrl?.let { url ->
                                setArtworkUri(android.net.Uri.parse(url))
                            }
                        }
                        .build()

                    val mediaItem = MediaItem.Builder()
                        .setUri(RadioApi.STREAM_URL)
                        .setMediaId("radio_live")
                        .setMediaMetadata(metadata)
                        .setLiveConfiguration(
                            MediaItem.LiveConfiguration.Builder().build()
                        )
                        .build()

                    controller.setMediaItem(mediaItem)
                    controller.prepare()
                    controller.play()

                    // Start polling for metadata updates
                    startRadioPolling()
                },
                onFailure = { error ->
                    _uiState.value = _uiState.value.copy(
                        error = "Failed to connect to radio: ${error.message}"
                    )
                }
            )
        }
    }

    /**
     * Stop the live radio stream and exit radio mode.
     */
    fun stopRadio() {
        radioPollingJob?.cancel()
        radioPollingJob = null
        mediaController?.stop()
        _uiState.value = _uiState.value.copy(
            isRadioMode = false,
            radioListenerCount = 0,
            isPlaying = false
        )
    }

    private fun startRadioPolling() {
        radioPollingJob?.cancel()
        radioPollingJob = viewModelScope.launch {
            while (isActive && _uiState.value.isRadioMode) {
                delay(15_000L)
                radioApi.fetchCurrentState().onSuccess { state ->
                    if (!_uiState.value.isRadioMode) return@onSuccess
                    val newSong = state.current?.toSong()
                    val currentId = _uiState.value.currentSong?.id
                    _uiState.value = _uiState.value.copy(
                        radioListenerCount = state.listenerCount
                    )
                    // Update song info if it changed (UI only — don't touch the player/stream)
                    if (newSong != null && newSong.id != currentId) {
                        _uiState.value = _uiState.value.copy(currentSong = newSong)
                    }
                }
            }
        }
    }

    override fun onCleared() {
        super.onCleared()
        // Save state before cleanup so it persists across app restarts
        isRestoringState = false  // Ensure save isn't blocked
        savePlaybackState()
        isCleared = true
        stopProgressUpdates()
        sleepTimerJob?.cancel()
        radioPollingJob?.cancel()
        playerListener?.let { mediaController?.removeListener(it) }
        playerListener = null
        controllerFuture?.let { MediaController.releaseFuture(it) }
        mediaController = null
    }
}
