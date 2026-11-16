package com.soul.neurokaraoke.ui.tv

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.soul.neurokaraoke.ui.components.LoginCard
import com.soul.neurokaraoke.viewmodel.AuthViewModel

private const val SITE_URL = "https://neurokaraoke.com"

/**
 * TV sign-in: username/password card ([LoginCard]) on the left, an "or" divider in the
 * middle, and QR device-login ([TvQrLoginPanel]) on the right. "Continue with Discord"
 * routes to device pairing; account/legal links open the website.
 */
@Composable
fun TvLoginScreen(
    authViewModel: AuthViewModel,
    onDiscord: () -> Unit,
) {
    val context = LocalContext.current
    val state by authViewModel.uiState.collectAsStateWithLifecycle()
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    val usernameFocus = remember { FocusRequester() }

    LaunchedEffect(Unit) {
        authViewModel.clearError()
        runCatching { usernameFocus.requestFocus() }
    }

    fun open(url: String) {
        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
    }

    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            LoginCard(
                username = username,
                onUsername = { username = it },
                password = password,
                onPassword = { password = it },
                error = state.error,
                loading = state.isLoading,
                onSignIn = { authViewModel.loginWithPassword(username, password) },
                onDiscord = onDiscord,
                onCreateAccount = { open(SITE_URL) },
                onTerms = { open(SITE_URL) },
                onPrivacy = { open(SITE_URL) },
                usernameFocusRequester = usernameFocus,
                modifier = Modifier.width(400.dp),
            )

            OrSeparator()

            TvQrLoginPanel(
                authViewModel = authViewModel,
                modifier = Modifier.width(400.dp),
            )
        }
    }
}

/** Vertical "or" divider between the two sign-in methods. */
@Composable
private fun OrSeparator() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Box(
            Modifier.width(1.dp).height(120.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
        Text(
            "or",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(vertical = 12.dp)
        )
        Box(
            Modifier.width(1.dp).height(120.dp)
                .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
        )
    }
}
