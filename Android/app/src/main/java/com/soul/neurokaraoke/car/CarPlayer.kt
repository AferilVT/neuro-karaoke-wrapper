package com.soul.neurokaraoke.car

import android.content.ComponentName
import android.content.Context
import android.net.Uri
import android.util.Log
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
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    fun ensureConnected() {
        if (android.os.Looper.myLooper() != android.os.Looper.getMainLooper()) {
            mainHandler.post { ensureConnected() }
            return
        }

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
        }, { mainHandler.post(it) })
    }

    fun playSongs(songs: List<Song>, startIndex: Int) {
        val count = songs.size
        val start = startIndex
        Log.d("NK_CAR_PLAYER", "playSongs: count=$count, startIndex=$start")
        ensureConnected()
        
        // Map to MediaItems on whatever thread we are on
        val items = songs.map { it.toMediaItem() }
        
        runOnController { c ->
            Log.d("NK_CAR_PLAYER", "Executing setMediaItems on controller: items=${items.size}, firstId=${items.firstOrNull()?.mediaId}")
            try {
                c.setMediaItems(items, start.coerceAtLeast(0), 0L)
                c.prepare()
                c.play()
                Log.d("NK_CAR_PLAYER", "Playback commands sent successfully")
            } catch (e: Exception) {
                Log.e("NK_CAR_PLAYER", "Error executing playSongs", e)
            }
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
    }

    private fun runOnController(block: (MediaController) -> Unit) {
        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
            val c = controller
            if (c != null) {
                block(c)
            } else {
                connecting?.addListener({
                    controller?.let { 
                        if (android.os.Looper.myLooper() == android.os.Looper.getMainLooper()) {
                            block(it)
                        } else {
                            mainHandler.post { block(it) }
                        }
                    }
                }, { mainHandler.post(it) })
            }
        } else {
            mainHandler.post { runOnController(block) }
        }
    }

    private fun Song.toMediaItem(): MediaItem {
        val songUri = if (audioUrl.isNotBlank()) Uri.parse(audioUrl) else Uri.parse("https://idk.neurokaraoke.com/empty.mp3")
        val coverUri = if (coverUrl.isNotBlank()) Uri.parse(coverUrl) else null
        
        return MediaItem.Builder()
            .setUri(songUri)
            .setMediaId(id)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(songUri)
                    .build()
            )
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtist("$artist • $coverArtist")
                    .setAlbumTitle(coverArtist)
                    .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                    .setIsPlayable(true)
                    .apply {
                        if (coverUri != null) setArtworkUri(coverUri)
                    }
                    .build()
            )
            .build()
    }
}
