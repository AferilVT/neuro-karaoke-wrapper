package com.soul.neurokaraoke.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.soul.neurokaraoke.navigation.NavGraph
import com.soul.neurokaraoke.navigation.Screen
import com.soul.neurokaraoke.ui.components.AddToPlaylistSheet
import com.soul.neurokaraoke.ui.components.BottomNavBar
import com.soul.neurokaraoke.ui.components.MiniPlayer
import com.soul.neurokaraoke.ui.components.NeuroTopBar
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository
import com.soul.neurokaraoke.ui.screens.player.PlayerScreen
import com.soul.neurokaraoke.ui.theme.CinematicBackground
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.DownloadViewModel
import com.soul.neurokaraoke.viewmodel.PlayerViewModel
import kotlinx.coroutines.launch
import android.util.Log

private const val FALLBACK_PLAYLIST_ID = "359bc793-0b63-4b89-b0ea-c3a4d068decc"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    playerViewModel: PlayerViewModel = viewModel(),
    authViewModel: AuthViewModel = viewModel(),
    downloadViewModel: DownloadViewModel = viewModel()
) {
    val navController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = androidx.compose.ui.platform.LocalContext.current

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playerState by playerViewModel.uiState.collectAsState()
    val authState by authViewModel.uiState.collectAsState()
    val downloadedSongs by downloadViewModel.downloads.collectAsState()
    val downloadProgress by downloadViewModel.downloadProgress.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }

    val playerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
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

    // Load first available playlist on first launch
    LaunchedEffect(playerState.availablePlaylists.isNotEmpty()) {
        if (playerState.currentPlaylistId == null && playerState.availablePlaylists.isNotEmpty()) {
            val playlistId = playerState.availablePlaylists.firstOrNull()?.id ?: FALLBACK_PLAYLIST_ID
            playerViewModel.loadPlaylist(playlistId)
        }
    }

    Scaffold(
        topBar = {
            NeuroTopBar(
                onProfileClick = {
                    if (authState.isLoggedIn) {
                        navController.navigate(Screen.About.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    } else {
                        context.startActivity(authViewModel.getSignInIntent())
                    }
                },
                avatarUrl = authState.user?.avatarUrl
            )
        },
        bottomBar = {
            Column {
                // Mini player floats above the nav bar
                MiniPlayer(
                    currentSong = playerState.currentSong,
                    isPlaying = playerState.isPlaying,
                    progress = playerState.progress,
                    onPlayPauseClick = { playerViewModel.togglePlayPause() },
                    onPreviousClick = { playerViewModel.playPrevious() },
                    onNextClick = { playerViewModel.playNext() },
                    onExpandClick = {
                        if (playerState.isRadioMode) {
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
                    onRadioStopClick = { playerViewModel.stopRadio() }
                )

                // Bottom navigation bar
                BottomNavBar(
                    currentRoute = currentRoute,
                    onNavigate = { screen ->
                        navController.navigate(screen.route) {
                            popUpTo(Screen.Home.route) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { paddingValues ->
        CinematicBackground(
            modifier = Modifier.padding(paddingValues)
        ) {
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
                onSongClick = { songId -> playerViewModel.playSongById(songId) },
                onSearchSongClick = { songId -> playerViewModel.playSongFromAllSongs(songId) },
                onPlaySongWithQueue = { song, queue -> playerViewModel.playSongWithQueue(song, queue) },
                onPlaylistSelect = { playlist -> playerViewModel.selectPlaylist(playlist) },
                onExpandPlayer = { showFullPlayer = true },
                onLoadAllSongs = { playerViewModel.loadAllSongs() },
                apiArtists = playerState.apiArtists,
                isLoadingArtists = playerState.isLoadingArtists,
                onLoadArtists = { playerViewModel.loadArtists() },
                onPlayClick = {
                    playerState.songs.firstOrNull()?.let { song ->
                        playerViewModel.playSongById(song.id)
                    }
                },
                onShuffleClick = {
                    if (!playerState.isShuffleEnabled) { playerViewModel.toggleShuffle() }
                    playerState.songs.randomOrNull()?.let { song ->
                        playerViewModel.playSongById(song.id)
                    }
                },
                downloadedSongs = downloadedSongs,
                downloadProgress = downloadProgress,
                downloadTotalSize = downloadViewModel.getTotalSizeFormatted(),
                onDownloadSong = { song -> downloadViewModel.downloadSong(song) },
                onRemoveDownload = { songId -> downloadViewModel.removeSong(songId) },
                onRemoveAllDownloads = { downloadViewModel.removeAll() },
                isDownloaded = { songId -> downloadViewModel.isDownloaded(songId) },
                onPlayDownloaded = { songId ->
                    val dlSongs = downloadedSongs.map { it.toSong() }
                    val song = dlSongs.find { it.id == songId }
                    if (song != null) { playerViewModel.playSongWithQueue(song, dlSongs) }
                },
                onPlayAllDownloads = {
                    val dlSongs = downloadedSongs.map { it.toSong() }
                    dlSongs.firstOrNull()?.let { song ->
                        playerViewModel.playSongWithQueue(song, dlSongs)
                    }
                },
                onShuffleDownloads = {
                    val dlSongs = downloadedSongs.map { it.toSong() }
                    if (!playerState.isShuffleEnabled) { playerViewModel.toggleShuffle() }
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
                    if (token != null) { scope.launch { favoritesRepository.syncFromServer(token) } }
                },
                isSyncingPlaylists = isSyncingPlaylists,
                onRefreshPlaylists = {
                    val token = authViewModel.getAccessToken()
                    if (token != null) { scope.launch { userPlaylistRepository.syncFromServer(token) } }
                },
                isRadioPlaying = playerState.isRadioMode && playerState.isPlaying,
                onRadioListen = { playerViewModel.playRadio() },
                onRadioStop = { playerViewModel.stopRadio() },
                authUser = authState.user,
                isLoggedIn = authState.isLoggedIn,
                onSignInClick = { context.startActivity(authViewModel.getSignInIntent()) },
                // settingsRepository is a singleton, accessed directly by SettingsScreen
            )
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
