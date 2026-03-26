package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Casino
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.navigation.Screen
import com.soul.neurokaraoke.ui.theme.CyberLabelStyle
import com.soul.neurokaraoke.ui.theme.DuetColor
import com.soul.neurokaraoke.ui.theme.EvilColor
import com.soul.neurokaraoke.ui.theme.GradientText
import com.soul.neurokaraoke.ui.theme.LocalThemeMode
import com.soul.neurokaraoke.ui.theme.LocalThemeToggle
import com.soul.neurokaraoke.ui.theme.NeonDivider
import com.soul.neurokaraoke.ui.theme.NeonTheme
import com.soul.neurokaraoke.ui.theme.NeuroColor
import com.soul.neurokaraoke.ui.theme.ThemeMode

@Composable
fun NavigationDrawerContent(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    onClose: () -> Unit,
    isLoggedIn: Boolean = false,
    userName: String? = null,
    userAvatarUrl: String? = null,
    onSignInClick: () -> Unit = {},
    onSignOutClick: () -> Unit = {},
    onRandomSongClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeToggle.current
    val primaryColor = MaterialTheme.colorScheme.primary

    Column(
        modifier = modifier
            .fillMaxHeight()
            .width(280.dp)
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.95f))
            .drawBehind {
                // Subtle ambient background
                drawCircle(
                    color = primaryColor.copy(alpha = 0.02f),
                    radius = size.width * 0.8f,
                    center = Offset(size.width * 0.1f, size.height * 0.1f)
                )
            }
            .padding(vertical = 16.dp)
            .verticalScroll(rememberScrollState())
    ) {
        // Header with GradientText title
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp)
        ) {
            Image(
                painter = painterResource(id = R.mipmap.neuro_foreground),
                contentDescription = "NeuroKaraoke Logo",
                modifier = Modifier.size(40.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            GradientText(
                text = "Neuro Karaoke",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                gradientColors = NeonTheme.colors.gradientColors
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Main navigation items
        Screen.mainNavItems.forEach { screen ->
            NavigationDrawerItem(
                screen = screen,
                isSelected = currentRoute == screen.route,
                onClick = {
                    onNavigate(screen)
                    onClose()
                }
            )
        }

        // Random Song button
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 2.dp)
                .clip(RoundedCornerShape(8.dp))
                .clickable(onClick = onRandomSongClick)
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Casino,
                contentDescription = null,
                tint = primaryColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
            Text(
                text = "Random Song",
                style = MaterialTheme.typography.bodyLarge,
                color = primaryColor,
                fontWeight = FontWeight.Medium
            )
        }

        NeonDivider(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp)
        )

        // Library section with CyberLabelStyle
        Text(
            text = "YOUR LIBRARY",
            style = CyberLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )

        Screen.libraryItems.forEach { screen ->
            NavigationDrawerItem(
                screen = screen,
                isSelected = currentRoute == screen.route,
                comingSoon = false,
                onClick = {
                    onNavigate(screen)
                    onClose()
                }
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        NeonDivider(
            modifier = Modifier.padding(vertical = 16.dp, horizontal = 16.dp)
        )

        // Theme selector with CyberLabelStyle
        Text(
            text = "THEME",
            style = CyberLabelStyle,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)
        ) {
            ThemeChip(
                text = "Auto",
                isSelected = themeMode == ThemeMode.AUTO,
                color = MaterialTheme.colorScheme.primary,
                onClick = { setThemeMode(ThemeMode.AUTO) },
                modifier = Modifier.weight(1f)
            )
            ThemeChip(
                text = "Neuro",
                isSelected = themeMode == ThemeMode.NEURO,
                color = NeuroColor,
                onClick = { setThemeMode(ThemeMode.NEURO) },
                modifier = Modifier.weight(1f)
            )
            ThemeChip(
                text = "Evil",
                isSelected = themeMode == ThemeMode.EVIL,
                color = EvilColor,
                onClick = { setThemeMode(ThemeMode.EVIL) },
                modifier = Modifier.weight(1f)
            )
            ThemeChip(
                text = "Duet",
                isSelected = themeMode == ThemeMode.DUET,
                color = DuetColor,
                onClick = { setThemeMode(ThemeMode.DUET) },
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // User profile / Sign In
        if (isLoggedIn && userName != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    if (userAvatarUrl != null) {
                        AsyncImage(
                            model = userAvatarUrl,
                            contentDescription = "Avatar",
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = userName,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = "Signed in with Discord",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = onSignOutClick) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Logout,
                        contentDescription = "Sign Out",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .clickable(onClick = onSignInClick)
                    .padding(horizontal = 16.dp, vertical = 12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(36.dp)
                    )
                }
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sign In",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "Connect with Discord",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
private fun NavigationDrawerItem(
    screen: Screen,
    isSelected: Boolean,
    onClick: () -> Unit,
    comingSoon: Boolean = false
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    val backgroundColor = if (isSelected) {
        primaryColor.copy(alpha = 0.1f)
    } else {
        Color.Transparent
    }

    val contentColor = if (comingSoon) {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.5f)
    } else if (isSelected) {
        primaryColor
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 2.dp)
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) {
                    // Left-border accent
                    Modifier.drawBehind {
                        drawRoundRect(
                            color = primaryColor,
                            topLeft = Offset(0f, size.height * 0.15f),
                            size = Size(3.dp.toPx(), size.height * 0.7f),
                            cornerRadius = CornerRadius(2.dp.toPx())
                        )
                    }
                } else Modifier
            )
            .background(backgroundColor)
            .then(if (!comingSoon) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    ) {
        screen.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(24.dp)
            )
            Spacer(modifier = Modifier.width(16.dp))
        }
        Text(
            text = screen.title,
            style = MaterialTheme.typography.bodyLarge,
            color = contentColor,
            modifier = Modifier.weight(1f)
        )
        if (comingSoon) {
            Text(
                text = "Coming soon",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ThemeChip(
    text: String,
    isSelected: Boolean,
    color: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val backgroundColor = if (isSelected) color.copy(alpha = 0.1f) else Color.Transparent
    val textColor = if (isSelected) color else MaterialTheme.colorScheme.onSurfaceVariant

    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .then(
                if (isSelected) Modifier.border(
                    width = 1.dp,
                    color = color.copy(alpha = 0.6f),
                    shape = RoundedCornerShape(8.dp)
                ) else Modifier
            )
            .background(backgroundColor)
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp, horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
