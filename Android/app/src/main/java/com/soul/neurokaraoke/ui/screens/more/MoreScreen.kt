package com.soul.neurokaraoke.ui.screens.more

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Groups
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.ui.theme.DuetColor
import com.soul.neurokaraoke.ui.theme.EvilColor
import com.soul.neurokaraoke.ui.theme.LocalThemeMode
import com.soul.neurokaraoke.ui.theme.LocalThemeToggle
import com.soul.neurokaraoke.ui.theme.NeuroColor
import com.soul.neurokaraoke.ui.theme.ThemeMode
import androidx.compose.ui.res.stringResource
import com.soul.neurokaraoke.R

data class MoreMenuItem(
    val title: String,
    val icon: ImageVector,
    val onClick: () -> Unit
)

@Composable
fun MoreScreen(
    onSoundbitesClick: () -> Unit,
    onSetlistsClick: () -> Unit,
    onArtistsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onUploadSongsClick: () -> Unit,
    onAboutClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val items = listOf(
        MoreMenuItem(stringResource(R.string.more_item_soundbites), Icons.Default.GraphicEq, onSoundbitesClick),
        MoreMenuItem(stringResource(R.string.more_item_setlists), Icons.AutoMirrored.Filled.QueueMusic, onSetlistsClick),
        MoreMenuItem(stringResource(R.string.more_item_artists), Icons.Default.Groups, onArtistsClick),
        MoreMenuItem(stringResource(R.string.more_item_local_music), Icons.Default.FileUpload, onUploadSongsClick),
        MoreMenuItem(stringResource(R.string.more_item_settings), Icons.Default.Settings, onSettingsClick),
        MoreMenuItem(stringResource(R.string.more_item_about), Icons.Default.Info, onAboutClick)
    )

    val themeMode = LocalThemeMode.current
    val setThemeMode = LocalThemeToggle.current

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Title
        Text(
            text = stringResource(R.string.more_header_title),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 16.dp)
        )

        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp
        )

        // Menu items
        items.forEachIndexed { index, item ->
            // Divider before utility section (Local Music)
            if (index == 3) {
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
                    thickness = 0.5.dp,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
            MoreListItem(
                title = item.title,
                icon = item.icon,
                onClick = item.onClick
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        // Theme selector
        HorizontalDivider(
            color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 16.dp)
        )

        Text(
            text = stringResource(R.string.more_section_theme),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            ThemeOption(stringResource(R.string.theme_option_auto), themeMode == ThemeMode.AUTO, MaterialTheme.colorScheme.primary, { setThemeMode(ThemeMode.AUTO) }, Modifier.weight(1f))
            ThemeOption(stringResource(R.string.theme_option_neuro), themeMode == ThemeMode.NEURO, NeuroColor, { setThemeMode(ThemeMode.NEURO) }, Modifier.weight(1f))
            ThemeOption(stringResource(R.string.theme_option_evil), themeMode == ThemeMode.EVIL, EvilColor, { setThemeMode(ThemeMode.EVIL) }, Modifier.weight(1f))
            ThemeOption(stringResource(R.string.theme_option_duet), themeMode == ThemeMode.DUET, DuetColor, { setThemeMode(ThemeMode.DUET) }, Modifier.weight(1f))
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun MoreListItem(
    title: String,
    icon: ImageVector,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = title,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(24.dp)
        )

        Spacer(modifier = Modifier.width(16.dp))

        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )

        Icon(
            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.size(24.dp)
        )
    }
}

@Composable
private fun ThemeOption(
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
            .padding(vertical = 10.dp, horizontal = 4.dp)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = textColor,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
        )
    }
}
