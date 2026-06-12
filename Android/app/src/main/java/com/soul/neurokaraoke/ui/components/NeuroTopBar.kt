package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import androidx.compose.ui.res.stringResource

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NeuroTopBar(
    onMenuClick: () -> Unit = {},  // Deprecated — kept for compile compat until MainScreen rewrite
    onProfileClick: () -> Unit = {},
    onPairCar: () -> Unit = {},
    onSignOut: () -> Unit = {},
    isLoggedIn: Boolean = false,
    avatarUrl: String? = null,
    modifier: Modifier = Modifier
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var showMenu by remember { mutableStateOf(false) }

    CenterAlignedTopAppBar(
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Image(
                    painter = painterResource(id = R.mipmap.neuro_foreground),
                    contentDescription = stringResource(R.string.topbar_content_description_logo),
                    modifier = Modifier.size(40.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
        },
        actions = {
            Box {
                IconButton(onClick = {
                    if (isLoggedIn) showMenu = true else onProfileClick()
                }) {
                    if (avatarUrl != null) {
                        AsyncImage(
                            model = avatarUrl,
                            contentDescription = stringResource(R.string.topbar_content_description_profile),
                            modifier = Modifier
                                .size(30.dp)
                                .clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Default.AccountCircle,
                            contentDescription = stringResource(R.string.topbar_content_description_profile),
                            modifier = Modifier.size(30.dp),
                            tint = primaryColor.copy(alpha = 0.7f)
                        )
                    }
                }
                DropdownMenu(
                    expanded = showMenu,
                    onDismissRequest = { showMenu = false }
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.topbar_menu_pair_car)) },
                        leadingIcon = { Icon(Icons.Default.DirectionsCar, contentDescription = null) },
                        onClick = { showMenu = false; onPairCar() }
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.topbar_menu_sign_out)) },
                        leadingIcon = { Icon(Icons.Default.Logout, contentDescription = null) },
                        onClick = { showMenu = false; onSignOut() }
                    )
                }
            }
        },
        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
            containerColor = MaterialTheme.colorScheme.background
        ),
        modifier = modifier.drawBehind {
            val lineGradient = Brush.horizontalGradient(
                colors = listOf(
                    Color.Transparent,
                    primaryColor.copy(alpha = 0.15f),
                    primaryColor.copy(alpha = 0.25f),
                    primaryColor.copy(alpha = 0.15f),
                    Color.Transparent
                )
            )
            drawLine(
                brush = lineGradient,
                start = Offset(0f, size.height),
                end = Offset(size.width, size.height),
                strokeWidth = 1.dp.toPx()
            )
        }
    )
}
