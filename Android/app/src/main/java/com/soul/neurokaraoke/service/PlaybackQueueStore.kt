package com.soul.neurokaraoke.service

import android.content.Context
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.songFromJson
import com.soul.neurokaraoke.data.toJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Persists the last queue handed to the player so a cold start (app launch, or a
 * Bluetooth / system-media "resume") can rebuild the setlist / user playlist the song
 * came from instead of a single song followed by random autoplay.
 *
 * The full all-songs catalog is stored as a flag (SongCache already holds it);
 * any other queue is stored song-by-song.
 */
object PlaybackQueueStore {

    class SavedQueue(val isCustom: Boolean, val isAllSongs: Boolean, val songs: List<Song>)

    private const val FILE_NAME = "playback_queue.json"
    private val lock = Mutex()
    private val writeScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Volatile
    private var pendingJson: String? = null

    fun save(context: Context, queue: List<Song>, isCustom: Boolean, isAllSongs: Boolean) {
        val root = JSONObject()
            .put("custom", isCustom)
            .put("allSongs", isAllSongs)
        if (!isAllSongs) {
            root.put("songs", JSONArray().apply { queue.forEach { put(it.toJson()) } })
        }
        pendingJson = root.toString()
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        writeScope.launch {
            lock.withLock {
                // Always write the newest snapshot, whichever launch gets here first
                val json = pendingJson ?: return@withLock
                pendingJson = null
                try {
                    file.writeText(json)
                } catch (e: Exception) {
                    if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
                }
            }
        }
    }

    suspend fun load(context: Context): SavedQueue? = withContext(Dispatchers.IO) {
        val file = File(context.applicationContext.filesDir, FILE_NAME)
        lock.withLock {
            // A save may still be queued — read the newest snapshot, not the stale file
            val json = pendingJson ?: if (file.exists()) file.readText() else return@withLock null
            try {
                val root = JSONObject(json)
                val array = root.optJSONArray("songs")
                val songs = if (array == null) emptyList() else {
                    (0 until array.length()).map { songFromJson(array.getJSONObject(it)) }
                }
                SavedQueue(
                    isCustom = root.optBoolean("custom", false),
                    isAllSongs = root.optBoolean("allSongs", false),
                    songs = songs
                )
            } catch (e: Exception) {
                if (com.soul.neurokaraoke.BuildConfig.DEBUG) e.printStackTrace()
                null
            }
        }
    }
}

/** Playable queue item for the phone player (URI + notification / Auto metadata). */
fun Song.toQueueMediaItem(): MediaItem {
    val extras = android.os.Bundle()
    extras.putBoolean("androidx.media3.session.MediaSession.EXTRA_KEY_QUEUE_SUPPORTED", true)
    extras.putInt("androidx.media3.session.MediaSession.EXTRA_KEY_QUEUE_SUPPORTED", 1)
    extras.putBoolean("androidx.media.utils.Extras.SLOT_RESERVATION_QUEUE", true)
    extras.putBoolean("androidx.media.PlaybackStateCompat.Extras.COMMAND_GET_TIMELINE", true)
    extras.putBoolean("android.media.session.extra.QUEUE_SUPPORTED", true)
    extras.putInt("android.media.session.extra.QUEUE_SUPPORTED", 1)

    return MediaItem.Builder()
        .setUri(audioUrl)
        .setMediaId(id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist("$artist • $coverArtist")
                .setAlbumTitle(coverArtist)
                .setArtworkUri(android.net.Uri.parse(coverUrl))
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setIsBrowsable(false)
                .setIsPlayable(true)
                .setExtras(extras)
                .build()
        )
        .build()
}
