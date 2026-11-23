package com.soul.neurokaraoke.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.viewmodel.PlaybackProgress
import com.soul.neurokaraoke.viewmodel.RepeatMode
import kotlinx.coroutines.flow.StateFlow

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * Two-pane now-playing layout for wide/landscape screens (tablets, foldables):
 * album art + metadata + transport on the left, live synced lyrics on the right.
 * Inspired by the Twinskaraoke iOS landscape player.
 */
@Composable
fun TabletNowPlaying(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    playbackProgressFlow: StateFlow<PlaybackProgress>? = null,
    isShuffleEnabled: Boolean,
    repeatMode: RepeatMode,
    isFavorite: Boolean,
    accessToken: String?,
    isDownloaded: Boolean,
    downloadProgress: Float?,
    sleepTimerActive: Boolean,
    isRadioMode: Boolean,
    onSeekTo: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onShuffleClick: () -> Unit,
    onRepeatClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    onCollapse: () -> Unit,
    onDownloadClick: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onOpenQueue: () -> Unit,
    onOpenEqualizer: () -> Unit,
    onOpenSleepTimer: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Ambient blurred cover-art background (no-op blur < API 31; scrim still applies)
        if (song.coverUrl.isNotBlank()) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(60.dp)
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.80f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.93f)
                    )
                )
            )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Top bar
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onCollapse) {
                    Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.player_content_description_collapse), tint = MaterialTheme.colorScheme.onSurface)
                }
                Text(
                    stringResource(R.string.player_title_now_playing),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Row {
                    if (!isRadioMode) {
                        IconButton(onClick = { if (!isDownloaded && downloadProgress == null) onDownloadClick() }) {
                            when {
                                downloadProgress != null -> CircularProgressIndicator(progress = { downloadProgress }, modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
                                isDownloaded -> Icon(Icons.Default.DownloadDone, contentDescription = stringResource(R.string.player_content_description_downloaded), tint = MaterialTheme.colorScheme.primary)
                                else -> Icon(Icons.Default.Download, contentDescription = stringResource(R.string.player_content_description_download), tint = MaterialTheme.colorScheme.onSurface)
                            }
                        }
                        IconButton(onClick = onAddToPlaylist) {
                            Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = stringResource(R.string.player_content_description_add_to_playlist), tint = MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onOpenSleepTimer) {
                            Icon(Icons.Default.Bedtime, contentDescription = stringResource(R.string.player_content_description_sleep_timer), tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface)
                        }
                        IconButton(onClick = onOpenQueue) {
                            Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = stringResource(R.string.player_button_view_queue), tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                    IconButton(onClick = onOpenEqualizer) {
                        Icon(Icons.Default.Equalizer, contentDescription = stringResource(R.string.player_content_description_equalizer), tint = MaterialTheme.colorScheme.onSurface)
                    }
                }
            }

            // Two panes
            Row(modifier = Modifier.fillMaxSize().weight(1f)) {
                // LEFT: art + meta + transport
                Column(
                    modifier = Modifier.weight(0.42f).fillMaxHeight().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Box(
                        modifier = Modifier
                            .widthIn(max = 340.dp)
                            .fillMaxWidth()
                            .aspectRatio(1f)
                            .clip(RoundedCornerShape(16.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        AsyncImage(model = song.coverUrl, contentDescription = song.title, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                    Spacer(Modifier.height(24.dp))
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(song.title, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.onSurface, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            Text(song.artist, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                        if (!isRadioMode) {
                            IconButton(onClick = onToggleFavorite) {
                                Icon(
                                    if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                                    contentDescription = stringResource(if (isFavorite) R.string.player_content_description_remove_favorite else R.string.player_content_description_add_favorite),
                                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    TabletProgressScrubber(
                        fallbackPosition = currentPosition,
                        fallbackDuration = duration,
                        playbackProgressFlow = playbackProgressFlow,
                        onSeekTo = onSeekTo
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onShuffleClick) {
                            Icon(Icons.Default.Shuffle, contentDescription = stringResource(R.string.player_content_description_shuffle), tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(26.dp))
                        }
                        IconButton(onClick = onPrevious) {
                            Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_content_description_previous), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
                        }
                        Box(
                            modifier = Modifier.size(64.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            IconButton(onClick = onPlayPause) {
                                Icon(
                                    if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                    contentDescription = stringResource(if (isPlaying) R.string.player_content_description_pause else R.string.player_content_description_play),
                                    tint = MaterialTheme.colorScheme.onPrimary,
                                    modifier = Modifier.size(34.dp)
                                )
                            }
                        }
                        IconButton(onClick = onNext) {
                            Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_content_description_next), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(34.dp))
                        }
                        IconButton(onClick = onRepeatClick) {
                            Icon(
                                if (repeatMode == RepeatMode.ONE) Icons.Default.RepeatOne else Icons.Default.Repeat,
                                contentDescription = stringResource(R.string.player_content_description_repeat),
                                tint = if (repeatMode == RepeatMode.OFF) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(26.dp)
                            )
                        }
                    }
                }

                // RIGHT: lyrics
                TabletSyncedLyricsPanel(
                    song = song,
                    fallbackPosition = currentPosition,
                    fallbackDuration = duration,
                    playbackProgressFlow = playbackProgressFlow,
                    onSeekTo = onSeekTo,
                    accessToken = accessToken,
                    modifier = Modifier.weight(0.58f).fillMaxHeight().padding(end = 12.dp)
                )
            }
        }
    }
}

@Composable
private fun TabletProgressScrubber(
    fallbackPosition: Long,
    fallbackDuration: Long,
    playbackProgressFlow: StateFlow<PlaybackProgress>?,
    onSeekTo: (Float) -> Unit
) {
    val progressState = playbackProgressFlow?.collectAsStateWithLifecycle()?.value
    val position = progressState?.currentPosition ?: fallbackPosition
    val duration = progressState?.duration?.takeIf { it > 0 } ?: fallbackDuration

    Slider(
        value = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f,
        onValueChange = { onSeekTo(it) },
        colors = SliderDefaults.colors(thumbColor = MaterialTheme.colorScheme.primary, activeTrackColor = MaterialTheme.colorScheme.primary)
    )
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(formatMs(position), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(formatMs(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TabletSyncedLyricsPanel(
    song: Song,
    fallbackPosition: Long,
    fallbackDuration: Long,
    playbackProgressFlow: StateFlow<PlaybackProgress>?,
    onSeekTo: (Float) -> Unit,
    accessToken: String?,
    modifier: Modifier = Modifier
) {
    val progressState = playbackProgressFlow?.collectAsStateWithLifecycle()?.value
    val position = progressState?.currentPosition ?: fallbackPosition
    val duration = progressState?.duration?.takeIf { it > 0 } ?: fallbackDuration
    LyricsPanel(
        song = song,
        currentPosition = position,
        duration = duration,
        onSeekTo = onSeekTo,
        accessToken = accessToken,
        modifier = modifier
    )
}
