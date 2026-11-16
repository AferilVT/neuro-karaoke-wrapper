package com.soul.neurokaraoke.ui.tv

import android.view.KeyEvent
import android.view.View
import androidx.activity.compose.BackHandler
import androidx.annotation.OptIn
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.soul.neurokaraoke.data.api.Video
import com.soul.neurokaraoke.viewmodel.PlayerViewModel

/** Auto-hide timeout for the transport bar + title (ms). */
private const val CONTROLS_TIMEOUT_MS = 3000

/**
 * Fullscreen native video player overlay. Hosts a Media3 [PlayerView] bound to a
 * dedicated [ExoPlayer] streaming the Bunny HLS URL. The transport bar auto-hides after
 * [CONTROLS_TIMEOUT_MS] and reappears on any D-pad press; the title fades in/out in sync.
 * OK (D-pad center) toggles play/pause. Pauses app audio on open; releases on dispose.
 */
@OptIn(UnstableApi::class)
@Composable
fun TvVideoPlayerScreen(
    video: Video,
    playerViewModel: PlayerViewModel,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    BackHandler { onBack() }

    // Don't let app audio play underneath the video.
    LaunchedEffect(Unit) { playerViewModel.pausePlayback() }

    val exo = remember(video.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(video.hlsUrl))
            prepare()
            playWhenReady = true
        }
    }
    var error by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }

    DisposableEffect(exo) {
        val listener = object : Player.Listener {
            override fun onPlayerError(e: PlaybackException) { error = true }
        }
        exo.addListener(listener)
        onDispose {
            exo.removeListener(listener)
            exo.release()
        }
    }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                PlayerView(ctx).apply {
                    player = exo
                    useController = true
                    controllerShowTimeoutMs = CONTROLS_TIMEOUT_MS
                    controllerAutoShow = true
                    setShowNextButton(false)
                    setShowPreviousButton(false)
                    // Title fades with the transport bar.
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            controlsVisible = visibility == View.VISIBLE
                        }
                    )
                    // OK toggles play/pause even when the bar is hidden (then reveals it).
                    setOnKeyListener { _, keyCode, event ->
                        if (event.action == KeyEvent.ACTION_UP &&
                            (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER)
                        ) {
                            if (exo.isPlaying) exo.pause() else exo.play()
                            showController()
                            true
                        } else {
                            false
                        }
                    }
                }
            },
            modifier = Modifier.fillMaxSize()
        )

        // Title: single line, fades in/out with the controls.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Text(
                video.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.fillMaxWidth().padding(24.dp)
            )
        }

        if (error) {
            Text(
                "Playback failed — press Back",
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    }
}
