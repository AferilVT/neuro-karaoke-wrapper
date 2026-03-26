package com.soul.neurokaraoke.data.model

data class Soundbite(
    val id: String,
    val title: String,
    val comments: String?,
    val duration: Int,
    val absolutePath: String?,
    val tag: Int, // 0=Neuro, 1=Evil, 2=Vedal, 3=Other
    val audioUrl: String,
    val uploadedAt: String?,
    val uploadedBy: String?,
    val imageUrl: String?,
    val embeddable: Boolean,
    val playCount: Int
) {
    val tagLabel: String
        get() = when (tag) {
            0 -> "NEURO"
            1 -> "EVIL"
            2 -> "VEDAL"
            3 -> "OTHER"
            else -> "OTHER"
        }

    val displayTitle: String
        get() = title.replace("_", " ")
}
