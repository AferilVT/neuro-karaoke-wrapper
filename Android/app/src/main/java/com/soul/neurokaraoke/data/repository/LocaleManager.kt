package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.content.SharedPreferences
import android.content.res.Configuration
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

/**
 * To add a new language:
 *   1. Add a SupportedLocale entry to SUPPORTED_LOCALES below.
 *   2. Create app/src/main/res/values-<tag>/strings.xml with all translated strings.
 *      Use TRANSLATING.md at the repo root for the full template and instructions.
 *
 * That's it — the Settings screen and language switching pick it up automatically.
 */
data class SupportedLocale(
    val code: String,       // BCP-47 tag used for res folder (e.g. "zh-CN" → values-zh-rCN)
    val locale: Locale,     // Java Locale for context wrapping
    val nativeName: String  // Shown in Settings — always in the language itself, never translated
)

object LocaleManager {

    // ── Add new languages here ────────────────────────────────────────────────
    val SUPPORTED_LOCALES: List<SupportedLocale> = listOf(
        SupportedLocale("en",    Locale.ENGLISH,      "English"),
        SupportedLocale("zh-CN", Locale("zh", "CN"),  "简体中文"),
    )
    // ─────────────────────────────────────────────────────────────────────────

    val DEFAULT_LANGUAGE: String = SUPPORTED_LOCALES.first().code
    private val supportedCodes: Set<String> = SUPPORTED_LOCALES.map { it.code }.toSet()

    private var prefs: SharedPreferences? = null
    private var appContext: Context? = null

    private val _currentLanguage = MutableStateFlow(DEFAULT_LANGUAGE)
    val currentLanguage: StateFlow<String> = _currentLanguage.asStateFlow()

    @Synchronized
    fun initialize(context: Context) {
        if (prefs != null) return
        val ctx = context.applicationContext ?: context
        appContext = ctx
        prefs = ctx.getSharedPreferences("app_settings", Context.MODE_PRIVATE)
        val stored = prefs?.getString(KEY_LANGUAGE, null)
        val language = if (stored != null && stored in supportedCodes) {
            stored
        } else {
            prefs?.edit()?.putString(KEY_LANGUAGE, DEFAULT_LANGUAGE)?.apply()
            DEFAULT_LANGUAGE
        }
        _currentLanguage.value = language
    }

    fun setLanguage(languageCode: String) {
        if (languageCode == _currentLanguage.value) return
        val validCode = if (languageCode in supportedCodes) languageCode else DEFAULT_LANGUAGE
        prefs?.edit()?.putString(KEY_LANGUAGE, validCode)?.apply()
        _currentLanguage.value = validCode
    }

    fun wrapContext(baseContext: Context): Context {
        return try {
            val locale = SUPPORTED_LOCALES.firstOrNull { it.code == _currentLanguage.value }?.locale
                ?: Locale.ENGLISH
            val config = Configuration(baseContext.resources.configuration)
            config.setLocale(locale)
            baseContext.createConfigurationContext(config)
        } catch (_: Exception) {
            baseContext
        }
    }

    private const val KEY_LANGUAGE = "app_language"
}
