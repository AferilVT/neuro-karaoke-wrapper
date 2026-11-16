package com.soul.neurokaraoke
import com.soul.neurokaraoke.ui.tv.neurolings.*
import org.junit.Assert.*
import org.junit.Test
class ShimejiPhysicsTest {
    @Test fun gravity_accumulates_and_clamps() {
        var v = ShimejiPhysics.Vel(0.0, 0.0)
        repeat(100) { v = ShimejiPhysics.fallStep(v) }
        assertTrue(v.y <= ShimejiPhysics.MAX_VEL_Y + 1e-9)
        assertTrue(v.y > 0.0)
    }
    @Test fun floor_detection() {
        val env = ShimejiEnvironment(0.0, 0.0, 1920.0, 1080.0)
        assertTrue(env.onFloor(Anchor(500.0, 1080.0)))
        assertFalse(env.onFloor(Anchor(500.0, 500.0)))
        assertTrue(env.onRightWall(Anchor(1920.0, 500.0)))
    }
}
