package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.TvSettingsLogic
import org.junit.Assert.assertEquals
import org.junit.Test

class TvSettingsLogicTest {
    @Test fun step_up_increments() = assertEquals(1, TvSettingsLogic.stepCrossfade(0, 1))
    @Test fun step_down_decrements() = assertEquals(4, TvSettingsLogic.stepCrossfade(5, -1))
    @Test fun step_down_clamps_at_min() = assertEquals(0, TvSettingsLogic.stepCrossfade(0, -1))
    @Test fun step_up_clamps_at_max() = assertEquals(12, TvSettingsLogic.stepCrossfade(12, 1))
    @Test fun label_zero_is_off() = assertEquals("Off", TvSettingsLogic.crossfadeLabel(0))
    @Test fun label_nonzero_has_suffix() = assertEquals("5s", TvSettingsLogic.crossfadeLabel(5))
}
