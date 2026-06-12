package com.soul.neurokaraoke.aaos

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.service.MediaPlaybackService
import com.soul.neurokaraoke.ui.theme.NeuroKaraokeTheme

/**
 * AAOS launcher. Replaces CarAppActivity for the automotive flavor —
 * hosts a custom Compose UI tuned for car screens (big hit targets,
 * simplified nav, full layout freedom).
 */
class AaosLauncherActivity : ComponentActivity() {

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(LocaleManager.wrapContext(newBase))
    }

    private var controller: MediaController? = null
    private val pendingAuthCode = androidx.compose.runtime.mutableStateOf<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleAuthIntent(intent)
    }

    private fun handleAuthIntent(intent: Intent?) {
        val data = intent?.data ?: return
        if (data.scheme == "neurokaraoke" && data.host == "auth") {
            val code = data.getQueryParameter("code") ?: return
            pendingAuthCode.value = code
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        handleAuthIntent(intent)

        // Connect to MediaPlaybackService so playback flows through the
        // same service the phone uses — notifications, audio focus, the works.
        val token = SessionToken(this, ComponentName(this, MediaPlaybackService::class.java))
        val future = MediaController.Builder(this, token).buildAsync()
        future.addListener({
            try { controller = future.get() } catch (_: Exception) {}
        }, mainExecutor)

        setContent {
            // Collect current language to trigger recomposition across entire tree
            val currentLanguage by LocaleManager.currentLanguage.collectAsState()

            key(currentLanguage) {
                NeuroKaraokeTheme {
                    Surface(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(MaterialTheme.colorScheme.background)
                    ) {
                        val vm: AaosViewModel = viewModel()
                        LaunchedEffect(Unit) { vm.bootstrap(applicationContext) }
                        // Discord deep-link path no longer used (pairing code replaces it).
                        AaosApp(
                            viewModel = vm,
                            controllerProvider = { controller }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        controller?.release()
        controller = null
        super.onDestroy()
    }
}
