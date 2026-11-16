package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.runtime.*
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.focusRestorer
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.*
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.RecentlyPlayedStore
import com.soul.neurokaraoke.data.repository.SongRepository
import com.soul.neurokaraoke.viewmodel.PlayerViewModel

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvRail(title: String, songs: List<Song>, onPlay: (Song) -> Unit, subtitle: String = "") {
    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            title,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 56.dp)
        )
        if (subtitle.isNotEmpty()) {
            Text(
                subtitle, style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp)
            )
        }
        Spacer(Modifier.height(10.dp))
        LazyRow(
            modifier = Modifier.focusRestorer(),
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            items(songs, key = { it.id }) { song ->
                var focused by remember { mutableStateOf(false) }
                Column(
                    Modifier
                        .width(180.dp)
                        .onFocusChanged { focused = it.isFocused }
                        .focusable()
                        .onKeyEvent {
                            if (it.type == KeyEventType.KeyUp &&
                                (it.key == Key.Enter || it.key == Key.DirectionCenter)
                            ) {
                                onPlay(song); true
                            } else false
                        }
                        .tvFocusScale(focused)
                ) {
                    AsyncImage(
                        model = song.coverUrl, contentDescription = song.title,
                        modifier = Modifier.size(180.dp).tvCoverFocus(focused)
                    )
                    Spacer(Modifier.height(8.dp))
                    // Labels are always shown (like tvOS), not only on focus. The focused card is
                    // emphasized by the surrounding tvFocusScale; the title brightens on focus.
                    Text(
                        song.title,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (focused) MaterialTheme.colorScheme.onSurface
                        else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(180.dp)
                    )
                    Text(
                        song.artist,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.width(180.dp)
                    )
                }
            }
        }
    }
}

/**
 * TV Home screen: rails sourced from the same data the phone Home screen
 * has access to via [PlayerViewModel] — there is no dedicated "trending" or
 * "new releases" API, so those rails are derived from [PlayerUiState.allSongs]
 * and the newest setlist in [PlayerUiState.availablePlaylists].
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TvHomeScreen(
    onPlay: (Song) -> Unit,
    onOpenDetail: (Playlist) -> Unit,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val playerState by playerViewModel.uiState.collectAsStateWithLifecycle()
    val recentlyPlayed by RecentlyPlayedStore.songs.collectAsStateWithLifecycle()
    val songRepository = remember { SongRepository() }

    val allSongs = playerState.allSongs
    val newestPlaylist = remember(playerState.availablePlaylists) {
        playerState.availablePlaylists.firstOrNull()
    }
    var newReleases by remember { mutableStateOf<List<Song>>(emptyList()) }

    LaunchedEffect(newestPlaylist?.id) {
        val playlistId = newestPlaylist?.id
        newReleases = if (playlistId != null) {
            songRepository.getPlaylistSongs(playlistId).getOrNull() ?: allSongs.takeLast(20)
        } else {
            allSongs.takeLast(20)
        }
    }

    val trending = remember(allSongs) { allSongs.take(20) }

    // focusRestorer() + stable per-rail keys keep row identity (and D-pad focus) stable when
    // the "Recently Played" rail is inserted/removed at the top after a song plays — without
    // this, the shift in item position drops focus to null and the remote goes dead.
    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .focusRestorer()
    ) {
        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recently") {
                // Scope playback to the Recently Played list itself (matches the phone's
                // ui/screens/library/RecentlyPlayedScreen.kt, which uses playSongWithQueue
                // so "up next" stays within recents instead of falling back to allSongs).
                TvRail(
                    title = "Recently Played",
                    subtitle = "Pick up where you left off",
                    songs = recentlyPlayed,
                    onPlay = { song -> playerViewModel.playSongWithQueue(song, recentlyPlayed) }
                )
            }
        }
        item(key = "trending") {
            TvRail(
                title = "Trending",
                subtitle = "What everyone's singing",
                songs = trending,
                onPlay = onPlay
            )
        }
        item(key = "newreleases") {
            TvRail(
                title = "New Releases",
                subtitle = "Fresh from the catalog",
                songs = newReleases,
                onPlay = onPlay
            )
        }
    }
}
