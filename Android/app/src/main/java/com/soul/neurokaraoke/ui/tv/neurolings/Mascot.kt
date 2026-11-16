/*
 * Copyright (C) 2026 Aferil
 *
 * This file is part of Neuro Karaoke.
 *
 * Neuro Karaoke is free software: you can redistribute it and/or modify it under
 * the terms of the GNU General Public License as published by the Free Software
 * Foundation, version 3.
 *
 * Neuro Karaoke is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with
 * Neuro Karaoke. If not, see <https://www.gnu.org/licenses/>.
 *
 * This file is part of the Neurolings feature, ported from Shimeji-ee (maintained
 * by Kilkakon; original Shimeji by Yuki Yamada / Group Finity), which is under a
 * permissive BSD/zlib-style license. See THIRD_PARTY_NOTICES.md.
 */

package com.soul.neurokaraoke.ui.tv.neurolings

/**
 * One mascot's simulation: action interpreter + weighted-random behavior state machine + gravity.
 *
 * Pure Kotlin (no Android APIs) so it can be unit-tested on the plain JVM. The rendering side
 * (Task 6) reads [currentFrameImage] / [currentAnchor] every frame and draws at ([x], [y])
 * offset by the anchor.
 */
class Mascot(
    val set: MascotSet,
    startX: Double,
    startY: Double,
    private val rng: kotlin.random.Random,
) {
    private companion object {
        /** Max synchronous push-resolution depth (Sequence/Select nesting), as a cycle-safety net. */
        const val MAX_RESOLVE_DEPTH = 32

        /**
         * Hard cap on how many [Frame]s the interpreter stack may hold at once. This is the
         * bulletproof, path-independent recursion guard: every [pushResolved] call for a
         * Sequence/Select pushes a Frame before descending further, on EVERY path that reaches
         * it -- including [popFinished]'s next-sibling and `Loop="true"` restart paths, which
         * re-enter [pushChildRef] with a fresh (reset) visited-names set and so are NOT caught
         * by the per-descent cycle guard alone. Capping the stack size bounds both the
         * [ArrayDeque] growth and the native call-stack depth on every path, regardless of
         * whether a cycle is detectable by name.
         */
        const val MAX_FRAMES = 64
    }

    /** Anchor position (foot point). */
    var x: Double = startX
    var y: Double = startY
    var lookRight: Boolean = true

    /** Name of the top-level behavior currently executing (null if none started yet). */
    var currentBehaviorName: String? = null

    // Last-rendered pose image/anchor. EmbeddedAction has no poses of its own (Task 2 dropped
    // inline embedded animations), so during Fall/Jump/etc. we keep showing the mascot's last
    // real pose rather than crashing or returning an empty image.
    private var lastImage: String = ""
    private var lastAnchor: Pair<Int, Int> = 0 to 0

    // NextBehaviorList carried over from the behavior that just finished (if it declared one).
    private var pendingNext: NextBehaviorList? = null
    private var behaviorNextOnFinish: NextBehaviorList? = null

    // Interpreter stack: root (top-level behavior action) at index 0, current leaf action on top.
    // Sequence/Select frames stay on the stack (to track progress) while a leaf (Stay/Move/
    // Animate/Embedded) frame sits on top and is the one actually ticked.
    private val stack = ArrayDeque<Frame>()

    private class Frame(val action: ShimejiAction, val overrides: Map<String, String>) {
        var seqIndex: Int = 0
        var animIndex: Int = -1
        var poseIndex: Int = 0
        var poseTicksLeft: Int = 0
        var durationTicksLeft: Int? = null
        var vel: ShimejiPhysics.Vel = ShimejiPhysics.Vel(0.0, 0.0)
        // Move destination (Shimeji "TargetX"/"TargetY"): a Move walks toward this coordinate and
        // finishes when it arrives. Null for Move actions without a target (fall back to raw
        // pose-velocity motion) and for all non-Move actions.
        var targetX: Double? = null
        var targetY: Double? = null
    }

    // Targets of the currently-ticking leaf, mirrored here so the expression Scope can resolve the
    // `TargetX`/`TargetY`/`FootX` variables that some Animation/Select conditions reference
    // (e.g. ClimbWall picks its up vs down animation from `#{TargetY < mascot.anchor.y}`).
    private var curTargetX: Double? = null
    private var curTargetY: Double? = null

    init {
        // Guarantee a valid frame is available immediately after construction, before the
        // first tick() runs the real behavior-selection/interpreter machinery. We don't have
        // an environment yet at construction time, so just seed from the first pose we can
        // find anywhere in the mascot pack rather than starting a full behavior.
        seedInitialFrame()
    }

    private fun seedInitialFrame() {
        for (action in set.actions.values) {
            val animations = when (action) {
                is StayAction -> action.animations
                is MoveAction -> action.animations
                is AnimateAction -> action.animations
                else -> null
            } ?: continue
            val pose = animations.firstNotNullOfOrNull { it.poses.firstOrNull() }
            if (pose != null) {
                applyPose(pose)
                return
            }
        }
    }

    fun currentFrameImage(): String = lastImage

    fun currentAnchor(): Pair<Int, Int> = lastAnchor

    /** Advance the simulation by one 40ms tick. */
    fun tick(env: ShimejiEnvironment, totalCount: Int) {
        // Gravity guard: if we're supposedly standing on the floor but aren't, start falling.
        val leaf = stack.lastOrNull()
        if (leaf != null && leafBorder(leaf) == BorderType.FLOOR && !env.onFloor(Anchor(x, y))) {
            triggerFall(env, totalCount)
        }

        if (stack.isEmpty()) {
            val name = selectBehavior(env, totalCount)
            if (name != null) {
                startBehavior(name, env, totalCount)
            } else if (!env.onFloor(Anchor(x, y))) {
                // No applicable behavior while off the floor (e.g. a wall/ceiling climb sequence
                // ended mid-air, where no border condition matches): fall back to the floor to
                // recover instead of freezing in place. This mirrors Shimeji's always-available
                // Fall fallback for an unsupported mascot.
                triggerFall(env, totalCount)
            }
        }

        stepStack(env, totalCount)
        clampToEnv(env)
    }

    /** Weighted-random behavior pick honoring conditions. Exposed for tests; no side effects. */
    fun selectBehavior(env: ShimejiEnvironment, totalCount: Int): String? {
        val scope = MascotScope(totalCount, env)
        val candidates = candidateList().filter { c ->
            c.frequency > 0 && (c.condition == null || ShimejiExpr.evalBoolean(c.condition, scope))
        }
        if (candidates.isEmpty()) return null
        val totalWeight = candidates.sumOf { it.frequency }
        if (totalWeight <= 0) return null
        var r = rng.nextInt(totalWeight)
        for (c in candidates) {
            if (r < c.frequency) return c.name
            r -= c.frequency
        }
        return candidates.last().name
    }

    // -------------------------------------------------------------------
    // Behavior candidate selection
    // -------------------------------------------------------------------

    private data class Candidate(val name: String, val frequency: Int, val condition: String?)

    private fun candidateList(): List<Candidate> {
        val nbl = pendingNext ?: return globalCandidates()
        val own = nbl.refs.map { Candidate(it.name, it.frequency, it.condition) }
        return if (nbl.add) own + globalCandidates() else own
    }

    private fun globalCandidates(): List<Candidate> =
        set.behaviors.map { Candidate(it.name, it.frequency, it.condition) }

    // -------------------------------------------------------------------
    // Behavior start / interpreter stack management
    // -------------------------------------------------------------------

    private fun startBehavior(name: String, env: ShimejiEnvironment, totalCount: Int) {
        currentBehaviorName = name
        pendingNext = null
        behaviorNextOnFinish = set.behaviors.firstOrNull { it.name == name }?.next
        stack.clear()
        val action = set.actions[name] ?: run {
            // Unknown action name: nothing to run; finish immediately so we reselect next tick.
            currentBehaviorName = null
            pendingNext = behaviorNextOnFinish
            behaviorNextOnFinish = null
            return
        }
        pushResolved(action, emptyMap(), env, totalCount)
    }

    private fun triggerFall(env: ShimejiEnvironment, totalCount: Int) {
        if (set.actions.containsKey("Fall")) {
            startBehavior("Fall", env, totalCount)
        }
    }

    private fun pushResolved(
        action: ShimejiAction,
        overrides: Map<String, String>,
        env: ShimejiEnvironment,
        totalCount: Int,
        visiting: MutableSet<String> = mutableSetOf(),
        depth: Int = 0,
    ) {
        // Bulletproof, path-independent recursion guard: checked before this call pushes
        // anything or recurses further, on every path (including popFinished's next-sibling
        // and Loop="true" restart re-entries, which reset the per-descent `visiting` set below
        // and so cannot be relied on alone). A cyclic/self-referential action tree degrades
        // gracefully -- abandon the whole in-progress behavior -- instead of growing the
        // ArrayDeque and the native call stack without bound.
        if (stack.size >= MAX_FRAMES) {
            abandonCurrentBehavior()
            return
        }

        // Secondary, cheaper guard: catches direct/transitive self-reference within a single
        // synchronous descent (e.g. non-looping A -> A) before it even reaches the stack cap.
        val name = action.name
        val cyclic = name != null && !visiting.add(name)
        if (cyclic || depth > MAX_RESOLVE_DEPTH) {
            popFinished(env, totalCount)
            return
        }
        when (action) {
            is SequenceAction -> {
                val frame = Frame(action, overrides)
                stack.addLast(frame)
                if (action.refs.isEmpty()) {
                    popFinished(env, totalCount)
                } else {
                    pushChildRef(action.refs[0], env, totalCount, visiting, depth + 1)
                }
            }
            is SelectAction -> {
                val scope = MascotScope(totalCount, env)
                val chosen = action.children.firstOrNull { (cond, _) -> cond == null || ShimejiExpr.evalBoolean(cond, scope) }
                val frame = Frame(action, overrides)
                stack.addLast(frame)
                if (chosen == null) {
                    popFinished(env, totalCount)
                } else {
                    pushChildRef(chosen.second, env, totalCount, visiting, depth + 1)
                }
            }
            else -> {
                val frame = Frame(action, overrides)
                initFrame(frame, env, totalCount)
                stack.addLast(frame)
            }
        }
    }

    private fun pushChildRef(
        ref: ActionRef,
        env: ShimejiEnvironment,
        totalCount: Int,
        visiting: MutableSet<String> = mutableSetOf(),
        depth: Int = 0,
    ) {
        val target = set.actions[ref.name]
        if (target == null) {
            // Unresolvable reference: skip forward as if it finished instantly.
            popFinished(env, totalCount)
            return
        }
        pushResolved(target, ref.overrides, env, totalCount, visiting, depth)
    }

    /**
     * Bail out of whatever behavior/action tree is currently in progress (e.g. because the
     * [MAX_FRAMES] cap tripped) without any further resolution work. Clears the interpreter
     * stack outright and lines up the next selection, exactly like a normal, non-cyclic
     * behavior finishing -- the next tick()/selectBehavior() call picks a fresh behavior.
     */
    private fun abandonCurrentBehavior() {
        stack.clear()
        pendingNext = behaviorNextOnFinish
        behaviorNextOnFinish = null
        currentBehaviorName = null
    }

    /** Called after the frame on top of the stack has just been removed because it finished. */
    private fun popFinished(env: ShimejiEnvironment, totalCount: Int) {
        while (stack.isNotEmpty()) {
            val top = stack.last()
            when (val a = top.action) {
                is SequenceAction -> {
                    top.seqIndex++
                    if (top.seqIndex < a.refs.size) {
                        pushChildRef(a.refs[top.seqIndex], env, totalCount)
                        return
                    } else if (a.loop && a.refs.isNotEmpty()) {
                        top.seqIndex = 0
                        pushChildRef(a.refs[0], env, totalCount)
                        return
                    } else {
                        stack.removeLast()
                        continue
                    }
                }
                is SelectAction -> {
                    // Select is a one-shot dispatcher: once its chosen child finishes, so does it.
                    stack.removeLast()
                    continue
                }
                else -> {
                    // Leaf frames are removed by stepStack before calling popFinished.
                    stack.removeLast()
                    continue
                }
            }
        }
        // Stack is empty: the whole behavior chain finished. Line up the next selection.
        abandonCurrentBehavior()
    }

    private fun stepStack(env: ShimejiEnvironment, totalCount: Int) {
        if (stack.isEmpty()) return
        val frame = stack.last()
        val finished = tickLeaf(frame, env, totalCount)
        if (finished) {
            stack.removeLast()
            popFinished(env, totalCount)
        }
    }

    private fun leafBorder(frame: Frame): BorderType? = when (val a = frame.action) {
        is StayAction -> a.border
        is MoveAction -> a.border
        is AnimateAction -> a.border
        else -> null
    }

    // -------------------------------------------------------------------
    // Leaf frame initialization
    // -------------------------------------------------------------------

    private fun initFrame(frame: Frame, env: ShimejiEnvironment, totalCount: Int) {
        val scope = MascotScope(totalCount, env)
        frame.overrides["Duration"]?.let {
            frame.durationTicksLeft = ShimejiExpr.evalDouble(it, scope).toInt()
        }
        var vx = 0.0
        var vy = 0.0
        frame.overrides["InitialVX"]?.let { vx = ShimejiExpr.evalDouble(it, scope) }
        frame.overrides["InitialVY"]?.let { vy = ShimejiExpr.evalDouble(it, scope) }
        frame.vel = ShimejiPhysics.Vel(vx, vy)

        // Resolve the move target (if any) BEFORE picking the animation, so conditions that
        // reference TargetX/TargetY (via the Scope's live curTarget* fields) see the right values.
        frame.targetX = frame.overrides["TargetX"]?.let { ShimejiExpr.evalDouble(it, scope) }
        frame.targetY = frame.overrides["TargetY"]?.let { ShimejiExpr.evalDouble(it, scope) }
        curTargetX = frame.targetX
        curTargetY = frame.targetY
        // Face the horizontal target from the start so the walk animation mirrors correctly.
        val border = leafBorder(frame)
        if (frame.targetX != null && (border == BorderType.FLOOR || border == BorderType.CEILING)) {
            lookRight = frame.targetX!! > x
        }

        val animations = when (val a = frame.action) {
            is StayAction -> a.animations
            is MoveAction -> a.animations
            is AnimateAction -> a.animations
            else -> null
        }
        if (animations != null) {
            val idx = pickAnimationIndex(animations, scope)
            frame.animIndex = idx
            val poses = if (idx >= 0) animations[idx].poses else emptyList()
            if (poses.isNotEmpty()) {
                frame.poseIndex = 0
                frame.poseTicksLeft = poses[0].duration.coerceAtLeast(1)
                applyPose(poses[0])
            } else {
                frame.animIndex = -1
            }
        }
    }

    private fun pickAnimationIndex(animations: List<Animation>, scope: ShimejiExpr.Scope): Int {
        for (i in animations.indices) {
            val cond = animations[i].condition
            if (cond == null || ShimejiExpr.evalBoolean(cond, scope)) return i
        }
        return if (animations.isNotEmpty()) 0 else -1
    }

    private fun applyPose(pose: Pose) {
        lastImage = pose.image
        lastAnchor = pose.anchorX to pose.anchorY
    }

    // -------------------------------------------------------------------
    // Per-tick leaf execution. Returns true if the frame just finished.
    // -------------------------------------------------------------------

    private fun tickLeaf(frame: Frame, env: ShimejiEnvironment, totalCount: Int): Boolean {
        val durationExpired = tickDuration(frame)
        return when (val a = frame.action) {
            is StayAction -> {
                stepPose(frame, a.animations, loop = true)
                durationExpired
            }
            is AnimateAction -> {
                val done = stepPose(frame, a.animations, loop = false)
                done || durationExpired
            }
            is MoveAction -> {
                stepPose(frame, a.animations, loop = true)
                val reached = currentPoseOf(frame, a.animations)
                    ?.let { moveTowardTarget(frame, a.border, it) } ?: false
                reached || borderHit(a.border, env) || durationExpired
            }
            is EmbeddedAction -> tickEmbedded(frame, a, env, totalCount) || durationExpired
            else -> true // Sequence/Select never sit at the top of the stack as a leaf.
        }
    }

    private fun tickDuration(frame: Frame): Boolean {
        val d = frame.durationTicksLeft ?: return false
        val nd = d - 1
        frame.durationTicksLeft = nd
        return nd <= 0
    }

    private fun stepPose(frame: Frame, animations: List<Animation>, loop: Boolean): Boolean {
        if (frame.animIndex < 0 || frame.animIndex >= animations.size) return true
        frame.poseTicksLeft--
        if (frame.poseTicksLeft > 0) return false
        val anim = animations[frame.animIndex]
        if (anim.poses.isEmpty()) return true
        var nextIndex = frame.poseIndex + 1
        if (nextIndex >= anim.poses.size) {
            if (!loop) return true
            nextIndex = 0
        }
        frame.poseIndex = nextIndex
        val pose = anim.poses[nextIndex]
        frame.poseTicksLeft = pose.duration.coerceAtLeast(1)
        applyPose(pose)
        return false
    }

    private fun currentPoseOf(frame: Frame, animations: List<Animation>): Pose? {
        if (frame.animIndex < 0 || frame.animIndex >= animations.size) return null
        return animations[frame.animIndex].poses.getOrNull(frame.poseIndex)
    }

    /**
     * Advance a [MoveAction] one tick. When the frame has a target coordinate (Shimeji
     * `TargetX`/`TargetY`), walk toward it at the current pose's speed and report whether it has
     * arrived; facing follows the travel direction. Floor/Ceiling moves seek [Frame.targetX]
     * horizontally; Wall moves (climbing) seek [Frame.targetY] vertically. With no target, fall
     * back to raw pose-velocity motion (never "reached").
     */
    private fun moveTowardTarget(frame: Frame, border: BorderType, pose: Pose): Boolean {
        val vertical = border == BorderType.WALL
        if (!vertical) {
            val tx = frame.targetX ?: run { applyMoveVelocity(pose); return false }
            val speed = kotlin.math.abs(pose.velX.toDouble())
            val dist = tx - x
            val dir = if (dist > 0.0) 1.0 else if (dist < 0.0) -1.0 else 0.0
            x += dir * kotlin.math.min(speed, kotlin.math.abs(dist))
            y += pose.velY.toDouble()
            if (dir > 0.0) lookRight = true else if (dir < 0.0) lookRight = false
            return kotlin.math.abs(tx - x) <= 0.5
        } else {
            val ty = frame.targetY ?: run { applyMoveVelocity(pose); return false }
            val speed = kotlin.math.abs(pose.velY.toDouble())
            val dist = ty - y
            val dir = if (dist > 0.0) 1.0 else if (dist < 0.0) -1.0 else 0.0
            y += dir * kotlin.math.min(speed, kotlin.math.abs(dist))
            x += pose.velX.toDouble()
            return kotlin.math.abs(ty - y) <= 0.5
        }
    }

    /** No-target fallback: poses are authored facing left; sign-flip velX when facing right. */
    private fun applyMoveVelocity(pose: Pose) {
        val dx = if (lookRight) -pose.velX.toDouble() else pose.velX.toDouble()
        x += dx
        y += pose.velY.toDouble()
        if (dx > 0.0) lookRight = true else if (dx < 0.0) lookRight = false
    }

    private fun borderHit(border: BorderType, env: ShimejiEnvironment): Boolean {
        val a = Anchor(x, y)
        return when (border) {
            BorderType.FLOOR -> env.onWall(a)
            BorderType.WALL -> env.onFloor(a) || env.onCeiling(a)
            BorderType.CEILING -> env.onWall(a)
            BorderType.NONE -> false
        }
    }

    private fun tickEmbedded(frame: Frame, action: EmbeddedAction, env: ShimejiEnvironment, totalCount: Int): Boolean {
        // className is the full Shimeji class path (e.g. com.group_finity.mascot.action.Fall);
        // match on the final segment.
        return when (action.className.substringAfterLast('.')) {
            "Fall", "Jump" -> {
                frame.vel = ShimejiPhysics.fallStep(frame.vel)
                x += frame.vel.x
                y += frame.vel.y
                env.onFloor(Anchor(x, y))
            }
            "Look" -> {
                // The pack sets facing via <ActionReference Name="Look" LookRight="true|false|expr"/>.
                frame.overrides["LookRight"]?.let {
                    lookRight = ShimejiExpr.evalBoolean(it, MascotScope(totalCount, env))
                }
                true
            }
            "Offset" -> {
                val scope = MascotScope(totalCount, env)
                frame.overrides["X"]?.let { x += ShimejiExpr.evalDouble(it, scope) }
                frame.overrides["Y"]?.let { y += ShimejiExpr.evalDouble(it, scope) }
                true
            }
            // Dragged/ThrowIE/WalkWithIE/Breed/Interact/ScanMove all need cursor/IE/target,
            // which are always MISSING in this port -> no-op, finish immediately.
            else -> true
        }
    }

    private fun clampToEnv(env: ShimejiEnvironment) {
        if (x < env.left) x = env.left
        if (x > env.right) x = env.right
        if (y < env.top) y = env.top
        if (y > env.bottom) y = env.bottom
    }

    // -------------------------------------------------------------------
    // Expression scope
    // -------------------------------------------------------------------

    private inner class MascotScope(private val totalCount: Int, private val env: ShimejiEnvironment) : ShimejiExpr.Scope {
        override fun variable(path: String): Any? = when (path) {
            "mascot.anchor" -> Anchor(x, y)
            "mascot.anchor.x", "FootX" -> x
            "mascot.anchor.y" -> y
            "mascot.lookRight" -> lookRight
            "mascot.totalCount" -> totalCount.toDouble()
            "mascot.environment.screen.width" -> env.width
            "mascot.environment.screen.height" -> env.height
            "mascot.environment.workArea.left" -> env.left
            "mascot.environment.workArea.right" -> env.right
            "mascot.environment.workArea.top" -> env.top
            "mascot.environment.workArea.bottom" -> env.bottom
            "mascot.environment.workArea.width" -> env.width
            "mascot.environment.workArea.height" -> env.height
            "TargetX" -> curTargetX ?: ShimejiExpr.MISSING
            "TargetY" -> curTargetY ?: ShimejiExpr.MISSING
            else -> ShimejiExpr.MISSING
        }

        override fun method(path: String, args: List<Any?>): Any? {
            if (path.startsWith("mascot.environment.") && path.endsWith(".isOn")) {
                val anchor = args.getOrNull(0) as? Anchor ?: return ShimejiExpr.MISSING
                return when (path.removePrefix("mascot.environment.").removeSuffix(".isOn")) {
                    "floor", "workArea.bottomBorder" -> env.onFloor(anchor)
                    "ceiling", "workArea.topBorder" -> env.onCeiling(anchor)
                    "wall" -> env.onWall(anchor)
                    "workArea.leftBorder" -> env.onLeftWall(anchor)
                    "workArea.rightBorder" -> env.onRightWall(anchor)
                    else -> ShimejiExpr.MISSING
                }
            }
            return ShimejiExpr.MISSING
        }
    }
}
