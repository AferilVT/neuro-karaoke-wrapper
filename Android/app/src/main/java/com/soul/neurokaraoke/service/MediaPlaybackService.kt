package com.soul.neurokaraoke.service

import android.app.Application
import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.CommandButton
import androidx.media3.session.LibraryResult
import androidx.media3.session.MediaLibraryService
import androidx.media3.session.MediaSession
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.collect.ImmutableList
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import com.soul.neurokaraoke.aaos.AaosRadioPoller
import com.soul.neurokaraoke.audio.AudioCacheManager
import com.soul.neurokaraoke.audio.EqualizerManager
import com.soul.neurokaraoke.data.repository.DownloadRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.withContext

@UnstableApi
class MediaPlaybackService : MediaLibraryService() {
    private var librarySession: MediaLibrarySession? = null
    private var player: ExoPlayer? = null
    private var playerListener: Player.Listener? = null
    private lateinit var browseTree: AutoBrowseTree
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun getAttributionTag(): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) "neuroAudio" else super.getAttributionTag()
    }

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

        // Load and apply saved playback settings (shuffle/repeat)
        val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
        player?.shuffleModeEnabled = prefs.getBoolean("shuffle_enabled", false)
        val repeatModeName = prefs.getString("repeat_mode", "OFF")
        player?.repeatMode = when (repeatModeName) {
            "ONE" -> Player.REPEAT_MODE_ONE
            "ALL" -> Player.REPEAT_MODE_ALL
            else -> Player.REPEAT_MODE_OFF
        }

        val sessionActivityPendingIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, com.soul.neurokaraoke.MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        player?.let { exoPlayer ->
            // Wrap player to ensure all commands are always reported as available.
            // This is a "power user" approach to force system UIs to enable all controls.
            val forwardingPlayer = object : ForwardingPlayer(exoPlayer) {
                override fun getAvailableCommands(): Player.Commands {
                    val mediaId = wrappedPlayer.currentMediaItem?.mediaId
                    val isRadio = mediaId == "radio_live" || mediaId == "nk_radio"

                    val commands = Player.Commands.Builder().addAllCommands()
                    if (isRadio) {
                        commands.remove(Player.COMMAND_SEEK_TO_NEXT)
                        commands.remove(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM)
                        commands.remove(Player.COMMAND_SEEK_TO_PREVIOUS)
                        commands.remove(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                        commands.remove(Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM)
                    }
                    return commands.build()
                }

                override fun isCommandAvailable(command: Int): Boolean {
                    val mediaId = wrappedPlayer.currentMediaItem?.mediaId
                    val isRadio = mediaId == "radio_live" || mediaId == "nk_radio"

                    if (isRadio) {
                        return when (command) {
                            Player.COMMAND_SEEK_TO_NEXT,
                            Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM,
                            Player.COMMAND_SEEK_TO_PREVIOUS,
                            Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM,
                            Player.COMMAND_SEEK_IN_CURRENT_MEDIA_ITEM -> false
                            else -> true
                        }
                    }
                    return true
                }

                override fun getCurrentPosition(): Long {
                    val mediaId = wrappedPlayer.currentMediaItem?.mediaId
                    if (mediaId == "radio_live" || mediaId == "nk_radio") {
                        return AaosRadioPoller.getRadioProgressMs()
                    }
                    return super.getCurrentPosition()
                }

                override fun getDuration(): Long {
                    val mediaId = wrappedPlayer.currentMediaItem?.mediaId
                    if (mediaId == "radio_live" || mediaId == "nk_radio") {
                        return AaosRadioPoller.getRadioDurationMs()
                    }
                    return super.getDuration()
                }

                override fun getBufferedPosition(): Long {
                    val mediaId = wrappedPlayer.currentMediaItem?.mediaId
                    if (mediaId == "radio_live" || mediaId == "nk_radio") {
                        return AaosRadioPoller.getRadioProgressMs()
                    }
                    return super.getBufferedPosition()
                }
            }

            val listener = object : Player.Listener {
                override fun onPlaybackStateChanged(playbackState: Int) {
                    if (playbackState == Player.STATE_READY) {
                        EqualizerManager.initialize(exoPlayer.audioSessionId)
                    }
                }

                override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                    val mediaId = exoPlayer.currentMediaItem?.mediaId
                    val isRadio = mediaId == "radio_live" || mediaId == AutoBrowseTree.RADIO_ID
                    if (isRadio) {
                        if (playWhenReady) AaosRadioPoller.start(exoPlayer)
                        else AaosRadioPoller.stop()
                    }
                }

                override fun onTimelineChanged(timeline: Timeline, reason: Int) {
                    librarySession?.let { s ->
                        val count = s.player.mediaItemCount
                        s.notifyChildrenChanged("@android:queue@", count, null)
                        s.notifyChildrenChanged("@android:queue_all@", count, null)
                        s.notifyChildrenChanged("nk_queue", count, null)
                    }
                }

                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                    val mediaId = mediaItem?.mediaId
                    val isRadio = mediaId == "radio_live" || mediaId == AutoBrowseTree.RADIO_ID
                    if (isRadio) {
                        if (exoPlayer.playWhenReady) {
                            AaosRadioPoller.start(exoPlayer)
                        }
                    } else {
                        AaosRadioPoller.stop()
                    }

                    librarySession?.let { s ->
                        s.setCustomLayout(getCustomLayout(s.player))
                    }
                }
            }
            playerListener = listener
            exoPlayer.addListener(listener)

            librarySession = MediaLibrarySession.Builder(this, forwardingPlayer, LibraryCallback())
                .setSessionActivity(sessionActivityPendingIntent)
                .setBitmapLoader(CacheBitmapLoader(androidx.media3.datasource.DataSourceBitmapLoader(this)))
                .build()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaLibrarySession? {
        return librarySession
    }


    private fun getCustomLayout(p: Player?): ImmutableList<CommandButton> {
        val mediaId = p?.currentMediaItem?.mediaId
        if (mediaId == "radio_live" || mediaId == "nk_radio") {
            return ImmutableList.of()
        }

        val isShuffle = p?.shuffleModeEnabled == true
        val repeatMode = p?.repeatMode ?: Player.REPEAT_MODE_OFF

        val shuffleIcon = if (isShuffle)
            androidx.media3.ui.R.drawable.exo_icon_shuffle_on
        else
            androidx.media3.ui.R.drawable.exo_icon_shuffle_off

        val repeatIcon = when (repeatMode) {
            Player.REPEAT_MODE_ONE -> androidx.media3.ui.R.drawable.exo_icon_repeat_one
            Player.REPEAT_MODE_ALL -> androidx.media3.ui.R.drawable.exo_icon_repeat_all
            else -> androidx.media3.ui.R.drawable.exo_icon_repeat_off
        }

        val shuffleButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand("ACTION_SHUFFLE", android.os.Bundle.EMPTY))
            .setIconResId(shuffleIcon)
            .setDisplayName("Shuffle")
            .build()
        val repeatButton = CommandButton.Builder()
            .setSessionCommand(SessionCommand("ACTION_REPEAT", android.os.Bundle.EMPTY))
            .setIconResId(repeatIcon)
            .setDisplayName("Repeat")
            .build()

        return ImmutableList.of(shuffleButton, repeatButton)
    }

    private inner class LibraryCallback : MediaLibrarySession.Callback {

        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            val song = browseTree.resumeSong()
            if (song != null) {
                MediaSession.MediaItemsWithStartPosition(
                    ImmutableList.of(browseTree.resolve("${AutoBrowseTree.RESUME_PREFIX}${song.id}")!!),
                    0,
                    0L
                )
            } else {
                throw UnsupportedOperationException()
            }
        }

        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo
        ): MediaSession.ConnectionResult {
            val shuffleCommand = SessionCommand("ACTION_SHUFFLE", android.os.Bundle.EMPTY)
            val repeatCommand = SessionCommand("ACTION_REPEAT", android.os.Bundle.EMPTY)
            val sessionCommands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .add(shuffleCommand)
                .add(repeatCommand)
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_LIBRARY_ROOT))
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_CHILDREN))
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_ITEM))
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_SEARCH))
                .add(SessionCommand(SessionCommand.COMMAND_CODE_LIBRARY_GET_SEARCH_RESULT))
                .build()

            val playerCommands = Player.Commands.Builder()
                .addAllCommands()
                .build()

            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(sessionCommands)
                .setAvailablePlayerCommands(playerCommands)
                .setCustomLayout(getCustomLayout(session.player))
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: android.os.Bundle
        ): ListenableFuture<SessionResult> {
            val action = customCommand.customAction
            if (action == "ACTION_SHUFFLE") {
                session.player.shuffleModeEnabled = !session.player.shuffleModeEnabled
                
                // Persist change so it survives service/app restarts and stays in sync with ViewModel
                val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
                prefs.edit().putBoolean("shuffle_enabled", session.player.shuffleModeEnabled).apply()

                // Force a full session refresh by simulating the onTimelineChanged event
                librarySession?.let { s ->
                    s.notifyChildrenChanged("@android:queue@", s.player.mediaItemCount, null)
                    s.notifyChildrenChanged("@android:queue_all@", s.player.mediaItemCount, null)
                    s.notifyChildrenChanged("nk_queue", s.player.mediaItemCount, null)
                    s.notifyChildrenChanged("nk_root", 0, null)
                }
                librarySession?.setCustomLayout(getCustomLayout(session.player))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            } else if (action == "ACTION_REPEAT") {
                session.player.repeatMode = when (session.player.repeatMode) {
                    Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
                    Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
                    else -> Player.REPEAT_MODE_OFF
                }

                // Persist change so it survives service/app restarts and stays in sync with ViewModel
                val repeatModeName = when (session.player.repeatMode) {
                    Player.REPEAT_MODE_ONE -> "ONE"
                    Player.REPEAT_MODE_ALL -> "ALL"
                    else -> "OFF"
                }
                val prefs = getSharedPreferences("playback_state", MODE_PRIVATE)
                prefs.edit().putString("repeat_mode", repeatModeName).apply()

                // Notify that the queue "changed" (status icons/order might need refresh in some UIs)
                librarySession?.let { s ->
                    s.notifyChildrenChanged("@android:queue@", s.player.mediaItemCount, null)
                    s.notifyChildrenChanged("@android:queue_all@", s.player.mediaItemCount, null)
                    s.notifyChildrenChanged("nk_queue", s.player.mediaItemCount, null)
                }

                librarySession?.setCustomLayout(getCustomLayout(session.player))
                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
            }
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
            // First, try to find the item in the current player's timeline
            val itemFromPlayer = withContext(Dispatchers.Main) {
                val player = session.player
                for (i in 0 until player.mediaItemCount) {
                    val item = player.getMediaItemAt(i)
                    if (item.mediaId == mediaId) {
                        return@withContext item
                    }
                }
                null
            }

            if (itemFromPlayer != null) {
                return@future LibraryResult.ofItem(itemFromPlayer, null)
            }

            // Fallback to the browse tree
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
            // Handle special system IDs for the playback queue
            if (parentId == "@android:queue@" || parentId == "@android:queue_all@" || parentId == "nk_queue") {
                val items = withContext(Dispatchers.Main) {
                    val player = session.player
                    val queueItems = mutableListOf<MediaItem>()
                    
                    if (player.shuffleModeEnabled && parentId != "@android:queue_all@") {
                        // Return items in shuffled order as they will be played
                        val timeline = player.currentTimeline
                        var nextIdx = player.currentMediaItemIndex
                        val seen = mutableSetOf<Int>()
                        
                        while (nextIdx != C.INDEX_UNSET && !seen.contains(nextIdx) && queueItems.size < player.mediaItemCount) {
                            queueItems.add(player.getMediaItemAt(nextIdx))
                            seen.add(nextIdx)
                            nextIdx = timeline.getNextWindowIndex(nextIdx, Player.REPEAT_MODE_OFF, true)
                        }
                        
                        // If we missed some items (e.g. they were before current in shuffle order),
                        // fill them in to ensure a full list.
                        if (queueItems.size < player.mediaItemCount) {
                            for (i in 0 until player.mediaItemCount) {
                                if (!seen.contains(i)) {
                                    queueItems.add(player.getMediaItemAt(i))
                                    seen.add(i)
                                }
                            }
                        }
                    } else {
                        // Standard order
                        for (i in 0 until player.mediaItemCount) {
                            queueItems.add(player.getMediaItemAt(i))
                        }
                    }
                    ImmutableList.copyOf(queueItems)
                }
                return@future LibraryResult.ofItemList(items, params)
            }

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
         * System sends play requests with mediaId only — we must add the URI
         * by resolving against our song list. Also expands folders into playlists.
         */

        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>
        ): ListenableFuture<MutableList<MediaItem>> = serviceScope.future {
            Log.d("NK_SERVICE", "onAddMediaItems: count=${mediaItems.size}, ids=${mediaItems.map { it.mediaId }}")
            val result = resolveAndExpand(mediaItems)
            Log.d("NK_SERVICE", "onAddMediaItems: returning ${result.size} items")
            result
        }

        override fun onSetMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
            startIndex: Int,
            startPositionMs: Long
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = serviceScope.future {
            Log.d("NK_SERVICE", "onSetMediaItems: count=${mediaItems.size}, startIndex=$startIndex, ids=${mediaItems.map { it.mediaId }}")
            
            var itemsToProcess = mediaItems.toMutableList()
            var finalStartIndex = startIndex

            // If a single song from a public playlist is played, try to expand the whole playlist
            if (mediaItems.size == 1) {
                val item = mediaItems[0]
                if (item.mediaId.startsWith(AutoBrowseTree.SONG_URL_PREFIX)) {
                    val fullId = item.mediaId.removePrefix(AutoBrowseTree.SONG_URL_PREFIX)
                    val parts = fullId.split("|", limit = 2)
                    if (parts.size == 2) {
                        val parentId = parts[0]
                        val originalUrl = parts[1]
                        Log.d("NK_SERVICE", "Detected single public song, expanding parent: $parentId")
                        val children = browseTree.children(parentId)
                        if (children.isNotEmpty()) {
                            itemsToProcess = children.toMutableList()
                            val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalUrl) }
                            if (foundIdx != -1) finalStartIndex = foundIdx
                        }
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.FAVORITE_SONG_PREFIX)) {
                    val originalId = item.mediaId.removePrefix(AutoBrowseTree.FAVORITE_SONG_PREFIX)
                    Log.d("NK_SERVICE", "Detected single favorite song, expanding full favorites list")
                    val children = browseTree.children(AutoBrowseTree.FAVORITES_ID)
                    if (children.isNotEmpty()) {
                        itemsToProcess = children.toMutableList()
                        val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                        if (foundIdx != -1) finalStartIndex = foundIdx
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.ALL_SONGS_PREFIX)) {
                    val originalId = item.mediaId.removePrefix(AutoBrowseTree.ALL_SONGS_PREFIX)
                    Log.d("NK_SERVICE", "Detected single 'all songs' item, expanding list")
                    val children = browseTree.children(AutoBrowseTree.ALL_SONGS_ID)
                    if (children.isNotEmpty()) {
                        itemsToProcess = children.toMutableList()
                        val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                        if (foundIdx != -1) finalStartIndex = foundIdx
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.NEURO_SONG_PREFIX)) {
                    val originalId = item.mediaId.removePrefix(AutoBrowseTree.NEURO_SONG_PREFIX)
                    Log.d("NK_SERVICE", "Detected single Neuro song, expanding list")
                    val children = browseTree.children(AutoBrowseTree.NEURO_ID)
                    if (children.isNotEmpty()) {
                        itemsToProcess = children.toMutableList()
                        val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                        if (foundIdx != -1) finalStartIndex = foundIdx
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.EVIL_SONG_PREFIX)) {
                    val originalId = item.mediaId.removePrefix(AutoBrowseTree.EVIL_SONG_PREFIX)
                    Log.d("NK_SERVICE", "Detected single Evil song, expanding list")
                    val children = browseTree.children(AutoBrowseTree.EVIL_ID)
                    if (children.isNotEmpty()) {
                        itemsToProcess = children.toMutableList()
                        val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                        if (foundIdx != -1) finalStartIndex = foundIdx
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.DUET_SONG_PREFIX)) {
                    val originalId = item.mediaId.removePrefix(AutoBrowseTree.DUET_SONG_PREFIX)
                    Log.d("NK_SERVICE", "Detected single Duet song, expanding list")
                    val children = browseTree.children(AutoBrowseTree.DUET_ID)
                    if (children.isNotEmpty()) {
                        itemsToProcess = children.toMutableList()
                        val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                        if (foundIdx != -1) finalStartIndex = foundIdx
                    }
                } else if (item.mediaId.startsWith(AutoBrowseTree.PERSONAL_SONG_PREFIX) ||
                    item.mediaId.startsWith(AutoBrowseTree.OFFICIAL_SONG_PREFIX)) {
                    val isPersonal = item.mediaId.startsWith(AutoBrowseTree.PERSONAL_SONG_PREFIX)
                    val fullId = if (isPersonal) 
                        item.mediaId.removePrefix(AutoBrowseTree.PERSONAL_SONG_PREFIX)
                    else 
                        item.mediaId.removePrefix(AutoBrowseTree.OFFICIAL_SONG_PREFIX)
                    
                    val parts = fullId.split("|", limit = 2)
                    if (parts.size == 2) {
                        val parentId = parts[0]
                        val originalId = parts[1]
                        Log.d("NK_SERVICE", "Detected single playlist song, expanding parent: $parentId")
                        val children = browseTree.children(parentId)
                        if (children.isNotEmpty()) {
                            itemsToProcess = children.toMutableList()
                            val foundIdx = children.indexOfFirst { it.mediaId.endsWith(originalId) }
                            if (foundIdx != -1) finalStartIndex = foundIdx
                        }
                    }
                }
            }

            val resolved = resolveAndExpand(itemsToProcess)
            Log.d("NK_SERVICE", "onSetMediaItems: resolved count=${resolved.size}, startIdx=$finalStartIndex")
            MediaSession.MediaItemsWithStartPosition(resolved, finalStartIndex, startPositionMs)
        }

        private suspend fun resolveAndExpand(mediaItems: List<MediaItem>): MutableList<MediaItem> {
            val expandedItems = mutableListOf<MediaItem>()

            for (item in mediaItems) {
                // 1. Expansion: If it's a folder, get its children
                if (item.mediaMetadata.isBrowsable == true ||
                    isFolderId(item.mediaId)) {
                    Log.d("NK_SERVICE", "Expanding folder: ${item.mediaId}")
                    val children = browseTree.children(item.mediaId)
                    Log.d("NK_SERVICE", "Folder ${item.mediaId} expanded to ${children.size} items")
                    if (children.isNotEmpty()) {
                        expandedItems.addAll(children)
                    } else {
                        expandedItems.add(item)
                    }
                } else {
                    expandedItems.add(item)
                }
            }

            // 2. Resolution: Ensure every item in the final list has a valid playback URI
            val finalItems = expandedItems.map { item ->
                val existingUri = item.localConfiguration?.uri ?: item.requestMetadata.mediaUri
                if (existingUri != null) {
                    item.buildUpon().setUri(existingUri).build()
                } else {
                    val resolved = browseTree.resolve(item.mediaId)
                    if (resolved != null) {
                        resolved
                    } else {
                        Log.w("NK_SERVICE", "Failed to resolve URI for mediaId: ${item.mediaId}, using placeholder")
                        item.buildUpon().setUri(android.net.Uri.parse("https://idk.neurokaraoke.com/empty.mp3")).build()
                    }
                }
            }.toMutableList()
            
            Log.d("NK_SERVICE", "resolveAndExpand finished: ${finalItems.size} items")
            return finalItems
        }

        private fun isFolderId(id: String): Boolean {
            return id == AutoBrowseTree.FAVORITES_ID ||
                   id == AutoBrowseTree.ALL_SONGS_ID ||
                   id == AutoBrowseTree.NEURO_ID ||
                   id == AutoBrowseTree.EVIL_ID ||
                   id == AutoBrowseTree.DUET_ID ||
                   id == AutoBrowseTree.PUBLIC_PLAYLISTS_ID ||
                   id.startsWith(AutoBrowseTree.PUBLIC_PLAYLIST_PREFIX) ||
                   id.startsWith(AutoBrowseTree.PERSONAL_PLAYLIST_PREFIX) ||
                   id.startsWith(AutoBrowseTree.OFFICIAL_PLAYLIST_PREFIX)
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
        AaosRadioPoller.stop()
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
