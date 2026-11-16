package com.soul.neurokaraoke.ui.screens.player

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.LyricsCache
import com.soul.neurokaraoke.data.api.LyricLine
import com.soul.neurokaraoke.data.api.LyricsApi
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.launch

/**
 * Reusable scrolling synced-lyrics panel (no transport controls). Used by the
 * tablet two-pane now-playing layout and anywhere a lyrics column is needed.
 * Fetches lyrics (cache -> NeuroKaraoke -> LRCLIB), auto-scrolls to the active
 * line, supports tap-to-seek and an optional per-line translation toggle.
 */
@Composable
fun LyricsPanel(
    song: Song,
    currentPosition: Long,
    duration: Long,
    onSeekTo: (Float) -> Unit,
    accessToken: String?,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(horizontal = 24.dp, vertical = 96.dp)
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
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

    LaunchedEffect(song.audioUrl, song.title) {
        isLoading = true
        lyricLines = emptyList(); isSynced = false; hasTranslations = false; showTranslation = false
        val cached = lyricsCache.getCachedLyrics(song.title, song.artist)
        if (cached?.syncedLyrics?.isNotBlank() == true) {
            lyricLines = lyricsApi.parseSyncedLyrics(cached.syncedLyrics!!); isSynced = true
        } else if (cached?.plainLyrics?.isNotBlank() == true) {
            lyricLines = lyricsApi.parsePlainLyrics(cached.plainLyrics!!); isSynced = false
        }
        if (lyricLines.isEmpty()) {
            // Prefer the song's own NeuroKaraoke id (radio + catalog songs carry the real
            // UUID). Radio's audioUrl is the shared stream URL and never matches a song, so
            // audioUrl lookup is only the fallback for songs without a usable id.
            val songId = LyricsApi.neuroSongIdFromLocalId(song.id)
                ?: if (song.audioUrl.isNotBlank()) neuroApi.findSongIdByAudioUrl(song.audioUrl) else null
            resolvedSongId = songId
            if (songId != null) {
                neuroApi.fetchSongLyrics(songId).onSuccess { lines ->
                    if (lines.isNotEmpty()) { lyricLines = lines; isSynced = true }
                }
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

    val currentLineIndex = remember(currentPosition, lyricLines, isSynced) {
        if (!isSynced || lyricLines.isEmpty()) -1
        else lyricLines.indexOfLast { it.timestamp <= currentPosition }
    }
    val listState = rememberLazyListState()
    LaunchedEffect(currentLineIndex) {
        if (currentLineIndex >= 0 && isSynced) {
            listState.animateScrollToItem(currentLineIndex, scrollOffset = -260)
        }
    }

    fun toggleTranslation() {
        if (hasTranslations) { showTranslation = !showTranslation; return }
        val token = accessToken
        val songId = resolvedSongId
        if (token.isNullOrBlank() || songId == null || lyricLines.isEmpty() || isTranslating) return
        isTranslating = true
        scope.launch {
            lyricsApi.translateLyrics(songId, lyricLines, token).onSuccess { translated ->
                lyricLines = translated
                hasTranslations = translated.any { it.translatedText != null }
                showTranslation = hasTranslations
            }
            isTranslating = false
        }
    }

    Box(modifier = modifier) {
        when {
            isLoading -> CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.primary
            )
            lyricLines.isEmpty() -> Text(
                stringResource(R.string.lyrics_empty_title),
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            else -> LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = contentPadding,
                verticalArrangement = Arrangement.spacedBy(16.dp)
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
                            style = if (active) MaterialTheme.typography.headlineSmall
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

        // Translate toggle (top-end), shown when signed in
        if (isTranslating) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.TopEnd).padding(8.dp).size(24.dp),
                strokeWidth = 2.dp,
                color = MaterialTheme.colorScheme.primary
            )
        } else if (!accessToken.isNullOrBlank() && lyricLines.isNotEmpty()) {
            IconButton(
                onClick = { toggleTranslation() },
                modifier = Modifier.align(Alignment.TopEnd)
            ) {
                Icon(
                    Icons.Default.Translate,
                    contentDescription = stringResource(R.string.lyrics_translate),
                    tint = if (showTranslation) MaterialTheme.colorScheme.primary
                           else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
