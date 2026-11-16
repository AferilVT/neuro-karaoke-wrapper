package com.soul.neurokaraoke
import com.soul.neurokaraoke.ui.tv.neurolings.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class MascotBehaviorTest {
    private fun anim(img: String) = Animation(null, listOf(Pose(img, 64, 128, 0, 0, 10)))
    private val set = MascotSet(
        name = "T",
        actions = mapOf(
            "Stand" to StayAction("Stand", BorderType.FLOOR, listOf(anim("/s.png"))),
            "Walk" to MoveAction("Walk", BorderType.FLOOR, listOf(Animation(null, listOf(Pose("/w.png",64,128,-2,0,7))))),
            "ChaseMouse" to StayAction("ChaseMouse", BorderType.FLOOR, listOf(anim("/c.png")))
        ),
        behaviors = listOf(
            Behavior("Stand", 200, false, "#{mascot.environment.floor.isOn(mascot.anchor)}", null),
            Behavior("Walk", 100, false, "#{mascot.environment.floor.isOn(mascot.anchor)}", null),
            // Mouse behavior: condition references cursor -> MISSING -> never chosen
            Behavior("ChaseMouse", 10000, false, "#{mascot.environment.cursor.x > 0}", null)
        ),
        imgDir = "x"
    )

    @Test fun mouse_behavior_never_selected_and_floor_behaviors_are() {
        val env = ShimejiEnvironment(0.0, 0.0, 1000.0, 500.0)
        val m = Mascot(set, 500.0, 500.0, Random(42))   // on the floor (y == bottom)
        val picks = (1..200).map { m.selectBehavior(env, totalCount = 1) }.toSet()
        assertTrue("Stand" in picks)
        assertTrue("Walk" in picks)
        assertFalse("ChaseMouse" in picks)   // cursor condition is MISSING -> false
    }

    @Test fun currentFrameImage_is_non_empty_before_any_tick() {
        val m = Mascot(set, 500.0, 500.0, Random(1))
        assertTrue(m.currentFrameImage().isNotEmpty())
    }

    @Test fun move_walks_toward_target_and_faces_left() {
        // A behavior whose action is a Sequence walking Walk toward a TargetX to the LEFT of the
        // mascot's start. Regression for "everyone walks right into the wall and freezes": the Move
        // must seek the target (go left), flip facing left, and finish when it arrives.
        val walkLeft = MascotSet(
            name = "WL",
            actions = mapOf(
                "GoLeft" to SequenceAction("GoLeft", loop = false,
                    refs = listOf(ActionRef("Walk", mapOf("TargetX" to "100")))),
                "Walk" to MoveAction("Walk", BorderType.FLOOR,
                    listOf(Animation(null, listOf(Pose("/w.png", 64, 128, -2, 0, 1))))),
                "Stand" to StayAction("Stand", BorderType.FLOOR, listOf(anim("/s.png")))
            ),
            behaviors = listOf(Behavior("GoLeft", 100, false, null, null)),
            imgDir = "x"
        )
        val env = ShimejiEnvironment(0.0, 0.0, 1000.0, 500.0)
        val m = Mascot(walkLeft, startX = 500.0, startY = 500.0, rng = Random(3))
        repeat(400) { m.tick(env, totalCount = 1) }
        assertTrue("should have moved left toward 100 (was ${m.x})", m.x <= 101.0)
        assertFalse("should face left while walking left", m.lookRight)
    }

    @Test fun stuck_midair_mascot_falls_to_recover() {
        // A pack whose only behavior requires being on the floor. Dropped mid-air, the mascot can
        // select no behavior; rather than freezing (the "climbs the wall, crosses the ceiling, then
        // bugs out" report), it must fall back to the floor. Also exercises that the embedded Fall
        // action actually applies gravity (className is the full com.group_finity... path).
        val set = MascotSet(
            name = "R",
            actions = mapOf(
                "Stand" to StayAction("Stand", BorderType.FLOOR, listOf(anim("/s.png"))),
                "Fall" to EmbeddedAction("Fall", "com.group_finity.mascot.action.Fall")
            ),
            behaviors = listOf(
                Behavior("Stand", 100, false, "#{mascot.environment.floor.isOn(mascot.anchor)}", null)
            ),
            imgDir = "x"
        )
        val env = ShimejiEnvironment(0.0, 0.0, 1000.0, 500.0)
        val m = Mascot(set, startX = 500.0, startY = 100.0, rng = Random(1)) // mid-air
        val y0 = m.y
        repeat(30) { m.tick(env, totalCount = 1) }
        assertTrue("should have fallen from $y0 toward the floor (now ${m.y})", m.y > y0)
    }

    @Test fun cyclic_loop_sequence_does_not_stack_overflow() {
        // Action "A" is a Sequence that loops forever by referencing only itself. Nothing in
        // this pack ever terminates naturally -- this reproduces the pathological case where a
        // mascot pack's action graph is (accidentally or maliciously) cyclic.
        val cyclicSet = MascotSet(
            name = "Cyclic",
            actions = mapOf(
                "A" to SequenceAction("A", loop = true, refs = listOf(ActionRef("A", emptyMap())))
            ),
            behaviors = listOf(
                Behavior("A", 100, false, null, null)
            ),
            imgDir = "x"
        )
        val env = ShimejiEnvironment(0.0, 0.0, 1000.0, 500.0)
        val m = Mascot(cyclicSet, 500.0, 500.0, Random(7))
        // Must not throw StackOverflowError (or anything else); the interpreter should degrade
        // gracefully by abandoning the unresolvable behavior and trying again next tick.
        for (i in 0 until 5) {
            m.tick(env, totalCount = 1)
        }
    }
}
