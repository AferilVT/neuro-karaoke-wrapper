package com.soul.neurokaraoke.car

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.service.MediaPlaybackService

/**
 * Lazy MediaController connecting to MediaPlaybackService so the car UI
 * can drive playback without re-implementing the player.
 */
class CarPlayer(private val context: Context) {

    private var controller: MediaController? = null
    private var connecting: com.google.common.util.concurrent.ListenableFuture<MediaController>? = null
    private val executor: java.util.concurrent.ExecutorService = java.util.concurrent.Executors.newSingleThreadExecutor()

    fun ensureConnected() {
        if (controller != null || connecting != null) return
        val token = SessionToken(
            context,
            ComponentName(context, MediaPlaybackService::class.java)
        )
        val future = MediaController.Builder(context, token).buildAsync()
        connecting = future
        future.addListener({
            try {
                controller = future.get()
            } catch (_: Exception) {
                // Service may be starting; will retry on next play
            } finally {
                connecting = null
            }
        }, executor)
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        ensureConnected()
        val items = songs.map { it.toMediaItem() }
        runOnController { c ->
            c.setMediaItems(items, startIndex.coerceAtLeast(0), 0L)
            c.prepare()
            c.play()
        }
    }

    fun playRadio() {
        ensureConnected()
        val item = MediaItem.Builder()
            .setUri(RadioApi.STREAM_URL)
            .setMediaId("radio_live")
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(context.getString(R.string.car_radio_station_title))
                    .setArtist(context.getString(R.string.player_label_live))
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .build()
            )
            .build()
        runOnController { c ->
            c.setMediaItem(item)
            c.prepare()
            c.play()
        }
    }

    fun release() {
        controller?.release()
        controller = null
        connecting?.cancel(true)
        connecting = null
        executor.shutdown()
    }

    private fun runOnController(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) {
            block(c)
        } else {
            // Retry once the connection completes
            connecting?.addListener({
                controller?.let(block)
            }, executor)
        }
    }

    private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
        .setUri(audioUrl)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("$artist • $coverArtist")
                .setAlbumTitle(coverArtist)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .apply {
                    if (coverUrl.isNotBlank()) setArtworkUri(Uri.parse(coverUrl))
                }
                .build()
        )
        .build()
}
