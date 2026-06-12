package com.soul.neurokaraoke.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soul.neurokaraoke.data.api.SyncApi
import androidx.compose.ui.res.stringResource
import com.soul.neurokaraoke.R
import kotlinx.coroutines.delay

/**
 * Phone-side: shows a 6-char pairing code that the user types into AAOS to log in.
 * Mount from any screen, gated by isLoggedIn.
 *
 * Usage:
 *   if (showPair) PairCarDialog(jwt = jwt) { showPair = false }
 */
@Composable
fun PairCarDialog(jwt: String, onDismiss: () -> Unit) {
    var code by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var secondsLeft by remember { mutableIntStateOf(300) }
    val expired = secondsLeft <= 0

    LaunchedEffect(Unit) {
        SyncApi().createPairingCode(jwt).fold(
            onSuccess = { code = it },
            onFailure = { error = it.message ?: "Failed to fetch code" }
        )
    }

    LaunchedEffect(code) {
        if (code == null) return@LaunchedEffect
        while (secondsLeft > 0) {
            delay(1_000L)
            secondsLeft--
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.pair_car_title)) },
        text = {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    stringResource(R.string.pair_car_instruction),
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(16.dp))
                Box(
                    modifier = Modifier.fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    when {
                        expired -> Text(
                            stringResource(R.string.pair_car_code_expired),
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.error
                        )
                        code != null -> Text(
                            code!!,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        error != null -> Text(
                            stringResource(R.string.pair_car_error, error ?: ""),
                            color = MaterialTheme.colorScheme.error
                        )
                        else -> Text(stringResource(R.string.pair_car_generating), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                Spacer(Modifier.height(8.dp))
                if (!expired && code != null) {
                    val m = secondsLeft / 60
                    val s = secondsLeft % 60
                    Text(
                        stringResource(R.string.pair_car_expires_in, m, s),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (secondsLeft <= 60) MaterialTheme.colorScheme.error
                                else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.pair_car_button_done)) } }
    )
}
