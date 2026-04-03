package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.navigation.Screen

@Composable
fun BottomNavBar(
    currentRoute: String?,
    onNavigate: (Screen) -> Unit,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface

    Column(
        modifier = modifier
            .fillMaxWidth()
            .drawBehind {
                // Top accent line (gradient fade)
                val lineGradient = Brush.horizontalGradient(
                    colors = listOf(
                        Color.Transparent,
                        primaryColor.copy(alpha = 0.3f),
                        primaryColor.copy(alpha = 0.4f),
                        primaryColor.copy(alpha = 0.3f),
                        Color.Transparent
                    )
                )
                drawLine(
                    brush = lineGradient,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 1.dp.toPx()
                )
            }
            .background(surfaceColor.copy(alpha = 0.95f))
            .windowInsetsPadding(WindowInsets.navigationBars)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Routes that should highlight the More tab
            val moreRoutes = setOf(
                Screen.Soundbites.route,
                Screen.Setlists.route, Screen.Artists.route,
                Screen.About.route, Screen.SetlistDetail.route,
                Screen.ArtistDetail.route, Screen.Settings.route,
                Screen.UploadSongs.route
            )
            // Routes that should highlight the Library tab
            val libraryRoutes = setOf(
                Screen.Favorites.route, Screen.Playlists.route,
                Screen.Downloads.route, Screen.UserPlaylistDetail.route
            )

            Screen.bottomNavItems.forEach { screen ->
                val isSelected = when (screen) {
                    Screen.More -> currentRoute == Screen.More.route || currentRoute in moreRoutes
                    Screen.Library -> currentRoute == Screen.Library.route || currentRoute in libraryRoutes
                    else -> currentRoute == screen.route
                }
                BottomNavItem(
                    screen = screen,
                    isSelected = isSelected,
                    primaryColor = primaryColor,
                    onClick = { onNavigate(screen) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun BottomNavItem(
    screen: Screen,
    isSelected: Boolean,
    primaryColor: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val iconColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
    val labelColor = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
        modifier = modifier
            .clickable(
                indication = null,
                interactionSource = remember { MutableInteractionSource() },
                onClick = onClick
            )
            .padding(vertical = 8.dp)
    ) {
        screen.icon?.let { icon ->
            Icon(
                imageVector = icon,
                contentDescription = screen.title,
                tint = iconColor,
                modifier = Modifier.size(24.dp)
            )
        }
        Text(
            text = screen.title,
            style = MaterialTheme.typography.labelSmall,
            color = labelColor,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
        )
    }
}
