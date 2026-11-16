package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import com.soul.neurokaraoke.ui.tv.neurolings.MascotAssets
import com.soul.neurokaraoke.ui.tv.neurolings.MascotManager
import com.soul.neurokaraoke.ui.tv.neurolings.NeurolingsCounts
import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiEnvironment
import com.soul.neurokaraoke.ui.tv.neurolings.ShimejiPhysics
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Engine-backed overlay of little walking mascots ("Neurolings") — a purely-cosmetic, dev-only
 * toy (per-character counts set from Developer Options). Drives the pure-Kotlin Shimeji engine
 * ([MascotManager]) at a fixed 25fps tick and renders its snapshot as plain [Image]s. Holds no
 * focusable/clickable/pointer modifiers, so it never interferes with D-pad navigation.
 */
private class NeurolingsEngine(val manager: MascotManager, val assets: MascotAssets)

/** Render scale for the mascot sprites — the pack frames are small (~128px), so scale up for TV. */
private const val MASCOT_SCALE = 2f

@Composable
fun TvNeurolings(counts: Map<String, Int>, modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Load all character packs + build the manager once, off the main thread.
    val engine by produceState<NeurolingsEngine?>(null) {
        value = withContext(Dispatchers.Default) {
            val assets = MascotAssets(context.assets)
            val sets = NeurolingsCounts.CHARACTERS.associateWith { assets.loadSet(it) }
            NeurolingsEngine(MascotManager(sets), assets)
        }
    }

    BoxWithConstraints(modifier.fillMaxSize()) {
        val eng = engine ?: return@BoxWithConstraints
        // Stable across recompositions for fixed TV bounds — a fresh instance every recomposition
        // (ShimejiEnvironment has no equals()) would re-key the tick LaunchedEffect below and
        // reset its accumulator every frame, degrading the fixed-timestep loop to jittery ticks.
        val env = remember(constraints.maxWidth, constraints.maxHeight) {
            ShimejiEnvironment(
                left = 0.0,
                top = 0.0,
                right = constraints.maxWidth.toDouble(),
                bottom = constraints.maxHeight.toDouble()
            )
        }

        LaunchedEffect(counts, eng) { eng.manager.sync(counts, env) }

        // Fixed-timestep tick loop: accumulate real elapsed ms, step the engine every
        // ShimejiPhysics.TICK_MS (25fps), and bump frameTick so the render snapshot recomposes.
        // Must be `remember`ed (not a bare property delegate) so the tick coroutine's own writes
        // to it don't get treated as a fresh initial value each recomposition.
        var frameTick by remember { mutableLongStateOf(0L) }
        // Keyed on `eng` alone: `env` is now stable for the composable's lifetime (recreated only
        // if the TV's pixel bounds actually change), so the accumulator (`acc`/`last`) survives
        // across recompositions instead of restarting every tick.
        LaunchedEffect(eng) {
            var acc = 0L
            var last = 0L
            while (true) {
                withFrameNanos { now ->
                    if (last != 0L) acc += (now - last) / 1_000_000
                    last = now
                    while (acc >= ShimejiPhysics.TICK_MS) {
                        eng.manager.tick(env)
                        acc -= ShimejiPhysics.TICK_MS
                        frameTick++
                    }
                }
            }
        }
        @Suppress("UNUSED_EXPRESSION")
        frameTick // read to force recomposition on every tick

        for (r in eng.manager.snapshot()) {
            val bitmap = runCatching { eng.assets.frame(r.setName, r.image) }.getOrNull() ?: continue
            Image(
                bitmap = bitmap,
                contentDescription = null,
                modifier = Modifier.graphicsLayer {
                    // Place the frame's own anchor point at (x, y); mirror to face travel direction.
                    // Pivot the scale around that same foot anchor so the mascot stays planted at
                    // (x, y) as it grows, rather than drifting when scaled about its center.
                    val w = bitmap.width.toFloat()
                    val h = bitmap.height.toFloat()
                    transformOrigin = TransformOrigin(
                        if (w > 0f) r.anchorX / w else 0.5f,
                        if (h > 0f) r.anchorY / h else 1f,
                    )
                    translationX = (r.x - r.anchorX).toFloat()
                    translationY = (r.y - r.anchorY).toFloat()
                    scaleX = if (r.mirrored) -MASCOT_SCALE else MASCOT_SCALE
                    scaleY = MASCOT_SCALE
                }
            )
        }
    }
}
