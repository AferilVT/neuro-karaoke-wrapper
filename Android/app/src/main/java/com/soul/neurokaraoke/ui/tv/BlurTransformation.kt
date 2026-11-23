package com.soul.neurokaraoke.ui.tv

import android.graphics.Bitmap
import coil.size.Size
import coil.transform.Transformation

/**
 * Cross-API blur for the ambient background art.
 *
 * `Modifier.blur()` is backed by RenderEffect and is a no-op below API 31, so on
 * older Android TV / Fire OS devices the immersive backdrop rendered as a sharp
 * cover behind the scrim (reported on-device). This downsamples the bitmap and
 * box-blurs it on the CPU — cheap because it runs on the tiny downsampled bitmap,
 * off the main thread (Coil calls [transform] on a worker) — then Coil upscales the
 * result to fill the screen. Smooth on every API level and lighter than full-res.
 */
class BlurTransformation(
    private val radius: Int = 8,
    private val sampling: Int = 8,
) : Transformation {

    companion object {
        val Default = BlurTransformation()
    }

    override val cacheKey: String = "blur(r=$radius,s=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = (input.width / sampling).coerceAtLeast(1)
        val h = (input.height / sampling).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, w, h, true)
        val out = if (scaled.config == Bitmap.Config.ARGB_8888 && scaled.isMutable && scaled !== input) {
            scaled
        } else {
            val copy = scaled.copy(Bitmap.Config.ARGB_8888, true)
            if (scaled !== input) scaled.recycle()
            copy
        }
        val src = IntArray(w * h)
        val tmp = IntArray(w * h)
        out.getPixels(src, 0, w, 0, 0, w, h)
        boxBlur(src, tmp, w, h, radius)
        boxBlur(src, tmp, w, h, radius) // two passes approximate a gaussian
        out.setPixels(src, 0, w, 0, 0, w, h)
        return out
    }

    /** O(W * H) separable sliding-window box blur (independent of radius). */
    private fun boxBlur(src: IntArray, tmp: IntArray, w: Int, h: Int, radius: Int) {
        if (w < 2 || h < 2) return
        val r = radius.coerceIn(1, minOf(w, h) - 1)
        val div = r * 2 + 1
        val wMax = w - 1
        val hMax = h - 1

        // Horizontal pass: src -> tmp
        for (y in 0 until h) {
            val row = y * w
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (k in -r..r) {
                val c = src[row + k.coerceIn(0, wMax)]
                a += (c ushr 24) and 0xFF
                rr += (c ushr 16) and 0xFF
                gg += (c ushr 8) and 0xFF
                bb += c and 0xFF
            }
            for (x in 0 until w) {
                tmp[row + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
                val addC = src[row + (x + r + 1).coerceAtMost(wMax)]
                val subC = src[row + (x - r).coerceAtLeast(0)]
                a += ((addC ushr 24) and 0xFF) - ((subC ushr 24) and 0xFF)
                rr += ((addC ushr 16) and 0xFF) - ((subC ushr 16) and 0xFF)
                gg += ((addC ushr 8) and 0xFF) - ((subC ushr 8) and 0xFF)
                bb += (addC and 0xFF) - (subC and 0xFF)
            }
        }

        // Vertical pass: tmp -> src
        for (x in 0 until w) {
            var a = 0; var rr = 0; var gg = 0; var bb = 0
            for (k in -r..r) {
                val c = tmp[k.coerceIn(0, hMax) * w + x]
                a += (c ushr 24) and 0xFF
                rr += (c ushr 16) and 0xFF
                gg += (c ushr 8) and 0xFF
                bb += c and 0xFF
            }
            for (y in 0 until h) {
                src[y * w + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
                val addC = tmp[(y + r + 1).coerceAtMost(hMax) * w + x]
                val subC = tmp[(y - r).coerceAtLeast(0) * w + x]
                a += ((addC ushr 24) and 0xFF) - ((subC ushr 24) and 0xFF)
                rr += ((addC ushr 16) and 0xFF) - ((subC ushr 16) and 0xFF)
                gg += ((addC ushr 8) and 0xFF) - ((subC ushr 8) and 0xFF)
                bb += (addC and 0xFF) - (subC and 0xFF)
            }
        }
    }
}
