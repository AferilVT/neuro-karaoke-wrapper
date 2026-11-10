package com.soul.neurokaraoke

import com.soul.neurokaraoke.data.api.UploadedSong
import com.soul.neurokaraoke.data.api.UserSongsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class UserSongsApiTest {

    private fun song(id: String, title: String, date: String? = null) = UploadedSong(id, title, date)

    @Test
    fun `no new entry returns null`() {
        val before = setOf("a", "b")
        val after = listOf(song("a", "One"), song("b", "Two"))
        assertNull(UserSongsApi.findNewUpload(before, after, "Three"))
    }

    @Test
    fun `single new entry is picked even if the server renamed it`() {
        val after = listOf(song("a", "One"), song("new", "three (server title)"))
        assertEquals("new", UserSongsApi.findNewUpload(setOf("a"), after, "Three")?.id)
    }

    @Test
    fun `several new entries prefer a title match`() {
        val after = listOf(
            song("x", "Other", "2026-11-10T10:00:00"),
            song("y", "My Song", "2026-11-10T09:00:00")
        )
        assertEquals("y", UserSongsApi.findNewUpload(emptySet(), after, " my song ")?.id)
    }

    @Test
    fun `several new entries without title match pick the newest`() {
        val after = listOf(
            song("x", "A", "2026-11-10T09:00:00"),
            song("y", "B", "2026-11-10T11:00:00"),
            song("z", "C", null)
        )
        assertEquals("y", UserSongsApi.findNewUpload(emptySet(), after, "Nope")?.id)
    }
}
