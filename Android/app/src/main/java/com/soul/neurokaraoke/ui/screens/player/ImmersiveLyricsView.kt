package com.soul.neurokaraoke.ui.screens.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.LyricsCache
import com.soul.neurokaraoke.data.api.LyricLine
import com.soul.neurokaraoke.data.api.LyricsApi
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.launch

/**
 * Full-screen, Apple Music-style immersive lyrics view. Blurred cover-art
 * background, large spotlighted active line, dimmed context lines, optional
 * per-line translation (POST /api/lyrics/translate), progress bar + transport
 * controls (no volume slider, by request).
 */
@Composable
fun ImmersiveLyricsView(
    song: Song,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Float) -> Unit,
    onPlayPause: () -> Unit,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    accessToken: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScopeForLyrics()
    val lyricsApi = remember { LyricsApi() }
    val neuroApi = remember { NeuroKaraokeApi() }
    val lyricsCache = remember { LyricsCache(context) }

    var lyricLines by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var isSynced by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvedSongId by remember { mutableStateOf<String?>(null) }
    var showTranslation by remember { mutableStateOf(false) }
    var isTranslating by remember { mutableStateOf(false) }
    var hasTranslations by remember { mutableStateOf(false) }

    // Fetch lyrics (cache -> NeuroKaraoke -> LRCLIB), same source order as the sheet.
    LaunchedEffect(song.audioUrl, song.title) {
        isLoading = true
        lyricLines = emptyList(); isSynced = false; hasTranslations = false; showTranslation = false
        val cached = lyricsCache.getCachedLyrics(song.title, song.artist)
        if (cached?.syncedLyrics?.isNotBlank() == true) {
            lyricLines = lyricsApi.parseSyncedLyrics(cached.syncedLyrics!!); isSynced = true
        } else if (cached?.plainLyrics?.isNotBlank() == true) {
            lyricLines = lyricsApi.parsePlainLyrics(cached.plainLyrics!!); isSynced = false
        }
        val songId = LyricsApi.neuroSongIdFromLocalId(song.id)
            ?: song.audioUrl.takeIf { it.isNotBlank() }?.let { neuroApi.findSongIdByAudioUrl(it) }
        resolvedSongId = songId
        if (songId != null && lyricLines.isEmpty()) {
            neuroApi.fetchSongLyrics(songId).onSuccess { lines ->
                if (lines.isNotEmpty()) { lyricLines = lines; isSynced = true }
            }
        }
        if (lyricLines.isEmpty()) {
            lyricsApi.searchLyrics(song.title, song.artist).onSuccess { r ->
                when {
                    r?.syncedLyrics?.isNotBlank() == true -> { lyricLines = lyricsApi.parseSyncedLyrics(r.syncedLyrics!!); isSynced = true }
                    r?.plainLyrics?.isNotBlank() == true -> { lyricLines = lyricsApi.parsePlainLyrics(r.plainLyrics!!); isSynced = false }
                }
            }
        }
        isLoading = false
    }

    val timestamps = remember(lyricLines) { lyricLines.map { it.timestamp } }
    val currentLineIndex = remember(currentPosition, timestamps, isSynced) {
        if (!isSynced || timestamps.isEmpty()) -1
        else {
            val idx = timestamps.binarySearch(currentPosition)
            if (idx >= 0) idx else -(idx + 1) - 1
        }
    }

    val listState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && isSynced) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -320)
        }
    }

    fun toggleTranslation() {
        if (hasTranslations) { showTranslation = !showTranslation; return }
        val token = accessToken
        val songId = resolvedSongId
        if (token.isNullOrBlank() || songId == null || lyricLines.isEmpty() || isTranslating) return
        isTranslating = true
        scope.launch {
            lyricsApi.translateLyrics(songId, lyricLines, token)
                .onSuccess { translated ->
                    lyricLines = translated
                    hasTranslations = translated.any { it.translatedText != null }
                    showTranslation = hasTranslations
                }
            isTranslating = false
        }
    }

    Box(modifier = modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        // Blurred cover-art background (blur() is a no-op below API 31; scrim still applies)
        if (song.coverUrl.isNotBlank()) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize().blur(40.dp)
            )
        }
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    listOf(
                        MaterialTheme.colorScheme.background.copy(alpha = 0.82f),
                        MaterialTheme.colorScheme.background.copy(alpha = 0.92f)
                    )
                )
            )
        )

        Column(modifier = Modifier.fillMaxSize()) {
            // Header: collapse + now-playing + translate toggle
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.Default.KeyboardArrowDown,
                        contentDescription = stringResource(R.string.player_content_description_collapse),
                        tint = MaterialTheme.colorScheme.onSurface
                    )
                }
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        song.title,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1, overflow = TextOverflow.Ellipsis
                    )
                }
                if (isTranslating) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(24.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (!accessToken.isNullOrBlank()) {
                    IconButton(onClick = { toggleTranslation() }) {
                        Icon(
                            Icons.Default.Translate,
                            contentDescription = stringResource(R.string.lyrics_translate),
                            tint = if (showTranslation) MaterialTheme.colorScheme.primary
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            // Lyrics
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                if (isLoading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (lyricLines.isEmpty()) {
                    Text(
                        stringResource(R.string.lyrics_empty_title),
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(18.dp)
                    ) {
                        itemsIndexed(lyricLines) { index, line ->
                            val active = isSynced && index == currentLineIndex
                            val past = isSynced && index < currentLineIndex
                            val lineColor = when {
                                active -> MaterialTheme.colorScheme.onSurface
                                past -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                                else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.55f)
                            }
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .then(
                                        if (isSynced && duration > 0) Modifier.clickable {
                                            onSeekTo((line.timestamp.toFloat() / duration).coerceIn(0f, 1f))
                                        } else Modifier
                                    )
                            ) {
                                Text(
                                    text = line.text.ifBlank { "♪" },
                                    style = if (active) MaterialTheme.typography.headlineMedium
                                            else MaterialTheme.typography.titleLarge,
                                    fontWeight = if (active) FontWeight.Bold else FontWeight.SemiBold,
                                    color = lineColor
                                )
                                if (showTranslation && !line.translatedText.isNullOrBlank()) {
                                    Text(
                                        text = line.translatedText!!,
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium,
                                        color = lineColor.copy(alpha = if (active) 0.8f else 0.4f)
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Progress + transport (no volume slider)
            Column(modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp)) {
                Slider(
                    value = if (duration > 0) (currentPosition.toFloat() / duration).coerceIn(0f, 1f) else 0f,
                    onValueChange = { onSeekTo(it) },
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary
                    )
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(formatMs(currentPosition), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(formatMs(duration), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onPrevious) {
                        Icon(Icons.Default.SkipPrevious, contentDescription = stringResource(R.string.player_content_description_previous), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                    }
                    IconButton(onClick = onPlayPause) {
                        Icon(
                            if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                            contentDescription = stringResource(if (isPlaying) R.string.player_content_description_pause else R.string.player_content_description_play),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(52.dp)
                        )
                    }
                    IconButton(onClick = onNext) {
                        Icon(Icons.Default.SkipNext, contentDescription = stringResource(R.string.player_content_description_next), tint = MaterialTheme.colorScheme.onSurface, modifier = Modifier.size(36.dp))
                    }
                }
            }
        }
    }
}

private fun formatMs(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@Composable
private fun rememberCoroutineScopeForLyrics() = androidx.compose.runtime.rememberCoroutineScope()
