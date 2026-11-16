package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.model.Badge
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.ProfileViewModel

private val tvProfileGradient = listOf(Color(0xFF7C5CFC), Color(0xFFB47BFF))

/**
 * TV Account screen: mirrors ui/screens/profile/ProfileScreen.kt (avatar, level/XP,
 * usage tiles, badges) fed by the same [ProfileViewModel]. Signed-out state mirrors
 * the sign-in prompt used by ui/screens/library/LibraryScreen.kt.
 */
@Composable
fun TvAccountScreen(authViewModel: AuthViewModel) {
    val authState by authViewModel.uiState.collectAsStateWithLifecycle()
    val user = authState.user
    var showPairing by remember { mutableStateOf(false) }

    if (!authState.isLoggedIn || user == null) {
        if (showPairing) {
            TvPairScreen(
                onPaired = { jwt ->
                    authViewModel.handleJwtFromWebView(jwt)
                    showPairing = false
                },
                onBack = { showPairing = false }
            )
        } else {
            // The sign-in card carries both username/password and a Discord (→ pairing) option.
            TvLoginScreen(
                authViewModel = authViewModel,
                onDiscord = { showPairing = true }
            )
        }
        return
    }

    val profileViewModel: ProfileViewModel = viewModel()
    val state by profileViewModel.uiState.collectAsStateWithLifecycle()
    val accessToken = authViewModel.getAccessToken().orEmpty()

    LaunchedEffect(accessToken) {
        if (accessToken.isNotEmpty()) profileViewModel.load(accessToken)
    }

    // Mirrors ProfileScreen.kt's `state.isLoading && profile == null` branch: show a
    // spinner during the initial load instead of silently rendering the User-only fallback.
    if (state.isLoading && state.profile == null) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        return
    }

    val profile = state.profile
    val displayName = profile?.displayName?.takeIf { it.isNotEmpty() } ?: user.displayName
    val avatarUrl = profile?.avatarUrl?.takeIf { it.isNotEmpty() } ?: user.avatarUrl

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 48.dp, vertical = 32.dp)
    ) {
        // Profile card
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
            ) {
                if (!avatarUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = displayName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Brush.linearGradient(tvProfileGradient)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayName.firstOrNull()?.uppercase() ?: "?",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(24.dp))

            Column {
                Text(displayName, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

                if (profile?.level != null && profile.level > 0) {
                    Spacer(modifier = Modifier.height(6.dp))
                    val titleSuffix = profile.levelTitle?.takeIf { it.isNotEmpty() }?.let { " · $it" } ?: ""
                    Text(
                        text = "LV ${profile.level}$titleSuffix",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (profile?.levelProgress != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { (profile.levelProgress / 100.0).coerceIn(0.0, 1.0).toFloat() },
                        modifier = Modifier
                            .width(320.dp)
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Row {
                        profile.totalXP?.let {
                            Text(
                                text = "$it XP",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        profile.xpToNextLevel?.takeIf { it > 0 }?.let {
                            Text(
                                text = "$it XP to next level",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(32.dp))

        // Usage tiles
        val limits = state.uploadLimits
        if (limits != null) {
            val usedMb = limits.usedStorageBytes / 1_048_576
            val maxMb = limits.maxStorageBytes / 1_048_576
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                UsageTile(label = "Songs", value = "${limits.currentSongCount} / ${limits.maxSongs}")
                UsageTile(label = "Storage", value = "$usedMb / $maxMb MB")
                UsageTile(label = "Playlists", value = "${limits.currentPlaylistCount} / ${limits.playlistLimit}")
                UsageTile(label = "Songs per Playlist", value = "${limits.songPerPlaylistLimit}")
            }
            Spacer(modifier = Modifier.height(32.dp))
        }

        // Badges
        val unlockedCount = profile?.unlockedBadges ?: state.badges.count { it.unlocked }
        val totalCount = profile?.totalBadges ?: state.badges.size
        Text("Badges", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = "$unlockedCount of $totalCount unlocked",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.badges.isEmpty()) {
            Text(
                text = if (state.isLoading) "Loading..." else "Sing more songs to start unlocking badges.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        } else {
            LazyRow(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                items(state.badges, key = { it.id }) { badge -> BadgeChip(badge) }
            }
        }
    }
}

@Composable
private fun UsageTile(label: String, value: String) {
    Column(
        modifier = Modifier
            .width(168.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BadgeChip(badge: Badge) {
    var focused by remember { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .width(96.dp)
            .onFocusChanged { focused = it.isFocused }
            .focusable()
            .tvFocusScale(focused),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (!badge.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = badge.iconUrl,
                    contentDescription = badge.name,
                    modifier = Modifier.size(52.dp),
                    contentScale = ContentScale.Fit,
                    alpha = if (badge.unlocked) 1f else 0.4f
                )
            } else {
                Text(
                    text = if (badge.unlocked) "✓" else "🔒",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (badge.unlocked) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = if (badge.unlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

