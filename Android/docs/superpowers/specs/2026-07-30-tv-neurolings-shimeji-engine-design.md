# Android TV — Neurolings (Shimeji engine port)

**Date:** 2026-07-30
**Status:** Approved (design)
**Scope:** Replace the placeholder walking-icon overlay with a faithful Kotlin port of the Shimeji-ee desktop-pet engine, driven by the real NeurolingsCE mascot pack (6 characters). Dev-only toy, toggled from the existing Developer Options → Neurolings switch.

## Goal

The current `TvNeurolings` renders the launcher icon bobbing along the bottom. Replace it with the **same algorithm the NeurolingsCE / Shimeji-ee engine uses**, translated to Kotlin: a data-driven action interpreter + weighted-random behavior state machine + gravity/physics, rendering the actual pack sprite frames. The mascots wander, walk/run, sit, lie down, wink, blow hearts, climb walls, cross the ceiling, jump, and fall under gravity — chosen by the pack's own `behaviors.xml`, animated by its `actions.xml`.

## Source format

Each character is a standard Shimeji-ee pack (a zip inside the downloaded `.mascot`): `actions.xml`, `behaviors.xml`, `img/*.png`, `info.json`. The 6 characters are **Neuron, Weuron, Eviling, Vedaling, Cerber, Tuteling**. The engine is fully data-driven, so it parses each pack's own XML — no per-character code.

The Shimeji model:
- **Pose** = `{image, imageAnchor (the foot/anchor pixel, e.g. 64,128), velocity (px/tick), duration (ticks)}`.
- **Animation** = an ordered list of Poses, optionally guarded by a `Condition`.
- **Action** types:
  - `Stay` — hold an animation in place (idle loops).
  - `Move` — advance by each pose's velocity along a `BorderType` (Floor/Wall/Ceiling).
  - `Animate` — play an animation once.
  - `Sequence` — run child `ActionReference`s in order (`Loop` true/false); a reference may override `Duration`/`InitialVX`/`InitialVY` via an EL expression.
  - `Select` — run the first child action whose `Condition` passes (if/else).
  - `Embedded` — a built-in physics primitive named by Java class (`Fall`, `Jump`, `Look`, `Offset`, …).
- **Behavior** = a named entry mapping to the action of the same name, with a `Frequency` (weight), an optional `Condition`, an optional `NextBehaviorList` (chained follow-ups, `Add` = merge-with-or-replace the global list). Behaviors are grouped under environment `Condition` blocks (on floor / wall / ceiling / work-area border).
- **Tick** = ~40 ms (25 fps). Velocity is px/tick; Duration is a tick count.

## Scope / non-goals

**In scope (Phase 1 — the solo engine):**
- Parse `actions.xml` + `behaviors.xml` from bundled assets into the model above.
- The action interpreter for `Stay`, `Move`, `Animate`, `Sequence`, `Select`, and the Embedded primitives that function without a mouse or window: `Fall` (gravity), `Jump`, `Look` (set facing toward a point), `Offset`. `ScanMove` is included in its degenerate no-target form (walks toward screen-relative targets only where a target exists; otherwise not selected).
- The weighted-random behavior selector with environment conditions and `NextBehaviorList` chaining.
- A real evaluator for the EL-subset actually used (see below).
- Gravity + a screen-derived environment: bottom = floor, left/right = walls, top = ceiling. Mascots wander the floor, climb walls, cross the ceiling, jump between them, and fall when unsupported — exactly as the pack's behaviors dictate.
- Render the real pack frames at 25 fps, positioned by `imageAnchor`, mirrored horizontally for facing.
- All 6 characters bundled; **per-character population control** (each 0–10, default 0) — the overlay spawns exactly that many of each character and roams them over **every** TV screen.

**Deferred / inert (not this build):**
- **Mouse behaviors** (`ChaseMouse`, `SitAndFaceMouse`, `Dragged`, `Thrown`, pat `Hotspot`s) — a TV has no cursor. Their conditions reference `mascot.environment.cursor.*`; the environment reports "no cursor" so these are never selected. Faithful: same engine, cursor simply never exists.
- **IE / active-window behaviors** (`WalkWithIE`, `ThrowIE`, `FallWithIE`, climb/jump-on-IE) — no browser window on a TV. `activeIE.visible` is always false, so these never fire.
- **Inter-mascot interaction** (`Breed`/`SplitIntoTwo`, `Interact`/`HugEvil`/`HurlEvil`) — active on any platform but a substantial second system (target selection between mascots, coordinated two-body animations, spawning/culling). Deferred to a Phase 2. Their behaviors stay in the parsed data but the manager exposes no interaction target, so they are not selected.

**Non-goals:** not shipped to non-dev users (gated behind the 7-tap Developer Options); no per-character Kotlin; no editing tools.

## Architecture

New package `ui/tv/neurolings/`, each file one responsibility:

- `ShimejiModel.kt` — immutable data classes: `MascotSet(name, actions, behaviors, imageDir)`, `ShimejiAction` (sealed: `Stay`, `Move`, `Animate`, `Sequence`, `Select`, `Embedded`), `Pose`, `Animation`, `Behavior`, `BehaviorRef`, `ActionRef`, `Hotspot` (parsed but unused in Phase 1).
- `ShimejiExpr.kt` — **pure** EL-subset evaluator. A small recursive-descent parser + evaluator over a `Map<String, Any?>`-style context, supporting: number/boolean literals; `+ - * /`; comparisons `< > <= >= == !=`; logical `&& || !`; ternary `?:`; `Math.random()`, `Math.abs(x)`; property paths (`mascot.anchor.x`, `mascot.environment.floor`, `mascot.totalCount`, `mascot.lookRight`, `mascot.environment.screen.height`, `workArea.left/right/top/bottom/width/height`, named loop vars `FootX`/`TargetY`); and the boolean method `border.isOn(anchor)`. Unresolved cursor/IE/target references evaluate to a sentinel that makes their conditions false. **Testable in isolation.**
- `ShimejiXmlLoader.kt` — parse `actions.xml` + `behaviors.xml` (Android `XmlPullParser`) from `assets/mascots/<Name>/` into a `MascotSet`. Resolves `ActionReference`/`BehaviorReference` names.
- `ShimejiPhysics.kt` — the constants and primitive steps: gravity, air resistance, terminal velocity, bounce damping, jump impulse — **using Shimeji-ee's standard values** (pinned from the reference engine in the plan). Pure functions for a falling step, a bounce, a border test.
- `ShimejiEnvironment.kt` — screen rect → the four borders and their `isOn(anchor)` tests; "no cursor / no IE" stubs.
- `Mascot.kt` — one mascot's simulation: current behavior + action + pose cursor + position + velocity + facing. `tick()` advances the pose timer, applies velocity, and on action-end runs the behavior selector (weighted random over passing conditions, honoring `NextBehaviorList`). Falls under gravity when unsupported.
- `MascotManager.kt` — owns the loaded `MascotSet`s and the live mascots; seeds mascots to match the requested **per-character counts** (adds/removes as the counts change, so adjusting a stepper live adds or culls that character); `tick(nowMs, bounds)` steps them all; exposes an immutable render snapshot (list of `{bitmap, x, y, mirrored}`).
- `MascotAssets.kt` — decode a pack's PNG frames into a cached `Map<String, ImageBitmap>` (decode once, reuse every frame).
- `TvNeurolings.kt` — **rewritten** Compose overlay: on first show, load the 6 sets (off the main thread) and build a `MascotManager`; drive a 25 fps clock via `withFrameNanos`; render each snapshot entry via `Canvas`/`Image` at `x,y` with horizontal mirror. Holds no focus, captures no input. Pauses its clock while not composed.

Assets: `app/src/main/assets/mascots/<Name>/{actions.xml, behaviors.xml, img/*.png}` for all 6, plus each `info.json` kept for attribution.

## Expression evaluator — supported grammar

From the packs, the evaluator must handle exactly these forms (superset across `Condition`, `Duration`, `Velocity`, `InitialVX/VY`):
- Literals: integers, decimals, `true`/`false`.
- Arithmetic: `+ - * /` with normal precedence and parentheses.
- Comparisons: `< > <= >= == !=`; logical `&& || !`; ternary `cond ? a : b`.
- Functions: `Math.random()` (0..1), `Math.abs(x)`.
- Variables / paths: `mascot.anchor.x|y`, `mascot.lookRight`, `mascot.totalCount`, `mascot.environment.screen.height`, `mascot.environment.{floor,wall,ceiling}`, `mascot.environment.workArea.{left,right,top,bottom,width,height}`, `mascot.environment.workArea.{left,right,top,bottom}Border`, and loop-scoped `FootX`, `TargetY`.
- Method: `<border>.isOn(mascot.anchor)` → boolean.
- Inert references (`mascot.environment.cursor.*`, `mascot.environment.activeIE.*`, `target.*`) resolve to a "missing" sentinel; any comparison/`isOn` against it is false, so mouse/IE/interaction conditions never pass.

`${...}` and `#{...}` are treated identically (Shimeji uses both spellings interchangeably here).

## Physics

Use Shimeji-ee's standard constants (gravity, air-resistance, bounce, jump impulse), pinned to exact numeric values from the reference engine when the plan is written, so `Fall`/`Jump`/`Bounce` match the original feel. Gravity accumulates on velocity.y each tick with air resistance; on hitting the floor border the `Fall` sequence transitions to `Bouncing` then `Stand` (per the pack's `Fall` action). Facing (`lookRight`) flips when horizontal velocity changes sign; the sprite mirrors accordingly.

## Rendering & performance

- Decode each frame PNG once into an `ImageBitmap` cache keyed by pack+filename; never decode per frame.
- One `withFrameNanos` loop accumulates real time and steps the simulation at the fixed 25 fps tick, so motion is framerate-independent.
- Position: draw the frame so its `imageAnchor` sits at the mascot's `(x, y)`; mirror horizontally when facing right (frames are authored facing left).
- Cap the live mascot count (default ~6, one per character) to keep the TV GPU/CPU comfortable; the count is a constant, easily raised.
- The overlay pauses its clock when not composed and is skipped entirely when the toggle is off, so there is zero cost for normal users.

## Integration & population control

- The single boolean `SettingsRepository.neurolingsEnabled` is **replaced** by per-character counts: `neurolingsCounts: StateFlow<Map<String, Int>>` (character name → 0..10), persisted (e.g. one `"Name=count"` string per character, or a small JSON blob, in the same `app_settings` prefs). `setNeurolingsCount(name, count)` clamps to `0..10`. Locking Developer Options resets every count to 0. There is no all-characters cap beyond the per-character 10 (max 60 total is the user's call via the steppers).
- Developer Options → **Neurolings** becomes a small sub-section: an attribution line, then **one stepper row per character** (Neuron, Weuron, Eviling, Vedaling, Cerber, Tuteling). Each row shows the character name and a `−  N  +` control; it is a focusable row where **D-pad Left decrements and Right increments** (0..10), reusing the crossfade-row interaction, with visible `−`/`+` glyphs so the plus/minus affordance is clear on the remote.
- `TvApp` renders the overlay on **all** tabs whenever the total requested count (`sum of the map`) is `> 0`, under the settings/detail overlays and above content. Adjusting a stepper updates the count flow; the manager adds/culls that character live.
- The overlay remains non-focusable and input-transparent.

## Licensing / attribution

The pack art is community work (e.g. Neuron by Paccha; configs by @promote., @dalekcraft) and NeurolingsCE is GPLv3. Keep this **dev-only** (never surfaced to normal users), bundle each pack's `info.json` credits unmodified, and surface an attribution line in Developer Options near the toggle. Easy to remove wholesale (delete the package + assets + toggle).

## Testable units

- `ShimejiExpr` — arithmetic, comparisons, ternary, `Math.*`, variable resolution, inert-sentinel falsity. (JVM unit tests.)
- Behavior selection — weighted-random pick over a set with a seeded RNG; conditions filter the candidate set. (Seeded, deterministic test.)
- Physics step — a `Fall` step accumulates gravity and clamps to terminal velocity; a border `isOn` test. (Pure unit tests.)
- Count clamp / persistence round-trip — `setNeurolingsCount` clamps to `0..10`; the map serializes and parses back identically. (Pure unit test on the serialize/parse + clamp helper.)
- XML parse — a small inline `actions.xml`/`behaviors.xml` fixture parses into the expected model. (Instrumented or Robolectric-free via a parser abstraction.)

## Verification (emulator, `emulator-5554`)

1. In Developer Options, each character starts at **0**. Increment a character's stepper with D-pad Right; that many of it appear and **animate through real frames** (not a static icon), wandering the floor. Decrement culls them; 0 for all removes the overlay. Steppers clamp at 0 and 10.
2. They walk/run and **flip to face travel direction**; idle animations (wink, hearts, sit, lie) occur over time.
3. Gravity: a mascot that ends up off the floor **falls and settles** on the bottom edge.
4. Wall/ceiling: over time a mascot climbs a side wall and/or crosses the top, per the pack's behaviors.
5. Mascots appear on **all** tabs (Home, Search, Library, Radio, Now Playing, Account) when enabled.
6. Toggling off removes them; normal (non-dev) users never see them.
7. No effect on D-pad navigation (overlay is input-transparent), and no jank at 25 fps.
