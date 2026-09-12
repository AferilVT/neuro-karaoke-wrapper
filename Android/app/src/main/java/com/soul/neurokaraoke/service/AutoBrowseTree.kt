package com.soul.neurokaraoke.service

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.soul.neurokaraoke.data.PlaylistCatalog
import com.soul.neurokaraoke.data.SongCache
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.api.RadioApi
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesRepository
import com.soul.neurokaraoke.data.repository.UserPlaylistRepository

/**
 * Builds the Android Auto browse tree from cached songs, favorites, and radio.
 * Tree is intentionally shallow (2 levels max) per Auto guidelines.
 */
class AutoBrowseTree(private val context: Context) {

    private val songCache = SongCache(context)
    private val favoritesRepo by lazy { FavoritesRepository(context) }
    private val userRepo by lazy { UserPlaylistRepository(context) }
    private val catalog by lazy { PlaylistCatalog(context) }
    private val karaokeApi = NeuroKaraokeApi()

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

    fun rootItem(): MediaItem = browsable(ROOT_ID, "Neuro Karaoke", null)

    private fun radioItem(): MediaItem = playable(
        mediaId = RADIO_ID,
        title = "Listen Live",
        subtitle = "Neuro 21 Station",
        artworkUri = "https://idk.neurokaraoke.com/radio.png", // Use a static icon for radio
        playbackUri = RadioApi.STREAM_URL
    )

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

        items += browsable(ALL_SONGS_ID, "All Songs", null)
        items += browsable(FAVORITES_ID, "Favorites", favoritesRepo.favorites.value.firstOrNull()?.coverUrl)
        items += radioItem()
        items += browsable(NEURO_ID, "Neuro Sings", allSongs.firstOrNull { it.singer == Singer.NEURO }?.coverUrl)
        items += browsable(EVIL_ID, "Evil Sings", allSongs.firstOrNull { it.singer == Singer.EVIL }?.coverUrl)
        items += browsable(DUET_ID, "Duets", allSongs.firstOrNull { it.singer == Singer.DUET }?.coverUrl)
        items += browsable(MORE_ID, "More", null)

        return items
    }

    suspend fun children(parentId: String): List<MediaItem> {
        Log.d("AutoBrowseTree", "children: parentId=$parentId")
        ensureLoaded()
        val result = when (parentId) {
            ROOT_ID -> rootChildren()
            FAVORITES_ID -> favoritesRepo.favorites.value.map { song ->
                song.toPlayable().buildUpon()
                    .setMediaId("$FAVORITE_SONG_PREFIX${song.id}")
                    .build()
            }
            ALL_SONGS_ID -> allSongs.sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { song ->
                song.toPlayable().buildUpon()
                    .setMediaId("$ALL_SONGS_PREFIX${song.id}")
                    .build()
            }
            NEURO_ID -> allSongs.filter { it.singer == Singer.NEURO }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { song ->
                    song.toPlayable().buildUpon()
                        .setMediaId("$NEURO_SONG_PREFIX${song.id}")
                        .build()
                }
            EVIL_ID -> allSongs.filter { it.singer == Singer.EVIL }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { song ->
                    song.toPlayable().buildUpon()
                        .setMediaId("$EVIL_SONG_PREFIX${song.id}")
                        .build()
                }
            DUET_ID -> allSongs.filter { it.singer == Singer.DUET }
                .sortedBy { it.title.lowercase() }.take(MAX_BROWSE_ITEMS).map { song ->
                    song.toPlayable().buildUpon()
                        .setMediaId("$DUET_SONG_PREFIX${song.id}")
                        .build()
                }
            MORE_ID -> listOf(
                browsable(PERSONAL_PLAYLISTS_ID, "My Playlists", null),
                browsable(OFFICIAL_PLAYLISTS_ID, "Official Setlists", null),
                browsable(PUBLIC_PLAYLISTS_ID, "Public Playlists", null),
                browsable(NEURO_ID, "Neuro Sings", null),
                browsable(EVIL_ID, "Evil Sings", null),
                browsable(DUET_ID, "Duets", null)
            )
            PERSONAL_PLAYLISTS_ID -> {
                userRepo.playlists.value.map { playlist ->
                    browsable(
                        mediaId = "$PERSONAL_PLAYLIST_PREFIX${playlist.id}",
                        title = playlist.title,
                        artworkUri = playlist.coverUrl.ifBlank { playlist.previewCovers.firstOrNull() }
                    )
                }
            }
            OFFICIAL_PLAYLISTS_ID -> {
                catalog.getPlaylists().map { playlist ->
                    browsable(
                        mediaId = "$OFFICIAL_PLAYLIST_PREFIX${playlist.id}",
                        title = playlist.title,
                        artworkUri = playlist.coverUrl.ifBlank { playlist.previewCovers.firstOrNull() }
                    )
                }
            }
            PUBLIC_PLAYLISTS_ID -> listOf(
                browsable(PUBLIC_PLAYLISTS_NEWEST_ID, "Updated - New to Old", null),
                browsable(PUBLIC_PLAYLISTS_OLDEST_ID, "Updated - Old to New", null),
                browsable(PUBLIC_PLAYLISTS_PLAYS_HIGH_ID, "Play Count - High to Low", null),
                browsable(PUBLIC_PLAYLISTS_PLAYS_LOW_ID, "Play Count - Low to High", null)
            )
            PUBLIC_PLAYLISTS_NEWEST_ID -> fetchAndSortPublicPlaylists { it.sortedByDescending { p -> p.updatedAt } }
            PUBLIC_PLAYLISTS_OLDEST_ID -> fetchAndSortPublicPlaylists { it.sortedBy { p -> p.updatedAt } }
            PUBLIC_PLAYLISTS_PLAYS_HIGH_ID -> fetchAndSortPublicPlaylists { it.sortedByDescending { p -> p.playCount } }
            PUBLIC_PLAYLISTS_PLAYS_LOW_ID -> fetchAndSortPublicPlaylists { it.sortedBy { p -> p.playCount } }
            else -> {
                if (parentId.startsWith(PUBLIC_PLAYLIST_PREFIX)) {
                    val playlistId = parentId.removePrefix(PUBLIC_PLAYLIST_PREFIX)
                    karaokeApi.fetchPlaylist(playlistId).getOrNull()?.map { apiSong ->
                        val coverArtists = apiSong.coverArtists.orEmpty()
                        val audioUrl = apiSong.audioUrl ?: "about:blank"
                        playable(
                            mediaId = if (audioUrl.isNotBlank() && audioUrl != "about:blank") {
                                "$SONG_URL_PREFIX$parentId|$audioUrl"
                            } else {
                                "empty_${System.currentTimeMillis()}"
                            },
                            title = apiSong.title,
                            subtitle = "${apiSong.originalArtists ?: "Unknown Artist"} • ${Singer.fromCoverArtists(coverArtists)}",
                            artworkUri = apiSong.getCoverArtUrl(),
                            playbackUri = audioUrl
                        )
                    } ?: emptyList()
                } else if (parentId.startsWith(PERSONAL_PLAYLIST_PREFIX)) {
                    val id = parentId.removePrefix(PERSONAL_PLAYLIST_PREFIX)
                    val playlist = userRepo.playlists.value.find { it.id == id }
                    
                    // If playlist found but has no songs, try to load them from API/DB
                    if (playlist != null && playlist.songs.isEmpty()) {
                        userRepo.loadPlaylistSongs(id)
                    }

                    userRepo.playlists.value.find { it.id == id }?.songs?.map { song ->
                        song.toPlayable().buildUpon()
                            .setMediaId("$PERSONAL_SONG_PREFIX$parentId|${song.id}")
                            .build()
                    } ?: emptyList()
                } else if (parentId.startsWith(OFFICIAL_PLAYLIST_PREFIX)) {
                    val id = parentId.removePrefix(OFFICIAL_PLAYLIST_PREFIX)
                    val apiSongs = karaokeApi.fetchPlaylist(id).getOrNull().orEmpty()
                    apiSongs.map { apiSong ->
                        val coverArtists = apiSong.coverArtists.orEmpty()
                        val audioUrl = apiSong.audioUrl ?: "about:blank"
                        playable(
                            mediaId = if (audioUrl.isNotBlank() && audioUrl != "about:blank") {
                                "$SONG_URL_PREFIX$parentId|$audioUrl"
                            } else {
                                "empty_${System.currentTimeMillis()}"
                            },
                            title = apiSong.title,
                            subtitle = "${apiSong.originalArtists ?: "Unknown Artist"} • ${Singer.fromCoverArtists(coverArtists)}",
                            artworkUri = apiSong.getCoverArtUrl(),
                            playbackUri = audioUrl
                        )
                    }
                } else {
                    emptyList()
                }
            }
        }
        Log.d("AutoBrowseTree", "children: parentId=$parentId, resultSize=${result.size}")
        return result
    }

    suspend fun item(mediaId: String): MediaItem? {
        ensureLoaded()
        return when {
            mediaId == ROOT_ID -> rootItem()
            mediaId == RADIO_ID -> radioItem()

            mediaId == FAVORITES_ID -> browsable(FAVORITES_ID, "Favorites", favoritesRepo.favorites.value.firstOrNull()?.coverUrl)
            mediaId == ALL_SONGS_ID -> browsable(ALL_SONGS_ID, "All Items", null)
            mediaId == NEURO_ID -> browsable(NEURO_ID, "Neuro Sings", allSongs.firstOrNull { it.singer == Singer.NEURO }?.coverUrl)
            mediaId == EVIL_ID -> browsable(EVIL_ID, "Evil Sings", allSongs.firstOrNull { it.singer == Singer.EVIL }?.coverUrl)
            mediaId == DUET_ID -> browsable(DUET_ID, "Duets", allSongs.firstOrNull { it.singer == Singer.DUET }?.coverUrl)
            mediaId == MORE_ID -> browsable(MORE_ID, "More", null)

            mediaId == PUBLIC_PLAYLISTS_ID -> browsable(PUBLIC_PLAYLISTS_ID, "Public Playlists", null)
            mediaId == PUBLIC_PLAYLISTS_NEWEST_ID -> browsable(PUBLIC_PLAYLISTS_NEWEST_ID, "Updated - New to Old", null)
            mediaId == PUBLIC_PLAYLISTS_OLDEST_ID -> browsable(PUBLIC_PLAYLISTS_OLDEST_ID, "Updated - Old to New", null)
            mediaId == PUBLIC_PLAYLISTS_PLAYS_HIGH_ID -> browsable(PUBLIC_PLAYLISTS_PLAYS_HIGH_ID, "Play Count - High to Low", null)
            mediaId == PUBLIC_PLAYLISTS_PLAYS_LOW_ID -> browsable(PUBLIC_PLAYLISTS_PLAYS_LOW_ID, "Play Count - Low to High", null)
            mediaId == PERSONAL_PLAYLISTS_ID -> browsable(PERSONAL_PLAYLISTS_ID, "My Playlists", null)
            mediaId == OFFICIAL_PLAYLISTS_ID -> browsable(OFFICIAL_PLAYLISTS_ID, "Official Setlists", null)
            mediaId.startsWith(PUBLIC_PLAYLIST_PREFIX) -> {
                browsable(mediaId, "Playlist", null)
            }
            mediaId.startsWith(PERSONAL_PLAYLIST_PREFIX) -> {
                val id = mediaId.removePrefix(PERSONAL_PLAYLIST_PREFIX)
                val pl = userRepo.playlists.value.find { it.id == id }
                val artwork = pl?.let { it.coverUrl.ifBlank { it.previewCovers.firstOrNull() } }
                browsable(mediaId, pl?.title ?: "My Playlist", artwork)
            }
            mediaId.startsWith(OFFICIAL_PLAYLIST_PREFIX) -> {
                val id = mediaId.removePrefix(OFFICIAL_PLAYLIST_PREFIX)
                val pl = catalog.getPlaylists().find { it.id == id }
                val artwork = pl?.let { it.coverUrl.ifBlank { it.previewCovers.firstOrNull() } }
                browsable(mediaId, pl?.title ?: "Setlist", artwork)
            }
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
        if (mediaId == RADIO_ID) return radioItem()
        
        if (mediaId.startsWith(SONG_URL_PREFIX)) {
            val fullId = mediaId.removePrefix(SONG_URL_PREFIX)
            val parts = fullId.split("|", limit = 2)
            val parentId = if (parts.size == 2) parts[0] else ""
            val url = if (parts.size == 2) parts[1] else parts[0]

            // Try to find in cache for better metadata, else return generic
            val song = allSongs.firstOrNull { it.audioUrl == url }
            val parentName = if (parentId.isNotBlank()) getParentName(parentId) else "Public Playlist"
            
            return if (song != null) {
                song.toPlayable(parentName)
            } else {
                playable(
                    mediaId = mediaId,
                    title = "Song",
                    subtitle = "Public Playlist",
                    artworkUri = null,
                    playbackUri = url,
                    albumTitle = parentName
                )
            }
        }


        if (mediaId.startsWith(ALL_SONGS_PREFIX)) {
            val songId = mediaId.removePrefix(ALL_SONGS_PREFIX)
            return resolveSongMediaItem(songId, "All Songs")
        }

        if (mediaId.startsWith(FAVORITE_SONG_PREFIX)) {
            val songId = mediaId.removePrefix(FAVORITE_SONG_PREFIX)
            return resolveSongMediaItem(songId, "Favorites")
        }

        if (mediaId.startsWith(NEURO_SONG_PREFIX)) {
            val songId = mediaId.removePrefix(NEURO_SONG_PREFIX)
            return resolveSongMediaItem(songId, "Neuro Sings")
        }

        if (mediaId.startsWith(EVIL_SONG_PREFIX)) {
            val songId = mediaId.removePrefix(EVIL_SONG_PREFIX)
            return resolveSongMediaItem(songId, "Evil Sings")
        }

        if (mediaId.startsWith(DUET_SONG_PREFIX)) {
            val songId = mediaId.removePrefix(DUET_SONG_PREFIX)
            return resolveSongMediaItem(songId, "Duets")
        }

        if (mediaId.startsWith(PERSONAL_SONG_PREFIX) || mediaId.startsWith(OFFICIAL_SONG_PREFIX)) {
            val fullId = if (mediaId.startsWith(PERSONAL_SONG_PREFIX)) 
                mediaId.removePrefix(PERSONAL_SONG_PREFIX) 
            else 
                mediaId.removePrefix(OFFICIAL_SONG_PREFIX)
            val parts = fullId.split("|", limit = 2)
            val parentId = if (parts.size == 2) parts[0] else ""
            val songId = if (parts.size == 2) parts[1] else parts[0]
            
            val parentName = if (parentId.isNotBlank()) getParentName(parentId) else "Playlist"
            return resolveSongMediaItem(songId, parentName)
        }

        val songId = mediaId.removePrefix(RESUME_PREFIX)
        return resolveSongMediaItem(songId)
    }

    private suspend fun getParentName(parentId: String): String {
        return when {
            parentId == FAVORITES_ID -> "Favorites"
            parentId == ALL_SONGS_ID -> "All Songs"
            parentId == NEURO_ID -> "Neuro Sings"
            parentId == EVIL_ID -> "Evil Sings"
            parentId == DUET_ID -> "Duets"
            parentId.startsWith(PERSONAL_PLAYLIST_PREFIX) -> {
                val id = parentId.removePrefix(PERSONAL_PLAYLIST_PREFIX)
                userRepo.playlists.value.find { it.id == id }?.title ?: "My Playlist"
            }
            parentId.startsWith(OFFICIAL_PLAYLIST_PREFIX) -> {
                val id = parentId.removePrefix(OFFICIAL_PLAYLIST_PREFIX)
                catalog.getPlaylists().find { it.id == id }?.title ?: "Setlist"
            }
            parentId.startsWith(PUBLIC_PLAYLIST_PREFIX) -> "Public Playlist"
            else -> "Neuro Karaoke"
        }
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
            .map { it.toPlayable("Search Results") }
            .toList()
    }

    fun resolveSongMediaItem(songId: String, albumTitle: String? = null): MediaItem? {
        // 1. Try to find in cached songs
        val song = allSongs.firstOrNull { it.id == songId }
        if (song != null) return song.toPlayable(albumTitle)

        // 2. Try to find in favorites
        val favorite = favoritesRepo.favorites.value.firstOrNull { it.id == songId }
        if (favorite != null) return favorite.toPlayable(albumTitle)

        // 3. Try to find in user playlists
        val userSong = userRepo.playlists.value.asSequence()
            .flatMap { it.songs }
            .firstOrNull { it.id == songId }
        if (userSong != null) return userSong.toPlayable(albumTitle)

        return null
    }

    private suspend fun fetchAndSortPublicPlaylists(
        sortBlock: (List<com.soul.neurokaraoke.data.api.ApiPublicPlaylist>) -> List<com.soul.neurokaraoke.data.api.ApiPublicPlaylist>
    ): List<MediaItem> {
        val playlists = karaokeApi.fetchPublicPlaylists().getOrNull() ?: return emptyList()
        return sortBlock(playlists).map { playlist ->
            browsable(
                mediaId = "$PUBLIC_PLAYLIST_PREFIX${playlist.id}",
                title = playlist.name,
                artworkUri = playlist.coverUrl
            )
        }
    }

    fun resumeSong(): Song? {
        val id = playbackPrefs.getString("last_song_id", null) ?: return null
        if (id.isBlank() || id == "radio_live") return null
        return allSongs.firstOrNull { it.id == id }
    }

    private fun Song.toPlayable(albumTitle: String? = null): MediaItem = playable(
        mediaId = id,
        title = title,
        subtitle = "$artist • $coverArtist",
        artworkUri = coverUrl,
        playbackUri = audioUrl.takeIf { it.isNotBlank() } ?: "about:blank",
        albumTitle = albumTitle ?: coverArtist
    )

    private fun browsable(mediaId: String, title: String, artworkUri: String?): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setIsBrowsable(true)
            .setIsPlayable(false)
            .setMediaType(MediaMetadata.MEDIA_TYPE_FOLDER_MIXED)
            .apply {
                if (!artworkUri.isNullOrBlank()) setArtworkUri(Uri.parse(artworkUri))
            }
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
        playbackUri: String,
        albumTitle: String? = null
    ): MediaItem {
        val metadata = MediaMetadata.Builder()
            .setTitle(title)
            .setArtist(subtitle)
            .setAlbumTitle(albumTitle)
            .setIsBrowsable(false)
            .setIsPlayable(true)
            .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
            .apply {
                if (!artworkUri.isNullOrBlank()) setArtworkUri(Uri.parse(artworkUri))
            }
            .build()
        
        val uri = if (playbackUri.isBlank() || playbackUri == "about:blank") {
            Uri.parse("https://idk.neurokaraoke.com/empty.mp3")
        } else {
            Uri.parse(playbackUri)
        }

        return MediaItem.Builder()
            .setMediaId(mediaId)
            .setUri(uri)
            .setRequestMetadata(
                MediaItem.RequestMetadata.Builder()
                    .setMediaUri(uri)
                    .build()
            )
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
        const val MORE_ID = "nk_more"
        const val PUBLIC_PLAYLISTS_ID = "nk_public_playlists"
        const val PUBLIC_PLAYLISTS_NEWEST_ID = "nk_public_playlists_newest"
        const val PUBLIC_PLAYLISTS_OLDEST_ID = "nk_public_playlists_oldest"
        const val PUBLIC_PLAYLISTS_PLAYS_HIGH_ID = "nk_public_playlists_plays_high"
        const val PUBLIC_PLAYLISTS_PLAYS_LOW_ID = "nk_public_playlists_plays_low"
        const val PERSONAL_PLAYLISTS_ID = "nk_personal_playlists"
        const val OFFICIAL_PLAYLISTS_ID = "nk_official_playlists"
        const val PUBLIC_PLAYLIST_PREFIX = "nk_public_playlist_"
        const val PERSONAL_PLAYLIST_PREFIX = "nk_personal_playlist_"
        const val OFFICIAL_PLAYLIST_PREFIX = "nk_official_playlist_"
        const val SONG_URL_PREFIX = "nk_url_"
        const val FAVORITE_SONG_PREFIX = "nk_fav_"
        const val ALL_SONGS_PREFIX = "nk_all_"
        const val NEURO_SONG_PREFIX = "nk_neuro_"
        const val EVIL_SONG_PREFIX = "nk_evil_"
        const val DUET_SONG_PREFIX = "nk_duet_"
        const val PERSONAL_SONG_PREFIX = "nk_personal_song_"
        const val OFFICIAL_SONG_PREFIX = "nk_official_song_"
        const val RESUME_PREFIX = "nk_resume_"

        private const val MAX_BROWSE_ITEMS = 500
        private const val MAX_SEARCH_RESULTS = 100
    }
}
