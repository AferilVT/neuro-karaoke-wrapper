package com.soul.neurokaraoke

import com.soul.neurokaraoke.data.api.LyricsApi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsSongIdTest {
    private val uuid = "04be8ac3-6309-45db-91ed-4eef7ad26f15"

    @Test
    fun strips_radio_prefix() {
        assertEquals(uuid, LyricsApi.neuroSongIdFromLocalId("radio_$uuid"))
    }

    @Test
    fun keeps_bare_uuid() {
        assertEquals(uuid, LyricsApi.neuroSongIdFromLocalId(uuid))
    }

    @Test
    fun null_for_hash_or_short_ids() {
        assertNull(LyricsApi.neuroSongIdFromLocalId("radio_123"))
        assertNull(LyricsApi.neuroSongIdFromLocalId("-1875421954"))
        assertNull(LyricsApi.neuroSongIdFromLocalId(""))
    }
}
