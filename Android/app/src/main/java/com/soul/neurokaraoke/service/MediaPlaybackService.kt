package com.soul.neurokaraoke.service

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import android.content.Context
import com.soul.neurokaraoke.MainActivity
import com.soul.neurokaraoke.audio.AudioCacheManager
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.audio.EqualizerManager
import com.soul.neurokaraoke.data.repository.DownloadRepository
import com.google.common.collect.ImmutableList
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future

@UnstableApi
class MediaPlaybackService : MediaLibraryService() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    private var librarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private lateinit var browseTree: AutoBrowseTree
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        DownloadRepository.initialize(this)
        AudioCacheManager.initialize(this)
        browseTree = AutoBrowseTree(this)

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(60_000, 180_000, 2_500, 5_000)
            .setPrioritizeTimeOverSizeThresholds(true)
            .setBackBuffer(30_000, true)
            .build()

        val cacheDataSourceFactory = AudioCacheManager.createCacheDataSourceFactory(this)
        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(cacheDataSourceFactory)

        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(mediaSourceFactory)
            .setLoadControl(loadControl)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                true
            )
            .setDeviceVolumeControlEnabled(true)
            .setHandleAudioBecomingNoisy(true)
            .build()

        player?.let { exoPlayer ->
            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        EqualizerManager.initialize(exoPlayer.audioSessionId)
                    }
                }
            }
            playerListener = listener
            exoPlayer.addListener(listener)
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        player?.let { exoPlayer ->
            librarySession = MediaLibrarySession.Builder(this, exoPlayer, LibraryCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .setBitmapLoader(CacheBitmapLoader(androidx.media3.datasource.DataSourceBitmapLoader(this)))
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return librarySession
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon().build()
            val playerCommands = MediaSession.ConnectionResult.DEFAULT_PLAYER_COMMANDS.buildUpon()
                .add(Player.COMMAND_SEEK_TO_NEXT)
                .add(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS)
                .add(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .add(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                .add(Player.COMMAND_PLAY_PAUSE)
                .add(Player.COMMAND_STOP)
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_NOT_SUPPORTED))
        }

        override fun onGetLibraryRoot(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<MediaItem>> {
            return Futures.immediateFuture(LibraryResult.ofItem(browseTree.rootItem(), params))
        }

        override fun onGetItem(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            mediaId: String
        ): ListenableFuture<LibraryResult<MediaItem>> = serviceScope.future {
            val item = browseTree.item(mediaId)
            if (item != null) LibraryResult.ofItem(item, null)
            else LibraryResult.ofError(LibraryResult.RESULT_ERROR_BAD_VALUE)
        }

        override fun onGetChildren(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            parentId: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val all = browseTree.children(parentId)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            val pageItems = if (from < to) all.subList(from, to) else emptyList()
            LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
        }

        override fun onSearch(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<Void>> = serviceScope.future {
            val results = browseTree.search(query)
            session.notifySearchResultChanged(browser, query, results.size, params)
            LibraryResult.ofVoid()
        }

        override fun onGetSearchResult(
            session: MediaLibrarySession,
            browser: MediaSession.ControllerInfo,
            query: String,
            page: Int,
            pageSize: Int,
            params: LibraryParams?
        ): ListenableFuture<LibraryResult<ImmutableList<MediaItem>>> = serviceScope.future {
            val all = browseTree.search(query)
            val from = (page * pageSize).coerceAtMost(all.size)
            val to = (from + pageSize).coerceAtMost(all.size)
            val pageItems = if (from < to) all.subList(from, to) else emptyList()
            LibraryResult.ofItemList(ImmutableList.copyOf(pageItems), params)
        }

        /**
         * Auto sends play requests with mediaId only — we must add the URI
         * by resolving against our song list.
         */
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            mediaItems.map { item ->
                if (item.localConfiguration?.uri != null) {
                    item
                } else {
                    browseTree.resolve(item.mediaId) ?: item
                }
            }.toMutableList()
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        savePlaybackStateFromService()
        librarySession?.player?.apply {
            stop()
            clearMediaItems()
        }
        stopSelf()
    }

    private fun savePlaybackStateFromService() {
        val p = player ?: return
        val mediaItem = p.currentMediaItem ?: return
        val metadata = mediaItem.mediaMetadata

        val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
        prefs.edit()
            .putString("last_song_id", mediaItem.mediaId)
            .putString("last_song_title", metadata.title?.toString() ?: "")
            .putString("last_song_artist", metadata.artist?.toString()?.split(" • ")?.firstOrNull() ?: "")
            .putString("last_song_cover_url", metadata.artworkUri?.toString() ?: "")
            .putString("last_song_audio_url", mediaItem.localConfiguration?.uri?.toString() ?: "")
            .putLong("last_position", p.currentPosition)
            .putLong("last_duration", p.duration.coerceAtLeast(0L))
            .commit()
    }

    override fun onDestroy() {
        EqualizerManager.release()
        playerListener?.let { listener ->
            player?.removeListener(listener)
        }
        playerListener = null
        player?.release()
        player = null
        librarySession?.release()
        librarySession = null
        super.onDestroy()
    }

    companion object {
        private const val TAG = "MediaPlaybackService"
    }
}
