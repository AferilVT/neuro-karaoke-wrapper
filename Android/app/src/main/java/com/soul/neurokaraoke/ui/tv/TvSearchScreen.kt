package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.viewmodel.PlayerViewModel

/** Matches the "m:ss" duration formatting used in TvDetailScreen.kt / the phone UI. */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

/**
 * TV Search screen: an on-screen [TvKeyboard] drives a live-filtered results list.
 * The filter predicate mirrors ui/screens/search/SearchScreen.kt's `filteredSongs`
 * (case-insensitive contains against title/artist plus the romaji/English fields),
 * applied to the same [PlayerUiState.allSongs] source TvHomeScreen/TvLibraryScreen use.
 */
@Composable
fun TvSearchScreen(
    onPlay: (Song, List<Song>) -> Unit,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    var query by remember { mutableStateOf("") }

    val results by remember(query, playerState.allSongs) {
        derivedStateOf {
            val songs = playerState.allSongs
            if (query.isBlank()) {
                songs
            } else {
                val lower = query.lowercase()
                songs.filter { song ->
                    song.title.contains(query, ignoreCase = true) ||
                        song.artist.contains(query, ignoreCase = true) ||
                        song.titleRomaji.contains(lower) ||
                        song.artistRomaji.contains(lower) ||
                        song.titleEnglish?.contains(query, ignoreCase = true) == true
                }
            }
        }
    }

    Row(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 48.dp, vertical = 24.dp)
    ) {
        // Left: query display + on-screen keyboard
        Column(modifier = Modifier.width(420.dp)) {
            Text(
                text = query.ifBlank { "Type to search..." },
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                color = if (query.isBlank()) MaterialTheme.colorScheme.onSurfaceVariant
                        else MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(24.dp))
            TvKeyboard(query = query, onQueryChange = { query = it })
        }

        Spacer(modifier = Modifier.width(48.dp))

        // Right: live results
        Column(modifier = Modifier.fillMaxHeight().weight(1f)) {
            if (results.isEmpty() && playerState.isLoadingAllSongs) {
                // Fresh install / cold start: allSongs hasn't arrived yet (TvApp kicks off
                // playerViewModel.loadAllSongs() on entry). Show a spinner instead of a
                // permanently blank pane while that load is in flight.
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "Loading songs...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                Text(
                    text = "${results.size} results",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    itemsIndexed(results, key = { _, song -> song.id }) { index, song ->
                        TvSearchResultRow(
                            index = index + 1,
                            song = song,
                            onPlay = { onPlay(song, results) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TvSearchResultRow(index: Int, song: Song, onPlay: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp &&
                    (it.key == Key.Enter || it.key == Key.DirectionCenter)
                ) {
                    onPlay(); true
                } else false
            }
            .tvFocusScale(focused)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "$index",
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Spacer(modifier = Modifier.width(16.dp))
        Text(formatDuration(song.duration), style = MaterialTheme.typography.bodyMedium)
    }
}
