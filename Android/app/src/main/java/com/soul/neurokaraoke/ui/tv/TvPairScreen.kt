package com.soul.neurokaraoke.ui.tv

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soul.neurokaraoke.data.api.SyncApi
import kotlinx.coroutines.launch

private val PAIR_ROWS = listOf("ABCDEFGHIJKLM", "NOPQRSTUVWXYZ", "0123456789")
private const val CODE_LEN = 6

/**
 * TV sign-in via device pairing — the same mechanism AAOS uses (SyncApi
 * create/redeem pairing code), so no WebView and no credentials are ever typed
 * on the TV. The already-signed-in phone shows a 6-char code (Pair a device);
 * the user enters it here with the D-pad and the TV redeems it for a JWT.
 */
@Composable
fun TvPairScreen(onPaired: (String) -> Unit, onBack: () -> Unit) {
    var code by remember { mutableStateOf("") }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val syncApi = remember { SyncApi() }
    val firstKey = remember { FocusRequester() }

    BackHandler { onBack() }

    fun redeem(entered: String) {
        loading = true
        error = null
        scope.launch {
            syncApi.redeemPairingCode(entered).fold(
                onSuccess = { jwt -> onPaired(jwt) },
                onFailure = {
                    error = "That code didn't work — check it or generate a new one on your phone."
                    code = ""
                    loading = false
                }
            )
        }
    }

    fun append(ch: Char) {
        if (loading || code.length >= CODE_LEN) return
        code += ch
        if (code.length == CODE_LEN) redeem(code)
    }

    LaunchedEffect(Unit) { runCatching { firstKey.requestFocus() } }

    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 40.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(
            "Sign in with your phone",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )
        Spacer(Modifier.height(6.dp))
        Text(
            "On your phone, open Neuro Karaoke → Pair a device to get a 6-character code, then enter it below.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(18.dp))

        // Entered-code slots
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            repeat(CODE_LEN) { i ->
                Box(
                    modifier = Modifier
                        .size(width = 52.dp, height = 60.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        code.getOrNull(i)?.toString() ?: "",
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))

        Box(modifier = Modifier.height(26.dp), contentAlignment = Alignment.Center) {
            when {
                loading -> CircularProgressIndicator(
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
                error != null -> Text(
                    error!!,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center
                )
            }
        }
        Spacer(Modifier.height(14.dp))

        // D-pad keypad
        PAIR_ROWS.forEachIndexed { rowIdx, row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEachIndexed { colIdx, ch ->
                    PairKey(
                        label = ch.toString(),
                        onActivate = { append(ch) },
                        focusRequester = if (rowIdx == 0 && colIdx == 0) firstKey else null
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
        }
        PairKey(label = "DELETE", wide = true, onActivate = { if (!loading) code = code.dropLast(1) })
    }
}

@Composable
private fun PairKey(
    label: String,
    onActivate: () -> Unit,
    wide: Boolean = false,
    focusRequester: FocusRequester? = null
) {
    var focused by remember { mutableStateOf(false) }
    Box(
        modifier = Modifier
            .then(if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier)
            .size(width = if (wide) 130.dp else 54.dp, height = 48.dp)
            .clip(RoundedCornerShape(10.dp))
            .background(
                if (focused) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surfaceVariant
            )
            .onFocusChanged { focused = it.isFocused }
            // clickable handles D-pad center/Enter AND pointer taps (focusable() alone
            // ignores taps), so the keypad is drivable by both remote and touch.
            .clickable { onActivate() }
            .tvFocusScale(focused, scale = 1.12f),
        contentAlignment = Alignment.Center
    ) {
        Text(
            label,
            fontWeight = FontWeight.SemiBold,
            color = if (focused) MaterialTheme.colorScheme.onPrimary
            else MaterialTheme.colorScheme.onSurface
        )
    }
}
