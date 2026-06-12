package com.soul.neurokaraoke.aaos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import androidx.compose.ui.draw.blur
import com.soul.neurokaraoke.R
import kotlinx.coroutines.delay

@Composable
fun AaosNowPlayingScreen(
    controllerProvider: () -> MediaController?,
    onBack: () -> Unit
) {
    val controller = controllerProvider()
    var title by remember { mutableStateOf("") }
    var artist by remember { mutableStateOf("") }
    var coverUrl by remember { mutableStateOf("") }
    var isPlaying by remember { mutableStateOf(false) }
    var isRadio by remember { mutableStateOf(false) }
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember { mutableLongStateOf(0L) }

    LaunchedEffect(controller) {
        controller ?: return@LaunchedEffect
        while (true) {
            val item = controller.currentMediaItem
            val md = item?.mediaMetadata
            title = md?.title?.toString().orEmpty()
            artist = md?.artist?.toString().orEmpty()
            coverUrl = md?.artworkUri?.toString().orEmpty()
            isPlaying = controller.isPlaying
            isRadio = item?.mediaId == "radio_live"
            position = controller.currentPosition.coerceAtLeast(0L)
            duration = controller.duration.coerceAtLeast(0L)
            delay(500)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // Blurred album-cover backdrop (Modifier.blur needs API 31+; falls back to plain image otherwise)
        if (coverUrl.isNotBlank()) {
            val bg = if (android.os.Build.VERSION.SDK_INT >= 31)
                Modifier.fillMaxSize().blur(40.dp) else Modifier.fillMaxSize()
            AsyncImage(
                model = coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = bg
            )
        }
        // 75% dark scrim for legibility
        Box(modifier = Modifier.fillMaxSize().background(Color.Black.copy(alpha = 0.75f)))

    Row(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Left side: cover art huge
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            BackButton(onBack)
            Spacer(Modifier.height(16.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth(0.85f)
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(20.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (coverUrl.isNotBlank()) {
                    AsyncImage(
                        model = coverUrl,
                        contentDescription = title,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        Spacer(Modifier.width(32.dp))

        // Right side: info + controls
        Column(
            modifier = Modifier.weight(1.2f).fillMaxSize(),
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = title.ifBlank { stringResource(R.string.aaos_now_playing_nothing) },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(40.dp))

            if (!isRadio) {
                ProgressBar(position, duration)
                Spacer(Modifier.height(40.dp))
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (!isRadio) {
                    CircleButton(
                        icon = Icons.Default.SkipPrevious,
                        size = 72.dp,
                        onClick = { controller?.seekToPrevious() }
                    )
                }
                if (isRadio) {
                    CircleButton(
                        icon = Icons.Default.Stop,
                        size = 96.dp,
                        primary = true,
                        onClick = {
                            AaosRadioPoller.stop()
                            controller?.stop()
                            onBack()
                        }
                    )
                } else {
                    CircleButton(
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        size = 96.dp,
                        primary = true,
                        onClick = {
                            if (controller?.isPlaying == true) controller.pause()
                            else controller?.play()
                        }
                    )
                }
                if (!isRadio) {
                    CircleButton(
                        icon = Icons.Default.SkipNext,
                        size = 72.dp,
                        onClick = { controller?.seekToNext() }
                    )
                }
            }
        }
    }
    }
}

@Composable
private fun BackButton(onBack: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                .clickable(onClick = onBack),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.aaos_now_playing_content_description_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
    }
}

@Composable
private fun ProgressBar(position: Long, duration: Long) {
    val frac = if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Column(modifier = Modifier.fillMaxWidth()) {
        LinearProgressIndicator(
            progress = { frac },
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp))
        )
        Spacer(Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                formatTime(position),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            Text(
                formatTime(duration),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

@Composable
private fun CircleButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    size: androidx.compose.ui.unit.Dp,
    primary: Boolean = false,
    onClick: () -> Unit
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onBackground
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(size * 0.5f))
    }
}

private fun formatTime(ms: Long): String {
    if (ms <= 0) return "0:00"
    val s = ms / 1000
    return "%d:%02d".format(s / 60, s % 60)
}
