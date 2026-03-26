package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.graphics.painter.ColorPainter
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import androidx.compose.foundation.border
import androidx.compose.ui.text.font.FontFamily
import com.soul.neurokaraoke.ui.theme.DuetColor
import com.soul.neurokaraoke.ui.theme.EvilColor
import com.soul.neurokaraoke.ui.theme.NeuroColor
import com.soul.neurokaraoke.ui.theme.Primary

@Composable
fun SongListItem(
    song: Song,
    index: Int? = null,
    onClick: () -> Unit,
    isFavorite: Boolean = false,
    onFavoriteClick: () -> Unit = {},
    isDownloaded: Boolean = false,
    downloadProgress: Float? = null,
    onDownloadClick: (() -> Unit)? = null,
    onRemoveDownloadClick: (() -> Unit)? = null,
    onAddToPlaylistClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showMenu by remember { mutableStateOf(false) }

    val singerColor = when (song.singer) {
        Singer.NEURO -> NeuroColor
        Singer.EVIL -> EvilColor
        Singer.DUET -> DuetColor
        Singer.OTHER -> Primary
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp)
    ) {
        // Index number (monospace)
        if (index != null) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
        }

        // Thumbnail with play overlay + singer-color border
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(4.dp))
                .border(1.dp, singerColor.copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = song.coverUrl,
                contentDescription = song.title,
                contentScale = ContentScale.Crop,
                placeholder = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                error = ColorPainter(MaterialTheme.colorScheme.surfaceVariant),
                modifier = Modifier.matchParentSize()
            )
            // Play icon overlay
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(MaterialTheme.colorScheme.background.copy(alpha = 0.4f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = "Play",
                    tint = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.size(24.dp)
                )
            }

            // Downloaded badge
            if (isDownloaded) {
                Icon(
                    imageVector = Icons.Default.CheckCircle,
                    contentDescription = "Downloaded",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier
                        .size(16.dp)
                        .align(Alignment.BottomEnd)
                )
            }
        }

        Spacer(modifier = Modifier.width(12.dp))

        // Song info
        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Singer indicator dot
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(singerColor)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "${song.artist} • ${song.coverArtist}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Favorite button
        IconButton(
            onClick = onFavoriteClick
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) Primary else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        // More options with download menu
        Box {
            IconButton(onClick = { showMenu = true }) {
                if (downloadProgress != null) {
                    CircularProgressIndicator(
                        progress = { downloadProgress },
                        modifier = Modifier.size(24.dp),
                        color = MaterialTheme.colorScheme.primary,
                        strokeWidth = 2.dp
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "More options",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            DropdownMenu(
                expanded = showMenu,
                onDismissRequest = { showMenu = false }
            ) {
                if (onAddToPlaylistClick != null) {
                    DropdownMenuItem(
                        text = { Text("Add to Playlist") },
                        onClick = {
                            showMenu = false
                            onAddToPlaylistClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.PlaylistAdd,
                                contentDescription = null
                            )
                        }
                    )
                }
                if (isDownloaded) {
                    DropdownMenuItem(
                        text = { Text("Remove Download") },
                        onClick = {
                            showMenu = false
                            onRemoveDownloadClick?.invoke()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.DeleteOutline,
                                contentDescription = null
                            )
                        }
                    )
                } else if (onDownloadClick != null) {
                    DropdownMenuItem(
                        text = { Text("Download") },
                        onClick = {
                            showMenu = false
                            onDownloadClick()
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Download,
                                contentDescription = null
                            )
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun SimpleSongListItem(
    song: Song,
    index: Int? = null,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        if (index != null) {
            Text(
                text = index.toString().padStart(2, '0'),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.width(28.dp)
            )
        }

        Column(
            modifier = Modifier.weight(1f)
        ) {
            Text(
                text = song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onBackground,
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
    }
}
