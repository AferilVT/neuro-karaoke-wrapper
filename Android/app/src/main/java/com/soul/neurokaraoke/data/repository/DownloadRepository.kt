package com.soul.neurokaraoke.data.repository

import android.content.Context
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
        val songId = audioUrl.hashCode().toString()
        val downloaded = _downloads.value.find { it.id == songId } ?: return null
        val file = File(downloaded.localAudioPath)
        return if (file.exists()) downloaded.localAudioPath else null
    }

    suspend fun downloadSong(song: Song) {
        if (isDownloaded(song.id)) return
        if (song.audioUrl.isBlank()) return

        downloadSemaphore.withPermit {
            withContext(Dispatchers.IO) {
                try {
                    // Update progress
                    updateProgress(song.id, 0f)

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
                } catch (e: Exception) {
                    e.printStackTrace()
                    // Clean up partial files
                    File(audioDir, "${song.id}.mp3").delete()
                    File(coversDir, "${song.id}.jpg").delete()
                } finally {
                    removeProgress(song.id)
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
