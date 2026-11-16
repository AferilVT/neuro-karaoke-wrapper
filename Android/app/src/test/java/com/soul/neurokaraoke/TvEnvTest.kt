package com.soul.neurokaraoke

import android.content.res.Configuration
import com.soul.neurokaraoke.ui.tv.isTelevision
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvEnvTest {
    @Test
    fun tv_ui_mode_is_television() {
        assertTrue(isTelevision(Configuration.UI_MODE_TYPE_TELEVISION, false))
    }

    @Test
    fun leanback_feature_forces_television() {
        assertTrue(isTelevision(Configuration.UI_MODE_TYPE_NORMAL, true))
    }

    @Test
    fun phone_is_not_television() {
        assertFalse(isTelevision(Configuration.UI_MODE_TYPE_NORMAL, false))
    }

    @Test
    fun undefined_without_leanback_is_not_television() {
        assertFalse(isTelevision(Configuration.UI_MODE_TYPE_UNDEFINED, false))
    }
}
