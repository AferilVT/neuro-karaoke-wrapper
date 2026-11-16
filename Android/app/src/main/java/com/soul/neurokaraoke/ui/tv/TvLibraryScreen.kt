package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.viewmodel.PlayerViewModel

/** Library sub-categories, shown as a focusable segment bar above the grid. */
enum class TvLibrarySegment(val label: String) {
    SETLISTS("Setlists"),
    PUBLIC("Public"),
    FAVOURITES("Favourites"),
    YOUR_PLAYLISTS("Your Playlists")
}

/**
 * TV Library screen: a focusable segment bar (Setlists / Public / Favourites /
 * Your Playlists) over a single 5-column [LazyVerticalGrid]. Setlists, Public and
 * Your Playlists are playlist-cover grids that open the detail overlay; Favourites
 * is a song-cover grid that plays on select (favourites are songs, not playlists).
 * Favourites / Your Playlists require sign-in to sync from the server.
 */
@Composable
fun TvLibraryScreen(
    publicPlaylists: List<Playlist>,
    favourites: List<Song>,
    userPlaylists: List<Playlist>,
    isLoggedIn: Boolean,
    onOpenDetail: (Playlist) -> Unit,
    onOpenUserPlaylist: (Playlist) -> Unit,
    onPlayFavourite: (Song, List<Song>) -> Unit,
    onSignIn: () -> Unit,
    playerViewModel: PlayerViewModel = viewModel()
) {
    // Setlists come from the shared player state; read here (not hoisted to TvApp) so
    // playback ticks recompose only this screen while it's visible, not the whole app.
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val setlists = playerState.availablePlaylists
    var segment by remember { mutableStateOf(TvLibrarySegment.SETLISTS) }

    Column(Modifier.fillMaxSize()) {
        TvLibrarySegmentBar(selected = segment, onSelect = { segment = it })
        Box(Modifier.fillMaxSize()) {
            when (segment) {
                TvLibrarySegment.SETLISTS ->
                    TvPlaylistGrid(setlists, onOpenDetail, empty = "No setlists yet.")
                TvLibrarySegment.PUBLIC ->
                    TvPlaylistGrid(publicPlaylists, onOpenDetail, empty = "No public playlists yet.")
                TvLibrarySegment.FAVOURITES ->
                    if (!isLoggedIn) TvLibrarySignIn("Sign in to sync your favourites.", onSignIn)
                    else TvSongGrid(favourites, onPlayFavourite, empty = "No favourites yet.")
                TvLibrarySegment.YOUR_PLAYLISTS ->
                    if (!isLoggedIn) TvLibrarySignIn("Sign in to sync your playlists.", onSignIn)
                    else TvPlaylistGrid(userPlaylists, onOpenUserPlaylist, empty = "No playlists yet.")
            }
        }
    }
}

@Composable
private fun TvLibrarySegmentBar(
    selected: TvLibrarySegment,
    onSelect: (TvLibrarySegment) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 56.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        TvLibrarySegment.entries.forEach { seg ->
            var focused by remember { mutableStateOf(false) }
            val active = seg == selected
            Text(
                text = seg.label,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (active) FontWeight.Bold else FontWeight.Normal,
                color = when {
                    focused || active -> MaterialTheme.colorScheme.onSurface
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier
                    .clip(RoundedCornerShape(percent = 50))
                    .background(
                        if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.22f)
                        else if (active) MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)
                        else androidx.compose.ui.graphics.Color.Transparent
                    )
                    // Switch on focus (same idiom as the main TvNavBar).
                    .onFocusChanged { if (it.isFocused) { focused = true; onSelect(seg) } else focused = false }
                    .focusable()
                    .padding(horizontal = 20.dp, vertical = 8.dp)
            )
        }
    }
}

/** Shared 5-column playlist-cover grid. Selecting a card opens the detail overlay. */
@Composable
private fun TvPlaylistGrid(
    playlists: List<Playlist>,
    onOpenDetail: (Playlist) -> Unit,
    empty: String
) {
    if (playlists.isEmpty()) {
        TvLibraryEmpty(empty)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(playlists, key = { it.id }) { playlist ->
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp &&
                            (it.key == Key.Enter || it.key == Key.DirectionCenter)
                        ) {
                            onOpenDetail(playlist); true
                        } else false
                    }
                    .tvFocusScale(focused)
            ) {
                AsyncImage(
                    // Setlists store their art in previewCovers (2x2 on phone); coverUrl is
                    // usually blank for them, so fall back to the first preview cover.
                    model = playlist.coverUrl.ifBlank { playlist.previewCovers.firstOrNull().orEmpty() },
                    contentDescription = playlist.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .tvCoverFocus(focused)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    playlist.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                val songCount = if (playlist.songCount > 0) playlist.songCount else playlist.songs.size
                Text(
                    "$songCount songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

/** 5-column song-cover grid for Favourites. Selecting plays the song, queue = the grid. */
@Composable
private fun TvSongGrid(
    songs: List<Song>,
    onPlay: (Song, List<Song>) -> Unit,
    empty: String
) {
    if (songs.isEmpty()) {
        TvLibraryEmpty(empty)
        return
    }
    LazyVerticalGrid(
        columns = GridCells.Fixed(5),
        contentPadding = PaddingValues(horizontal = 56.dp, vertical = 24.dp),
        horizontalArrangement = Arrangement.spacedBy(20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp),
        modifier = Modifier.fillMaxSize()
    ) {
        items(songs, key = { it.id }) { song ->
            var focused by remember { mutableStateOf(false) }
            Column(
                Modifier
                    .fillMaxWidth()
                    .onFocusChanged { focused = it.isFocused }
                    .focusable()
                    .onKeyEvent {
                        if (it.type == KeyEventType.KeyUp &&
                            (it.key == Key.Enter || it.key == Key.DirectionCenter)
                        ) {
                            onPlay(song, songs); true
                        } else false
                    }
                    .tvFocusScale(focused)
            ) {
                AsyncImage(
                    model = song.coverUrl,
                    contentDescription = song.title,
                    modifier = Modifier
                        .fillMaxWidth()
                        .aspectRatio(1f)
                        .tvCoverFocus(focused)
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    song.title,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (focused) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    song.artist,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

@Composable
private fun TvLibraryEmpty(text: String) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun TvLibrarySignIn(message: String, onSignIn: () -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                .onFocusChanged { focused = it.isFocused }
                .focusable()
                .onKeyEvent {
                    if (it.type == KeyEventType.KeyUp &&
                        (it.key == Key.Enter || it.key == Key.DirectionCenter)
                    ) {
                        onSignIn(); true
                    } else false
                }
                .tvFocusScale(focused)
                .padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                message,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "Press OK to go to Account",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
