package com.soul.neurokaraoke.aaos

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.QueueMusic
import androidx.compose.material.icons.filled.Radio
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
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
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import coil.compose.AsyncImage
import androidx.compose.runtime.LaunchedEffect
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.api.RadioSong
import com.soul.neurokaraoke.data.model.Playlist
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.delay

private enum class AaosTab(@StringRes val labelRes: Int) {
    LIBRARY(R.string.aaos_tab_library),
    PLAYLISTS(R.string.aaos_tab_playlists),
    RADIO(R.string.aaos_tab_radio)
}

@Composable
fun AaosHomeScreen(
    viewModel: AaosViewModel,
    controllerProvider: () -> MediaController?,
    onPlaylistClick: (Playlist) -> Unit,
    onNowPlayingClick: () -> Unit,
    onPairClick: () -> Unit
) {
    var tab by remember { mutableStateOf(AaosTab.LIBRARY) }
    val songs by viewModel.songs.collectAsState()
    val setlists by viewModel.setlists.collectAsState()
    val userPlaylists by viewModel.userPlaylists.collectAsState()
    val favorites by viewModel.favorites.collectAsState()
    val trending by viewModel.trending.collectAsState()
    val dailyMix by viewModel.dailyMix.collectAsState()
    val communityPlaylists by viewModel.communityPlaylists.collectAsState()
    val loading by viewModel.loading.collectAsState()
    val isLoggedIn by viewModel.isLoggedIn.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        TopBar(
            tab = tab,
            isLoggedIn = isLoggedIn,
            onTabSelected = { tab = it },
            onNowPlayingClick = onNowPlayingClick,
            onSignInClick = onPairClick,
            onSignOutClick = { viewModel.logout() }
        )
        Spacer(Modifier.height(20.dp))

        when (tab) {
            AaosTab.LIBRARY -> LibraryCarousels(
                recent = songs.take(15),
                dailyMix = dailyMix,
                trending = trending,
                community = communityPlaylists,
                loading = loading,
                onSongClick = { row: List<Song>, idx: Int ->
                    playSongs(controllerProvider(), row, idx)
                    onNowPlayingClick()
                },
                onPlaylistClick = onPlaylistClick
            )
            AaosTab.PLAYLISTS -> {
                val favoritesTitle = stringResource(R.string.aaos_label_favorites)
                PlaylistsGrid(
                playlists = (userPlaylists + setlists).filter { it.title.isNotBlank() },
                favorites = favorites,
                loading = loading,
                onPlaylistClick = onPlaylistClick,
                onFavoritesClick = {
                    if (favorites.isNotEmpty()) {
                        onPlaylistClick(
                            Playlist(
                                id = "favorites",
                                title = favoritesTitle,
                                coverUrl = "",
                                previewCovers = favorites.take(4).map { it.coverUrl },
                                songCount = favorites.size,
                                songs = favorites
                            )
                        )
                    }
                }
            )
            }
            AaosTab.RADIO -> RadioPanel(
                controllerProvider = controllerProvider
            )
        }
    }
}

@Composable
private fun TopBar(
    tab: AaosTab,
    isLoggedIn: Boolean,
    onTabSelected: (AaosTab) -> Unit,
    onNowPlayingClick: () -> Unit,
    onSignInClick: () -> Unit,
    onSignOutClick: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        // Breadcrumb left (matches "Songs" label in CarPlay ref)
        Text(
            text = breadcrumbFor(tab),
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1f)
        )
        // Centered pill tabs (single rounded container)
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(28.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                .padding(4.dp),
            horizontalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            AaosTab.entries.forEach { t ->
                PillTab(
                    label = stringResource(t.labelRes),
                    selected = t == tab,
                    onClick = { onTabSelected(t) }
                )
            }
        }
        // Right-side: search + now-playing
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Spacer(Modifier.weight(1f))
            CircleIconButton(
                icon = Icons.Default.AccountCircle,
                tint = if (isLoggedIn) MaterialTheme.colorScheme.primary
                       else MaterialTheme.colorScheme.onBackground,
                onClick = if (isLoggedIn) onSignOutClick else onSignInClick
            )
            CircleIconButton(
                icon = Icons.Default.GraphicEq,
                tint = MaterialTheme.colorScheme.primary,
                onClick = onNowPlayingClick
            )
        }
    }
}

@Composable
private fun breadcrumbFor(tab: AaosTab): String = when (tab) {
    AaosTab.LIBRARY -> stringResource(R.string.aaos_breadcrumb_songs)
    AaosTab.PLAYLISTS -> stringResource(R.string.aaos_breadcrumb_playlists)
    AaosTab.RADIO -> stringResource(R.string.aaos_breadcrumb_radio)
}

@Composable
private fun CircleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    tint: Color = MaterialTheme.colorScheme.onBackground,
    onClick: () -> Unit
) {
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(28.dp))
    }
}

@Composable
private fun PillTab(label: String, selected: Boolean, onClick: () -> Unit) {
    val bg = if (selected) MaterialTheme.colorScheme.primary.copy(alpha = 0.85f)
             else Color.Transparent
    val fg = if (selected) MaterialTheme.colorScheme.onPrimary
             else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.85f)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .background(bg)
            .clickable(onClick = onClick)
            .padding(horizontal = 28.dp, vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            color = fg,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
    }
}

@Composable
private fun SectionHeader(title: String, onClick: (() -> Unit)? = null) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .let { if (onClick != null) it.clickable(onClick = onClick) else it }
    ) {
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun LibraryGrid(
    songs: List<Song>,
    loading: Boolean,
    onSongClick: (Int) -> Unit
) {
    if (loading && songs.isEmpty()) { CenterText(stringResource(R.string.aaos_label_loading)); return }
    if (songs.isEmpty()) { CenterText(stringResource(R.string.aaos_label_no_songs_cached)); return }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(stringResource(R.string.aaos_section_recently_added))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            items(songs.size) { idx ->
                val song = songs[idx]
                CoverTile(
                    imageUrl = song.coverUrl,
                    title = song.title.ifBlank { stringResource(R.string.aaos_label_untitled) },
                    subtitle = song.artist,
                    onClick = { onSongClick(idx) }
                )
            }
        }
    }
}

@Composable
private fun LibraryCarousels(
    recent: List<Song>,
    dailyMix: List<Song>,
    trending: List<Song>,
    community: List<Playlist>,
    loading: Boolean,
    onSongClick: (List<Song>, Int) -> Unit,
    onPlaylistClick: (Playlist) -> Unit
) {
    if (loading && recent.isEmpty()) { CenterText(stringResource(R.string.aaos_label_loading)); return }
    LazyColumn(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (recent.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.aaos_section_songs)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(recent.size) { idx ->
                        val s = recent[idx]
                        Box(modifier = Modifier.width(200.dp)) {
                            CoverTile(imageUrl = s.coverUrl, title = s.title.ifBlank { stringResource(R.string.aaos_label_untitled) },
                                subtitle = s.artist, onClick = { onSongClick(recent, idx) })
                        }
                    }
                }
            }
        }
        if (dailyMix.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.aaos_section_daily_mix)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(dailyMix.size) { idx ->
                        val s = dailyMix[idx]
                        Box(modifier = Modifier.width(200.dp)) {
                            CoverTile(imageUrl = s.coverUrl, title = s.title.ifBlank { stringResource(R.string.aaos_label_untitled) },
                                subtitle = s.artist, onClick = { onSongClick(dailyMix, idx) })
                        }
                    }
                }
            }
        }
        if (trending.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.aaos_section_trending)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(trending.size) { idx ->
                        val s = trending[idx]
                        Box(modifier = Modifier.width(200.dp)) {
                            CoverTile(imageUrl = s.coverUrl, title = s.title.ifBlank { stringResource(R.string.aaos_label_untitled) },
                                subtitle = s.artist, onClick = { onSongClick(trending, idx) })
                        }
                    }
                }
            }
        }
        if (community.isNotEmpty()) {
            item { SectionHeader(stringResource(R.string.aaos_section_community)) }
            item {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                    items(community.size) { idx ->
                        val pl = community[idx]
                        Box(modifier = Modifier.width(200.dp)) {
                            CoverTile(
                                imageUrl = pl.coverUrl.ifBlank { pl.previewCovers.firstOrNull() ?: "" },
                                title = pl.title,
                                subtitle = if (pl.songCount > 0) stringResource(R.string.common_label_songs_format, pl.songCount) else stringResource(R.string.playlists_label_playlist),
                                onClick = { onPlaylistClick(pl) }
                            )
                        }
                    }
                }
            }
        }
        if (recent.isEmpty() && !loading) {
            item { CenterText(stringResource(R.string.aaos_label_no_songs_cached)) }
        }
    }
}

@Composable
private fun PlaylistsGrid(
    playlists: List<Playlist>,
    favorites: List<Song>,
    loading: Boolean,
    onPlaylistClick: (Playlist) -> Unit,
    onFavoritesClick: () -> Unit
) {
    if (loading && playlists.isEmpty()) { CenterText(stringResource(R.string.aaos_label_loading)); return }
    Column(modifier = Modifier.fillMaxSize()) {
        SectionHeader(stringResource(R.string.aaos_section_your_playlists))
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 200.dp),
            contentPadding = PaddingValues(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalArrangement = Arrangement.spacedBy(24.dp),
            modifier = Modifier.fillMaxSize()
        ) {
            item {
                FavoritesTile(count = favorites.size, onClick = onFavoritesClick)
            }
            items(playlists.size) { idx ->
                val pl = playlists[idx]
                CoverTile(
                    imageUrl = pl.coverUrl.ifBlank { pl.previewCovers.firstOrNull() ?: "" },
                    title = pl.title,
                    subtitle = if (pl.songCount > 0) stringResource(R.string.common_label_songs_format, pl.songCount) else stringResource(R.string.playlists_label_playlist),
                    onClick = { onPlaylistClick(pl) }
                )
            }
        }
    }
}

@Composable
private fun FavoritesTile(count: Int, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(8.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Favorite,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(64.dp)
            )
        }
        Spacer(Modifier.height(8.dp))
        Text(
            stringResource(R.string.aaos_label_favorites),
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            stringResource(R.string.common_label_songs_format, count),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun RadioPanel(controllerProvider: () -> MediaController?) {
    var currentSong by remember { mutableStateOf<RadioSong?>(null) }
    var isRadioPlaying by remember { mutableStateOf(false) }

    // Poll current-state from server every 15s
    LaunchedEffect(Unit) {
        val api = RadioApi()
        while (true) {
            api.fetchCurrentState().onSuccess { state ->
                currentSong = state.current
            }
            delay(15_000L)
        }
    }

    // Track whether radio is actually playing in the player
    LaunchedEffect(Unit) {
        while (true) {
            val ctrl = controllerProvider()
            isRadioPlaying = ctrl?.isPlaying == true &&
                ctrl.currentMediaItem?.mediaId == "radio_live"
            delay(500L)
        }
    }

    Row(
        modifier = Modifier.fillMaxSize().padding(top = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(40.dp)
    ) {
        // Album art (live song art or radio icon placeholder)
        Box(
            modifier = Modifier
                .size(320.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            val artUrl = currentSong?.coverUrl.orEmpty()
            if (artUrl.isNotBlank()) {
                AsyncImage(
                    model = artUrl,
                    contentDescription = currentSong?.title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize().clip(RoundedCornerShape(24.dp))
                )
            } else {
                Icon(
                    imageVector = Icons.Default.Radio,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(120.dp)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            // Station badge
            Text(
                stringResource(R.string.radio_station_badge_live),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
            Spacer(Modifier.height(8.dp))

            // Current song title
            Text(
                text = currentSong?.title ?: stringResource(R.string.radio_default_title),
                color = MaterialTheme.colorScheme.onBackground,
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.height(6.dp))

            // Artists
            if (currentSong != null) {
                val artists = currentSong!!.originalArtists.joinToString(", ")
                val covers = currentSong!!.coverArtistDisplay
                Text(
                    stringResource(R.string.radio_covered_by, artists, covers),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            } else {
                Text(
                    stringResource(R.string.radio_default_title),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
            }

            Spacer(Modifier.height(32.dp))

            // Listen Live / Stop toggle
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(36.dp))
                    .background(
                        if (isRadioPlaying) MaterialTheme.colorScheme.error
                        else MaterialTheme.colorScheme.primary
                    )
                    .clickable {
                        if (isRadioPlaying) {
                            AaosRadioPoller.stop()
                            controllerProvider()?.stop()
                        } else {
                            playRadio(controllerProvider())
                        }
                    }
                    .padding(horizontal = 32.dp, vertical = 20.dp)
            ) {
                Icon(
                    imageVector = if (isRadioPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = if (isRadioPlaying) MaterialTheme.colorScheme.onError
                           else MaterialTheme.colorScheme.onPrimary,
                    modifier = Modifier.size(40.dp)
                )
                Spacer(Modifier.width(12.dp))
                Text(
                    text = if (isRadioPlaying) stringResource(R.string.aaos_radio_button_stop) else stringResource(R.string.aaos_radio_button_listen_live),
                    color = if (isRadioPlaying) MaterialTheme.colorScheme.onError
                            else MaterialTheme.colorScheme.onPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
fun CoverTile(
    imageUrl: String,
    title: String,
    subtitle: String,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clip(RoundedCornerShape(24.dp))
            .clickable(onClick = onClick)
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(24.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant)
        ) {
            if (imageUrl.isNotBlank()) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = title,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            } else {
                Icon(
                    imageVector = Icons.Default.QueueMusic,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp).align(Alignment.Center)
                )
            }
        }
        Spacer(Modifier.height(10.dp))
        Text(
            text = title,
            color = MaterialTheme.colorScheme.onBackground,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = subtitle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun CenterText(text: String) {
    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(text, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

internal fun playSongs(controller: MediaController?, songs: List<Song>, startIndex: Int) {
    val c = controller ?: return
    AaosRadioPoller.stop()
    val items = songs.map { it.toMediaItem() }
    c.setMediaItems(items, startIndex.coerceAtLeast(0), 0L)
    c.prepare()
    c.play()
}

internal fun playRadio(controller: MediaController?) {
    val c = controller ?: return
    val item = MediaItem.Builder()
        .setUri(RadioApi.STREAM_URL)
        .setMediaId("radio_live")
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle("Neuro 21 Station")
                .setArtist("LIVE")
                .setAlbumTitle("Neuro 21 Station • LIVE")
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsPlayable(true)
                .build()
        )
        .build()
    c.setMediaItem(item)
    c.prepare()
    c.play()
    AaosRadioPoller.start(c)
}

private fun Song.toMediaItem(): MediaItem = MediaItem.Builder()
    .setUri(audioUrl)
    .setMediaId(id)
    .setMediaMetadata(
        MediaMetadata.Builder()
            .setTitle(title)
            .setArtist("$artist • $coverArtist")
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .setIsPlayable(true)
            .apply {
                if (coverUrl.isNotBlank()) setArtworkUri(android.net.Uri.parse(coverUrl))
            }
            .build()
    )
    .build()
