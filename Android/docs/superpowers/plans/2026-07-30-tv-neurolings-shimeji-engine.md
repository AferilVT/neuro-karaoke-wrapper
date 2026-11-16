# TV Neurolings — Shimeji Engine Port Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Port the Shimeji-ee desktop-pet engine to Kotlin, driven by the 6 bundled NeurolingsCE mascot packs, so the dev-only Neurolings overlay animates the real sprite frames via the packs' own behavior/action data, with a per-character population stepper (0–10 each) in Developer Options.

**Architecture:** New `ui/tv/neurolings/` package. A pure EL-subset expression evaluator and a DOM-based XML loader turn each pack's `actions.xml`/`behaviors.xml` into an immutable model. A `Mascot` runs the action interpreter + weighted-random behavior state machine + gravity against a screen-derived environment; a `MascotManager` seeds/culls mascots to match per-character counts and produces a render snapshot each 25 fps tick. `TvNeurolings` (rewritten) decodes frames once and renders the snapshot in Compose. Mouse/IE and inter-mascot behaviors are inert (their conditions never pass).

**Tech Stack:** Kotlin, Jetpack Compose, `javax.xml` DOM (JVM-testable parsing), Android `BitmapFactory`→`ImageBitmap`, JUnit4.

## Global Constraints

- Build env (bash): `export JAVA_HOME="/c/Program Files/Android/Android Studio/jbr"`, `export ANDROID_HOME="/c/Users/Aferil/AppData/Local/Android/Sdk"`; run gradle from `Android/`.
- Compile gate: `./gradlew :app:assembleDebug`. Unit-test gate: `./gradlew :app:testDebugUnitTest`.
- Assets already bundled at `app/src/main/assets/mascots/<Name>/{actions.xml,behaviors.xml,img/*.png,info.json}` for the 6 characters: **Neuron, Weuron, Eviling, Vedaling, Cerber, Tuteling** (committed `637074c`). Do NOT re-add them.
- Shimeji timing: **1 tick = 40 ms (25 fps)**. Pose `Velocity` is px/tick; Pose `Duration` is a tick count. Sprites are authored **facing left**; mirror horizontally when facing right.
- Physics constants (Shimeji-standard, consistent with the packs' `InitialVX/VY` magnitudes of ±5..±20): `GRAVITY = 2.0` px/tick² added to `velY` each tick while unsupported; air drag `DRAG_X = 0.05`, `DRAG_Y = 0.1` applied multiplicatively (`v *= (1-drag)`) before adding gravity; terminal `MAX_VEL_Y = 20.0`. On reaching the floor border the `Fall` action sequence hands off to its `Bouncing`→`Stand` children per the pack XML.
- Faithful-but-inert: any expression referencing `mascot.environment.cursor.*`, `mascot.environment.activeIE.*`, or `target.*` resolves to the MISSING sentinel; comparisons/`isOn`/boolean use of MISSING is **false**, so mouse/IE/inter-mascot behaviors are never selected. This is intended, not a bug.
- Dev-only: gated behind the existing 7-tap Developer Options. Overlay is non-focusable and input-transparent.
- Per-character count range: **0..10**, default **0**.

---

### Task 1: `ShimejiExpr` — pure EL-subset evaluator

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/ShimejiExpr.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/ShimejiExprTest.kt`

**Interfaces:**
- Produces:
  - `object ShimejiExpr`
  - `val MISSING: Any` — sentinel for unresolved cursor/IE/target references.
  - `interface Scope { fun variable(path: String): Any?; fun method(path: String, args: List<Any?>): Any? }` — `variable` returns `Double`, `Boolean`, an opaque object, `null`, or `ShimejiExpr.MISSING`; `method` handles calls like `path="mascot.environment.floor.isOn"` with evaluated `args`.
  - `fun eval(expr: String, scope: Scope): Any?`
  - `fun evalDouble(expr: String, scope: Scope, default: Double = 0.0): Double`
  - `fun evalBoolean(expr: String, scope: Scope): Boolean` — MISSING → false.

**Algorithm:** Recursive-descent parser + evaluator. Strip a leading `${` or `#{` and trailing `}` if present (treat both spellings identically). Grammar (precedence low→high): ternary `?:` → `||` → `&&` → equality `== !=` → comparison `< <= > >=` → additive `+ -` → multiplicative `* /` → unary `! -` → primary. Primary: number literal, `true`/`false`, parenthesized expr, `Math.random()` (returns `Math.random()`), `Math.abs(x)`, or a dotted identifier optionally followed by `(args)`. A dotted identifier `a.b.c` with no call → `scope.variable("a.b.c")`; with a call `a.b.c(x, y)` → `scope.method("a.b.c", listOf(evaluated args))`. Numeric ops coerce operands to `Double`; if any operand is MISSING or non-numeric, an arithmetic/comparison result is MISSING (comparisons involving MISSING → **false** in boolean context). `evalBoolean` maps MISSING and null → false; `Boolean` passes through; nonzero Double → true.

- [ ] **Step 1: Write the failing test**

```kotlin
package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiExpr
import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiExpr.MISSING
import org.junit.Assert.*
import org.junit.Test

class ShimejiExprTest {
    private fun scope(vars: Map<String, Any?> = emptyMap(),
                      methods: Map<String, Any?> = emptyMap()) = object : ShimejiExpr.Scope {
        override fun variable(path: String): Any? = if (path in vars) vars[path] else MISSING
        override fun method(path: String, args: List<Any?>): Any? = methods[path] ?: MISSING
    }

    @Test fun arithmetic_precedence() =
        assertEquals(7.0, ShimejiExpr.evalDouble("1 + 2 * 3", scope()), 1e-9)

    @Test fun duration_expression_range() {
        val v = ShimejiExpr.evalDouble("500+Math.random()*1000", scope())
        assertTrue(v in 500.0..1500.0)
    }

    @Test fun ternary_on_lookRight() =
        assertEquals(-10.0, ShimejiExpr.evalDouble("mascot.lookRight ? -10 : 10",
            scope(vars = mapOf("mascot.lookRight" to true))), 1e-9)

    @Test fun comparison_true() =
        assertTrue(ShimejiExpr.evalBoolean("mascot.totalCount < 50",
            scope(vars = mapOf("mascot.totalCount" to 3.0))))

    @Test fun isOn_method_true() =
        assertTrue(ShimejiExpr.evalBoolean("mascot.environment.floor.isOn(mascot.anchor)",
            scope(vars = mapOf("mascot.anchor" to Any()),
                  methods = mapOf("mascot.environment.floor.isOn" to true))))

    @Test fun cursor_reference_is_false() =
        assertFalse(ShimejiExpr.evalBoolean("mascot.environment.cursor.y < mascot.environment.screen.height/2",
            scope(vars = mapOf("mascot.environment.screen.height" to 1080.0))))

    @Test fun logical_and_or_not() {
        val s = scope(vars = mapOf("a" to true, "b" to false))
        assertTrue(ShimejiExpr.evalBoolean("a && !b", s))
        assertFalse(ShimejiExpr.evalBoolean("a && b", s))
        assertTrue(ShimejiExpr.evalBoolean("a || b", s))
    }

    @Test fun hashbrace_and_dollarbrace_stripped() =
        assertEquals(4.0, ShimejiExpr.evalDouble("\${2+2}", scope()), 1e-9)
}
```

- [ ] **Step 2: Run test to verify it fails** — `./gradlew :app:testDebugUnitTest --tests "com.soul.neurokaraoke.ShimejiExprTest"` → FAIL (unresolved `ShimejiExpr`).

- [ ] **Step 3: Implement `ShimejiExpr.kt`** per the Algorithm above. Pure Kotlin, no Android imports.

- [ ] **Step 4: Run test to verify it passes** (8 tests PASS).

- [ ] **Step 5: Commit** — `git commit -m "feat(tv): Shimeji EL-subset expression evaluator"`

---

### Task 2: `ShimejiModel` + `ShimejiXmlLoader` — data model & pack parsing

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/ShimejiModel.kt`
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/ShimejiXmlLoader.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/ShimejiXmlLoaderTest.kt`

**Interfaces:**
- Consumes: nothing (model is standalone; the loader takes raw XML streams).
- Produces (`ShimejiModel.kt`):
  ```kotlin
  data class Pose(val image: String, val anchorX: Int, val anchorY: Int,
                  val velX: Int, val velY: Int, val duration: Int)
  data class Animation(val condition: String?, val poses: List<Pose>)
  enum class BorderType { FLOOR, WALL, CEILING, NONE }
  data class ActionRef(val name: String, val overrides: Map<String, String>) // Duration/InitialVX/InitialVY/Condition
  sealed interface ShimejiAction { val name: String? }
  data class StayAction(override val name: String?, val border: BorderType, val animations: List<Animation>) : ShimejiAction
  data class MoveAction(override val name: String?, val border: BorderType, val animations: List<Animation>) : ShimejiAction
  data class AnimateAction(override val name: String?, val border: BorderType, val animations: List<Animation>) : ShimejiAction
  data class SequenceAction(override val name: String?, val loop: Boolean, val refs: List<ActionRef>) : ShimejiAction
  data class SelectAction(override val name: String?, val children: List<Pair<String?, ActionRef>>) : ShimejiAction // (condition, ref)
  data class EmbeddedAction(override val name: String?, val className: String) : ShimejiAction
  data class BehaviorRef(val name: String, val frequency: Int, val condition: String?, val hidden: Boolean)
  data class NextBehaviorList(val add: Boolean, val refs: List<BehaviorRef>)
  data class Behavior(val name: String, val frequency: Int, val hidden: Boolean,
                      val condition: String?, val next: NextBehaviorList?)
  data class MascotSet(val name: String, val actions: Map<String, ShimejiAction>,
                       val behaviors: List<Behavior>, val imgDir: String)
  ```
  Notes: flatten `behaviors.xml` `<Condition>` groups by pushing each group's condition onto its child behaviors' own `condition` (AND-combine if a child already has one, joined `"(a) && (b)"`). Inline anonymous `<Action>`s inside Sequence/Select get synthesized unique names.
- Produces (`ShimejiXmlLoader.kt`):
  ```kotlin
  object ShimejiXmlLoader {
      fun parse(name: String, imgDir: String, actionsXml: java.io.InputStream, behaviorsXml: java.io.InputStream): MascotSet
  }
  ```
  Uses `javax.xml.parsers.DocumentBuilderFactory` (JVM + Android). `Pose` velocity `"x,y"` and `ImageAnchor "x,y"` split on `,`. `Duration`/anchor/velocity parsed as Int (a Duration that is an EL expression stays on the owning `ActionRef.overrides`, not on inline poses — inline poses always have integer durations in these packs).

- [ ] **Step 1: Write the failing test** — parse a small inline fixture and assert structure:

```kotlin
package com.soul.neurokaraoke

import com.soul.neurokaraoke.ui.tv.neurolings.*
import org.junit.Assert.*
import org.junit.Test

class ShimejiXmlLoaderTest {
    private val actions = """
      <Mascot><ActionList>
        <Action Name="Stand" Type="Stay" BorderType="Floor">
          <Animation><Pose Image="/a.png" ImageAnchor="64,128" Velocity="0,0" Duration="150"/></Animation>
        </Action>
        <Action Name="Walk" Type="Move" BorderType="Floor">
          <Animation><Pose Image="/w1.png" ImageAnchor="64,128" Velocity="-2,0" Duration="7"/></Animation>
        </Action>
        <Action Name="Fall" Type="Embedded" Class="com.group_finity.mascot.action.Fall"/>
      </ActionList></Mascot>""".trimIndent()

    private val behaviors = """
      <Mascot><BehaviorList>
        <Behavior Name="Fall" Frequency="0" Hidden="true"/>
        <Condition Condition="#{mascot.environment.floor.isOn(mascot.anchor)}">
          <Behavior Name="Stand" Frequency="200"/>
          <Behavior Name="Walk" Frequency="100"/>
        </Condition>
      </BehaviorList></Mascot>""".trimIndent()

    @Test fun parses_actions_and_behaviors() {
        val set = ShimejiXmlLoader.parse("Test", "mascots/Test/img",
            actions.byteInputStream(), behaviors.byteInputStream())
        assertEquals(3, set.actions.size)
        val walk = set.actions["Walk"] as MoveAction
        assertEquals(-2, walk.animations[0].poses[0].velX)
        assertEquals(BorderType.FLOOR, walk.border)
        val stand = set.behaviors.first { it.name == "Stand" }
        assertEquals(200, stand.frequency)
        // Condition-group condition was pushed onto the child behavior:
        assertTrue(stand.condition!!.contains("floor.isOn"))
        val fall = set.actions["Fall"] as EmbeddedAction
        assertTrue(fall.className.endsWith("Fall"))
    }
}
```

- [ ] **Step 2: Run test → FAIL.**
- [ ] **Step 3: Implement `ShimejiModel.kt` then `ShimejiXmlLoader.kt`** per the interfaces/notes.
- [ ] **Step 4: Run test → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(tv): Shimeji pack model + DOM XML loader"`

---

### Task 3: `ShimejiPhysics` + `ShimejiEnvironment` + `NeurolingsCounts`

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/ShimejiPhysics.kt`
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/ShimejiEnvironment.kt`
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/NeurolingsCounts.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/ShimejiPhysicsTest.kt`, `app/src/test/java/com/soul/neurokaraoke/NeurolingsCountsTest.kt`

**Interfaces:**
- `ShimejiPhysics.kt`:
  ```kotlin
  object ShimejiPhysics {
      const val TICK_MS = 40L
      const val GRAVITY = 2.0
      const val DRAG_X = 0.05
      const val DRAG_Y = 0.1
      const val MAX_VEL_Y = 20.0
      data class Vel(val x: Double, val y: Double)
      /** One falling integration step: apply drag then gravity, clamp vy to terminal. */
      fun fallStep(v: Vel): Vel
  }
  ```
  `fallStep`: `vx' = v.x*(1-DRAG_X)`, `vy' = min(v.y*(1-DRAG_Y) + GRAVITY, MAX_VEL_Y)`.
- `ShimejiEnvironment.kt`:
  ```kotlin
  data class Anchor(val x: Double, val y: Double)
  /** Screen/work-area rect. Borders: floor=bottom, ceiling=top, walls=left|right. */
  class ShimejiEnvironment(val left: Double, val top: Double, val right: Double, val bottom: Double) {
      val width get() = right - left
      val height get() = bottom - top
      fun onFloor(a: Anchor, eps: Double = 1.0): Boolean   // a.y >= bottom - eps
      fun onCeiling(a: Anchor, eps: Double = 1.0): Boolean
      fun onLeftWall(a: Anchor, eps: Double = 1.0): Boolean
      fun onRightWall(a: Anchor, eps: Double = 1.0): Boolean
      fun onWall(a: Anchor, eps: Double = 1.0): Boolean     // left or right
  }
  ```
- `NeurolingsCounts.kt`:
  ```kotlin
  object NeurolingsCounts {
      const val MIN = 0
      const val MAX = 10
      val CHARACTERS = listOf("Neuron", "Weuron", "Eviling", "Vedaling", "Cerber", "Tuteling")
      fun clamp(n: Int): Int
      fun step(current: Int, delta: Int): Int          // clamp(current+delta)
      fun serialize(counts: Map<String, Int>): String  // "Name=count;..." only for characters with count>0
      fun parse(s: String?): Map<String, Int>          // inverse; clamps; ignores unknown names
      fun total(counts: Map<String, Int>): Int
  }
  ```

- [ ] **Step 1: Write failing tests**

`ShimejiPhysicsTest.kt`:
```kotlin
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
```
`NeurolingsCountsTest.kt`:
```kotlin
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
```

- [ ] **Step 2: Run both tests → FAIL.**
- [ ] **Step 3: Implement the three files** per interfaces.
- [ ] **Step 4: Run tests → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(tv): Shimeji physics, environment borders, Neurolings counts"`

---

### Task 4: `Mascot` — action interpreter + behavior state machine + gravity

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/Mascot.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/MascotBehaviorTest.kt`

**Interfaces:**
- Consumes: `ShimejiModel`, `ShimejiExpr`, `ShimejiPhysics`, `ShimejiEnvironment` (Tasks 1–3).
- Produces:
  ```kotlin
  class Mascot(val set: MascotSet, startX: Double, startY: Double, private val rng: kotlin.random.Random) {
      var x: Double; var y: Double           // anchor position (foot point)
      var lookRight: Boolean
      fun currentFrameImage(): String        // image path of the current pose
      fun currentAnchor(): Pair<Int, Int>
      fun tick(env: ShimejiEnvironment, totalCount: Int)   // advance one 40ms tick
      // Exposed for tests:
      var currentBehaviorName: String?
      fun selectBehavior(env: ShimejiEnvironment, totalCount: Int): String?  // weighted-random pick honoring conditions
  }
  ```
- **Behavior selection algorithm:** build the candidate list = the current `NextBehaviorList` (if the finished behavior had one and `add=false` use only it; if `add=true` merge with the global list) else the global `behaviors`. Keep only candidates whose `condition` is null or `ShimejiExpr.evalBoolean(condition, scope)` is true, and `frequency > 0` (hidden+freq 0 are event-only; never randomly picked). Weighted-random pick by `frequency` using `rng`. The `Scope` resolves: `mascot.anchor` → this mascot's `Anchor`; `mascot.lookRight`/`mascot.totalCount`; `mascot.environment.{floor,ceiling,wall,workArea.*Border}` → objects whose `.isOn(anchor)` maps to the matching `env.onX`; `mascot.environment.screen.height` → `env.height`; cursor/IE/target → MISSING.
- **Action interpreter:** map the selected behavior name → `set.actions[name]`. Run it:
  - `Stay`/`Animate`: step poses by their `duration` ticks; `Stay` loops until the behavior's own duration elapses (a `Stay` referenced from a `Sequence` gets a `Duration` override evaluated once via `ShimejiExpr`), `Animate` plays once.
  - `Move`: same pose stepping, plus each tick add `velX` (sign-flipped when `lookRight`) to `x` and `velY` to `y`; set `lookRight` from velocity sign; stop at the action's border or screen edge.
  - `Sequence`: run child refs in order (resolve `ActionRef.name`→action, apply `Duration`/`InitialVX/VY` overrides); `loop` restarts.
  - `Select`: first child whose `condition` passes (via `ShimejiExpr`).
  - `Embedded Fall`: apply `ShimejiPhysics.fallStep` each tick, integrate position, until `env.onFloor` → finish (the behavior chain/`Fall` action's own Sequence then transitions). Other embedded (`Jump` uses InitialVX/VY + gravity; `Look`/`Offset` set facing/position; `Dragged`/`ThrowIE`/`WalkWithIE`/`Breed`/`Interact`/`ScanMove` → no-op finish, since they need cursor/IE/target which are MISSING).
  - Gravity guard: at the start of a tick, if the current action's border is `FLOOR` but the mascot is not `env.onFloor`, switch to the `Fall` behavior.

**Note for implementer:** this is the largest unit. Keep the pose-stepping and the behavior-selection in separate private methods. The test below pins only the deterministic, pure-ish selection surface; full animation is verified on the emulator in Task 6.

- [ ] **Step 1: Write the failing test** (seeded, deterministic selection over a tiny hand-built `MascotSet`):

```kotlin
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
}
```

- [ ] **Step 2: Run test → FAIL.**
- [ ] **Step 3: Implement `Mascot.kt`** per the algorithms.
- [ ] **Step 4: Run test → PASS.**
- [ ] **Step 5: Commit** — `git commit -m "feat(tv): Mascot action interpreter + behavior state machine"`

---

### Task 5: `MascotAssets` + `MascotManager` — frame cache, seeding, snapshot

**Files:**
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/MascotAssets.kt`
- Create: `app/src/main/java/com/soul/neurokaraoke/ui/tv/neurolings/MascotManager.kt`
- Test: `app/src/test/java/com/soul/neurokaraoke/MascotManagerTest.kt`

**Interfaces:**
- `MascotAssets.kt` (Android):
  ```kotlin
  class MascotAssets(private val assets: android.content.res.AssetManager) {
      /** Load & parse a pack from assets/mascots/<name>/. */
      fun loadSet(name: String): MascotSet
      /** Decode a frame PNG (cached) as an ImageBitmap. image is like "/shime1.png". */
      fun frame(setName: String, image: String): androidx.compose.ui.graphics.ImageBitmap
  }
  ```
  `loadSet` opens `mascots/<name>/actions.xml` and `.../behaviors.xml` via `assets.open(...)` and calls `ShimejiXmlLoader.parse`. `frame` decodes `mascots/<name>/img/<file>` with `BitmapFactory` once and caches by `"$setName$image"`.
- `MascotManager.kt` (pure enough to test the seeding/culling):
  ```kotlin
  data class MascotRender(val setName: String, val image: String,
                          val x: Double, val y: Double, val anchorX: Int, val anchorY: Int, val mirrored: Boolean)
  class MascotManager(private val sets: Map<String, MascotSet>, private val rng: kotlin.random.Random = Random.Default) {
      /** Add/remove mascots so each character's live count matches `counts` (clamped 0..10). */
      fun sync(counts: Map<String, Int>, env: ShimejiEnvironment)
      fun tick(env: ShimejiEnvironment)
      fun snapshot(): List<MascotRender>
      fun liveCountOf(setName: String): Int   // for tests
  }
  ```
  `sync`: for each character, if live < requested, spawn new `Mascot`s at a random x on the floor; if live > requested, remove extras. `tick`: `mascot.tick(env, totalLiveCount)` for all. `snapshot`: map each to its current frame + position + mirror (`mirrored = lookRight`).

- [ ] **Step 1: Write the failing test** (seeding/culling with fake sets, no Android):

```kotlin
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
```

- [ ] **Step 2: Run test → FAIL.**
- [ ] **Step 3: Implement `MascotManager.kt` then `MascotAssets.kt`.** (Manager first so the test compiles without Android; Assets is thin Android glue.)
- [ ] **Step 4: Run test → PASS**, then `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL (compiles the Android `MascotAssets`).
- [ ] **Step 5: Commit** — `git commit -m "feat(tv): mascot asset loader + manager (seed/cull/snapshot)"`

---

### Task 6: Settings counts + Dev Options steppers + renderer integration

**Files:**
- Modify: `app/src/main/java/com/soul/neurokaraoke/data/repository/SettingsRepository.kt`
- Modify: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvSettingsScreen.kt`
- Rewrite: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvNeurolings.kt`
- Modify: `app/src/main/java/com/soul/neurokaraoke/ui/tv/TvApp.kt`

**Interfaces:**
- Consumes: `NeurolingsCounts`, `MascotAssets`, `MascotManager`, `ShimejiEnvironment`, `ShimejiPhysics.TICK_MS` (Tasks 3/5).
- Produces: updated `SettingsRepository` API:
  ```kotlin
  // REMOVE: neurolingsEnabled / setNeurolingsEnabled / KEY_NEUROLINGS boolean.
  // ADD:
  val neurolingsCounts: StateFlow<Map<String, Int>>
  fun setNeurolingsCount(name: String, count: Int)   // clamps via NeurolingsCounts.clamp; persists via NeurolingsCounts.serialize under KEY_NEUROLINGS_COUNTS
  // setDevOptionsUnlocked(false) must reset counts to empty (all zero).
  ```

- [ ] **Step 1: SettingsRepository — replace boolean with counts.**
  - Remove `_neurolingsEnabled`/`neurolingsEnabled`/`setNeurolingsEnabled`/`KEY_NEUROLINGS`.
  - Add `private val _neurolingsCounts = MutableStateFlow<Map<String,Int>>(emptyMap())` + `val neurolingsCounts = _neurolingsCounts.asStateFlow()`.
  - In `initialize`: `_neurolingsCounts.value = NeurolingsCounts.parse(p.getString(KEY_NEUROLINGS_COUNTS, null))`.
  - Add `fun setNeurolingsCount(name: String, count: Int) { val m = _neurolingsCounts.value.toMutableMap(); val c = NeurolingsCounts.clamp(count); if (c <= 0) m.remove(name) else m[name] = c; val nm = m.toMap(); prefs?.edit()?.putString(KEY_NEUROLINGS_COUNTS, NeurolingsCounts.serialize(nm))?.apply(); _neurolingsCounts.value = nm }`.
  - In `setDevOptionsUnlocked(false)` branch: replace `setNeurolingsEnabled(false)` with clearing counts: `prefs?.edit()?.remove(KEY_NEUROLINGS_COUNTS)?.apply(); _neurolingsCounts.value = emptyMap()`.
  - Add `private const val KEY_NEUROLINGS_COUNTS = "neurolings_counts"` (remove old `KEY_NEUROLINGS`).

- [ ] **Step 2: TvSettingsScreen — replace the Neurolings toggle with per-character steppers.**
  In the `if (devUnlocked)` block, replace the single `TvSettingsToggleRow("Neurolings", …)` with: an attribution line (`TvAboutText("Neurolings", "Shimeji mascots by the NeurolingsCE community — dev-only toy")`), then for each `NeurolingsCounts.CHARACTERS` a stepper row. Add a `TvStepperRow` composable (model on `TvCrossfadeRow`: focusable, D-pad Left/Right adjust; shows `−  N  +`):
  ```kotlin
  @Composable
  private fun TvStepperRow(label: String, value: Int, onStep: (Int) -> Unit) {
      var focused by remember { mutableStateOf(false) }
      Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp))
          .onFocusChanged { focused = it.isFocused }.focusable()
          .onKeyEvent { e -> if (e.type == KeyEventType.KeyUp) when (e.key) {
              Key.DirectionLeft -> { onStep(-1); true }; Key.DirectionRight -> { onStep(1); true }; else -> false
          } else false }
          .background(if (focused) MaterialTheme.colorScheme.primary.copy(alpha = 0.18f) else Color.Transparent)
          .padding(horizontal = 20.dp, vertical = 14.dp),
          horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
          Text(label, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
          Row(verticalAlignment = Alignment.CenterVertically) {
              Text("−", style = MaterialTheme.typography.titleLarge,
                  color = if (value > NeurolingsCounts.MIN) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
              Text("$value", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                  color = MaterialTheme.colorScheme.onSurface, modifier = Modifier.padding(horizontal = 20.dp))
              Text("+", style = MaterialTheme.typography.titleLarge,
                  color = if (value < NeurolingsCounts.MAX) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
          }
      }
  }
  ```
  Wire: collect `val counts by SettingsRepository.neurolingsCounts.collectAsState()`; per character `TvStepperRow(char, counts[char] ?: 0) { d -> SettingsRepository.setNeurolingsCount(char, NeurolingsCounts.step(counts[char] ?: 0, d)) }`. Keep the "Lock developer options" row (its repository call already clears counts now).

- [ ] **Step 3: Rewrite `TvNeurolings.kt`** as the engine-backed overlay:
  ```kotlin
  @Composable
  fun TvNeurolings(counts: Map<String, Int>, modifier: Modifier = Modifier) {
      val context = LocalContext.current
      val density = LocalDensity.current
      // Load all sets + manager once (off main thread).
      val manager by produceState<MascotManager?>(null) {
          value = withContext(Dispatchers.Default) {
              val assets = MascotAssets(context.assets)
              val sets = NeurolingsCounts.CHARACTERS.associateWith { assets.loadSet(it) }
              MascotManager(sets) to assets  // keep assets ref; see note
          }.let { (m, _) -> m }
          // store assets alongside for frame() lookups — implementer: hold both in a small holder class
      }
      BoxWithConstraints(modifier.fillMaxSize()) {
          val env = ShimejiEnvironment(0.0, 0.0, constraints.maxWidth.toDouble(), constraints.maxHeight.toDouble())
          val mgr = manager ?: return@BoxWithConstraints
          LaunchedEffect(counts, mgr) { mgr.sync(counts, env) }
          var frameTick by remember { mutableStateOf(0L) }
          LaunchedEffect(mgr, env) {
              var acc = 0L; var last = 0L
              while (true) { withFrameNanos { now -> if (last != 0L) acc += (now - last) / 1_000_000; last = now
                  while (acc >= ShimejiPhysics.TICK_MS) { mgr.tick(env); acc -= ShimejiPhysics.TICK_MS; frameTick++ } } }
          }
          frameTick // read to recompose
          for (r in mgr.snapshot()) {
              val bmp = /* assets.frame(r.setName, r.image) */
              Image(bitmap = bmp, contentDescription = null,
                  modifier = Modifier.graphicsLayer {
                      // place so anchor sits at (x,y); mirror if r.mirrored
                      translationX = (r.x - r.anchorX).toFloat(); translationY = (r.y - r.anchorY).toFloat()
                      scaleX = if (r.mirrored) -1f else 1f
                  })
          }
      }
  }
  ```
  Implementer note: introduce a tiny holder (e.g. `class Engine(val manager: MascotManager, val assets: MascotAssets)`) produced together so `snapshot()` frames resolve via `assets.frame(...)`. Positioning uses raw pixels (anchor is in the frame's own pixels; place top-left at `x-anchorX, y-anchorY`). Cap total via the counts (already ≤60). This composable holds no focusable/clickable modifiers.

- [ ] **Step 4: TvApp — render on all tabs when total > 0.**
  - Replace `val neurolingsEnabled by SettingsRepository.neurolingsEnabled.collectAsStateWithLifecycle()` with `val neurolingsCounts by SettingsRepository.neurolingsCounts.collectAsStateWithLifecycle()`.
  - Replace the render block `if (neurolingsEnabled && (tab == TvTab.RADIO || tab == TvTab.NOW_PLAYING)) { TvNeurolings() }` with:
    ```kotlin
    if (NeurolingsCounts.total(neurolingsCounts) > 0) {
        TvNeurolings(counts = neurolingsCounts)
    }
    ```
    (unchanged placement: after the content `Column`, before the detail overlay).

- [ ] **Step 5: Build** — `./gradlew :app:assembleDebug` → BUILD SUCCESSFUL. Then full unit suite `./gradlew :app:testDebugUnitTest` → all green.

- [ ] **Step 6: Commit** — `git commit -m "feat(tv): per-character Neurolings steppers + Shimeji-engine renderer"`

- [ ] **Step 7: Emulator verification (`emulator-5554`)** — install, open Developer Options, raise Neuron to 3 and Eviling to 2 with D-pad Right; confirm on a content tab that the mascots appear, **animate through real frames** (not a static icon), walk and flip to face travel, and that idle/gravity behaviors occur; confirm they show on multiple tabs; decrement to 0 and confirm they vanish. Capture screenshots.

---

## Self-Review

**Spec coverage:** EL evaluator (T1) ✅; XML model+loader (T2) ✅; physics/environment/gravity constants (T3) ✅; per-character counts + serialize/clamp (T3) ✅; action interpreter + weighted behavior state machine + mouse/IE inert (T4) ✅; asset frame cache + seed/cull/snapshot (T5) ✅; steppers UI + all-tabs render + 25fps renderer (T6) ✅; dev-only gating preserved (T6 keeps the 7-tap unlock + lock-resets-counts) ✅; attribution line (T6) ✅. Deferred items (mouse/IE/inter-mascot) are explicitly inert per Global Constraints ✅.

**Placeholder scan:** Task 6 Step 3 gives skeleton code with an explicit implementer note (holder class + frame lookup) rather than a copy-paste-complete composable — deliberate, since the renderer wiring is integration work best verified on the emulator; all signatures and the tick/positioning math are concrete. No vague "add error handling" anywhere.

**Type consistency:** `MascotSet`/`ShimejiAction`/`Pose`/`Behavior` (T2) are used unchanged in T4/T5. `ShimejiExpr.Scope` (T1) is consumed by `Mascot` (T4). `NeurolingsCounts` (T3) drives `SettingsRepository`/steppers/`MascotManager` (T5/T6). `MascotRender` (T5) is consumed by the T6 renderer. `ShimejiPhysics.TICK_MS`/`fallStep` (T3) used in T4/T6. Names checked across tasks.
