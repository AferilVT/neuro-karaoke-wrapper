package com.soul.neurokaraoke.aaos

import android.net.Uri
import android.os.SystemClock
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.api.RadioState
import com.soul.neurokaraoke.service.AutoBrowseTree
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Polls RadioApi every 15s and pushes fresh title/artist/cover into the
 * Player's current item metadata so the now-playing UI updates
 * as live songs change.
 */
object AaosRadioPoller {
    private val api = RadioApi()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var job: Job? = null
    
    private var lastState: RadioState? = null
    private var lastFetchTime: Long = 0

    fun getRadioProgressMs(): Long {
        val state = lastState ?: return 0L
        if (state.currentProgress <= 0) return 0L
        val elapsed = SystemClock.elapsedRealtime() - lastFetchTime
        return (state.currentProgress * 1000L) + elapsed
    }

    fun getRadioDurationMs(): Long {
        val state = lastState ?: return 0L
        if (state.totalDuration > 0) return state.totalDuration * 1000L
        return lastState?.current?.duration?.toLong()?.times(1000) ?: 0L
    }

    fun start(player: Player) {
        stop()
        job = scope.launch {
            // Push immediately, then every 15s
            while (isActive) {
                api.fetchCurrentState().onSuccess { state ->
                    lastState = state
                    lastFetchTime = SystemClock.elapsedRealtime()

                    val current = state.current ?: return@onSuccess
                    val newMeta = MediaMetadata.Builder()
                        .setTitle(current.title)
                        .setArtist(current.originalArtists.joinToString(", "))
                        .setAlbumTitle("Neuro 21 Station • LIVE")
                        .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                        .setIsPlayable(true)
                        .apply {
                            if (current.coverUrl.isNotBlank()) {
                                setArtworkUri(Uri.parse(current.coverUrl))
                            }
                        }
                        .build()

                    withContext(Dispatchers.Main) {
                        val currentItem = player.currentMediaItem
                        val mediaId = currentItem?.mediaId
                        val isRadio = mediaId == "radio_live" || mediaId == AutoBrowseTree.RADIO_ID

                        if (player.mediaItemCount > 0 && isRadio) {
                            val updated = currentItem
                                ?.buildUpon()
                                ?.setMediaMetadata(newMeta)
                                ?.build()
                            if (updated != null) {
                                try {
                                    player.replaceMediaItem(player.currentMediaItemIndex, updated)
                                } catch (_: Exception) {
                                    // older Media3 — ignore
                                }
                            }
                        } else {
                            // Stream switched away from radio — stop polling
                            stop()
                        }
                    }
                }
                delay(15_000L)
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
    }
}
