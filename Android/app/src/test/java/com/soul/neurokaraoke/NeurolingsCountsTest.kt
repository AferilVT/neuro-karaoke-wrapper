package com.soul.neurokaraoke
import com.soul.neurokaraoke.ui.tv.neurolings.NeurolingsCounts
import org.junit.Assert.*
import org.junit.Test
class NeurolingsCountsTest {
    @Test fun step_clamps() {
        assertEquals(0, NeurolingsCounts.step(0, -1))
        assertEquals(10, NeurolingsCounts.step(10, 1))
        assertEquals(4, NeurolingsCounts.step(3, 1))
    }
    @Test fun serialize_roundtrip() {
        val m = mapOf("Neuron" to 3, "Eviling" to 2, "Weuron" to 0)
        val back = NeurolingsCounts.parse(NeurolingsCounts.serialize(m))
        assertEquals(3, back["Neuron"])
        assertEquals(2, back["Eviling"])
        assertNull(back["Weuron"])       // zero omitted
        assertEquals(5, NeurolingsCounts.total(back))
    }
    @Test fun parse_clamps_and_ignores_unknown() {
        val m = NeurolingsCounts.parse("Neuron=99;Bogus=3;Weuron=-4")
        assertEquals(10, m["Neuron"])
        assertNull(m["Bogus"])
    }
}
