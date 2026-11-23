package com.soul.neurokaraoke.ui.tv

import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.drawscope.withTransform
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
 * ([MascotManager]) at a fixed 25fps tick and draws its snapshot in a single [Canvas] pass
 * (zero 25fps recompositions and zero main-thread PNG decoding).
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
        val env = remember(constraints.maxWidth, constraints.maxHeight) {
            ShimejiEnvironment(
                left = 0.0,
                top = 0.0,
                right = constraints.maxWidth.toDouble(),
                bottom = constraints.maxHeight.toDouble()
            )
        }

        // Sync active counts and pre-decode/evict sprite frames off the main thread.
        LaunchedEffect(counts, eng) {
            val activeNames = counts.filterValues { it > 0 }.keys
            withContext(Dispatchers.IO) {
                eng.assets.retainOnly(activeNames)
                for (name in activeNames) {
                    eng.assets.preloadSet(name)
                }
            }
            eng.manager.sync(counts, env)
        }

        // Fixed-timestep tick loop: only read inside Canvas draw phase to avoid recomposition.
        var frameTick by remember { mutableLongStateOf(0L) }
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

        Canvas(modifier = Modifier.fillMaxSize()) {
            // Read frameTick inside the draw phase so ticks invalidate drawing only, never composition.
            if (frameTick < 0L) return@Canvas

            for (r in eng.manager.snapshot()) {
                val bitmap = eng.assets.cachedFrame(r.setName, r.image) ?: continue
                withTransform({
                    translate(
                        left = (r.x - r.anchorX).toFloat(),
                        top = (r.y - r.anchorY).toFloat()
                    )
                    scale(
                        scaleX = if (r.mirrored) -MASCOT_SCALE else MASCOT_SCALE,
                        scaleY = MASCOT_SCALE,
                        pivot = Offset(r.anchorX.toFloat(), r.anchorY.toFloat())
                    )
                }) {
                    drawImage(bitmap)
                }
            }
        }
    }
}
