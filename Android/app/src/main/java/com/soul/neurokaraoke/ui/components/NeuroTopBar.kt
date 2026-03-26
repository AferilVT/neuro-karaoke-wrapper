package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.soul.neurokaraoke.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuroTopBar(
    onMenuClick: () -> Unit,
    onProfileClick: () -> Unit = {},
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    CenterAlignedTopAppBar(
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                Image(
                    painter = painterResource(id = R.mipmap.neuro_foreground),
                    contentDescription = "NeuroKaraoke Logo",
                    modifier = Modifier.size(45.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        },
        navigationIcon = {
            IconButton(onClick = onMenuClick) {
                Icon(
                    imageVector = Icons.Default.Menu,
                    contentDescription = "Menu",
                    tint = primaryColor.copy(alpha = 0.8f)
                )
            }
        },
        actions = {
            IconButton(onClick = onProfileClick) {
                if (avatarUrl != null) {
                    AsyncImage(
                        model = avatarUrl,
                        contentDescription = "Profile",
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.AccountCircle,
                        contentDescription = "Profile",
                        modifier = Modifier.size(28.dp),
                        tint = primaryColor.copy(alpha = 0.8f)
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier.drawBehind {
            // Subtle neon bottom border line (gradient fade)
            val gradientBrush = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryColor.copy(alpha = 0.4f),
                    primaryColor.copy(alpha = 0.6f),
                    primaryColor.copy(alpha = 0.4f),
                    Color.Transparent
                )
            )
            drawLine(
                brush = gradientBrush,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    )
}
