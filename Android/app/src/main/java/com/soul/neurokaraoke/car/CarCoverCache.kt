package com.soul.neurokaraoke.car

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Downloads cover-art bitmaps for the AA UI and caches them in-memory.
 * Car App Library's CarIcon needs a Bitmap synchronously — async-prefetch
 * then re-invalidate the Screen when bitmaps arrive.
 */
class CarCoverCache(private val context: Context) {
    private val cache = ConcurrentHashMap<String, Bitmap>()
    private val inFlight = ConcurrentHashMap<String, Boolean>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val mutex = Mutex()

    private val loader: ImageLoader by lazy {
        ImageLoader.Builder(context)
            .crossfade(false)
            .build()
    }

    fun get(url: String?): Bitmap? = url?.let { cache[it] }

    /**
     * Kick off downloads for the given URLs. Calls onAnyReady whenever a new
     * bitmap lands so the caller can invalidate the screen.
     */
    fun prefetch(urls: List<String>, onAnyReady: () -> Unit) {
        urls.filter { it.isNotBlank() && cache[it] == null && inFlight[it] != true }
            .forEach { url ->
                inFlight[url] = true
                scope.launch {
                    try {
                        val req = ImageRequest.Builder(context)
                            .data(url)
                            .size(SIZE_PX)
                            .allowHardware(false)
                            .build()
                        val drawable = loader.execute(req).drawable ?: return@launch
                        val bmp = (drawable as? BitmapDrawable)?.bitmap
                            ?: drawable.toBitmap(SIZE_PX, SIZE_PX, Bitmap.Config.ARGB_8888)
                        mutex.withLock {
                            cache[url] = bmp
                            inFlight.remove(url)
                        }
                        onAnyReady()
                    } catch (_: Exception) {
                        inFlight.remove(url)
                    }
                }
            }
    }

    companion object {
        private const val SIZE_PX = 320
    }
}
