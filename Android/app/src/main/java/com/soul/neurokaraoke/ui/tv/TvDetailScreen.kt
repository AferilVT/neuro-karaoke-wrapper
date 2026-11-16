package com.soul.neurokaraoke.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Button
import androidx.tv.material3.ExperimentalTvMaterial3Api
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.SongRepository

/** Matches the "m:ss" duration formatting used across the phone UI (e.g. TabletNowPlaying.kt). */
private fun formatDuration(ms: Long): String {
    val totalSec = (ms / 1000).coerceAtLeast(0)
    return "%d:%02d".format(totalSec / 60, totalSec % 60)
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun TvDetailScreen(
    playlist: Playlist,
    onPlayAll: (List<Song>) -> Unit,
    onPlaySong: (Song, List<Song>) -> Unit,
    onBack: () -> Unit,
    // Public endpoint loader (SongRepository) works for setlists + public playlists.
    // Private user playlists need auth — callers pass a loader backed by UserPlaylistRepository.
    songLoader: (suspend (Playlist) -> List<Song>)? = null
) {
    val repo = remember { SongRepository() }
    var songs by remember { mutableStateOf(playlist.songs) }

    LaunchedEffect(playlist.id) {
        val loaded = if (songLoader != null) songLoader(playlist)
        else repo.getPlaylistSongs(playlist.id).getOrNull()
        if (!loaded.isNullOrEmpty()) songs = loaded
    }

    BackHandler { onBack() }

    val songCount = if (playlist.songCount > 0) playlist.songCount else playlist.songs.size
    val playButtonFocusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        playButtonFocusRequester.requestFocus()
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 48.dp, vertical = 24.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AsyncImage(
                    // coverUrl is usually blank for setlists — fall back to a preview cover.
                    model = playlist.coverUrl.ifBlank { playlist.previewCovers.firstOrNull().orEmpty() },
                    contentDescription = playlist.title,
                    modifier = Modifier
                        .size(240.dp)
                        .clip(RoundedCornerShape(16.dp))
                )
                Spacer(modifier = Modifier.width(32.dp))
                Column {
                    Text(playlist.title, style = MaterialTheme.typography.headlineLarge, maxLines = 2)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("$songCount songs", style = MaterialTheme.typography.bodyLarge)
                    Spacer(modifier = Modifier.height(24.dp))
                    Button(
                        onClick = { onPlayAll(songs) },
                        modifier = Modifier.focusRequester(playButtonFocusRequester)
                    ) {
                        Text("Play")
                    }
                }
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
            var focused by remember { mutableStateOf(false) }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(
                        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp &&
                            (it.key == Key.Enter || it.key == Key.DirectionCenter)
                        ) {
                            onPlaySong(song, songs); true
                        } else false
                    }
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "${index + 1}",
                    style = MaterialTheme.typography.bodyLarge,
                    modifier = Modifier.width(40.dp)
                )
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .size(56.dp)
                        .clip(RoundedCornerShape(8.dp))
                )
                Spacer(modifier = Modifier.width(16.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(song.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(song.artist, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Spacer(modifier = Modifier.width(16.dp))
                Text(formatDuration(song.duration), style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}
