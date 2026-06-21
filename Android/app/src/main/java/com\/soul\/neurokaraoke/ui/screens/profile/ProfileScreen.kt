package com.soul.neurokaraoke.ui.screens.profile

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.soul.neurokaraoke.data.model.Badge
import com.soul.neurokaraoke.data.model.Profile
import com.soul.neurokaraoke.data.model.UploadLimits
import com.soul.neurokaraoke.data.model.User
import com.soul.neurokaraoke.viewmodel.ProfileViewModel

private val profileGradientColors = listOf(Color(0xFF7C5CFC), Color(0xFFB47BFF))
private val rarityColors = listOf(
    Color(0xFF8E8E93),
    Color(0xFF4FA8FF),
    Color(0xFFB47BFF),
    Color(0xFFFFB347)
)

private fun rarityColor(rarity: Int): Color =
    rarityColors[rarity.coerceIn(0, rarityColors.lastIndex)]

private fun rarityLabel(rarity: Int) = when (rarity) {
    0 -> "Common"
    1 -> "Rare"
    2 -> "Epic"
    else -> "Legendary"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProfileScreen(
    user: User,
    accessToken: String,
    onBackClick: () -> Unit,
    profileViewModel: ProfileViewModel = viewModel()
) {
    val state by profileViewModel.uiState.collectAsState()
    var selectedBadge by remember { mutableStateOf<Badge?>(null) }

    LaunchedEffect(accessToken) {
        profileViewModel.load(accessToken)
    }

    val profile = state.profile
    val displayName = profile?.displayName?.takeIf { it.isNotEmpty() } ?: user.displayName
    val avatarUrl = profile?.avatarUrl?.takeIf { it.isNotEmpty() } ?: user.avatarUrl
    val unlocked = state.badges.filter { it.unlocked }
    val locked = state.badges.filter { !it.unlocked }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBackClick) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "Back",
                    tint = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Profile",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.weight(1f))
            if (state.isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .padding(end = 16.dp),
                    strokeWidth = 2.dp
                )
            }
        }

        when {
            state.isLoading && profile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            }
            state.error != null && profile == null -> {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Failed to load profile",
                            style = MaterialTheme.typography.bodyLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Button(onClick = { profileViewModel.refresh(accessToken) }) {
                            Text("Retry")
                        }
                    }
                }
            }
            else -> LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 96.dp),
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                horizontal = 16.dp,
                vertical = 12.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Profile header
            item(span = { GridItemSpan(maxLineSpan) }) {
                ProfileHeader(
                    displayName = displayName,
                    avatarUrl = avatarUrl,
                    profile = profile,
                    unlockedCount = unlocked.size,
                    totalCount = state.badges.size
                )
            }

            // Achievement strip
            if (state.badges.isNotEmpty()) {
                val nextBadge = locked.firstOrNull { it.conditionValue > 0 } ?: locked.firstOrNull()
                item(span = { GridItemSpan(maxLineSpan) }) {
                    AchievementStrip(
                        unlockedCount = profile?.unlockedBadges ?: unlocked.size,
                        totalCount = profile?.totalBadges ?: state.badges.size,
                        nextBadge = nextBadge,
                        onSelectBadge = { selectedBadge = it }
                    )
                }
            }

            // Upload limits
            val limits = state.uploadLimits
            if (limits != null) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    UploadLimitsCard(limits = limits)
                }
            }

            // Unlocked badges header
            if (unlocked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BadgeSectionHeader(
                        title = "Unlocked",
                        count = profile?.unlockedBadges ?: unlocked.size
                    )
                }
                items(unlocked) { badge ->
                    BadgeGridCell(badge = badge, onClick = { selectedBadge = badge })
                }
            }

            // Locked badges header
            if (locked.isNotEmpty()) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    BadgeSectionHeader(title = "Locked", count = locked.size)
                }
                items(locked) { badge ->
                    BadgeGridCell(badge = badge, onClick = { selectedBadge = badge })
                }
            }

            // Empty state
            if (state.badges.isEmpty() && !state.isLoading) {
                item(span = { GridItemSpan(maxLineSpan) }) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Sing more songs to start unlocking badges.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        } // end else (LazyVerticalGrid)
    } // end when
    } // end Column

    // Badge detail sheet
    selectedBadge?.let { badge ->
        ModalBottomSheet(
            onDismissRequest = { selectedBadge = null },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ) {
            BadgeDetailSheet(badge = badge, onDismiss = { selectedBadge = null })
        }
    }
}

@Composable
private fun ProfileHeader(
    displayName: String,
    avatarUrl: String?,
    profile: Profile?,
    unlockedCount: Int,
    totalCount: Int
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Avatar
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
                        .background(Brush.linearGradient(profileGradientColors)),
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

        Spacer(modifier = Modifier.height(12.dp))

        Text(
            text = displayName,
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        if (profile?.level != null && profile.level > 0) {
            Spacer(modifier = Modifier.height(6.dp))
            LevelChip(level = profile.level, title = profile.levelTitle)
        }

        if (profile?.levelProgress != null) {
            Spacer(modifier = Modifier.height(10.dp))
            XPBar(
                progress = profile.levelProgress / 100.0,
                totalXP = profile.totalXP,
                xpToNextLevel = profile.xpToNextLevel
            )
        }

        Spacer(modifier = Modifier.height(14.dp))

        StatsCard(
            unlocked = profile?.unlockedBadges ?: unlockedCount,
            total = profile?.totalBadges ?: totalCount,
            level = profile?.level ?: 0
        )
    }
}

@Composable
private fun LevelChip(level: Int, title: String?) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier
                .background(Brush.linearGradient(profileGradientColors), RoundedCornerShape(50))
                .padding(horizontal = 10.dp, vertical = 4.dp)
        ) {
            Text(
                text = "LV $level",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
        }
        if (!title.isNullOrEmpty()) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun XPBar(progress: Double, totalXP: Int?, xpToNextLevel: Int?) {
    Column(modifier = Modifier.fillMaxWidth()) {
        GradientLinearProgressBar(progress = progress.coerceIn(0.0, 1.0).toFloat())
        Row(modifier = Modifier.fillMaxWidth()) {
            if (totalXP != null) {
                Text(
                    text = "$totalXP XP",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (xpToNextLevel != null && xpToNextLevel > 0) {
                Text(
                    text = "$xpToNextLevel to next level",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun GradientLinearProgressBar(progress: Float, modifier: Modifier = Modifier) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 200f),
        label = "xp_progress"
    )
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(50))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animatedProgress)
                .height(6.dp)
                .background(Brush.linearGradient(profileGradientColors))
        )
    }
}

@Composable
private fun StatsCard(unlocked: Int, total: Int, level: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        StatItem(value = "$unlocked", label = "Unlocked")
        HorizontalDivider(
            modifier = Modifier
                .height(32.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        StatItem(value = "$total", label = "Total")
        HorizontalDivider(
            modifier = Modifier
                .height(32.dp)
                .width(1.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.3f)
        )
        StatItem(value = "$level", label = "Level")
    }
}

@Composable
private fun StatItem(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun AchievementStrip(
    unlockedCount: Int,
    totalCount: Int,
    nextBadge: Badge?,
    onSelectBadge: (Badge) -> Unit
) {
    val completion = if (totalCount > 0) (unlockedCount.toFloat() / totalCount).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            AchievementMeter(progress = completion)
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Achievements",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "$unlockedCount of $totalCount badges unlocked",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "Keep singing to complete your collection.",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (nextBadge != null) {
            Spacer(modifier = Modifier.height(12.dp))
            NextBadgeCallout(badge = nextBadge, onClick = { onSelectBadge(nextBadge) })
        }
    }
}

@Composable
private fun AchievementMeter(progress: Float) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = 0.8f, stiffness = 120f),
        label = "achievement_meter"
    )
    val gradBrush = Brush.linearGradient(profileGradientColors)
    val bgColor = MaterialTheme.colorScheme.surfaceVariant

    Box(
        modifier = Modifier
            .size(74.dp)
            .drawWithCache {
                val stroke = 8.dp.toPx()
                val inset = stroke / 2f
                val arcSize = Size(size.width - stroke, size.height - stroke)
                val topLeft = Offset(inset, inset)
                onDrawBehind {
                    drawArc(
                        color = bgColor,
                        startAngle = -90f,
                        sweepAngle = 360f,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                    drawArc(
                        brush = gradBrush,
                        startAngle = -90f,
                        sweepAngle = 360f * animatedProgress,
                        useCenter = false,
                        topLeft = topLeft,
                        size = arcSize,
                        style = Stroke(width = stroke, cap = StrokeCap.Round)
                    )
                }
            },
        contentAlignment = Alignment.Center
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = "${(animatedProgress * 100).toInt()}%",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Text(
                text = "done",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun NextBadgeCallout(badge: Badge, onClick: () -> Unit) {
    val ratio = if (badge.conditionValue > 0)
        (badge.currentProgress.toFloat() / badge.conditionValue).coerceIn(0f, 1f)
    else 0f
    val progressText = if (badge.conditionValue > 0)
        "${badge.currentProgress} / ${badge.conditionValue}"
    else "View badge"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        BadgeMiniIcon(badge = badge)
        Column(modifier = Modifier.weight(1f)) {
            Row(modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = "NEXT BADGE",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.weight(1f))
                Text(
                    text = progressText,
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = badge.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            if (badge.conditionValue > 0) {
                Spacer(modifier = Modifier.height(4.dp))
                GradientLinearProgressBar(progress = ratio)
            }
        }
    }
}

@Composable
private fun BadgeMiniIcon(badge: Badge) {
    val ringColor = rarityColor(badge.rarity)
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        if (!badge.iconUrl.isNullOrEmpty()) {
            AsyncImage(
                model = badge.iconUrl,
                contentDescription = badge.name,
                modifier = Modifier
                    .size(38.dp)
                    .then(if (!badge.unlocked) Modifier else Modifier),
                contentScale = ContentScale.Fit,
                alpha = if (badge.unlocked) 1f else 0.45f
            )
        } else {
            Icon(
                imageVector = if (badge.unlocked) Icons.Default.Check else Icons.Default.Lock,
                contentDescription = null,
                tint = ringColor.copy(alpha = if (badge.unlocked) 1f else 0.45f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
    Box(
        modifier = Modifier
            .size(46.dp)
            .clip(CircleShape)
    ) {
        // ring drawn via border
    }
}

@Composable
private fun BadgeSectionHeader(title: String, count: Int) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Text(
            text = "$count",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
fun BadgeGridCell(badge: Badge, onClick: () -> Unit) {
    val ringColor = rarityColor(badge.rarity)
    val ratio = if (badge.conditionValue > 0)
        (badge.currentProgress.toFloat() / badge.conditionValue).coerceIn(0f, 1f)
    else 0f

    Column(
        modifier = Modifier.clickable(onClick = onClick),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.surfaceVariant)
                .drawWithCache {
                    val stroke = 2.dp.toPx()
                    val inset = stroke / 2f
                    val arcSize = Size(size.width - stroke, size.height - stroke)
                    val topLeft = Offset(inset, inset)
                    onDrawBehind {
                        drawArc(
                            color = ringColor.copy(alpha = if (badge.unlocked) 1f else 0.4f),
                            startAngle = -90f,
                            sweepAngle = 360f,
                            useCenter = false,
                            topLeft = topLeft,
                            size = arcSize,
                            style = Stroke(width = stroke)
                        )
                        if (!badge.unlocked && badge.conditionValue > 0) {
                            drawArc(
                                color = ringColor,
                                startAngle = -90f,
                                sweepAngle = 360f * ratio,
                                useCenter = false,
                                topLeft = topLeft,
                                size = arcSize,
                                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            if (!badge.iconUrl.isNullOrEmpty()) {
                AsyncImage(
                    model = badge.iconUrl,
                    contentDescription = badge.name,
                    modifier = Modifier.size(52.dp).padding(4.dp),
                    contentScale = ContentScale.Fit,
                    alpha = if (badge.unlocked) 1f else 0.4f
                )
            } else {
                Icon(
                    imageVector = if (badge.unlocked) Icons.Default.Check else Icons.Default.Lock,
                    contentDescription = null,
                    tint = ringColor.copy(alpha = if (badge.unlocked) 1f else 0.4f),
                    modifier = Modifier.size(24.dp)
                )
            }

            if (!badge.unlocked) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(19.dp)
                        .clip(CircleShape)
                        .background(Color.Black.copy(alpha = 0.72f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(10.dp)
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = badge.name,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = FontWeight.SemiBold,
            color = if (badge.unlocked) MaterialTheme.colorScheme.onSurface
                    else MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        Text(
            text = if (badge.unlocked) "Unlocked"
                   else if (badge.conditionValue > 0) "${badge.currentProgress} / ${badge.conditionValue}"
                   else "",
            style = MaterialTheme.typography.labelSmall,
            color = if (badge.unlocked) ringColor else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun BadgeDetailSheet(badge: Badge, onDismiss: () -> Unit) {
    val ringColor = rarityColor(badge.rarity)
    val ratio = if (badge.conditionValue > 0)
        (badge.currentProgress.toFloat() / badge.conditionValue).coerceIn(0f, 1f)
    else 0f

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 24.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp, vertical = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Badge icon large
            Box(
                modifier = Modifier
                    .size(128.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surfaceVariant)
                    .drawWithCache {
                        val stroke = 3.dp.toPx()
                        val inset = stroke / 2f
                        onDrawBehind {
                            drawArc(
                                color = ringColor.copy(alpha = 0.85f),
                                startAngle = -90f,
                                sweepAngle = 360f,
                                useCenter = false,
                                topLeft = Offset(inset, inset),
                                size = Size(size.width - stroke, size.height - stroke),
                                style = Stroke(width = stroke)
                            )
                        }
                    },
                contentAlignment = Alignment.Center
            ) {
                if (!badge.iconUrl.isNullOrEmpty()) {
                    AsyncImage(
                        model = badge.iconUrl,
                        contentDescription = badge.name,
                        modifier = Modifier.size(108.dp).padding(16.dp),
                        contentScale = ContentScale.Fit,
                        alpha = if (badge.unlocked) 1f else 0.4f
                    )
                } else {
                    Icon(
                        imageVector = if (badge.unlocked) Icons.Default.Check else Icons.Default.Lock,
                        contentDescription = null,
                        tint = ringColor,
                        modifier = Modifier.size(48.dp)
                    )
                }

                if (!badge.unlocked) {
                    Box(
                        modifier = Modifier
                            .align(Alignment.BottomEnd)
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.76f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = badge.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                // Status pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = if (badge.unlocked) Icons.Default.Check else Icons.Default.Lock,
                            contentDescription = null,
                            tint = if (badge.unlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(12.dp)
                        )
                        Text(
                            text = if (badge.unlocked) "Unlocked" else "Locked",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (badge.unlocked) Color(0xFF4CAF50) else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                // Rarity pill
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(ringColor.copy(alpha = 0.12f))
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Text(
                        text = rarityLabel(badge.rarity),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = ringColor
                    )
                }
            }

            if (!badge.description.isNullOrEmpty()) {
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    text = badge.description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }

            if (badge.conditionValue > 0) {
                Spacer(modifier = Modifier.height(20.dp))
                GradientLinearProgressBar(progress = ratio, modifier = Modifier.fillMaxWidth(0.8f))
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "${badge.currentProgress} / ${badge.conditionValue}",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        IconButton(
            onClick = onDismiss,
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Close,
                contentDescription = "Close",
                tint = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun UploadLimitsCard(limits: UploadLimits) {
    val usedMb = limits.usedStorageBytes / 1_048_576f
    val maxMb = limits.maxStorageBytes / 1_048_576f
    val storageRatio = if (maxMb > 0) (usedMb / maxMb).coerceIn(0f, 1f) else 0f

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
            .padding(16.dp)
    ) {
        Text(
            text = "Storage",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(modifier = Modifier.height(10.dp))
        LinearProgressIndicator(
            progress = { storageRatio },
            modifier = Modifier
                .fillMaxWidth()
                .height(6.dp)
                .clip(RoundedCornerShape(50)),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surface
        )
        Spacer(modifier = Modifier.height(6.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${usedMb.toInt()} MB used",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${maxMb.toInt()} MB total",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.height(8.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${limits.currentSongCount} / ${limits.maxSongs} songs",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${limits.currentPlaylistCount} / ${limits.playlistLimit} playlists",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
