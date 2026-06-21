package com.soul.neurokaraoke.data.model

data class ProfileResponse(
    val profile: Profile,
    val badges: List<Badge>
)

data class Profile(
    val displayName: String,
    val avatarUrl: String?,
    val level: Int?,
    val levelTitle: String?,
    val totalXP: Int?,
    val totalBadges: Int?,
    val unlockedBadges: Int?,
    val levelProgress: Double?,
    val xpToNextLevel: Int?
)

data class Badge(
    val id: String,
    val name: String,
    val description: String?,
    val rarity: Int,
    val unlocked: Boolean,
    val currentProgress: Int,
    val conditionValue: Int,
    val absolutePath: String?
) {
    val iconUrl: String?
        get() = absolutePath?.takeIf { it.isNotEmpty() }
            ?.let { "https://images.neurokaraoke.com$it/public" }
}

data class UploadLimits(
    val maxSongs: Int,
    val maxStorageBytes: Long,
    val usedStorageBytes: Long,
    val currentSongCount: Int,
    val currentPlaylistCount: Int,
    val playlistLimit: Int,
    val songPerPlaylistLimit: Int
)
