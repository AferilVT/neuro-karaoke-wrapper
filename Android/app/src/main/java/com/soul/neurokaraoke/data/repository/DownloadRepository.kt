package com.soul.neurokaraoke.data.repository

import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import com.soul.neurokaraoke.MainActivity
import com.soul.neurokaraoke.NeuroKaraokeApp
import com.soul.neurokaraoke.R
import com.soul.neurokaraoke.data.model.Singer
import com.soul.neurokaraoke.data.model.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.atomic.AtomicInteger

data class DownloadedSong(
    val id: String,
    val title: String,
    val artist: String,
    val coverArtist: String,
    val coverUrl: String,
    val audioUrl: String,
    val singer: Singer,
    val localAudioPath: String,
    val localCoverPath: String?,
    val fileSize: Long,
    val downloadedAt: Long
) {
    fun toSong(): Song = Song(
        id = id,
        title = title,
        artist = artist,
        coverUrl = coverUrl,
        audioUrl = audioUrl,
        singer = singer
    )
}

object DownloadRepository {

    private var context: Context? = null
    private var downloadsDir: File? = null
    private var audioDir: File? = null
    private var coversDir: File? = null
    private var metadataFile: File? = null

    private val _downloads = MutableStateFlow<List<DownloadedSong>>(emptyList())
    val downloads: StateFlow<List<DownloadedSong>> = _downloads.asStateFlow()

    private val _downloadProgress = MutableStateFlow<Map<String, Float>>(emptyMap())
    val downloadProgress: StateFlow<Map<String, Float>> = _downloadProgress.asStateFlow()

    private val downloadSemaphore = Semaphore(3)

    // Notification tracking
    private const val NOTIFICATION_ID_PROGRESS = 9001
    private const val NOTIFICATION_ID_COMPLETE = 9002
    private val activeDownloadCount = AtomicInteger(0)
    private val completedDownloadCount = AtomicInteger(0)
    private val failedDownloadCount = AtomicInteger(0)
    private var totalQueuedCount = AtomicInteger(0)

    @Synchronized
    fun initialize(context: Context) {
        if (this.context != null) return
        this.context = context.applicationContext

        downloadsDir = File(context.filesDir, "downloads").also { it.mkdirs() }
        audioDir = File(downloadsDir, "audio").also { it.mkdirs() }
        coversDir = File(downloadsDir, "covers").also { it.mkdirs() }
        metadataFile = File(downloadsDir, "download_metadata.json")

        loadMetadata()
    }

    fun isDownloaded(songId: String): Boolean {
        return _downloads.value.any { it.id == songId }
    }

    fun getLocalAudioPath(audioUrl: String): String? {
        val downloaded = _downloads.value.find { it.audioUrl == audioUrl } ?: return null
        val file = File(downloaded.localAudioPath)
        return if (file.exists()) downloaded.localAudioPath else null
    }

    suspend fun downloadSong(song: Song) {
        if (isDownloaded(song.id)) return
        if (song.audioUrl.isBlank()) return

        beginDownloadBatch(1)
        activeDownloadCount.incrementAndGet()

        downloadSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    // Update progress
                    updateProgress(song.id, 0f)
                    showProgressNotification(song.title)

                    val audioFile = File(audioDir, "${song.id}.mp3")
                    val coverFile = File(coversDir, "${song.id}.jpg")

                    // Download audio
                    downloadFile(song.audioUrl, audioFile) { progress ->
                        updateProgress(song.id, progress * 0.9f) // 90% for audio
                    }

                    // Download cover
                    var localCoverPath: String? = null
                    if (song.coverUrl.isNotBlank()) {
                        try {
                            downloadFile(song.coverUrl, coverFile) { progress ->
                                updateProgress(song.id, 0.9f + progress * 0.1f)
                            }
                            localCoverPath = coverFile.absolutePath
                        } catch (_: Exception) {
                            // Cover download failure is non-critical
                        }
                    }

                    updateProgress(song.id, 1f)

                    val downloaded = DownloadedSong(
                        id = song.id,
                        title = song.title,
                        artist = song.artist,
                        coverArtist = song.coverArtist,
                        coverUrl = song.coverUrl,
                        audioUrl = song.audioUrl,
                        singer = song.singer,
                        localAudioPath = audioFile.absolutePath,
                        localCoverPath = localCoverPath,
                        fileSize = audioFile.length(),
                        downloadedAt = System.currentTimeMillis()
                    )

                    val current = _downloads.value.toMutableList()
                    current.add(downloaded)
                    _downloads.value = current

                    saveMetadata()
                    completedDownloadCount.incrementAndGet()
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Clean up partial files
                    File(audioDir, "${song.id}.mp3").delete()
                    File(coversDir, "${song.id}.jpg").delete()
                    failedDownloadCount.incrementAndGet()
                } finally {
                    removeProgress(song.id)
                    if (activeDownloadCount.decrementAndGet() == 0) {
                        showCompletionNotification()
                    } else {
                        // Update progress notification with next song info
                        val nextSongId = _downloadProgress.value.keys.firstOrNull()
                        if (nextSongId != null) {
                            showProgressNotification(song.title)
                        }
                    }
                }
            }
        }
    }

    fun removeSong(songId: String) {
        val song = _downloads.value.find { it.id == songId } ?: return
        File(song.localAudioPath).delete()
        song.localCoverPath?.let { File(it).delete() }

        _downloads.value = _downloads.value.filter { it.id != songId }
        saveMetadata()
    }

    fun removeAll() {
        _downloads.value.forEach { song ->
            File(song.localAudioPath).delete()
            song.localCoverPath?.let { File(it).delete() }
        }
        _downloads.value = emptyList()
        saveMetadata()
    }

    fun getTotalSizeBytes(): Long {
        return _downloads.value.sumOf { it.fileSize }
    }

    private fun updateProgress(songId: String, progress: Float) {
        _downloadProgress.update { current ->
            current + (songId to progress)
        }
    }

    private fun removeProgress(songId: String) {
        _downloadProgress.update { current ->
            current - songId
        }
    }

    private fun downloadFile(
        urlString: String,
        outputFile: File,
        onProgress: (Float) -> Unit
    ) {
        var connection: HttpURLConnection? = null
        try {
            val url = URL(urlString)
            connection = url.openConnection() as HttpURLConnection
            connection.requestMethod = "GET"
            connection.connectTimeout = 15000
            connection.readTimeout = 30000
            connection.setRequestProperty("User-Agent", "NeuroKaraoke Android App")

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw Exception("HTTP error: $responseCode")
            }

            val contentLength = connection.contentLength.toLong()
            var bytesRead = 0L

            connection.inputStream.buffered().use { input ->
                outputFile.outputStream().buffered().use { output ->
                    val buffer = ByteArray(8192)
                    var read: Int
                    while (input.read(buffer).also { read = it } != -1) {
                        output.write(buffer, 0, read)
                        bytesRead += read
                        if (contentLength > 0) {
                            onProgress(bytesRead.toFloat() / contentLength)
                        }
                    }
                }
            }

            if (contentLength <= 0) {
                onProgress(1f)
            }
        } finally {
            connection?.disconnect()
        }
    }

    /**
     * Call before starting a batch (or single) download to reset counters.
     */
    private fun beginDownloadBatch(count: Int) {
        if (activeDownloadCount.get() == 0) {
            // New batch — reset counters
            completedDownloadCount.set(0)
            failedDownloadCount.set(0)
            totalQueuedCount.set(count)
        } else {
            // Downloads already in flight — just grow the total
            totalQueuedCount.addAndGet(count)
        }
    }

    private fun hasNotificationPermission(): Boolean {
        val ctx = context ?: return false
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    private fun showProgressNotification(songTitle: String) {
        val ctx = context ?: return
        if (!hasNotificationPermission()) return

        val done = completedDownloadCount.get() + failedDownloadCount.get()
        val total = totalQueuedCount.get()
        val remaining = total - done

        val contentText = if (total > 1) {
            "Downloading \"$songTitle\" ($remaining remaining)"
        } else {
            "Downloading \"$songTitle\""
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(ctx, NeuroKaraokeApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle("Downloading songs")
            .setContentText(contentText)
            .setProgress(total, done, false)
            .setOngoing(true)
            .setSilent(true)
            .setContentIntent(pendingIntent)
            .build()

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID_PROGRESS, notification)
    }

    private fun showCompletionNotification() {
        val ctx = context ?: return
        if (!hasNotificationPermission()) return

        val manager = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        // Dismiss progress notification
        manager.cancel(NOTIFICATION_ID_PROGRESS)

        val completed = completedDownloadCount.get()
        val failed = failedDownloadCount.get()

        val title = when {
            failed == 0 -> "Download complete"
            completed == 0 -> "Download failed"
            else -> "Download complete"
        }
        val text = when {
            failed == 0 && completed == 1 -> "1 song downloaded"
            failed == 0 -> "$completed songs downloaded"
            completed == 0 && failed == 1 -> "1 song failed to download"
            completed == 0 -> "$failed songs failed to download"
            else -> "$completed downloaded, $failed failed"
        }

        val intent = Intent(ctx, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            ctx, 0, intent,
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )

        val notification = NotificationCompat.Builder(ctx, NeuroKaraokeApp.CHANNEL_DOWNLOADS)
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        manager.notify(NOTIFICATION_ID_COMPLETE, notification)
    }

    private fun loadMetadata() {
        val file = metadataFile ?: return
        if (!file.exists()) return

        try {
            val json = file.readText()
            val array = JSONArray(json)
            val songs = mutableListOf<DownloadedSong>()

            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val localAudioPath = obj.getString("localAudioPath")

                // Skip entries where audio file no longer exists
                if (!File(localAudioPath).exists()) continue

                songs.add(
                    DownloadedSong(
                        id = obj.getString("id"),
                        title = obj.getString("title"),
                        artist = obj.getString("artist"),
                        coverArtist = obj.getString("coverArtist"),
                        coverUrl = obj.optString("coverUrl", ""),
                        audioUrl = obj.getString("audioUrl"),
                        singer = try {
                            Singer.valueOf(obj.getString("singer"))
                        } catch (_: Exception) {
                            Singer.NEURO
                        },
                        localAudioPath = localAudioPath,
                        localCoverPath = obj.optString("localCoverPath").takeIf { it.isNotBlank() },
                        fileSize = obj.optLong("fileSize", 0L),
                        downloadedAt = obj.optLong("downloadedAt", 0L)
                    )
                )
            }

            _downloads.value = songs
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun saveMetadata() {
        val file = metadataFile ?: return
        try {
            val array = JSONArray()
            for (song in _downloads.value) {
                val obj = JSONObject().apply {
                    put("id", song.id)
                    put("title", song.title)
                    put("artist", song.artist)
                    put("coverArtist", song.coverArtist)
                    put("coverUrl", song.coverUrl)
                    put("audioUrl", song.audioUrl)
                    put("singer", song.singer.name)
                    put("localAudioPath", song.localAudioPath)
                    put("localCoverPath", song.localCoverPath ?: "")
                    put("fileSize", song.fileSize)
                    put("downloadedAt", song.downloadedAt)
                }
                array.put(obj)
            }

            // Atomic write: write to tmp then rename
            val tmpFile = File(file.parentFile, "${file.name}.tmp")
            tmpFile.writeText(array.toString(2))
            tmpFile.renameTo(file)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
