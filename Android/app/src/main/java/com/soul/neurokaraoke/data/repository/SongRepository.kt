package com.soul.neurokaraoke.data.repository

import com.soul.neurokaraoke.data.api.ApiSong
import com.soul.neurokaraoke.data.api.NeuroKaraokeApi
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.util.EnglishTitleMap
import com.soul.neurokaraoke.data.util.RomajiUtil

class SongRepository(
    private val api: NeuroKaraokeApi = NeuroKaraokeApi()
) {
    /**
     * Fetch songs from a playlist, resolving server UUIDs for each song.
     */
    suspend fun getPlaylistSongs(playlistId: String): Result<List<Song>> {
        val result = api.fetchPlaylist(playlistId)
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)

        val apiSongs = result.getOrThrow()
        // Build the server ID map once (lazy, cached after first call)
        api.ensureSongIdMap()

        val songs = apiSongs.mapIndexed { index, apiSong ->
            val song = apiSong.toSong(playlistId, index)
            // Replace hash-based ID with server UUID if available
            val serverId = if (song.audioUrl.isNotBlank()) {
                api.findSongIdByAudioUrl(song.audioUrl)
            } else null
            if (serverId != null) song.copy(id = serverId) else song
        }
        return Result.success(songs)
    }

    /**
     * Fetch every song from server via POST /api/songs.
     * Each item already carries server UUID — no songIdMap lookup needed.
     */
    suspend fun getAllSongs(): Result<List<Song>> {
        val result = api.fetchAllSongs()
        if (result.isFailure) return Result.failure(result.exceptionOrNull()!!)
        val entries = result.getOrThrow()
        val songs = entries.mapIndexed { index, entry ->
            entry.apiSong.toSong(playlistId = "all", index = index).copy(
                id = entry.id,
                duration = entry.durationSeconds * 1000L
            )
        }
        return Result.success(songs)
    }

    /**
     * Find a specific song in a playlist
     */
    fun findSong(playlistId: String, title: String, artist: String? = null): Song? {
        return api.findSong(playlistId, title, artist)?.toSong(playlistId, 0)
    }

    private fun ApiSong.toSong(playlistId: String, index: Int): Song {
        // Singer is only a theme/filter category. Display text comes from the API.
        val singer = Singer.fromCoverArtists(coverArtists)

        // Fallback ID if server UUID can't be resolved
        val fallbackId = audioUrl?.hashCode()?.toString() ?: "${playlistId}_$index"

        val songTitle = title
        val songArtist = originalArtists ?: "Unknown Artist"

        return Song(
            id = fallbackId,
            title = songTitle,
            artist = songArtist,
            coverUrl = getCoverArtUrl() ?: "",
            audioUrl = audioUrl ?: "",
            duration = 0L,
            singer = singer,
            coverArtists = coverArtists.orEmpty(),
            artCredit = artCredit?.takeIf { it.isNotBlank() },
            titleRomaji = RomajiUtil.toRomaji(songTitle),
            titleEnglish = EnglishTitleMap.getEnglishTitle(songTitle),
            artistRomaji = RomajiUtil.toRomaji(songArtist)
        )
    }
}
