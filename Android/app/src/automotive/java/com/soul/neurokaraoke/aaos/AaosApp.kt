package com.soul.neurokaraoke.aaos

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.media3.session.MediaController
import com.soul.neurokaraoke.data.model.Playlist

sealed interface AaosRoute {
    data object Home : AaosRoute
    data class PlaylistDetail(val playlist: Playlist) : AaosRoute
    data object NowPlaying : AaosRoute
    data object Pairing : AaosRoute
}

@Composable
fun AaosApp(
    viewModel: AaosViewModel,
    controllerProvider: () -> MediaController?,
    navOverride: AaosRoute? = null,
    onNavOverrideHandled: () -> Unit = {}
) {
    var route: AaosRoute by rememberSaveable(stateSaver = AaosRouteSaver) {
        mutableStateOf(AaosRoute.Home)
    }

    androidx.compose.runtime.LaunchedEffect(navOverride) {
        if (navOverride != null) {
            route = navOverride
            onNavOverrideHandled()
        }
    }

    val pairingStatus by viewModel.pairingStatus.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    androidx.compose.runtime.LaunchedEffect(isLoggedIn, pairingStatus) {
        if (isLoggedIn && pairingStatus == "Signed in") {
            route = AaosRoute.Home
            viewModel.clearPairingStatus()
        }
    }

    when (val r = route) {
        is AaosRoute.Home -> AaosHomeScreen(
            viewModel = viewModel,
            controllerProvider = controllerProvider,
            onPlaylistClick = { route = AaosRoute.PlaylistDetail(it) },
            onNowPlayingClick = { route = AaosRoute.NowPlaying },
            onPairClick = { route = AaosRoute.Pairing }
        )
        is AaosRoute.PlaylistDetail -> AaosPlaylistDetailScreen(
            playlist = r.playlist,
            viewModel = viewModel,
            controllerProvider = controllerProvider,
            onBack = { route = AaosRoute.Home },
            onNowPlayingClick = { route = AaosRoute.NowPlaying }
        )
        is AaosRoute.NowPlaying -> AaosNowPlayingScreen(
            controllerProvider = controllerProvider,
            onBack = { route = AaosRoute.Home }
        )
        is AaosRoute.Pairing -> AaosPairingScreen(
            statusMessage = pairingStatus,
            onSubmit = { viewModel.redeemPairingCode(it) },
            onBack = { route = AaosRoute.Home; viewModel.clearPairingStatus() }
        )
    }
}

private val AaosRouteSaver = androidx.compose.runtime.saveable.Saver<AaosRoute, String>(
    save = { when (it) {
        AaosRoute.Home -> "home"
        AaosRoute.NowPlaying -> "now"
        AaosRoute.Pairing -> "home"
        is AaosRoute.PlaylistDetail -> "home" // don't restore deep state
    } },
    restore = { AaosRoute.Home }
)
