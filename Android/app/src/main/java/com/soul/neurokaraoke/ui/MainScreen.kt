package com.soul.neurokaraoke.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.unit.dp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soul.neurokaraoke.navigation.NavGraph
import com.soul.neurokaraoke.navigation.Screen
import com.soul.neurokaraoke.ui.components.AddToPlaylistSheet
import com.soul.neurokaraoke.ui.components.MiniPlayer
import com.soul.neurokaraoke.ui.components.NavigationDrawerContent
import com.soul.neurokaraoke.ui.components.NeuroTopBar
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository
import com.soul.neurokaraoke.ui.screens.player.PlayerScreen
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.DownloadViewModel
import com.soul.neurokaraoke.viewmodel.PlayerViewModel
import com.soul.neurokaraoke.ui.theme.CyberpunkBackground
import com.soul.neurokaraoke.ui.theme.GlassCard
import com.soul.neurokaraoke.ui.theme.NeonTheme
import com.soul.neurokaraoke.viewmodel.RepeatMode
import kotlinx.coroutines.launch
import android.util.Log

// Fallback playlist ID if catalog is empty
private const val FALLBACK_PLAYLIST_ID = "359bc793-0b63-4b89-b0ea-c3a4d068decc"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val navController = rememberNavController()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    // Collect player state from ViewModel
    val playerState by playerViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val downloadedSongs by downloadViewModel.downloads.collectAsState()
    val downloadProgress by downloadViewModel.downloadProgress.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }

    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showAlphaBanner by remember { mutableStateOf(true) }
    var songToAddToPlaylist by remember { mutableStateOf<Song?>(null) }
    val userPlaylistRepository = remember { UserPlaylistRepository(context) }
    val favoritesRepository = remember { FavoritesRepository(context) }
    val favoriteSongs by favoritesRepository.favorites.collectAsState()
    val isRefreshingFavorites by favoritesRepository.isSyncing.collectAsState()
    val isSyncingPlaylists by userPlaylistRepository.isSyncing.collectAsState()

    // Sync favorites and playlists when user logs in
    LaunchedEffect(authState.isLoggedIn) {
        val token = authViewModel.getAccessToken()
        if (authState.isLoggedIn && token != null) {
            Log.d("MainScreen", "User logged in, syncing favorites and playlists...")
            favoritesRepository.syncFromServer(token)
            userPlaylistRepository.syncFromServer(token)
        }
    }

    // Load first available playlist on first launch only.
    // Key on isNotEmpty() — not the full list — so refreshPlaylistNames() updates don't
    // re-fire this and clobber the queue when the user is playing from Search.
    LaunchedEffect(playerState.availablePlaylists.isNotEmpty()) {
        if (playerState.currentPlaylistId == null && playerState.availablePlaylists.isNotEmpty()) {
            val playlistId = playerState.availablePlaylists.firstOrNull()?.id ?: FALLBACK_PLAYLIST_ID
            playerViewModel.loadPlaylist(playlistId)
        }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet {
                NavigationDrawerContent(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                    onClose = {
                        scope.launch { drawerState.close() }
                    },
                    isLoggedIn = authState.isLoggedIn,
                    userName = authState.user?.displayName,
                    userAvatarUrl = authState.user?.avatarUrl,
                    onSignInClick = {
                        scope.launch { drawerState.close() }
                        context.startActivity(authViewModel.getSignInIntent())
                    },
                    onSignOutClick = {
                        authViewModel.logout()
                    },
                    onRandomSongClick = {
                        playerViewModel.playRandomSong()
                        scope.launch { drawerState.close() }
                    }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                NeuroTopBar(
                    onMenuClick = {
                        scope.launch { drawerState.open() }
                    },
                    onProfileClick = {
                        scope.launch { drawerState.open() }
                    },
                    avatarUrl = authState.user?.avatarUrl
                )
            }
        ) { paddingValues ->
            CyberpunkBackground(
                modifier = Modifier.padding(paddingValues)
            ) {
                // Main content
                NavGraph(
                    navController = navController,
                    songs = playerState.songs,
                    allSongs = playerState.allSongs,
                    isLoadingAllSongs = playerState.isLoadingAllSongs,
                    playlists = playerState.availablePlaylists,
                    currentPlaylistId = playerState.currentPlaylistId,
                    isLoading = playerState.isLoading,
                    favoriteSongs = favoriteSongs,
                    currentSong = playerState.currentSong,
                    onSongClick = { songId ->
                        playerViewModel.playSongById(songId)
                    },
                    onSearchSongClick = { songId ->
                        playerViewModel.playSongFromAllSongs(songId)
                    },
                    onPlaySongWithQueue = { song, queue ->
                        playerViewModel.playSongWithQueue(song, queue)
                    },
                    onPlaylistSelect = { playlist ->
                        playerViewModel.selectPlaylist(playlist)
                    },
                    onExpandPlayer = {
                        showFullPlayer = true
                    },
                    onLoadAllSongs = {
                        playerViewModel.loadAllSongs()
                    },
                    apiArtists = playerState.apiArtists,
                    isLoadingArtists = playerState.isLoadingArtists,
                    onLoadArtists = {
                        playerViewModel.loadArtists()
                    },
                    onPlayClick = {
                        // Play first song in current playlist
                        playerState.songs.firstOrNull()?.let { song ->
                            playerViewModel.playSongById(song.id)
                        }
                    },
                    onShuffleClick = {
                        // Enable shuffle and play
                        if (!playerState.isShuffleEnabled) {
                            playerViewModel.toggleShuffle()
                        }
                        playerState.songs.randomOrNull()?.let { song ->
                            playerViewModel.playSongById(song.id)
                        }
                    },
                    // Download-related
                    downloadedSongs = downloadedSongs,
                    downloadProgress = downloadProgress,
                    downloadTotalSize = downloadViewModel.getTotalSizeFormatted(),
                    onDownloadSong = { song -> downloadViewModel.downloadSong(song) },
                    onRemoveDownload = { songId -> downloadViewModel.removeSong(songId) },
                    onRemoveAllDownloads = { downloadViewModel.removeAll() },
                    isDownloaded = { songId -> downloadViewModel.isDownloaded(songId) },
                    onPlayDownloaded = { songId ->
                        // Find the song from downloads and play with all downloads as queue
                        val dlSongs = downloadedSongs.map { it.toSong() }
                        val song = dlSongs.find { it.id == songId }
                        if (song != null) {
                            playerViewModel.playSongWithQueue(song, dlSongs)
                        }
                    },
                    onPlayAllDownloads = {
                        val dlSongs = downloadedSongs.map { it.toSong() }
                        dlSongs.firstOrNull()?.let { song ->
                            playerViewModel.playSongWithQueue(song, dlSongs)
                        }
                    },
                    onShuffleDownloads = {
                        val dlSongs = downloadedSongs.map { it.toSong() }
                        if (!playerState.isShuffleEnabled) {
                            playerViewModel.toggleShuffle()
                        }
                        dlSongs.randomOrNull()?.let { song ->
                            playerViewModel.playSongWithQueue(song, dlSongs)
                        }
                    },
                    userPlaylistRepository = userPlaylistRepository,
                    onAddToPlaylist = { song -> songToAddToPlaylist = song },
                    favoritesRepository = favoritesRepository,
                    accessToken = authViewModel.getAccessToken(),
                    isRefreshingFavorites = isRefreshingFavorites,
                    onRefreshFavorites = {
                        val token = authViewModel.getAccessToken()
                        if (token != null) {
                            scope.launch { favoritesRepository.syncFromServer(token) }
                        }
                    },
                    // Playlists sync
                    isSyncingPlaylists = isSyncingPlaylists,
                    onRefreshPlaylists = {
                        val token = authViewModel.getAccessToken()
                        if (token != null) {
                            scope.launch { userPlaylistRepository.syncFromServer(token) }
                        }
                    },
                    // Radio
                    isRadioPlaying = playerState.isRadioMode && playerState.isPlaying,
                    onRadioListen = { playerViewModel.playRadio() },
                    onRadioStop = { playerViewModel.stopRadio() }
                )

                // Alpha banner at top
                if (showAlphaBanner) {
                    GlassCard(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp)
                            .align(Alignment.TopCenter),
                        backgroundAlpha = 0.92f,
                        showGlow = true,
                        glowRadius = 4.dp
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp, end = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = null,
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Alpha Version",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "This app is still in development. Report bugs and feedback to @aferil. on Discord!",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.8f)
                                )
                            }
                            IconButton(onClick = { showAlphaBanner = false }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Dismiss",
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }

                // Mini player at bottom
                MiniPlayer(
                    currentSong = playerState.currentSong,
                    isPlaying = playerState.isPlaying,
                    progress = playerState.progress,
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onPreviousClick = { playerViewModel.playPrevious() },
                    onNextClick = { playerViewModel.playNext() },
                    onExpandClick = {
                        if (playerState.isRadioMode) {
                            // Navigate to Radio screen instead of full player
                            navController.navigate(Screen.Radio.route) {
                                popUpTo(Screen.Home.route) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        } else {
                            showFullPlayer = true
                        }
                    },
                    sleepTimerActive = playerState.sleepTimerEndTimeMs != null || playerState.sleepTimerEndOfSong,
                    isRadioMode = playerState.isRadioMode,
                    onRadioStopClick = { playerViewModel.stopRadio() },
                    modifier = Modifier.align(Alignment.BottomCenter)
                )
            }
        }
    }

    // Full player bottom sheet
    val currentSong = playerState.currentSong
    if (showFullPlayer && currentSong != null) {
        ModalBottomSheet(
            onDismissRequest = { showFullPlayer = false },
            sheetState = playerSheetState,
            dragHandle = null,
            containerColor = MaterialTheme.colorScheme.background.copy(alpha = 0.95f)
        ) {
            PlayerScreen(
                song = currentSong,
                isPlaying = playerState.isPlaying,
                progress = playerState.progress,
                currentPosition = playerState.currentPosition,
                duration = playerState.duration,
                isShuffleEnabled = playerState.isShuffleEnabled,
                repeatMode = playerState.repeatMode,
                queue = playerState.queue,
                onPlayPauseClick = { playerViewModel.togglePlayPause() },
                onPreviousClick = { playerViewModel.playPrevious() },
                onNextClick = { playerViewModel.playNext() },
                onSeekTo = { playerViewModel.seekTo(it) },
                onShuffleClick = { playerViewModel.toggleShuffle() },
                onRepeatClick = { playerViewModel.cycleRepeatMode() },
                onCollapseClick = { showFullPlayer = false },
                onQueueSongClick = { songId -> playerViewModel.playSongById(songId) },
                isDownloaded = downloadViewModel.isDownloaded(currentSong.id),
                downloadProgress = downloadProgress[currentSong.id],
                onDownloadClick = { downloadViewModel.downloadSong(currentSong) },
                sleepTimerRemainingMs = playerState.sleepTimerRemainingMs,
                sleepTimerActive = playerState.sleepTimerEndTimeMs != null || playerState.sleepTimerEndOfSong,
                onSetSleepTimer = { minutes -> playerViewModel.setSleepTimer(minutes) },
                onCancelSleepTimer = { playerViewModel.cancelSleepTimer() },
                onSetSleepTimerEndOfSong = { playerViewModel.setSleepTimerEndOfSong() },
                isFavorite = favoritesRepository.isFavorite(currentSong.id, currentSong.audioUrl),
                onToggleFavorite = { favoritesRepository.toggleFavorite(currentSong, authViewModel.getAccessToken()) },
                onAddToPlaylist = { songToAddToPlaylist = currentSong }
            )
        }
    }

    // Add to Playlist sheet
    songToAddToPlaylist?.let { song ->
        AddToPlaylistSheet(
            song = song,
            repository = userPlaylistRepository,
            onDismiss = { songToAddToPlaylist = null }
        )
    }

}
