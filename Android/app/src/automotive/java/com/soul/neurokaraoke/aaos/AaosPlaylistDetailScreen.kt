package com.soul.neurokaraoke.aaos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Shuffle
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.SongRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.MainScope

@Composable
fun AaosPlaylistDetailScreen(
    playlist: Playlist,
    viewModel: AaosViewModel,
    controllerProvider: () -> MediaController?,
    onBack: () -> Unit,
    onNowPlayingClick: () -> Unit
) {
    var songs by remember { mutableStateOf(playlist.songs) }
    var loaded by remember { mutableStateOf(playlist.songs.isNotEmpty()) }

    LaunchedEffect(playlist.id) {
        if (!loaded) {
            withContext(Dispatchers.IO) {
                val repo = SongRepository(NeuroKaraokeApi())
                val fetched = repo.getPlaylistSongs(playlist.id).getOrNull().orEmpty()
                songs = fetched
                loaded = true
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        Header(
            playlist = playlist,
            songs = songs,
            controllerProvider = controllerProvider,
            onBack = onBack,
            onNowPlayingClick = onNowPlayingClick
        )
        Spacer(Modifier.height(24.dp))
        if (!loaded) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.aaos_playlist_detail_loading), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else if (songs.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(stringResource(R.string.aaos_playlist_detail_empty), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        } else {
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.fillMaxSize()
            ) {
                items(songs.size) { idx ->
                    val song = songs[idx]
                    TrackRow(
                        index = idx + 1,
                        song = song,
                        onClick = {
                            playSongs(controllerProvider(), songs, idx)
                            onNowPlayingClick()
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun Header(
    playlist: Playlist,
    songs: List<Song>,
    controllerProvider: () -> MediaController?,
    onBack: () -> Unit,
    onNowPlayingClick: () -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
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
                contentDescription = stringResource(R.string.aaos_playlist_detail_content_description_back),
                tint = MaterialTheme.colorScheme.onBackground
            )
        }
        Spacer(Modifier.width(24.dp))
        val coverUrl = playlist.coverUrl.ifBlank { playlist.previewCovers.firstOrNull() ?: "" }
        Box(
            modifier = Modifier
                .size(160.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (coverUrl.isNotBlank()) {
                AsyncImage(
                    model = coverUrl,
                    contentDescription = playlist.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
        Spacer(Modifier.width(24.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = playlist.title,
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = if (playlist.songCount > 0) stringResource(R.string.common_label_songs_format, playlist.songCount) else stringResource(R.string.playlists_label_playlist),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                ActionButton(
                    label = stringResource(R.string.aaos_playlist_detail_button_play),
                    icon = Icons.Default.PlayArrow,
                    primary = true,
                    onClick = {
                        playSongs(controllerProvider(), songs, 0)
                        onNowPlayingClick()
                    }
                )
                ActionButton(
                    label = stringResource(R.string.aaos_playlist_detail_button_shuffle),
                    icon = Icons.Default.Shuffle,
                    primary = false,
                    onClick = {
                        playSongs(controllerProvider(), songs.shuffled(), 0)
                        onNowPlayingClick()
                    }
                )
            }
        }
    }
}

@Composable
private fun ActionButton(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    primary: Boolean,
    onClick: () -> Unit
) {
    val bg = if (primary) MaterialTheme.colorScheme.primary
             else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
    val fg = if (primary) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onBackground
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .clip(RoundedCornerShape(28.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 14.dp)
    ) {
        Icon(icon, contentDescription = null, tint = fg, modifier = Modifier.size(24.dp))
        Spacer(Modifier.width(8.dp))
        Text(label, color = fg, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun TrackRow(index: Int, song: Song, onClick: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp)
    ) {
        Text(
            text = "$index",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.width(40.dp)
        )
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = song.title.ifBlank { stringResource(R.string.aaos_label_untitled) },
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${song.artist} • ${song.coverArtist}",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}
