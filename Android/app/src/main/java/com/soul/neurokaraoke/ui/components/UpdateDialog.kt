package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.soul.neurokaraoke.data.api.GitHubRelease
import com.soul.neurokaraoke.ui.theme.GradientText
import com.soul.neurokaraoke.ui.theme.NeonTheme
import com.soul.neurokaraoke.ui.theme.ambientGlow

@Composable
fun UpdateDialog(
    release: GitHubRelease,
    currentVersion: String,
    onUpdate: () -> Unit,
    onUninstallAndUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val newVersion = release.getVersionString()
    val releaseNotes = release.body.take(500).let {
        if (release.body.length > 500) "$it..." else it
    }
    val neonColors = NeonTheme.colors

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Update Available",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(4.dp))
                GradientText(
                    text = "v$currentVersion → v$newVersion",
                    style = MaterialTheme.typography.bodyMedium,
                    gradientColors = neonColors.gradientColors,
                    fontWeight = FontWeight.Medium
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                if (release.name.isNotBlank() && release.name != release.tagName) {
                    Text(
                        text = release.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                }

                if (releaseNotes.isNotBlank()) {
                    Text(
                        text = releaseNotes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        text = "A new version is available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

            }
        },
        confirmButton = {
            Column {
                Button(
                    onClick = onUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary,
                        contentColor = MaterialTheme.colorScheme.onPrimary
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .ambientGlow(
                            color = neonColors.glowColor,
                            radius = 6.dp,
                            cornerRadius = 20.dp,
                            alpha = 0.25f
                        )
                ) {
                    Text("Update")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Button(
                    onClick = onUninstallAndUpdate,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.surfaceVariant,
                        contentColor = MaterialTheme.colorScheme.onSurface
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Uninstall & Update")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(
                    text = "Not now",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
    )
}
