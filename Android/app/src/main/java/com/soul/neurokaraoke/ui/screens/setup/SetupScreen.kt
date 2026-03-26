package com.soul.neurokaraoke.ui.screens.setup

import android.annotation.SuppressLint
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.PlaylistCatalog
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.SongRepository
import com.soul.neurokaraoke.ui.theme.CyberLabelStyle
import com.soul.neurokaraoke.ui.theme.CyberpunkBackground
import com.soul.neurokaraoke.ui.theme.GradientText
import com.soul.neurokaraoke.ui.theme.NeonProgressBar
import com.soul.neurokaraoke.ui.theme.NeonTheme
import com.soul.neurokaraoke.ui.theme.neonGlow
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope

@Composable
fun SetupScreen(
    onSetupComplete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val songCache = remember { SongCache(context) }
    val playlistCatalog = remember { PlaylistCatalog(context) }
    val repository = remember { SongRepository() }
    val api = remember { NeuroKaraokeApi() }

    var progress by remember { mutableFloatStateOf(0f) }
    var statusText by remember { mutableStateOf("Preparing...") }
    var songsLoaded by remember { mutableStateOf(0) }
    var totalSongs by remember { mutableStateOf(0) }
    var currentPlaylist by remember { mutableStateOf("") }

    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        label = "progress"
    )

    val neonColors = NeonTheme.colors

    LaunchedEffect(Unit) {
        try {
            // Step 1: Load playlists
            statusText = "Loading playlists..."
            progress = 0.1f
            val playlists = playlistCatalog.getPlaylists()

            if (playlists.isEmpty()) {
                statusText = "No playlists found"
                progress = 1f
                songCache.markSetupComplete()
                onSetupComplete()
                return@LaunchedEffect
            }

            // Step 2: Fetch playlist info (names, covers) in parallel
            statusText = "Fetching playlist info..."
            progress = 0.2f

            coroutineScope {
                playlists.map { playlist ->
                    async {
                        val needsRefresh = playlist.title.isEmpty() ||
                            playlist.previewCovers.isEmpty() ||
                            playlist.songCount == 0

                        if (needsRefresh) {
                            api.fetchPlaylistInfo(playlist.id).fold(
                                onSuccess = { info ->
                                    val updated = playlist.copy(
                                        title = info.name.ifEmpty { playlist.title },
                                        coverUrl = info.coverUrl.ifEmpty { playlist.coverUrl },
                                        previewCovers = info.previewCovers.ifEmpty { playlist.previewCovers },
                                        songCount = if (info.songCount > 0) info.songCount else playlist.songCount
                                    )
                                    playlistCatalog.updatePlaylist(updated)
                                },
                                onFailure = { error ->
                                    error.printStackTrace()
                                }
                            )
                        }
                    }
                }.awaitAll()
            }

            // Step 3: Load all songs from all playlists in parallel
            statusText = "Loading songs..."
            progress = 0.3f

            val allSongs = mutableListOf<Song>()
            val playlistCount = playlists.size
            var completedPlaylists = 0

            coroutineScope {
                playlists.map { playlist ->
                    async {
                        currentPlaylist = playlist.title.ifEmpty { "Playlist ${playlist.id.take(8)}" }
                        repository.getPlaylistSongs(playlist.id).onSuccess { songs ->
                            synchronized(allSongs) {
                                allSongs.addAll(songs)
                                songsLoaded = allSongs.size
                            }
                        }
                        completedPlaylists++
                        progress = 0.3f + (0.6f * completedPlaylists / playlistCount)
                    }
                }.awaitAll()
            }

            // Step 4: Remove duplicates and cache
            statusText = "Caching ${allSongs.size} songs..."
            progress = 0.9f
            val uniqueSongs = allSongs.distinctBy { it.id }
            totalSongs = uniqueSongs.size

            songCache.cacheSongs(uniqueSongs)

            // Step 5: Complete
            statusText = "Setup complete!"
            progress = 1f
            songCache.markSetupComplete()

            kotlinx.coroutines.delay(500)
            onSetupComplete()

        } catch (e: Exception) {
            e.printStackTrace()
            statusText = "Setup failed: ${e.message}"
            songCache.markSetupComplete()
            kotlinx.coroutines.delay(2000)
            onSetupComplete()
        }
    }

    // Preload the Blazor WASM login page in a hidden WebView so the service worker
    // caches all .wasm files. This makes the Sign In WebView load much faster later.
    val preloadWebView = remember { mutableStateOf<WebView?>(null) }
    PreloadLoginWebView(
        onWebViewCreated = { preloadWebView.value = it }
    )
    DisposableEffect(Unit) {
        onDispose {
            preloadWebView.value?.destroy()
        }
    }

    CyberpunkBackground(modifier = modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp)
                .align(Alignment.Center)
        ) {
            // App logo with neon glow frame
            AsyncImage(
                model = "https://storage.neurokaraoke.com/image/neuro-evil.webp",
                contentDescription = "Neuro Karaoke",
                modifier = Modifier
                    .size(120.dp)
                    .neonGlow(
                        color = neonColors.glowColor,
                        radius = 16.dp,
                        cornerRadius = 24.dp,
                        alpha = 0.35f
                    )
                    .clip(RoundedCornerShape(24.dp))
            )

            Spacer(modifier = Modifier.height(32.dp))

            GradientText(
                text = "Neuro Karaoke",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.Bold,
                gradientColors = neonColors.gradientColors
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "FIRST TIME SETUP",
                style = CyberLabelStyle,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Neon progress bar
            NeonProgressBar(
                progress = animatedProgress,
                gradientColors = neonColors.gradientColors,
                height = 8.dp,
                cornerRadius = 4.dp,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Status text
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            if (songsLoaded > 0) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "$songsLoaded songs loaded",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            if (currentPlaylist.isNotEmpty() && progress < 0.9f) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = currentPlaylist,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.7f)
                )
            }

            Spacer(modifier = Modifier.height(48.dp))

            Text(
                text = "This only happens once.\nFuture launches will be instant!",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                textAlign = TextAlign.Center
            )
        }
    }
}

/**
 * Creates a hidden WebView that loads the login page to warm up the
 * Blazor WASM service worker cache. The WebView is never shown to the user.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
private fun PreloadLoginWebView(
    onWebViewCreated: (WebView) -> Unit
) {
    val context = LocalContext.current

    LaunchedEffect(Unit) {
        val webView = WebView(context).apply {
            settings.javaScriptEnabled = true
            settings.domStorageEnabled = true
            settings.databaseEnabled = true
            settings.cacheMode = WebSettings.LOAD_DEFAULT
            settings.userAgentString = settings.userAgentString + " NeuroKaraokeApp"

            CookieManager.getInstance().setAcceptCookie(true)
            CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)

            // No UI needed — just let the service worker cache the .wasm files
            webViewClient = android.webkit.WebViewClient()

            loadUrl("https://neurokaraoke.com/login-page")
        }
        onWebViewCreated(webView)
    }
}
