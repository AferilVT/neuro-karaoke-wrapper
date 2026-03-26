package com.soul.neurokaraoke.data.util

import android.icu.text.Transliterator

object RomajiUtil {

    private val transliterator: Transliterator by lazy {
        // Katakana-Latin and Hiragana-Latin handle kana; Any-Latin handles CJK ideographs
        // Latin-ASCII strips diacritics (ō → o, ū → u)
        Transliterator.getInstance("Katakana-Latin; Hiragana-Latin; Any-Latin; Latin-ASCII")
    }

    /**
     * Converts Japanese/Chinese text to romanized Latin characters.
     * Returns lowercase with accents stripped for easy search matching.
     * Non-CJK text passes through unchanged.
     */
    fun toRomaji(text: String): String {
        if (text.isBlank()) return ""
        return transliterator.transliterate(text).lowercase()
    }
}
