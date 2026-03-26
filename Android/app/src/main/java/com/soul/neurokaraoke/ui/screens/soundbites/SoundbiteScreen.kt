package com.soul.neurokaraoke.ui.screens.soundbites

import android.media.MediaPlayer
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.api.SoundbiteApi
import com.soul.neurokaraoke.data.model.Soundbite
import com.soul.neurokaraoke.ui.components.SearchBar
import com.soul.neurokaraoke.ui.theme.CyberLabelStyle
import com.soul.neurokaraoke.ui.theme.GlassCard
import com.soul.neurokaraoke.ui.theme.GradientText
import com.soul.neurokaraoke.ui.theme.NeonTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PAGE_SIZE = 30

private enum class SoundbiteSortOption(val label: String) {
    DEFAULT("Most Played"),
    NEWEST("Newest"),
    TITLE_AZ("Title A-Z"),
    SHORTEST("Shortest"),
    LONGEST("Longest")
}

private val TAG_FILTERS = listOf(
    -1 to "All",
    0 to "Neuro",
    1 to "Evil",
    2 to "Vedal",
    3 to "Other"
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SoundbiteScreen(modifier: Modifier = Modifier) {
    val api = remember { SoundbiteApi() }
    val scope = rememberCoroutineScope()

    // Data state
    var soundbites by remember { mutableStateOf<List<Soundbite>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var isLoadingMore by remember { mutableStateOf(false) }
    var currentPage by remember { mutableIntStateOf(1) }
    var totalCount by remember { mutableIntStateOf(0) }

    // Search & filter state
    var searchQuery by remember { mutableStateOf("") }
    var debouncedQuery by remember { mutableStateOf("") }
    var selectedTag by remember { mutableIntStateOf(-1) }
    var sortOption by remember { mutableStateOf(SoundbiteSortOption.DEFAULT) }
    var showSortMenu by remember { mutableStateOf(false) }

    // Playback state
    var playingSoundbiteId by remember { mutableStateOf<String?>(null) }
    val mediaPlayer = remember { MediaPlayer() }

    // Debounce search
    LaunchedEffect(searchQuery) {
        delay(400L)
        debouncedQuery = searchQuery
    }

    // Load data when search changes
    LaunchedEffect(debouncedQuery) {
        isLoading = true
        currentPage = 1
        soundbites = emptyList()
        api.fetchSoundbites(
            page = 1,
            pageSize = PAGE_SIZE,
            search = debouncedQuery.ifBlank { null }
        ).onSuccess { response ->
            soundbites = response.items
            totalCount = response.totalCount
        }
        isLoading = false
    }

    // Cleanup MediaPlayer
    DisposableEffect(Unit) {
        mediaPlayer.setOnCompletionListener { playingSoundbiteId = null }
        mediaPlayer.setOnErrorListener { _, _, _ -> playingSoundbiteId = null; true }
        onDispose {
            mediaPlayer.release()
        }
    }

    // Filtered + sorted list
    val displaySoundbites by remember(soundbites, selectedTag, sortOption) {
        derivedStateOf {
            val filtered = if (selectedTag == -1) soundbites
            else soundbites.filter { it.tag == selectedTag }
            when (sortOption) {
                SoundbiteSortOption.DEFAULT -> filtered
                SoundbiteSortOption.NEWEST -> filtered.sortedByDescending { it.uploadedAt ?: "" }
                SoundbiteSortOption.TITLE_AZ -> filtered.sortedBy { it.title.lowercase() }
                SoundbiteSortOption.SHORTEST -> filtered.sortedBy { it.duration }
                SoundbiteSortOption.LONGEST -> filtered.sortedByDescending { it.duration }
            }
        }
    }

    val hasMore = soundbites.size < totalCount

    fun loadMore() {
        if (isLoadingMore || !hasMore) return
        isLoadingMore = true
        scope.launch {
            val nextPage = currentPage + 1
            api.fetchSoundbites(
                page = nextPage,
                pageSize = PAGE_SIZE,
                search = debouncedQuery.ifBlank { null }
            ).onSuccess { response ->
                soundbites = soundbites + response.items
                currentPage = nextPage
                totalCount = response.totalCount
            }
            isLoadingMore = false
        }
    }

    fun playSoundbite(soundbite: Soundbite) {
        if (playingSoundbiteId == soundbite.id) {
            mediaPlayer.stop()
            mediaPlayer.reset()
            playingSoundbiteId = null
        } else {
            try {
                mediaPlayer.reset()
                mediaPlayer.setDataSource(soundbite.audioUrl)
                mediaPlayer.prepareAsync()
                mediaPlayer.setOnPreparedListener { it.start() }
                playingSoundbiteId = soundbite.id
            } catch (_: Exception) {
                playingSoundbiteId = null
            }
        }
    }

    LazyColumn(
        modifier = modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 100.dp)
    ) {
        // Header
        item {
            Column(modifier = Modifier.padding(16.dp)) {
                GradientText(
                    text = "Soundbites",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.Bold,
                    gradientColors = NeonTheme.colors.gradientColors
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A collection of soundbites featuring Neuro and Evil captured from streams. Soundbites created and edited by Rachinova and CJ",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                )
            }
        }

        // Search bar
        item {
            SearchBar(
                query = searchQuery,
                onQueryChange = { searchQuery = it },
                placeholder = "Search videos by name, description, or uploader...",
                modifier = Modifier.padding(horizontal = 16.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
        }

        // Filter chips + sort
        item {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    TAG_FILTERS.forEach { (tag, label) ->
                        FilterChip(
                            selected = selectedTag == tag,
                            onClick = { selectedTag = tag },
                            label = { Text(label, style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.3f),
                                selectedLabelColor = MaterialTheme.colorScheme.primary
                            )
                        )
                    }
                }

                Box {
                    IconButton(onClick = { showSortMenu = true }) {
                        Icon(
                            Icons.AutoMirrored.Filled.Sort,
                            contentDescription = "Sort",
                            tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f)
                        )
                    }
                    DropdownMenu(
                        expanded = showSortMenu,
                        onDismissRequest = { showSortMenu = false }
                    ) {
                        SoundbiteSortOption.entries.forEach { option ->
                            DropdownMenuItem(
                                text = { Text(option.label) },
                                onClick = {
                                    sortOption = option
                                    showSortMenu = false
                                },
                                trailingIcon = if (sortOption == option) {
                                    {
                                        Icon(
                                            Icons.Default.Check,
                                            contentDescription = null,
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                } else null
                            )
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
        }

        // Results count
        item {
            Text(
                text = "$totalCount soundbites",
                style = CyberLabelStyle,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f),
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
        }

        if (isLoading) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        } else if (displaySoundbites.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (searchQuery.isNotBlank()) "No soundbites found"
                        else "No soundbites available",
                        color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                    )
                }
            }
        } else {
            itemsIndexed(
                items = displaySoundbites,
                key = { _, soundbite -> soundbite.id }
            ) { index, soundbite ->
                SoundbiteRow(
                    soundbite = soundbite,
                    isPlaying = playingSoundbiteId == soundbite.id,
                    onClick = { playSoundbite(soundbite) }
                )

                // Trigger load more near bottom
                if (index >= displaySoundbites.size - 5 && hasMore && !isLoadingMore) {
                    LaunchedEffect(currentPage) { loadMore() }
                }
            }

            if (isLoadingMore) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(24.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 2.dp
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SoundbiteRow(
    soundbite: Soundbite,
    isPlaying: Boolean,
    onClick: () -> Unit
) {
    val tagColor = when (soundbite.tag) {
        0 -> Color(0xFF00E5FF) // Neuro cyan
        1 -> Color(0xFFFF69B4) // Evil pink
        2 -> Color(0xFF4CAF50) // Vedal green
        else -> Color(0xFF9E9E9E) // Other gray
    }

    GlassCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 3.dp),
        backgroundAlpha = if (isPlaying) 0.6f else 0.35f,
        showGlow = isPlaying,
        glowRadius = 4.dp,
        cornerRadius = 10.dp
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Avatar
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (soundbite.imageUrl != null) {
                    AsyncImage(
                        model = soundbite.imageUrl,
                        contentDescription = null,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                }
                if (isPlaying) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black.copy(alpha = 0.5f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Title
            Text(
                text = soundbite.displayTitle,
                style = MaterialTheme.typography.bodyMedium,
                color = if (isPlaying) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Tag badge
            Text(
                text = soundbite.tagLabel,
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = tagColor,
                modifier = Modifier
                    .background(tagColor.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )

            Spacer(modifier = Modifier.width(8.dp))

            // Duration
            Text(
                text = "${soundbite.duration}s",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
            )

            Spacer(modifier = Modifier.width(6.dp))

            // Play count
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.PlayArrow,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
                Text(
                    text = "${soundbite.playCount}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
                )
            }

            Spacer(modifier = Modifier.width(4.dp))

            // Play/Stop button
            IconButton(
                onClick = onClick,
                modifier = Modifier.size(36.dp)
            ) {
                Icon(
                    if (isPlaying) Icons.Default.Stop else Icons.Default.PlayArrow,
                    contentDescription = if (isPlaying) "Stop" else "Play",
                    tint = if (isPlaying) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}
