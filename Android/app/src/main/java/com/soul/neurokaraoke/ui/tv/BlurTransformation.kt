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

    override val cacheKey: String = "blur(r=$radius,s=$sampling)"

    override suspend fun transform(input: Bitmap, size: Size): Bitmap {
        val w = (input.width / sampling).coerceAtLeast(1)
        val h = (input.height / sampling).coerceAtLeast(1)
        val scaled = Bitmap.createScaledBitmap(input, w, h, true)
        val out = scaled.copy(Bitmap.Config.ARGB_8888, true)
        boxBlur(out, radius)
        boxBlur(out, radius) // two passes approximate a gaussian
        return out
    }

    private fun boxBlur(bmp: Bitmap, radius: Int) {
        val w = bmp.width
        val h = bmp.height
        if (w < 2 || h < 2) return
        val r = radius.coerceIn(1, minOf(w, h) - 1)
        val div = r * 2 + 1
        val src = IntArray(w * h)
        bmp.getPixels(src, 0, w, 0, 0, w, h)
        val tmp = IntArray(w * h)

        // Horizontal pass: src -> tmp
        for (y in 0 until h) {
            val row = y * w
            for (x in 0 until w) {
                var a = 0; var rr = 0; var gg = 0; var bb = 0
                for (k in -r..r) {
                    val c = src[row + (x + k).coerceIn(0, w - 1)]
                    a += (c ushr 24) and 0xFF
                    rr += (c ushr 16) and 0xFF
                    gg += (c ushr 8) and 0xFF
                    bb += c and 0xFF
                }
                tmp[row + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
            }
        }
        // Vertical pass: tmp -> src
        for (x in 0 until w) {
            for (y in 0 until h) {
                var a = 0; var rr = 0; var gg = 0; var bb = 0
                for (k in -r..r) {
                    val c = tmp[(y + k).coerceIn(0, h - 1) * w + x]
                    a += (c ushr 24) and 0xFF
                    rr += (c ushr 16) and 0xFF
                    gg += (c ushr 8) and 0xFF
                    bb += c and 0xFF
                }
                src[y * w + x] = ((a / div) shl 24) or ((rr / div) shl 16) or ((gg / div) shl 8) or (bb / div)
            }
        }
        bmp.setPixels(src, 0, w, 0, 0, w, h)
    }
}
