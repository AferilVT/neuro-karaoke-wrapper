package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object SettingsRepository {

    private var prefs: SharedPreferences? = null

    private val _crossfadeDuration = MutableStateFlow(0)
    val crossfadeDuration: StateFlow<Int> = _crossfadeDuration.asStateFlow()

    private val _gaplessPlayback = MutableStateFlow(true)
    val gaplessPlayback: StateFlow<Boolean> = _gaplessPlayback.asStateFlow()

    private val _normalizeVolume = MutableStateFlow(false)
    val normalizeVolume: StateFlow<Boolean> = _normalizeVolume.asStateFlow()

    private val _autoPlay = MutableStateFlow(true)
    val autoPlay: StateFlow<Boolean> = _autoPlay.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs?.let { p ->
            _crossfadeDuration.value = p.getInt(KEY_CROSSFADE, 0)
            _gaplessPlayback.value = p.getBoolean(KEY_GAPLESS, true)
            _normalizeVolume.value = p.getBoolean(KEY_NORMALIZE_VOLUME, false)
            _autoPlay.value = p.getBoolean(KEY_AUTO_PLAY, true)
        }
    }

    fun setCrossfadeDuration(seconds: Int) {
        val clamped = seconds.coerceIn(0, 12)
        prefs?.edit()?.putInt(KEY_CROSSFADE, clamped)?.apply()
        _crossfadeDuration.value = clamped
    }

    fun setGaplessPlayback(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_GAPLESS, enabled)?.apply()
        _gaplessPlayback.value = enabled
    }

    fun setNormalizeVolume(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_NORMALIZE_VOLUME, enabled)?.apply()
        _normalizeVolume.value = enabled
    }

    fun setAutoPlay(enabled: Boolean) {
        prefs?.edit()?.putBoolean(KEY_AUTO_PLAY, enabled)?.apply()
        _autoPlay.value = enabled
    }

    private const val KEY_CROSSFADE = "crossfade_duration"
    private const val KEY_GAPLESS = "gapless_playback"
    private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
    private const val KEY_AUTO_PLAY = "auto_play"
}
