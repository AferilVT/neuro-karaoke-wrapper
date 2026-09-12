package com.soul.neurokaraoke.car

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.annotations.ExperimentalCarApi
import androidx.car.app.media.model.MediaPlaybackTemplate
import androidx.car.app.model.Header
import androidx.car.app.model.Template
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.Player

@OptIn(ExperimentalCarApi::class)
class NowPlayingCarScreen(
    carContext: CarContext,
    private val carPlayer: CarPlayer
) : Screen(carContext) {

    private val playerListener = object : Player.Listener {
        override fun onMediaMetadataChanged(mediaMetadata: androidx.media3.common.MediaMetadata) {
            invalidate()
        }
        override fun onPlaybackStateChanged(playbackState: Int) {
            invalidate()
        }
    }

    init {
        lifecycle.addObserver(object : DefaultLifecycleObserver {
            override fun onCreate(owner: LifecycleOwner) {
                carPlayer.currentController?.addListener(playerListener)
            }
            override fun onDestroy(owner: LifecycleOwner) {
                carPlayer.currentController?.removeListener(playerListener)
            }
        })
    }

    override fun onGetTemplate(): Template {
        val controller = carPlayer.currentController
        val metadata = controller?.currentMediaItem?.mediaMetadata
        
        // prioritize album title (where we store playlist name), fallback to "Now Playing"
        val title = metadata?.albumTitle?.toString() ?: "Now Playing"

        return MediaPlaybackTemplate.Builder()
            .setHeader(
                Header.Builder()
                    .setTitle(title)
                    // Removing .setStartHeaderAction(Action.BACK) to fix "2 back arrows" issue.
                    // The host usually provides a way to go back from the Now Playing view, 
                    // or the template itself handles it.
                    .build()
            )
            .build()
    }
}
