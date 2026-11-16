package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.TvQueryEditor
import org.junit.Assert.assertEquals
import org.junit.Test

class TvQueryEditorTest {
    @Test fun append_adds_char() = assertEquals("lo", TvQueryEditor.append("l", 'o'))
    @Test fun space_adds_space() = assertEquals("a ", TvQueryEditor.space("a"))
    @Test fun backspace_removes_last() = assertEquals("lov", TvQueryEditor.backspace("love"))
    @Test fun backspace_on_empty_is_empty() = assertEquals("", TvQueryEditor.backspace(""))
}
