package com.soul.neurokaraoke.data.repository

import com.soul.neurokaraoke.data.model.Song

/**
 * A favorite change made on this device that the server hasn't confirmed yet.
 * [add] is the state the user wants; [attempts] counts tries where the song's
 * server id couldn't be resolved.
 */
data class PendingFavorite(val song: Song, val add: Boolean, val attempts: Int = 0)

/**
 * Pure merge rules for favorites sync (issue #25). The server list is the source of
 * truth for signed-in users; this device only layers its unconfirmed changes on top,
 * plus device-only songs (local files), which the server can't hold.
 */
object FavoritesSync {

    /** Give up resolving a song's server id after this many syncs. */
    const val MAX_RESOLVE_ATTEMPTS = 5

    /** Songs from the device's own storage (local music) never go to the server. */
    fun isDeviceOnly(song: Song): Boolean =
        song.audioUrl.startsWith("file:") || song.audioUrl.startsWith("content:")

    fun normalizeAudioUrl(url: String): String = url
        .removePrefix("https://storage.neurokaraoke.com/")
        .removePrefix("http://storage.neurokaraoke.com/")

    fun sameSong(a: Song, b: Song): Boolean {
        if (a.id == b.id) return true
        val ua = normalizeAudioUrl(a.audioUrl)
        val ub = normalizeAudioUrl(b.audioUrl)
        return ua.isNotBlank() && ua == ub
    }

    /** Record a toggle. A newer change to the same song replaces the older one. */
    fun recordToggle(pending: List<PendingFavorite>, song: Song, add: Boolean): List<PendingFavorite> {
        val others = pending.filterNot { sameSong(it.song, song) }
        return if (isDeviceOnly(song)) others else others + PendingFavorite(song, add)
    }

    /**
     * The list to show after a successful server fetch: server order first, then songs
     * added here that the server hasn't confirmed yet, then device-only songs.
     * Anything else that was only on this device is dropped, because it was removed elsewhere.
     */
    fun merge(server: List<Song>, local: List<Song>, pending: List<PendingFavorite>): List<Song> {
        val removals = pending.filter { !it.add }.map { it.song }
        val kept = server.filterNot { s -> removals.any { sameSong(it, s) } }
        val unconfirmedAdds = pending.filter { it.add }.map { it.song }
            .filterNot { p -> kept.any { sameSong(it, p) } }
        val deviceOnly = local.filter(::isDeviceOnly)
        return dedupe(kept + unconfirmedAdds + deviceOnly)
    }

    /**
     * One-time migration: favorites that older builds kept only on this device (never sent
     * to the server) become pending adds, so the first server-wins sync uploads them
     * instead of deleting them.
     */
    fun localOnlyAsPending(
        server: List<Song>,
        local: List<Song>,
        pending: List<PendingFavorite>
    ): List<PendingFavorite> {
        val newOnes = local
            .filterNot(::isDeviceOnly)
            .filterNot { l -> server.any { sameSong(it, l) } }
            .filterNot { l -> pending.any { sameSong(it.song, l) } }
            .map { PendingFavorite(it, add = true) }
        return pending + newOnes
    }

    /** Drop repeats (same id, or same audio file under different ids). */
    fun dedupe(songs: List<Song>): List<Song> {
        val seenIds = HashSet<String>()
        val seenAudio = HashSet<String>()
        return songs.filter { song ->
            val audio = normalizeAudioUrl(song.audioUrl)
            if (song.id in seenIds || (audio.isNotBlank() && audio in seenAudio)) return@filter false
            seenIds += song.id
            if (audio.isNotBlank()) seenAudio += audio
            true
        }
    }
}
