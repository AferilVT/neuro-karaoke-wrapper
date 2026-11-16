package com.soul.neurokaraoke

import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.FavoritesSync
import com.soul.neurokaraoke.data.repository.PendingFavorite
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritesSyncTest {

    private fun song(id: String, audio: String = "https://storage.neurokaraoke.com/audio/$id.mp3") =
        Song(id = id, title = id, artist = "a", coverUrl = "", audioUrl = audio)

    private fun ids(songs: List<Song>) = songs.map { it.id }

    @Test
    fun removedElsewhere_isDroppedLocally() {
        val merged = FavoritesSync.merge(
            server = listOf(song("a")),
            local = listOf(song("a"), song("b")),
            pending = emptyList()
        )
        assertEquals(listOf("a"), ids(merged))
    }

    @Test
    fun emptyServerList_clearsSyncedFavorites() {
        val merged = FavoritesSync.merge(emptyList(), listOf(song("a")), emptyList())
        assertTrue(merged.isEmpty())
    }

    @Test
    fun unconfirmedAdd_staysAtTheEnd() {
        val pending = listOf(PendingFavorite(song("c"), add = true))
        val merged = FavoritesSync.merge(listOf(song("a"), song("b")), emptyList(), pending)
        assertEquals(listOf("a", "b", "c"), ids(merged))
    }

    @Test
    fun unconfirmedRemove_hidesServerSong() {
        val pending = listOf(PendingFavorite(song("a"), add = false))
        val merged = FavoritesSync.merge(listOf(song("a"), song("b")), emptyList(), pending)
        assertEquals(listOf("b"), ids(merged))
    }

    @Test
    fun serverOrder_wins() {
        val merged = FavoritesSync.merge(
            server = listOf(song("b"), song("a")),
            local = listOf(song("a"), song("b")),
            pending = emptyList()
        )
        assertEquals(listOf("b", "a"), ids(merged))
    }

    @Test
    fun deviceOnlySongs_survive_andAreNeverQueued() {
        val local = song("track.mp3", audio = "content://media/external/audio/1")
        val merged = FavoritesSync.merge(listOf(song("a")), listOf(local), emptyList())
        assertEquals(listOf("a", "track.mp3"), ids(merged))
        assertTrue(FavoritesSync.recordToggle(emptyList(), local, add = true).isEmpty())
    }

    @Test
    fun matchesByAudioUrl_whenIdsDiffer() {
        val pending = listOf(PendingFavorite(song("hash123", audio = "audio/x.mp3"), add = false))
        val merged = FavoritesSync.merge(
            listOf(song("uuid-1", audio = "https://storage.neurokaraoke.com/audio/x.mp3")),
            emptyList(),
            pending
        )
        assertTrue(merged.isEmpty())
    }

    @Test
    fun latestToggle_replacesEarlierOne() {
        var pending = FavoritesSync.recordToggle(emptyList(), song("a"), add = true)
        pending = FavoritesSync.recordToggle(pending, song("a"), add = false)
        assertEquals(1, pending.size)
        assertEquals(false, pending.single().add)
    }

    @Test
    fun migration_queuesOnlyLocalOnlyServerSongs() {
        val device = song("f.mp3", audio = "file:///sdcard/f.mp3")
        val result = FavoritesSync.localOnlyAsPending(
            server = listOf(song("a")),
            local = listOf(song("a"), song("b"), device),
            pending = emptyList()
        )
        assertEquals(listOf("b"), result.map { it.song.id })
        assertTrue(result.all { it.add })
    }
}
