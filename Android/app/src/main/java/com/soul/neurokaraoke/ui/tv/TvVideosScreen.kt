package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.api.Video
import com.soul.neurokaraoke.data.api.VideoApi
import com.soul.neurokaraoke.data.api.VideoCategories

private const val PAGE_SIZE = 30

/**
 * TV Videos screen: one horizontal rail per category ([VideoCategories.ORDER]) over the
 * `/api/videos` gallery. Rails page lazily (30 at a time) as the user scrolls toward the
 * end. Selecting a card raises [onPlay] so [TvApp] can open the native HLS player overlay.
 */
@Composable
fun TvVideosScreen(onPlay: (Video) -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        itemsIndexed(VideoCategories.ORDER, key = { _, id -> id }) { _, category ->
            VideoRail(category = category, onPlay = onPlay)
        }
    }
}

@Composable
private fun VideoRail(category: Int, onPlay: (Video) -> Unit) {
    val api = remember { VideoApi() }
    val items = remember { mutableListOf<Video>().toMutableStateList() }
    var total by remember { mutableIntStateOf(-1) }     // -1 = not loaded yet
    var loading by remember { mutableStateOf(false) }
    var failed by remember { mutableStateOf(false) }

    suspend fun loadNext() {
        if (loading) return
        if (total in 0..items.size) return              // fully loaded
        loading = true
        api.fetchVideos(category, items.size, PAGE_SIZE).fold(
            onSuccess = { page -> total = page.totalCount; items.addAll(page.items) },
            onFailure = { failed = true },
        )
        loading = false
    }

    LaunchedEffect(category) { loadNext() }

    // Loaded and empty → render nothing (e.g. an empty category).
    if (total == 0) return

    Column(Modifier.padding(vertical = 12.dp)) {
        Text(
            VideoCategories.label(category),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(start = 56.dp)
        )
        Spacer(Modifier.height(10.dp))
        if (failed && items.isEmpty()) {
            Text(
                "Couldn't load videos.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 56.dp)
            )
            return@Column
        }
        LazyRow(
            contentPadding = PaddingValues(horizontal = 56.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            itemsIndexed(items, key = { _, v -> v.id }) { index, video ->
                // Page in more as focus/scroll nears the end of the loaded set.
                if (index >= items.size - 5 && items.size < total) {
                    LaunchedEffect(items.size) { loadNext() }
                }
                VideoCard(video = video, onPlay = onPlay)
            }
        }
    }
}

@Composable
private fun VideoCard(video: Video, onPlay: (Video) -> Unit) {
    var focused by remember { mutableStateOf(false) }
    Column(
        Modifier
            .width(280.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .onKeyEvent {
                if (it.type == KeyEventType.KeyUp &&
                    (it.key == Key.Enter || it.key == Key.DirectionCenter)
                ) {
                    onPlay(video); true
                } else false
            }
            .tvFocusScale(focused)
    ) {
        AsyncImage(
            model = video.thumbnailUrl,
            contentDescription = video.name,
            modifier = Modifier
                .width(280.dp)
                .aspectRatio(16f / 9f)
                .tvCoverFocus(focused)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            video.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (focused) MaterialTheme.colorScheme.onSurface
            else MaterialTheme.colorScheme.onSurface.copy(alpha = 0.85f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.width(280.dp)
        )
        Text(
            "${video.views} views",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
