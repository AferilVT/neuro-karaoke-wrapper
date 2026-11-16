package com.soul.neurokaraoke.aaos

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soul.neurokaraoke.R

@Composable
fun AaosPairingScreen(
    statusMessage: String?,
    onSubmit: (String) -> Unit,
    onBack: () -> Unit
) {
    var code by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize().padding(24.dp)) {
        // Header row
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f))
                    .clickable(onClick = onBack),
                contentAlignment = Alignment.Center
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.aaos_pairing_content_description_back),
                     tint = MaterialTheme.colorScheme.onBackground)
            }
            Spacer(Modifier.width(16.dp))
            Text(stringResource(R.string.aaos_pairing_title), color = MaterialTheme.colorScheme.onBackground,
                 style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
        }

        Spacer(Modifier.height(20.dp))

        // Body: instructions on left, keypad on right — uses wide AAOS screen
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(40.dp)
        ) {
            // Left column: guide + code display
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.Top
            ) {
                Text(
                    stringResource(R.string.aaos_pairing_step_1),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.aaos_pairing_step_2),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    stringResource(R.string.aaos_pairing_step_3),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.titleMedium
                )
                Spacer(Modifier.height(32.dp))
                CodeBoxes(code = code)
                statusMessage?.let {
                    Spacer(Modifier.height(16.dp))
                    Text(
                        it,
                        color = if (it.startsWith("Error")) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }

            // Right column: keypad
            Keypad(
                modifier = Modifier.weight(1f),
                onKey = { ch ->
                    if (code.length < 6) code = (code + ch).uppercase()
                    if (code.length == 6) { onSubmit(code); code = "" }
                },
                onBackspace = { if (code.isNotEmpty()) code = code.dropLast(1) }
            )
        }
    }
}

@Composable
private fun CodeBoxes(code: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        repeat(6) { i ->
            val ch = code.getOrNull(i)?.toString() ?: ""
            Box(
                modifier = Modifier
                    .size(width = 64.dp, height = 80.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                contentAlignment = Alignment.Center
            ) {
                Text(ch, color = MaterialTheme.colorScheme.onBackground,
                     fontSize = 36.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Keypad(modifier: Modifier = Modifier, onKey: (Char) -> Unit, onBackspace: () -> Unit) {
    val rows = listOf(
        listOf("1", "2", "3", "4", "5"),
        listOf("6", "7", "8", "9", "0"),
        listOf("A", "B", "C", "D", "E"),
        listOf("F", "G", "H", "J", "K"),
        listOf("L", "M", "N", "P", "Q"),
        listOf("R", "S", "T", "U", "V"),
        listOf("W", "X", "Y", "Z", "⌫")
    )
    Column(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
    ) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { key ->
                    Box(
                        modifier = Modifier
                            .size(width = 72.dp, height = 56.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.5f))
                            .clickable {
                                if (key == "⌫") onBackspace() else onKey(key[0])
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        if (key == "⌫") {
                            Icon(Icons.Default.Backspace, contentDescription = stringResource(R.string.aaos_pairing_content_description_backspace),
                                 tint = MaterialTheme.colorScheme.onBackground)
                        } else {
                            Text(key, color = MaterialTheme.colorScheme.onBackground,
                                 fontSize = 22.sp, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            }
        }
    }
}
