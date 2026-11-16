package com.soul.neurokaraoke
import com.soul.neurokaraoke.ui.tv.neurolings.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MascotManagerTest {
    private fun set(name: String) = MascotSet(name,
        mapOf("Stand" to StayAction("Stand", BorderType.FLOOR,
            listOf(Animation(null, listOf(Pose("/s.png",64,128,0,0,10)))))),
        listOf(Behavior("Stand", 200, false, null, null)), "x")

    @Test fun sync_seeds_and_culls_to_counts() {
        val env = ShimejiEnvironment(0.0, 0.0, 1000.0, 500.0)
        val mgr = MascotManager(mapOf("Neuron" to set("Neuron"), "Eviling" to set("Eviling")), Random(1))
        mgr.sync(mapOf("Neuron" to 3, "Eviling" to 2), env)
        assertEquals(3, mgr.liveCountOf("Neuron"))
        assertEquals(2, mgr.liveCountOf("Eviling"))
        mgr.sync(mapOf("Neuron" to 1), env)     // Eviling -> 0, Neuron -> 1
        assertEquals(1, mgr.liveCountOf("Neuron"))
        assertEquals(0, mgr.liveCountOf("Eviling"))
        assertEquals(1, mgr.snapshot().size)
    }
}
