package com.soul.neurokaraoke.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads cover-art bitmaps for the AA UI and caches them in a bounded LRU cache.
 * Reuses the app's singleton Coil ImageLoader instead of creating a duplicate pool.
 */
class CarCoverCache(private val context: Context) {
    private val cache = LruCache<String, Bitmap>(MAX_ENTRIES)
    private val inFlight = ConcurrentHashMap.newKeySet<String>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    fun get(url: String?): Bitmap? = url?.let { cache.get(it) }

    /**
     * Kick off downloads for the given URLs. Calls onAnyReady whenever a new
     * bitmap lands so the caller can invalidate the screen.
     */
    fun prefetch(urls: List<String>, onAnyReady: () -> Unit) {
        val loader = context.imageLoader
        urls.asSequence()
            .filter { it.isNotBlank() && cache.get(it) == null }
            .forEach { url ->
                if (!inFlight.add(url)) return@forEach
                scope.launch {
                    try {
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .size(SIZE_PX)
                            .allowHardware(false)
                            .crossfade(false)
                            .build()
                        val drawable = loader.execute(req).drawable ?: return@launch
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                            ?: drawable.toBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
                        cache.put(url, bmp)
                        onAnyReady()
                    } catch (_: Exception) {
                        // ignore failed artwork prefetch
                    } finally {
                        inFlight.remove(url)
                    }
                }
            }
    }

    companion object {
        private const val SIZE_PX = 320
        private const val MAX_ENTRIES = 50
    }
}
