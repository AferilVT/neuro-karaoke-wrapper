package com.soul.neurokaraoke.ui.screens.radio

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Headphones
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.api.RadioSong
import com.soul.neurokaraoke.data.api.RadioState
import com.soul.neurokaraoke.ui.theme.CyberLabelStyle
import com.soul.neurokaraoke.ui.theme.GlassCard
import com.soul.neurokaraoke.ui.theme.GradientText
import com.soul.neurokaraoke.ui.theme.NeonDivider
import com.soul.neurokaraoke.ui.theme.NeonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun RadioScreen(
    isRadioPlaying: Boolean,
    onListenClick: () -> Unit,
    onStopClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val radioApi = remember { RadioApi() }
    var radioState by remember { mutableStateOf<RadioState?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf<String?>(null) }
    var listenerCount by remember { mutableIntStateOf(0) }

    // Poll radio state every 15 seconds
    LaunchedEffect(Unit) {
        while (isActive) {
            radioApi.fetchCurrentState().fold(
                onSuccess = { state ->
                    radioState = state
                    listenerCount = state.listenerCount
                    isLoading = false
                    error = null
                },
                onFailure = { e ->
                    if (radioState == null) {
                        error = e.message ?: "Failed to connect"
                    }
                    isLoading = false
                }
            )
            delay(15_000L)
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item { Spacer(modifier = Modifier.height(8.dp)) }

        // Header
        item {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.fillMaxWidth()
            ) {
                GradientText(
                    text = "NEURO 21 STATION",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    gradientColors = NeonTheme.colors.gradientColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                LiveIndicator(
                    listenerCount = listenerCount,
                    isOffline = radioState?.offline == true,
                    isPlaying = isRadioPlaying
                )
            }
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = primaryColor)
                }
            }
        } else if (radioState?.offline == true) {
            item {
                GlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundAlpha = 0.6f,
                    showGlow = false
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.WifiOff,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = "Radio is currently offline",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Check back later!",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        } else {
            // Now Playing card
            radioState?.current?.let { currentSong ->
                item {
                    NowPlayingCard(
                        song = currentSong,
                        isPlaying = isRadioPlaying
                    )
                }
            }

            // Listen / Stop button
            item {
                if (isRadioPlaying) {
                    OutlinedButton(
                        onClick = onStopClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
                        border = androidx.compose.foundation.BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.error.copy(alpha = 0.5f)
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Stop,
                            contentDescription = null,
                            modifier = Modifier.size(24.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Stop Listening",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                } else {
                    Button(
                        onClick = onListenClick,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = primaryColor
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PlayArrow,
                            contentDescription = null,
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Listen Live",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Up Next
            val upcoming = radioState?.upcoming ?: emptyList()
            if (upcoming.isNotEmpty()) {
                item {
                    NeonDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "UP NEXT",
                        style = CyberLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(upcoming, key = { it.id }) { song ->
                    RadioSongCard(song = song)
                }
            }

            // Recently Played
            val history = radioState?.history ?: emptyList()
            if (history.isNotEmpty()) {
                item {
                    NeonDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Text(
                        text = "RECENTLY PLAYED",
                        style = CyberLabelStyle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )
                }
                items(history, key = { it.id }) { song ->
                    RadioSongCard(song = song)
                }
            }
        }

        // Bottom spacing for mini player
        item { Spacer(modifier = Modifier.height(80.dp)) }
    }
}

@Composable
private fun LiveIndicator(
    listenerCount: Int,
    isOffline: Boolean,
    isPlaying: Boolean
) {
    val infiniteTransition = rememberInfiniteTransition(label = "live_pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        if (!isOffline) {
            // Pulsing red dot
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(
                        if (isPlaying) Color.Red.copy(alpha = pulseAlpha)
                        else Color.Red.copy(alpha = 0.6f)
                    )
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "LIVE",
                style = CyberLabelStyle,
                color = Color.Red.copy(alpha = 0.9f),
                fontWeight = FontWeight.Bold
            )
            if (listenerCount > 0) {
                Spacer(modifier = Modifier.width(8.dp))
                Icon(
                    imageVector = Icons.Default.Headphones,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "$listenerCount",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            Text(
                text = "OFFLINE",
                style = CyberLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NowPlayingCard(
    song: RadioSong,
    isPlaying: Boolean
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundAlpha = 0.5f,
        showGlow = isPlaying,
        glowRadius = 8.dp
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Text(
                text = "NOW PLAYING",
                style = CyberLabelStyle,
                color = primaryColor
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Cover art
            Box(
                modifier = Modifier
                    .size(220.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .border(
                        width = 2.dp,
                        color = primaryColor.copy(alpha = if (isPlaying) 0.6f else 0.3f),
                        shape = RoundedCornerShape(12.dp)
                    )
                    .then(
                        if (isPlaying) {
                            Modifier.drawBehind {
                                // Neon glow effect
                                drawRoundRect(
                                    color = primaryColor.copy(alpha = 0.15f),
                                    cornerRadius = androidx.compose.ui.geometry.CornerRadius(12.dp.toPx()),
                                    size = size
                                )
                            }
                        } else Modifier
                    )
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .matchParentSize()
                        .clip(RoundedCornerShape(12.dp))
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Title
            Text(
                text = song.title,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            Spacer(modifier = Modifier.height(4.dp))

            // Artist info
            Text(
                text = "${song.originalArtists.joinToString(", ")} \u2022 ${song.coverArtistDisplay}",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            // Art credit
            song.artCredit?.let { credit ->
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = credit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    textAlign = TextAlign.Center
                )
            }

            // Duration
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RadioSongCard(song: RadioSong) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        backgroundAlpha = 0.3f,
        showGlow = false
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            // Cover art thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .border(
                        1.dp,
                        MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                        RoundedCornerShape(6.dp)
                    )
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.matchParentSize()
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Song info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${song.originalArtists.joinToString(", ")} \u2022 ${song.coverArtistDisplay}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Duration
            Text(
                text = formatDuration(song.duration),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

private fun formatDuration(seconds: Int): String {
    val mins = seconds / 60
    val secs = seconds % 60
    return "%d:%02d".format(mins, secs)
}
