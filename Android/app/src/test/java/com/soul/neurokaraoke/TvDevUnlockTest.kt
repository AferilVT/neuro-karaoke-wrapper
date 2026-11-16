package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.TvDevUnlock
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvDevUnlockTest {
    @Test fun first_tap_needs_six_more() {
        val r = TvDevUnlock.registerTap(emptyList(), 1_000L)
        assertFalse(r.unlocked)
        assertEquals(6, r.remaining)
        assertEquals(listOf(1_000L), r.taps)
    }

    @Test fun seven_rapid_taps_unlock() {
        var taps = emptyList<Long>()
        var last = TvDevUnlock.registerTap(taps, 0L)
        for (i in 1 until 7) last = TvDevUnlock.registerTap(last.taps, i * 100L)
        assertTrue(last.unlocked)
        assertEquals(0, last.remaining)
    }

    @Test fun taps_outside_window_are_dropped() {
        // Six taps land, then a long gap ages them all out; the next tap starts fresh.
        var last = TvDevUnlock.registerTap(emptyList(), 0L)
        for (i in 1 until 6) last = TvDevUnlock.registerTap(last.taps, i * 100L)
        val afterGap = TvDevUnlock.registerTap(last.taps, 10_000L)
        assertFalse(afterGap.unlocked)
        assertEquals(listOf(10_000L), afterGap.taps)
        assertEquals(6, afterGap.remaining)
    }
}
