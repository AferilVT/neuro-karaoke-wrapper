package com.soul.neurokaraoke.service

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesRepository

/**
 * Builds the Android Auto browse tree from cached songs, favorites, and radio.
 * Tree is intentionally shallow (2 levels max) per Auto guidelines.
 */
class AutoBrowseTree(private val context: Context) {

    private val songCache = SongCache(context)
    private val favoritesRepo by lazy { FavoritesRepository(context) }

    private val playbackPrefs = context.getSharedPreferences("playback_state", Context.MODE_PRIVATE)

    private var allSongs: List<Song> = emptyList()
    private var loaded = false

    suspend fun ensureLoaded() {
        if (!loaded) {
            allSongs = songCache.getCachedSongs()
            loaded = true
        }
    }

    /** Force a reload on the next access (favorites changed, cache rebuilt, etc.). */
    fun invalidate() {
        loaded = false
    }

    fun rootItem(): MediaItem = browsable(ROOT_ID, "Neuro Karaoke")

    /** Top-level categories shown on the Auto home screen. */
    suspend fun rootChildren(): List<MediaItem> {
        ensureLoaded()
        val items = mutableListOf<MediaItem>()

        // Resume last song (only if we have one)
        resumeSong()?.let { song ->
            items += playable(
                mediaId = "$RESUME_PREFIX${song.id}",
                title = "Resume: ${song.title}",
                subtitle = song.artist,
                artworkUri = song.coverUrl,
                playbackUri = song.audioUrl
            )
        }

        items += playable(
            mediaId = RADIO_ID,
            title = "Listen Live",
            subtitle = "Neuro 21 Station",
            artworkUri = null,
            playbackUri = RadioApi.STREAM_URL
        )

        items += browsable(QUEUE_ID, "Current Queue")
        items += browsable(FAVORITES_ID, "Favorites")
        items += browsable(ALL_SONGS_ID, "All Songs")
        items += browsable(NEURO_ID, "Neuro Sings")
        items += browsable(EVIL_ID, "Evil Sings")
        items += browsable(DUET_ID, "Duets")

        return items
    }

    suspend fun children(parentId: String): List<MediaItem> {
        ensureLoaded()
        return when (parentId) {
            ROOT_ID -> rootChildren()
            FAVORITES_ID -> favoritesRepo.favorites.value.map { it.toPlayable() }
            ALL_SONGS_ID -> allSongs.sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { it.toPlayable() }
            NEURO_ID -> allSongs.filter { it.singer == Singer.NEURO }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { it.toPlayable() }
            EVIL_ID -> allSongs.filter { it.singer == Singer.EVIL }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { it.toPlayable() }
            DUET_ID -> allSongs.filter { it.singer == Singer.DUET }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { it.toPlayable() }
            else -> emptyList()
        }
    }

    suspend fun item(mediaId: String): MediaItem? {
        ensureLoaded()
        return when {
            mediaId == ROOT_ID -> rootItem()
            mediaId == RADIO_ID -> playable(
                mediaId = RADIO_ID,
                title = "Listen Live",
                subtitle = "Neuro 21 Station",
                artworkUri = null,
                playbackUri = RadioApi.STREAM_URL
            )
            mediaId == FAVORITES_ID -> browsable(FAVORITES_ID, "Favorites")
            mediaId == ALL_SONGS_ID -> browsable(ALL_SONGS_ID, "All Songs")
            mediaId == NEURO_ID -> browsable(NEURO_ID, "Neuro Sings")
            mediaId == EVIL_ID -> browsable(EVIL_ID, "Evil Sings")
            mediaId == DUET_ID -> browsable(DUET_ID, "Duets")
            mediaId.startsWith(RESUME_PREFIX) -> {
                val songId = mediaId.removePrefix(RESUME_PREFIX)
                resolveSongMediaItem(songId)
            }
            else -> resolveSongMediaItem(mediaId)
        }
    }

    /**
     * Resolve a mediaId into a fully playable MediaItem (with URI) for the player.
     * Auto sends play requests with mediaId only — the service must add the URI.
     */
    suspend fun resolve(mediaId: String): MediaItem? {
        ensureLoaded()
        if (mediaId == RADIO_ID) {
            return playable(
                mediaId = RADIO_ID,
                title = "Listen Live",
                subtitle = "Neuro 21 Station",
                artworkUri = null,
                playbackUri = RadioApi.STREAM_URL
            )
        }
        val songId = mediaId.removePrefix(RESUME_PREFIX)
        return resolveSongMediaItem(songId)
    }

    suspend fun search(query: String): List<MediaItem> {
        ensureLoaded()
        if (query.isBlank()) return emptyList()
        val q = query.lowercase().trim()
        return allSongs.asSequence()
            .filter {
                it.title.lowercase().contains(q) ||
                    it.artist.lowercase().contains(q) ||
                    it.titleRomaji.lowercase().contains(q) ||
                    it.titleEnglish?.lowercase()?.contains(q) == true
            }
            .take(MAX_SEARCH_RESULTS)
            .map { it.toPlayable() }
            .toList()
    }

    private fun resolveSongMediaItem(songId: String): MediaItem? {
        val song = allSongs.firstOrNull { it.id == songId } ?: return null
        return song.toPlayable()
    }

    private fun resumeSong(): Song? {
        val id = playbackPrefs.getString("last_song_id", null) ?: return null
        if (id.isBlank() || id == "radio_live") return null
        return allSongs.firstOrNull { it.id == id }
    }

    private fun Song.toPlayable(): MediaItem = playable(
        mediaId = id,
        title = title,
        subtitle = "$artist • $coverArtist",
        artworkUri = coverUrl,
        playbackUri = audioUrl
    )

    private fun browsable(mediaId: String, title: String): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setMediaMetadata(metadata)
            .build()
    }

    private fun playable(
        mediaId: String,
        title: String,
        subtitle: String?,
        artworkUri: String?,
        playbackUri: String
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                if (!artworkUri.isNullOrBlank()) setArtworkUri(Uri.parse(artworkUri))
            }
            .build()
        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(playbackUri)
            .setMediaMetadata(metadata)
            .build()
    }

    companion object {
        const val ROOT_ID = "nk_root"
        const val RADIO_ID = "nk_radio"
        const val QUEUE_ID = "nk_queue"
        const val FAVORITES_ID = "nk_favorites"
        const val ALL_SONGS_ID = "nk_all_songs"
        const val NEURO_ID = "nk_neuro"
        const val EVIL_ID = "nk_evil"
        const val DUET_ID = "nk_duet"
        const val RESUME_PREFIX = "nk_resume_"

        private const val MAX_BROWSE_ITEMS = 500
        private const val MAX_SEARCH_RESULTS = 100
    }
}
