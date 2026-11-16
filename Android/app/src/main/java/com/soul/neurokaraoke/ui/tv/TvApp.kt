package com.soul.neurokaraoke.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusGroup
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import coil.request.ImageRequest
import com.soul.neurokaraoke.data.api.ApiPublicPlaylist
import com.soul.neurokaraoke.data.api.Video
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.SettingsRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository
import com.soul.neurokaraoke.ui.tv.neurolings.NeurolingsCounts
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.PlayerViewModel

@Composable
fun TvApp(playerViewModel: PlayerViewModel, authViewModel: AuthViewModel) {
    var tab by remember { mutableStateOf(TvTab.HOME) }
    // Holds the playlist the user drilled into from a rail's cover card.
    var selectedPlaylist by remember { mutableStateOf<Playlist?>(null) }
    // Private user playlists need the authenticated song loader in the detail overlay;
    // setlists/public use the default public loader. Tracks which the drilled-in card was.
    var selectedIsUserPlaylist by remember { mutableStateOf(false) }
    // The video the user opened from the Videos tab (fullscreen HLS player overlay).
    var selectedVideo by remember { mutableStateOf<Video?>(null) }
    // Immersive fullscreen for Radio / Now Playing — hides the nav bar so art +
    // lyrics fill the screen. Back exits it.
    var fullscreen by remember { mutableStateOf(false) }
    BackHandler(enabled = fullscreen) { fullscreen = false }

    // Settings overlay (corner gear). Gated the same way as the detail overlay.
    var showSettings by remember { mutableStateOf(false) }
    val gearFocusRequester = remember { FocusRequester() }
    // Only re-grab focus onto the gear when the panel actually *closes* (not on first
    // composition, where a nav tab should hold focus instead).
    var settingsWasOpen by remember { mutableStateOf(false) }
    LaunchedEffect(showSettings) {
        if (showSettings) {
            settingsWasOpen = true
        } else if (settingsWasOpen) {
            settingsWasOpen = false
            runCatching { gearFocusRequester.requestFocus() }
        }
    }
    val accessToken = authViewModel.getAccessToken()
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()

    // Library collections — hoisted here so their state survives tab switches and the
    // detail overlay. Setlists stay inside TvLibraryScreen (they ride the player state).
    val context = LocalContext.current
    val favoritesRepo = remember { FavoritesRepository(context) }
    val userPlaylistRepo = remember { UserPlaylistRepository(context) }
    val favourites by favoritesRepo.favorites.collectAsStateWithLifecycle()
    val userPlaylists by userPlaylistRepo.playlists.collectAsStateWithLifecycle()

    var publicPlaylists by remember { mutableStateOf<List<Playlist>>(emptyList()) }
    val nkApi = remember { NeuroKaraokeApi() }
    LaunchedEffect(Unit) {
        nkApi.fetchPublicPlaylists().onSuccess { list ->
            publicPlaylists = list.map { it.toTvPlaylist() }
        }
    }
    // Pull server-synced favourites + playlists once signed in.
    LaunchedEffect(accessToken) {
        val token = accessToken
        if (!token.isNullOrBlank()) {
            favoritesRepo.syncFromServer(token)
            userPlaylistRepo.syncFromServer(token)
        }
    }

    // Dev-only walking mascots, shown on every tab once any per-character count is above zero.
    val neurolingsCounts by SettingsRepository.neurolingsCounts.collectAsStateWithLifecycle()

    // Hoisted so TvApp can re-request nav focus after the detail overlay closes (TvNavBar
    // still does its own one-shot initial-focus request on first composition).
    val navFocusRequester = remember { FocusRequester() }

    // The detail overlay (TvDetailScreen) owns its own initial focus on its Play button when it
    // opens (selectedPlaylist becomes non-null), so this effect intentionally does nothing then —
    // it only fires on the transition back to null, i.e. when the overlay's BackHandler closes it
    // and nothing else in the tree still holds D-pad focus.
    LaunchedEffect(selectedPlaylist) {
        if (selectedPlaylist == null) {
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    // Same focus restore when the fullscreen video player overlay closes.
    LaunchedEffect(selectedVideo) {
        if (selectedVideo == null) {
            runCatching { navFocusRequester.requestFocus() }
        }
    }

    // TvApp is the TV entry point — unlike the phone, there is no NavGraph
    // composable(Screen.Search.route) { LaunchedEffect(Unit) { onLoadAllSongs() } }
    // to trigger this, so Home's "Trending"/"New Releases" rails and the Search
    // screen would otherwise stay empty forever on a fresh install.
    // availablePlaylists doesn't need a matching call here: PlayerViewModel.init()
    // already calls loadAvailablePlaylists() unconditionally on construction.
    LaunchedEffect(Unit) {
        playerViewModel.loadAllSongs()
    }

    Box(Modifier.fillMaxSize()) {
        // tvOS-style immersive backdrop: the current song's cover art, blurred and
        // dimmed, sits behind the whole UI so it doesn't read as flat black.
        TvImmersiveBackground(playerViewModel)

        Column(
            Modifier
                .fillMaxSize()
                .focusProperties { canFocus = selectedPlaylist == null && selectedVideo == null && !showSettings }
                .focusGroup()
        ) {
            if (!fullscreen) {
                Box(Modifier.fillMaxWidth().padding(24.dp)) {
                    // Nav pill centered; settings gear pinned to the right corner.
                    TvNavBar(
                        selected = tab,
                        onSelect = { tab = it },
                        focusRequester = navFocusRequester,
                        modifier = Modifier.align(Alignment.Center)
                    )
                    TvSettingsButton(
                        onClick = { showSettings = true },
                        focusRequester = gearFocusRequester,
                        modifier = Modifier.align(Alignment.CenterEnd)
                    )
                }
            }
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                when (tab) {
                    TvTab.HOME -> TvHomeScreen(
                        onPlay = { song -> playerViewModel.playSong(song) },
                        onOpenDetail = { playlist -> selectedPlaylist = playlist },
                        playerViewModel = playerViewModel
                    )
                    TvTab.LIBRARY -> TvLibraryScreen(
                        publicPlaylists = publicPlaylists,
                        favourites = favourites,
                        userPlaylists = userPlaylists,
                        isLoggedIn = authState.isLoggedIn,
                        onOpenDetail = { playlist ->
                            selectedIsUserPlaylist = false; selectedPlaylist = playlist
                        },
                        onOpenUserPlaylist = { playlist ->
                            selectedIsUserPlaylist = true; selectedPlaylist = playlist
                        },
                        onPlayFavourite = { song, queue -> playerViewModel.playSongWithQueue(song, queue) },
                        onSignIn = { tab = TvTab.ACCOUNT },
                        playerViewModel = playerViewModel
                    )
                    TvTab.NOW_PLAYING -> TvNowPlayingScreen(
                        playerViewModel = playerViewModel,
                        accessToken = accessToken,
                        fullscreen = fullscreen,
                        onToggleFullscreen = { fullscreen = !fullscreen }
                    )
                    TvTab.SEARCH -> TvSearchScreen(
                        onPlay = { song, results -> playerViewModel.playSongWithQueue(song, results) },
                        playerViewModel = playerViewModel
                    )
                    TvTab.RADIO -> TvRadioScreen(
                        playerViewModel = playerViewModel,
                        accessToken = accessToken,
                        fullscreen = fullscreen,
                        onToggleFullscreen = { fullscreen = !fullscreen }
                    )
                    TvTab.VIDEOS -> TvVideosScreen(onPlay = { selectedVideo = it })
                    TvTab.ACCOUNT -> TvAccountScreen(authViewModel = authViewModel)
                }
            }
        }

        // For-fun mascots walk on top of every tab (dev-only). Purely visual — no focus, no input
        // capture — and drawn under the settings/detail overlays so it never covers them.
        if (NeurolingsCounts.total(neurolingsCounts) > 0) {
            TvNeurolings(counts = neurolingsCounts)
        }

        selectedPlaylist?.let { playlist ->
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TvImmersiveBackground(playerViewModel)
                TvDetailScreen(
                    playlist = playlist,
                    onPlayAll = { songs -> songs.firstOrNull()?.let { playerViewModel.playSongWithQueue(it, songs) } },
                    onPlaySong = { song, songs -> playerViewModel.playSongWithQueue(song, songs) },
                    onBack = { selectedPlaylist = null },
                    songLoader = if (selectedIsUserPlaylist) {
                        { pl ->
                            userPlaylistRepo.loadPlaylistSongs(pl.id, accessToken)
                            userPlaylistRepo.getPlaylist(pl.id)?.songs ?: pl.songs
                        }
                    } else null
                )
            }
        }

        selectedVideo?.let { video ->
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
                TvVideoPlayerScreen(
                    video = video,
                    playerViewModel = playerViewModel,
                    onBack = { selectedVideo = null }
                )
            }
        }

        if (showSettings) {
            Box(
                Modifier
                    .fillMaxSize()
                    .background(MaterialTheme.colorScheme.background)
            ) {
                TvImmersiveBackground(playerViewModel)
                TvSettingsScreen(onClose = { showSettings = false })
            }
        }
    }
}

/**
 * Full-screen ambient backdrop: the now-playing cover art, heavily blurred and
 * covered by a light-to-medium scrim so foreground text and the nav bar stay
 * legible while the UI still reads as a rich tvOS-style surface rather than flat
 * black. Collects player state in its own composable so only this layer — not the
 * nav bar or content — recomposes on playback ticks. (blur is a no-op below API 31;
 * the scrim still applies, so it degrades gracefully to a dimmed cover.)
 */
/** Map a public-playlist API row to the shared [Playlist] model (mirrors ExploreScreen). */
private fun ApiPublicPlaylist.toTvPlaylist(): Playlist = Playlist(
    id = id,
    title = name,
    description = createdBy?.let { "by $it" } ?: "",
    coverUrl = coverUrl ?: "",
    previewCovers = mosaicCovers.ifEmpty { coverUrl?.let { listOf(it) } ?: emptyList() },
    songCount = songCount,
    updatedAt = updatedAt ?: 0L,
    playCount = playCount
)

@Composable
private fun TvImmersiveBackground(playerViewModel: PlayerViewModel) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val cover = playerState.currentSong?.coverUrl
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        if (!cover.isNullOrBlank()) {
            val context = LocalContext.current
            AsyncImage(
                // Blur via a Coil transformation, not Modifier.blur() — the latter is a
                // no-op below API 31, so older TV / Fire OS devices showed a sharp cover.
                model = ImageRequest.Builder(context)
                    .data(cover)
                    .transformations(BlurTransformation())
                    .crossfade(true)
                    .build(),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }
        Box(
            Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        listOf(
                            MaterialTheme.colorScheme.background.copy(alpha = 0.50f),
                            MaterialTheme.colorScheme.background.copy(alpha = 0.80f)
                        )
                    )
                )
        )
    }
}
