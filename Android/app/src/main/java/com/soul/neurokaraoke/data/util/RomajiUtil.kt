package com.soul.neurokaraoke.data.util

import android.icu.text.Transliterator
import android.os.Build

object RomajiUtil {

    // android.icu.text.Transliterator is API 29+. Built lazily, once, and only on
    // supported devices so minSdk-24 phones don't crash on catalog load (NoClassDefFound).
    private val transliterator: Transliterator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            // Katakana-Latin and Hiragana-Latin handle kana; Any-Latin handles CJK ideographs
            // Latin-ASCII strips diacritics (ō → o, ū → u)
            Transliterator.getInstance("Katakana-Latin; Hiragana-Latin; Any-Latin; Latin-ASCII")
        } else null
    }

    /**
     * Converts Japanese/Chinese text to romanized Latin characters.
     * Returns lowercase with accents stripped for easy search matching.
     * Non-CJK text passes through unchanged.
     *
     * On API < 29 (no Transliterator) it degrades to the original text lowercased —
     * Latin-query search still matches; kana/CJK just isn't romaji-expanded.
     */
    fun toRomaji(text: String): String {
        if (text.isBlank()) return ""
        // Explicit SDK_INT guard (not just the null-check) so lint can prove the
        // API 29 transliterate() call is only reached on supported devices.
        val t = transliterator
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && t != null) {
            t.transliterate(text).lowercase()
        } else {
            text.lowercase()
        }
    }
}
