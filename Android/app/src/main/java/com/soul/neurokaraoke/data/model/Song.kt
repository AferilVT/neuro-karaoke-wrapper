package com.soul.neurokaraoke.data.model

enum class Singer {
    NEURO,
    EVIL,
    DUET,
    OTHER;

    companion object {
        /**
         * Classify cover performers for theme/filter purposes only.
         * The actual artist text is kept separately and displayed verbatim.
         */
        fun fromCoverArtists(coverArtists: String?): Singer {
            val artists = coverArtists?.trim().orEmpty()
            if (artists.isBlank()) return OTHER

            val normalized = artists.lowercase()
            val hasMultiplePerformers =
                normalized.contains("duet") ||
                Regex("""\s(?:&|and|x|with|feat\.?|featuring)\s""").containsMatchIn(normalized) ||
                ',' in normalized ||
                (';' in normalized)

            return when {
                hasMultiplePerformers -> DUET
                "evil" in normalized -> EVIL
                "neuro" in normalized -> NEURO
                else -> OTHER
            }
        }
    }
}

data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val coverUrl: String,
    val audioUrl: String = "",
    val duration: Long = 0L,
    val singer: Singer = Singer.NEURO,
    val coverArtists: String = "",
    val artCredit: String? = null,
    val titleRomaji: String = "",
    val titleEnglish: String? = null,
    val artistRomaji: String = ""
) {
    /** The API-provided cover performer text, with a fallback for legacy/local songs. */
    val coverArtist: String
        get() = coverArtists.ifBlank {
            when (singer) {
                Singer.NEURO -> "Neuro-sama"
                Singer.EVIL -> "Evil Neuro"
                Singer.DUET -> "Neuro & Evil"
                Singer.OTHER -> "Neuro-sama"
            }
        }

    /**
     * Extract the social link from a human-readable art credit.
     * Supports full URLs, bare domains, and credits containing an @handle.
     */
    val artCreditUrl: String?
        get() {
            val credit = artCredit?.trim().orEmpty()
            if (credit.isBlank()) return null

            val link = Regex(
                """(?i)(?:https?://)?(?:www\.)?(?:x\.com|twitter\.com|bsky\.app|instagram\.com|pixiv\.net|deviantart\.com|artstation\.com|tumblr\.com|space\.bilibili\.com)/[^\s,;)]+"""
            ).find(credit)?.value

            if (link != null) {
                return if (link.startsWith("http", ignoreCase = true)) link else "https://$link"
            }

            val handle = Regex("""(?<![\w.])@([A-Za-z0-9_]{1,30})""")
                .find(credit)
                ?.groupValues
                ?.getOrNull(1)
            return handle?.let { "https://x.com/$it" }
        }
}
