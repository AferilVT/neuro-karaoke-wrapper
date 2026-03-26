package com.soul.neurokaraoke.ui.screens.player

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Brush
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Bedtime
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Lyrics
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Repeat
import androidx.compose.material.icons.filled.RepeatOne
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.audio.EqualizerManager
import com.soul.neurokaraoke.data.api.LyricLine
import com.soul.neurokaraoke.data.LyricsCache
import com.soul.neurokaraoke.data.api.LyricsApi
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.ui.theme.NeonTheme
import com.soul.neurokaraoke.ui.theme.animatedNeonGlow
import com.soul.neurokaraoke.ui.theme.neonBorder
import com.soul.neurokaraoke.viewmodel.RepeatMode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(
    song: Song,
    isPlaying: Boolean,
    progress: Float = 0f,
    currentPosition: Long = 0L,
    duration: Long = 0L,
    isShuffleEnabled: Boolean = false,
    repeatMode: RepeatMode = RepeatMode.OFF,
    queue: List<Song> = emptyList(),
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeekTo: (Float) -> Unit = {},
    onShuffleClick: () -> Unit = {},
    onRepeatClick: () -> Unit = {},
    onCollapseClick: () -> Unit,
    onQueueSongClick: (String) -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onDownloadClick: () -> Unit = {},
    sleepTimerRemainingMs: Long = 0L,
    sleepTimerActive: Boolean = false,
    onSetSleepTimer: (Int) -> Unit = {},
    onCancelSleepTimer: () -> Unit = {},
    onSetSleepTimerEndOfSong: () -> Unit = {},
    isFavorite: Boolean = false,
    onToggleFavorite: () -> Unit = {},
    onAddToPlaylist: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var showLyrics by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showSleepTimer by remember { mutableStateOf(false) }
    var isSeeking by remember { mutableStateOf(false) }
    var seekProgress by remember { mutableStateOf(0f) }

    val lyricsSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val queueSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val equalizerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val sleepTimerSheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    // Use seek progress while dragging, otherwise use actual progress
    val displayProgress = if (isSeeking) seekProgress else progress

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .windowInsetsPadding(WindowInsets.statusBars)
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Top bar
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onCollapseClick) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Collapse",
                    tint = MaterialTheme.colorScheme.onBackground
                )
            }

            Text(
                text = "Now Playing",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground
            )

            Row {
                // Download button
                IconButton(onClick = { if (!isDownloaded && downloadProgress == null) onDownloadClick() }) {
                    when {
                        downloadProgress != null -> {
                            CircularProgressIndicator(
                                progress = { downloadProgress },
                                modifier = Modifier.size(24.dp),
                                color = MaterialTheme.colorScheme.primary,
                                strokeWidth = 2.dp
                            )
                        }
                        isDownloaded -> {
                            Icon(
                                imageVector = Icons.Default.DownloadDone,
                                contentDescription = "Downloaded",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                        else -> {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = "Download",
                                tint = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }
                }

                // Add to playlist button
                IconButton(onClick = onAddToPlaylist) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                        contentDescription = "Add to playlist",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }

                // Sleep timer button
                IconButton(onClick = { showSleepTimer = true }) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Bedtime,
                            contentDescription = "Sleep timer",
                            tint = if (sleepTimerActive) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onBackground
                        )
                        if (sleepTimerActive && sleepTimerRemainingMs > 0) {
                            Text(
                                text = formatTimerCompact(sleepTimerRemainingMs),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                IconButton(onClick = { showEqualizer = true }) {
                    Icon(
                        imageVector = Icons.Default.Equalizer,
                        contentDescription = "Equalizer",
                        tint = MaterialTheme.colorScheme.onBackground
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Album art with subtle border
        Box(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .aspectRatio(1f)
                .neonBorder(
                    colors = NeonTheme.colors.neonBorderColors,
                    borderWidth = 1.dp,
                    cornerRadius = 16.dp,
                    glowRadius = 4.dp
                )
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        }

        // Art credit
        if (!song.artCredit.isNullOrBlank()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable {
                        val url = if (song.artCredit.startsWith("http")) {
                            song.artCredit
                        } else {
                            "https://${song.artCredit}"
                        }
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                        context.startActivity(intent)
                    }
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Brush,
                    contentDescription = "Art credit",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = song.artCredit,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    textDecoration = TextDecoration.Underline
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        } else {
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Song title and artist with action icons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = { showLyrics = true }) {
                Icon(
                    imageVector = Icons.Default.Lyrics,
                    contentDescription = "View lyrics",
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = song.title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = song.coverArtist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }

            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Progress bar
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            Slider(
                value = displayProgress,
                onValueChange = { newProgress ->
                    isSeeking = true
                    seekProgress = newProgress
                },
                onValueChangeFinished = {
                    onSeekTo(seekProgress)
                    isSeeking = false
                },
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = formatTime(if (isSeeking) (seekProgress * duration).toLong() else currentPosition),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = formatTime(duration),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Playback controls
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onShuffleClick) {
                Icon(
                    imageVector = Icons.Default.Shuffle,
                    contentDescription = "Shuffle",
                    tint = if (isShuffleEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            IconButton(
                onClick = onPreviousClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipPrevious,
                    contentDescription = "Previous",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(36.dp)
                )
            }

            // Play/Pause button
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .then(
                        if (isPlaying) Modifier.animatedNeonGlow(
                            color = NeonTheme.colors.glowColor,
                            baseRadius = 10.dp,
                            cornerRadius = 36.dp
                        ) else Modifier
                    )
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary),
                contentAlignment = Alignment.Center
            ) {
                IconButton(
                    onClick = onPlayPauseClick,
                    modifier = Modifier.fillMaxSize()
                ) {
                    Icon(
                        imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        contentDescription = if (isPlaying) "Pause" else "Play",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(40.dp)
                    )
                }
            }

            IconButton(
                onClick = onNextClick,
                modifier = Modifier.size(56.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.SkipNext,
                    contentDescription = "Next",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(36.dp)
                )
            }

            IconButton(onClick = onRepeatClick) {
                Icon(
                    imageVector = when (repeatMode) {
                        RepeatMode.ONE -> Icons.Default.RepeatOne
                        else -> Icons.Default.Repeat
                    },
                    contentDescription = "Repeat",
                    tint = when (repeatMode) {
                        RepeatMode.OFF -> MaterialTheme.colorScheme.onSurfaceVariant
                        else -> MaterialTheme.colorScheme.primary
                    }
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // View Queue button
        Button(
            onClick = { showQueue = true },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                contentColor = MaterialTheme.colorScheme.primary
            ),
            shape = RoundedCornerShape(24.dp),
            modifier = Modifier
                .fillMaxWidth(0.6f)
                .height(48.dp)
        ) {
            Text(
                text = "View Queue",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium
            )
        }
    }

    // Lyrics bottom sheet
    if (showLyrics) {
        ModalBottomSheet(
            onDismissRequest = { showLyrics = false },
            sheetState = lyricsSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            LyricsContent(
                songTitle = song.title,
                artistName = song.artist,
                songAudioUrl = song.audioUrl,
                currentPosition = currentPosition,
                duration = duration,
                onSeekTo = onSeekTo,
                onClose = { showLyrics = false }
            )
        }
    }

    // Queue bottom sheet
    if (showQueue) {
        ModalBottomSheet(
            onDismissRequest = { showQueue = false },
            sheetState = queueSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            QueueContent(
                currentSong = song,
                queue = queue,
                onSongClick = { songId ->
                    onQueueSongClick(songId)
                    showQueue = false
                },
                onClose = { showQueue = false }
            )
        }
    }

    // Equalizer bottom sheet
    if (showEqualizer) {
        ModalBottomSheet(
            onDismissRequest = { showEqualizer = false },
            sheetState = equalizerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            EqualizerContent(
                onClose = { showEqualizer = false }
            )
        }
    }

    // Sleep timer bottom sheet
    if (showSleepTimer) {
        ModalBottomSheet(
            onDismissRequest = { showSleepTimer = false },
            sheetState = sleepTimerSheetState,
            containerColor = MaterialTheme.colorScheme.surface
        ) {
            SleepTimerContent(
                sleepTimerActive = sleepTimerActive,
                sleepTimerRemainingMs = sleepTimerRemainingMs,
                onSetTimer = { minutes ->
                    onSetSleepTimer(minutes)
                    showSleepTimer = false
                },
                onSetEndOfSong = {
                    onSetSleepTimerEndOfSong()
                    showSleepTimer = false
                },
                onCancelTimer = {
                    onCancelSleepTimer()
                },
                onClose = { showSleepTimer = false }
            )
        }
    }
}

@Composable
private fun SleepTimerContent(
    sleepTimerActive: Boolean,
    sleepTimerRemainingMs: Long,
    onSetTimer: (Int) -> Unit,
    onSetEndOfSong: () -> Unit,
    onCancelTimer: () -> Unit,
    onClose: () -> Unit
) {
    val presets = listOf(5, 15, 30, 45, 60, 90, 120)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Bedtime,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sleep Timer",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (sleepTimerActive) {
            // Active timer display
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.1f))
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Timer Active",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = if (sleepTimerRemainingMs > 0) formatTimerDisplay(sleepTimerRemainingMs)
                           else "End of current song",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(16.dp))
                Button(
                    onClick = onCancelTimer,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    ),
                    shape = RoundedCornerShape(24.dp)
                ) {
                    Text("Cancel Timer")
                }
            }
        } else {
            // Timer presets
            Text(
                text = "Stop playing after",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(12.dp))

            // Preset chips in a flow layout
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                presets.forEach { minutes ->
                    val label = if (minutes < 60) "${minutes} min"
                                else "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}"
                    FilterChip(
                        selected = false,
                        onClick = { onSetTimer(minutes) },
                        label = { Text(label) },
                        colors = FilterChipDefaults.filterChipColors(
                            containerColor = MaterialTheme.colorScheme.surfaceVariant,
                            labelColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // End of song option
            FilterChip(
                selected = false,
                onClick = onSetEndOfSong,
                label = { Text("End of current song") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Default.Bedtime,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                },
                colors = FilterChipDefaults.filterChipColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant,
                    labelColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

private fun formatTimerDisplay(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val hours = totalSeconds / 3600
    val minutes = (totalSeconds % 3600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}

private fun formatTimerCompact(millis: Long): String {
    if (millis <= 0) return ""
    val totalSeconds = millis / 1000
    val minutes = (totalSeconds + 59) / 60 // Round up
    return if (minutes >= 60) {
        "${minutes / 60}h${if (minutes % 60 > 0) "${minutes % 60}m" else ""}"
    } else {
        "${minutes}m"
    }
}

@Composable
private fun LyricsContent(
    songTitle: String,
    artistName: String,
    songAudioUrl: String,
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Float) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lyricsApi = remember { LyricsApi() }
    val neuroApi = remember { NeuroKaraokeApi() }
    val lyricsCache = remember { LyricsCache(context) }
    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var isSynced by remember { mutableStateOf(false) }
    var lyricsOffset by remember { mutableStateOf(0f) } // Offset in seconds (-10 to +10)
    var lyricsSource by remember { mutableStateOf("lrclib") }

    val listState = rememberLazyListState()

    // Fetch lyrics when song changes (cache → NeuroKaraoke API → LRCLIB fallback)
    LaunchedEffect(songTitle, artistName) {
        isLoading = true
        errorMessage = null
        lyricsSource = "lrclib"

        // Try cache first
        val cached = lyricsCache.getCachedLyrics(songTitle, artistName)
        if (cached != null && (cached.syncedLyrics != null || cached.plainLyrics != null)) {
            lyricsSource = cached.source
            if (!cached.syncedLyrics.isNullOrBlank()) {
                lyricLines = lyricsApi.parseSyncedLyrics(cached.syncedLyrics)
                isSynced = true
            } else if (!cached.plainLyrics.isNullOrBlank()) {
                lyricLines = lyricsApi.parsePlainLyrics(cached.plainLyrics)
                isSynced = false
            }
            isLoading = false
            return@LaunchedEffect
        }

        // Try NeuroKaraoke API first (uses song ID lookup from audio URL)
        var foundLyrics = false
        if (songAudioUrl.isNotBlank()) {
            val songId = neuroApi.findSongIdByAudioUrl(songAudioUrl)
            if (songId != null) {
                neuroApi.fetchSongLyrics(songId).onSuccess { lines ->
                    if (lines.isNotEmpty()) {
                        lyricLines = lines
                        isSynced = true
                        lyricsSource = "neurokaraoke"
                        foundLyrics = true

                        // Convert to LRC format for cache compatibility
                        val lrcContent = lines.joinToString("\n") { line ->
                            val totalSec = line.timestamp / 1000
                            val min = totalSec / 60
                            val sec = totalSec % 60
                            val ms = (line.timestamp % 1000) / 10
                            "[%02d:%02d.%02d]%s".format(min, sec, ms, line.text)
                        }
                        lyricsCache.cacheLyrics(
                            songTitle, artistName, lrcContent, null, "neurokaraoke"
                        )
                    }
                }
            }
        }

        if (foundLyrics) {
            isLoading = false
            return@LaunchedEffect
        }

        // Fallback to LRCLIB
        val result = lyricsApi.searchLyrics(songTitle, artistName)
        result.fold(
            onSuccess = { lyrics ->
                if (lyrics != null) {
                    lyricsCache.cacheLyrics(
                        songTitle = songTitle,
                        artistName = artistName,
                        syncedLyrics = lyrics.syncedLyrics,
                        plainLyrics = lyrics.plainLyrics,
                        source = "lrclib"
                    )

                    if (!lyrics.syncedLyrics.isNullOrBlank()) {
                        lyricLines = lyricsApi.parseSyncedLyrics(lyrics.syncedLyrics)
                        isSynced = true
                    } else if (!lyrics.plainLyrics.isNullOrBlank()) {
                        lyricLines = lyricsApi.parsePlainLyrics(lyrics.plainLyrics)
                        isSynced = false
                    } else {
                        lyricLines = emptyList()
                        lyricsCache.cacheLyrics(songTitle, artistName, null, null)
                    }
                } else {
                    lyricLines = emptyList()
                    lyricsCache.cacheLyrics(songTitle, artistName, null, null)
                }
                isLoading = false
            },
            onFailure = { _ ->
                errorMessage = "Failed to load lyrics"
                isLoading = false
            }
        )
    }

    // Find current line index for synced lyrics (with offset applied)
    val offsetMs = (lyricsOffset * 1000).toLong()
    val adjustedPosition = currentPosition + offsetMs
    // Pre-extract timestamps for O(log n) binary search instead of O(n) linear scan
    val timestamps = remember(lyricLines) { lyricLines.map { it.timestamp } }
    val currentLineIndex = remember(adjustedPosition, timestamps, isSynced) {
        if (!isSynced || timestamps.isEmpty()) -1
        else {
            val insertionPoint = timestamps.binarySearch(adjustedPosition)
            if (insertionPoint >= 0) insertionPoint
            else -(insertionPoint + 1) - 1
        }
    }

    // Auto-scroll to current line
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex > 0 && isSynced) {
            listState.animateScrollToItem(
                index = maxOf(0, currentLineIndex - 2),
                scrollOffset = 0
            )
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Lyrics",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                if (isSynced && lyricLines.isNotEmpty()) {
                    Text(
                        text = "Synced • Tap to seek",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                    )
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        // Offset slider for synced lyrics
        if (isSynced && lyricLines.isNotEmpty()) {
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Offset:",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Slider(
                    value = lyricsOffset,
                    onValueChange = { lyricsOffset = it },
                    valueRange = -10f..10f,
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                )
                Text(
                    text = "${if (lyricsOffset >= 0) "+" else ""}${"%.1f".format(lyricsOffset)}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(48.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        when {
            isLoading -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Searching for lyrics...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = errorMessage!!,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
            lyricLines.isEmpty() -> {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "No lyrics found",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$songTitle - $artistName",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
            else -> {
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(400.dp)
                ) {
                    itemsIndexed(lyricLines) { index, line ->
                        val isCurrentLine = isSynced && index == currentLineIndex
                        val isPastLine = isSynced && index < currentLineIndex

                        Text(
                            text = line.text.ifBlank { "♪" },
                            style = if (isCurrentLine) {
                                MaterialTheme.typography.titleLarge
                            } else {
                                MaterialTheme.typography.bodyLarge
                            },
                            fontWeight = if (isCurrentLine) FontWeight.Bold else FontWeight.Normal,
                            color = when {
                                isCurrentLine -> MaterialTheme.colorScheme.primary
                                isPastLine -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                                else -> MaterialTheme.colorScheme.onSurface
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .then(
                                    if (isSynced && duration > 0) {
                                        Modifier.clickable {
                                            val adjustedTimestamp = line.timestamp - offsetMs
                                            val progress = adjustedTimestamp.toFloat() / duration.toFloat()
                                            onSeekTo(progress.coerceIn(0f, 1f))
                                        }
                                    } else Modifier
                                )
                                .padding(vertical = if (isCurrentLine) 12.dp else 6.dp),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Credit
        Text(
            text = when (lyricsSource) {
                "neurokaraoke" -> "Lyrics provided by NeuroKaraoke"
                else -> "Lyrics provided by LRCLIB"
            },
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(16.dp))
    }
}

@Composable
private fun QueueContent(
    currentSong: Song,
    queue: List<Song>,
    onSongClick: (String) -> Unit,
    onClose: () -> Unit
) {
    val currentIndex = queue.indexOfFirst { it.id == currentSong.id }
    val upNext = if (currentIndex >= 0 && currentIndex < queue.size - 1) {
        queue.subList(currentIndex + 1, queue.size)
    } else {
        emptyList()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Queue",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Now Playing
        Text(
            text = "Now Playing",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(8.dp))

        QueueItem(
            song = currentSong,
            isPlaying = true,
            onClick = { }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Up Next
        Text(
            text = "Up Next (${upNext.size} songs)",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(modifier = Modifier.height(8.dp))

        if (upNext.isEmpty()) {
            Text(
                text = "No more songs in queue",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyColumn(
                modifier = Modifier.height(300.dp)
            ) {
                items(upNext) { song ->
                    QueueItem(
                        song = song,
                        isPlaying = false,
                        onClick = { onSongClick(song.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun QueueItem(
    song: Song,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .background(
                if (isPlaying) MaterialTheme.colorScheme.primary.copy(alpha = 0.1f)
                else MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
            )
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Cover art
        AsyncImage(
            model = song.coverUrl,
            contentDescription = song.title,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
        )

        Spacer(modifier = Modifier.size(12.dp))

        // Song info
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (isPlaying) FontWeight.Bold else FontWeight.Normal,
                color = if (isPlaying) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.coverArtist}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (isPlaying) {
            Icon(
                imageVector = Icons.Default.PlayArrow,
                contentDescription = "Playing",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(24.dp)
            )
        }
    }

    Spacer(modifier = Modifier.height(4.dp))
}

private fun formatTime(millis: Long): String {
    if (millis <= 0) return "0:00"
    val totalSeconds = millis / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun EqualizerContent(
    onClose: () -> Unit
) {
    val eqState by EqualizerManager.state.collectAsState()
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("Equalizer", "Bass Boost")

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        // Header
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Default.Equalizer,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(28.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Audio Effects",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Close",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Tab selector
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            tabs.forEachIndexed { index, title ->
                FilterChip(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    label = { Text(title) },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (!eqState.isAvailable && !eqState.bassBoostAvailable) {
            // Effects not available
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Audio effects not available",
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "Start playing a song to enable",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            when (selectedTab) {
                0 -> EqualizerTab(eqState)
                1 -> BassBoostTab(eqState)
            }
        }

        Spacer(modifier = Modifier.height(32.dp))
    }
}

@Composable
private fun EqualizerTab(eqState: com.soul.neurokaraoke.audio.AudioEffectsState) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(400.dp)
            .verticalScroll(rememberScrollState())
    ) {
        if (!eqState.isAvailable) {
            Text(
                text = "Equalizer not available on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        // Enable/Disable toggle
        EffectToggleRow(
            title = "Enable Equalizer",
            checked = eqState.isEnabled,
            onCheckedChange = { EqualizerManager.setEnabled(it) }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Presets
        if (eqState.presets.isNotEmpty()) {
            Text(
                text = "Presets",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = eqState.currentPresetIndex == -1,
                    onClick = { EqualizerManager.resetToFlat() },
                    label = { Text("Flat") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primary,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                    )
                )

                eqState.presets.forEach { preset ->
                    FilterChip(
                        selected = eqState.currentPresetIndex == preset.index,
                        onClick = { EqualizerManager.usePreset(preset.index) },
                        label = { Text(preset.name) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primary,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimary
                        )
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
        }

        // Band sliders
        Text(
            text = "Frequency Bands",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        eqState.bands.forEach { band ->
            EqualizerBandSlider(
                band = band,
                enabled = eqState.isEnabled,
                onLevelChange = { level ->
                    EqualizerManager.setBandLevel(band.index, level)
                }
            )
            Spacer(modifier = Modifier.height(8.dp))
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Reset button
        Button(
            onClick = { EqualizerManager.resetToFlat() },
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant,
                contentColor = MaterialTheme.colorScheme.onSurface
            ),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                imageVector = Icons.Default.Refresh,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("Reset to Flat")
        }
    }
}

@Composable
private fun BassBoostTab(eqState: com.soul.neurokaraoke.audio.AudioEffectsState) {
    Column(modifier = Modifier.fillMaxWidth()) {
        if (!eqState.bassBoostAvailable) {
            Text(
                text = "Bass Boost not available on this device",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            return@Column
        }

        var bassStrength by remember { mutableStateOf(eqState.bassBoostStrength.toFloat()) }

        // Update local state when external state changes
        LaunchedEffect(eqState.bassBoostStrength) {
            bassStrength = eqState.bassBoostStrength.toFloat()
        }

        // Enable/Disable toggle
        EffectToggleRow(
            title = "Enable Bass Boost",
            checked = eqState.bassBoostEnabled,
            onCheckedChange = { EqualizerManager.setBassBoostEnabled(it) }
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Bass strength slider
        Text(
            text = "Strength",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Bass Level",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (eqState.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${(bassStrength / 10).toInt()}%",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = if (eqState.bassBoostEnabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Slider(
                value = bassStrength,
                onValueChange = { bassStrength = it },
                onValueChangeFinished = { EqualizerManager.setBassBoostStrength(bassStrength.toInt()) },
                valueRange = 0f..1000f,
                enabled = eqState.bassBoostEnabled,
                colors = SliderDefaults.colors(
                    thumbColor = MaterialTheme.colorScheme.primary,
                    activeTrackColor = MaterialTheme.colorScheme.primary,
                    inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text("Light", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Heavy", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EffectToggleRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface
        )
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = MaterialTheme.colorScheme.primary,
                checkedTrackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
            )
        )
    }
}

@Composable
private fun EqualizerBandSlider(
    band: com.soul.neurokaraoke.audio.EqualizerBand,
    enabled: Boolean,
    onLevelChange: (Int) -> Unit
) {
    var sliderValue by remember { mutableStateOf(band.currentLevel.toFloat()) }

    // Update local state when external state changes
    LaunchedEffect(band.currentLevel) {
        sliderValue = band.currentLevel.toFloat()
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = EqualizerManager.formatFrequency(band.centerFrequency),
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium,
                color = if (enabled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = EqualizerManager.formatLevel(sliderValue.toInt()),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Slider(
            value = sliderValue,
            onValueChange = { sliderValue = it },
            onValueChangeFinished = { onLevelChange(sliderValue.toInt()) },
            valueRange = band.minLevel.toFloat()..band.maxLevel.toFloat(),
            enabled = enabled,
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant,
                disabledThumbColor = MaterialTheme.colorScheme.onSurfaceVariant,
                disabledActiveTrackColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
            )
        )
    }
}

