package com.soul.neurokaraoke.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soul.neurokaraoke.viewmodel.AuthViewModel

private const val SITE_URL = "https://neurokaraoke.com"

/**
 * Phone sign-in, styled as the web sign-in card ([LoginCard]) inside a Dialog. Carries
 * both username/password (→ [AuthViewModel.loginWithPassword]) and Discord OAuth.
 */
@Composable
fun UsernameLoginDialog(authViewModel: AuthViewModel, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }

    LaunchedEffect(Unit) { authViewModel.clearError() }
    LaunchedEffect(state.isLoggedIn) { if (state.isLoggedIn) onDismiss() }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        LoginCard(
            username = username,
            onUsername = { username = it },
            password = password,
            onPassword = { password = it },
            error = state.error,
            loading = state.isLoading,
            onSignIn = { authViewModel.loginWithPassword(username, password) },
            onDiscord = { onDismiss(); context.startActivity(authViewModel.getSignInIntent()) },
            onCreateAccount = { open(SITE_URL) },
            onTerms = { open(SITE_URL) },
            onPrivacy = { open(SITE_URL) },
            modifier = Modifier.padding(24.dp),
        )
    }
}
