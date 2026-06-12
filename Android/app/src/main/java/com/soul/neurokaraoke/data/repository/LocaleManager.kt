package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

object LocaleManager {

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    val SUPPORTED_LANGUAGES: Set<String> = setOf("en", "zh-CN")
    val DEFAULT_LANGUAGE: String = "en"

    private val _currentLanguage = MutableStateFlow(DEFAULT_LANGUAGE)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        // Use context directly — applicationContext is null during attachBaseContext()
        val ctx = context.applicationContext ?: context
        appContext = ctx
        prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val stored = prefs?.getString(KEY_LANGUAGE, null)
        val language = if (stored != null && stored in SUPPORTED_LANGUAGES) {
            stored
        } else {
            // Normalize invalid/missing preference to default and persist correction
            prefs?.edit()?.putString(KEY_LANGUAGE, DEFAULT_LANGUAGE)?.apply()
            DEFAULT_LANGUAGE
        }
        _currentLanguage.value = language
    }

    fun setLanguage(languageCode: String) {
        // Same-value no-op check
        if (languageCode == _currentLanguage.value) return

        // Only accept supported language codes
        val validCode = if (languageCode in SUPPORTED_LANGUAGES) languageCode else DEFAULT_LANGUAGE

        // Async persist via apply()
        prefs?.edit()?.putString(KEY_LANGUAGE, validCode)?.apply()

        // Update the app-level resources Configuration so stringResource() picks up
        // the new locale without needing Activity.recreate()
        appContext?.let { ctx ->
            val locale = getLocaleForCode(validCode)
            val config = Configuration(ctx.resources.configuration)
            config.setLocale(locale)
            @Suppress("DEPRECATION")
            ctx.resources.updateConfiguration(config, ctx.resources.displayMetrics)
        }

        // Update StateFlow (triggers Compose recomposition)
        _currentLanguage.value = validCode
    }

    fun wrapContext(baseContext: Context): Context {
        return try {
            val locale = getLocaleForCode(_currentLanguage.value)
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        } catch (_: Exception) {
            // If wrapping fails, return original context (English display)
            baseContext
        }
    }

    private fun getLocaleForCode(code: String): Locale {
        return when (code) {
            "en" -> Locale.ENGLISH
            "zh-CN" -> Locale("zh", "CN")
            else -> Locale.ENGLISH
        }
    }

    private const val KEY_LANGUAGE = "app_language"
}
