package com.soul.neurokaraoke.car

import android.content.Intent
import android.support.v4.media.session.MediaSessionCompat
import androidx.car.app.Screen
import androidx.car.app.Session
import androidx.car.app.media.MediaPlaybackManager
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import com.soul.neurokaraoke.data.repository.LocaleManager
import com.soul.neurokaraoke.service.GlobalMediaToken
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.launch

class NeuroKaraokeCarSession : Session() {

    private var homeScreen: HomeCarScreen? = null

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    override fun onCreateScreen(intent: Intent): Screen {
        android.util.Log.e("NK_CAR", "onCreateScreen: action=${intent.action}")
        val home = HomeCarScreen(carContext)
        homeScreen = home

        // Register the media session token
        registerToken()

        if (isMediaIntent(intent)) {
            android.util.Log.e("NK_CAR", "isMediaIntent=true -> Navigating to NowPlaying")
            carContext.getCarService(androidx.car.app.ScreenManager::class.java).push(home)
            return NowPlayingCarScreen(carContext, home.carPlayer)
        }

        return home
    }

    override fun onNewIntent(intent: Intent) {
        android.util.Log.e("NK_CAR", "onNewIntent: action=${intent.action}")
        registerToken()
        if (isMediaIntent(intent)) {
            android.util.Log.e("NK_CAR", "isMediaIntent=true in onNewIntent -> Pushing NowPlaying")
            val screenManager = carContext.getCarService(androidx.car.app.ScreenManager::class.java)
            if (screenManager.top !is NowPlayingCarScreen) {
                screenManager.push(NowPlayingCarScreen(carContext, homeScreen?.carPlayer ?: CarPlayer(carContext)))
            }
        }
    }

    @androidx.annotation.OptIn(androidx.car.app.annotations.ExperimentalCarApi::class)
    private fun registerToken() {
        GlobalMediaToken.token?.let { token ->
            try {
                val playbackManager = carContext.getCarService(MediaPlaybackManager::class.java)
                playbackManager.registerMediaPlaybackToken(token)
                android.util.Log.e("NK_CAR", "Token registered")
            } catch (e: Exception) {
                android.util.Log.e("NK_CAR", "Token registration failed", e)
            }
        }
    }

    private fun isMediaIntent(intent: Intent): Boolean {
        val action = intent.action
        val command = intent.getIntExtra("androidx.car.app.extra.START_CAR_APP_ACTION_COMMAND", -1)
        
        val match = action == "androidx.car.app.media.action.VIEW_PLAYBACK" ||
                action == "androidx.car.app.media.action.TRANSPORT_CONTROLS" ||
                action == "android.media.action.MEDIA_PLAYBACK" ||
                action == "androidx.car.app.action.PLAYBACK" ||
                action == "MEDIA_SHOW_PLAYBACK_VIEW" ||
                action?.contains("PLAYBACK") == true ||
                command == 1 ||
                (action == Intent.ACTION_MAIN && GlobalMediaToken.token != null)
        
        android.util.Log.e("NK_CAR", "isMediaIntent eval: action=$action, command=$command, result=$match")
        return match
    }
}
