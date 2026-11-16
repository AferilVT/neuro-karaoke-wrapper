package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import com.soul.neurokaraoke.ui.theme.ThemeMode
import com.soul.neurokaraoke.ui.tv.neurolings.NeurolingsCounts
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

    private val _themeMode = MutableStateFlow(ThemeMode.AUTO)
    val themeMode: StateFlow<ThemeMode> = _themeMode.asStateFlow()

    // TV developer options: hidden until the version row is tapped 7x in 5s (see TvDevUnlock).
    private val _devOptionsUnlocked = MutableStateFlow(false)
    val devOptionsUnlocked: StateFlow<Boolean> = _devOptionsUnlocked.asStateFlow()

    // Walking mascots ("Neurolings") — a for-fun dev-only overlay, per-character live counts.
    private val _neurolingsCounts = MutableStateFlow<Map<String, Int>>(emptyMap())
    val neurolingsCounts: StateFlow<Map<String, Int>> = _neurolingsCounts.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        prefs = context.applicationContext.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        prefs?.let { p ->
            _crossfadeDuration.value = p.getInt(KEY_CROSSFADE, 0)
            _gaplessPlayback.value = p.getBoolean(KEY_GAPLESS, true)
            _normalizeVolume.value = p.getBoolean(KEY_NORMALIZE_VOLUME, false)
            _autoPlay.value = p.getBoolean(KEY_AUTO_PLAY, true)
            val themeName = p.getString(KEY_THEME_MODE, ThemeMode.AUTO.name) ?: ThemeMode.AUTO.name
            _themeMode.value = try { ThemeMode.valueOf(themeName) } catch (_: Exception) { ThemeMode.AUTO }
            _devOptionsUnlocked.value = p.getBoolean(KEY_DEV_OPTIONS, false)
            _neurolingsCounts.value = NeurolingsCounts.parse(p.getString(KEY_NEUROLINGS_COUNTS, null))
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

    fun setThemeMode(mode: ThemeMode) {
        prefs?.edit()?.putString(KEY_THEME_MODE, mode.name)?.apply()
        _themeMode.value = mode
    }

    fun setDevOptionsUnlocked(unlocked: Boolean) {
        prefs?.edit()?.putBoolean(KEY_DEV_OPTIONS, unlocked)?.apply()
        _devOptionsUnlocked.value = unlocked
        // Locking dev options also switches off anything gated behind it.
        if (!unlocked) {
            prefs?.edit()?.remove(KEY_NEUROLINGS_COUNTS)?.apply()
            _neurolingsCounts.value = emptyMap()
        }
    }

    fun setNeurolingsCount(name: String, count: Int) {
        val m = _neurolingsCounts.value.toMutableMap()
        val c = NeurolingsCounts.clamp(count)
        if (c <= 0) m.remove(name) else m[name] = c
        val nm = m.toMap()
        prefs?.edit()?.putString(KEY_NEUROLINGS_COUNTS, NeurolingsCounts.serialize(nm))?.apply()
        _neurolingsCounts.value = nm
    }

    private const val KEY_CROSSFADE = "crossfade_duration"
    private const val KEY_GAPLESS = "gapless_playback"
    private const val KEY_NORMALIZE_VOLUME = "normalize_volume"
    private const val KEY_AUTO_PLAY = "auto_play"
    private const val KEY_THEME_MODE = "theme_mode"
    private const val KEY_DEV_OPTIONS = "dev_options_unlocked"
    private const val KEY_NEUROLINGS_COUNTS = "neurolings_counts"
}
