package com.soul.neurokaraoke

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.model.User
import com.soul.neurokaraoke.ui.MainScreen
import com.soul.neurokaraoke.ui.components.UpdateDialog
import com.soul.neurokaraoke.ui.screens.setup.SetupScreen
import com.soul.neurokaraoke.ui.theme.NeuroKaraokeTheme
import com.soul.neurokaraoke.viewmodel.AuthViewModel
import com.soul.neurokaraoke.viewmodel.PlayerViewModel
import com.soul.neurokaraoke.viewmodel.UpdateViewModel

class MainActivity : ComponentActivity() {

    private val authViewModel: AuthViewModel by viewModels()
    private val playerViewModel: PlayerViewModel by viewModels()
    private val updateViewModel: UpdateViewModel by viewModels()
    private var isSetupComplete by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Check if first-time setup is needed
        val songCache = SongCache(this)
        isSetupComplete = songCache.isSetupComplete()

        // Handle deep link if app was launched from it
        handleDeepLink(intent)

        setContent {
            val playerState by playerViewModel.uiState.collectAsState()
            val updateState by updateViewModel.uiState.collectAsState()
            val currentSinger = playerState.currentSong?.singer?.name
            val context = LocalContext.current

            // Check for updates when setup completes
            LaunchedEffect(isSetupComplete) {
                if (isSetupComplete) {
                    updateViewModel.checkForUpdate()
                }
            }

            NeuroKaraokeTheme(currentSinger = currentSinger) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    if (!isSetupComplete) {
                        SetupScreen(
                            onSetupComplete = {
                                isSetupComplete = true
                                // Reload cached songs into ViewModel
                                playerViewModel.loadCachedSongs()
                            }
                        )
                    } else {
                        MainScreen(
                            playerViewModel = playerViewModel,
                            authViewModel = authViewModel
                        )
                    }

                    // Update dialog
                    if (updateState.showDialog && updateState.latestRelease != null) {
                        UpdateDialog(
                            release = updateState.latestRelease!!,
                            currentVersion = updateState.currentVersion,
                            onUpdate = {
                                updateViewModel.getUpdateIntent()?.let { intent ->
                                    context.startActivity(intent)
                                }
                                updateViewModel.hideDialog()
                            },
                            onUninstallAndUpdate = {
                                // Open download URL first so user can get the new APK
                                updateViewModel.getUpdateIntent()?.let { intent ->
                                    context.startActivity(intent)
                                }
                                // Then prompt to uninstall current app (fixes signing key conflict)
                                context.startActivity(updateViewModel.getUninstallIntent())
                                updateViewModel.hideDialog()
                            },
                            onDismiss = {
                                updateViewModel.dismissUpdate()
                            }
                        )
                    }
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        // Save playback state when Activity goes to background
        // This ensures the state is persisted even if the process is killed afterward
        playerViewModel.savePlaybackStateNow()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // Handle deep link when app is already running (singleTask)
        handleDeepLink(intent)
    }

    /**
     * Handle incoming deep links for Discord auth callback
     *
     * Expected URL formats:
     * - neurokaraoke://auth?id=xxx&username=xxx&discriminator=xxx&avatar=xxx&token=xxx
     * - https://neurokaraoke.com/app-auth?id=xxx&username=xxx&discriminator=xxx&avatar=xxx&token=xxx
     */
    private fun handleDeepLink(intent: Intent?) {
        val uri: Uri? = intent?.data

        if (uri == null) return

        Log.d("MainActivity", "Received deep link: $uri")

        // Check if this is an auth callback
        val isAuthCallback = when {
            uri.scheme == "neurokaraoke" && uri.host == "auth" -> true
            uri.scheme == "https" && uri.host == "neurokaraoke.com" && uri.path == "/app-auth" -> true
            else -> false
        }

        if (!isAuthCallback) return

        // Check for OAuth authorization code (direct Discord redirect)
        val code = uri.getQueryParameter("code")
        if (code != null) {
            Log.d("MainActivity", "Auth callback with code, exchanging for token...")
            authViewModel.handleAuthCallback(code)
            return
        }

        // Fallback: parse user data from URL parameters (backend-processed redirect)
        val userId = uri.getQueryParameter("id")
        val username = uri.getQueryParameter("username")
        val discriminator = uri.getQueryParameter("discriminator") ?: "0"
        val avatar = uri.getQueryParameter("avatar")
        val accessToken = uri.getQueryParameter("token")

        Log.d("MainActivity", "Auth callback: user=$username, id=$userId")

        if (userId != null && username != null) {
            val user = User(
                id = userId,
                username = username,
                discriminator = discriminator,
                avatar = avatar,
                accessToken = accessToken
            )
            authViewModel.handleUserFromDeepLink(user)
        }
    }
}
