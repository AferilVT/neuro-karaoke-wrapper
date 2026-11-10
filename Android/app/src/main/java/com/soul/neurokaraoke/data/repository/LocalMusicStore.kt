package com.soul.neurokaraoke.data.repository

import android.content.Context
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.util.Log
import org.json.JSONObject
import java.io.File

/** A song imported into the app's private `local_music` folder. */
data class LocalSong(
    val uri: Uri,
    val fileName: String,
    val title: String,
    val artist: String,
    val coverArtists: String,
    /** Absolute path of the cover image (user-picked or extracted from the file), or null. */
    val coverPath: String?
)

/**
 * On-device store for imported local music. Audio files live in `filesDir/local_music/`;
 * user edits (title, artist, cover artist, custom cover) live in `local_music/metadata.json`
 * keyed by file name, and cover images in `local_music/covers/`. Nothing is uploaded.
 *
 * All functions do disk I/O — call them off the main thread.
 */
object LocalMusicStore {
    private const val TAG = "LocalMusicStore"
    private const val DIR = "local_music"
    private const val META_FILE = "metadata.json"
    private const val COVERS_DIR = "covers"
    /** Suffix of covers extracted from the audio file (as opposed to user-picked ones). */
    const val EMBEDDED_COVER_SUFFIX = ".embedded.jpg"

    private data class Overrides(
        val title: String?,
        val artist: String?,
        val coverArtists: String?,
        val coverFile: String?
    )

    private fun musicDir(context: Context) = File(context.filesDir, DIR)
    private fun coversDir(context: Context) = File(musicDir(context), COVERS_DIR)
    private fun metaFile(context: Context) = File(musicDir(context), META_FILE)

    @Synchronized
    private fun readMeta(context: Context): MutableMap<String, Overrides> {
        val file = metaFile(context)
        if (!file.exists()) return mutableMapOf()
        return try {
            val root = JSONObject(file.readText())
            val map = mutableMapOf<String, Overrides>()
            root.keys().forEach { key ->
                val o = root.getJSONObject(key)
                map[key] = Overrides(
                    title = o.optString("title").takeIf { it.isNotBlank() },
                    artist = o.optString("artist").takeIf { it.isNotBlank() },
                    coverArtists = o.optString("coverArtists").takeIf { it.isNotBlank() },
                    coverFile = o.optString("coverFile").takeIf { it.isNotBlank() }
                )
            }
            map
        } catch (e: Exception) {
            Log.e(TAG, "Failed to read metadata: ${e.message}")
            mutableMapOf()
        }
    }

    @Synchronized
    private fun writeMeta(context: Context, map: Map<String, Overrides>) {
        val root = JSONObject()
        map.forEach { (key, o) ->
            root.put(key, JSONObject().apply {
                o.title?.let { put("title", it) }
                o.artist?.let { put("artist", it) }
                o.coverArtists?.let { put("coverArtists", it) }
                o.coverFile?.let { put("coverFile", it) }
            })
        }
        val file = metaFile(context)
        file.parentFile?.mkdirs()
        val tmp = File(file.parentFile, "$META_FILE.tmp")
        tmp.writeText(root.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(root.toString())
            tmp.delete()
        }
    }

    /** Loads every imported song, applying user edits on top of the file's own tags. */
    fun loadAll(context: Context): List<LocalSong> {
        val dir = musicDir(context)
        if (!dir.exists()) return emptyList()
        val meta = readMeta(context)
        return dir.listFiles()
            ?.filter { it.isFile && it.extension.equals("mp3", ignoreCase = true) }
            ?.sortedBy { it.lastModified() }
            ?.map { buildSong(context, it, meta[it.name]) }
            .orEmpty()
    }

    private fun buildSong(context: Context, file: File, overrides: Overrides?): LocalSong {
        var tagTitle: String? = null
        var tagArtist: String? = null
        var embeddedCover: String? = null
        val retriever = MediaMetadataRetriever()
        try {
            retriever.setDataSource(file.absolutePath)
            tagTitle = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
            tagArtist = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_ARTIST)
            if (overrides?.coverFile == null) {
                embeddedCover = embeddedCoverPath(context, file, retriever)
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        val customCover = overrides?.coverFile
            ?.let { File(coversDir(context), it) }
            ?.takeIf { it.exists() }
            ?.absolutePath

        return LocalSong(
            uri = Uri.fromFile(file),
            fileName = file.name,
            title = overrides?.title ?: tagTitle?.takeIf { it.isNotBlank() } ?: file.nameWithoutExtension,
            artist = overrides?.artist ?: tagArtist?.takeIf { it.isNotBlank() } ?: "Unknown Artist",
            coverArtists = overrides?.coverArtists.orEmpty(),
            coverPath = customCover ?: embeddedCover
        )
    }

    /** Extracts the file's embedded album art once and caches it next to the custom covers. */
    private fun embeddedCoverPath(context: Context, file: File, retriever: MediaMetadataRetriever): String? {
        val cached = File(coversDir(context), "${file.name}$EMBEDDED_COVER_SUFFIX")
        if (cached.exists()) return cached.absolutePath
        val bytes = retriever.embeddedPicture ?: return null
        return try {
            cached.parentFile?.mkdirs()
            cached.writeBytes(bytes)
            cached.absolutePath
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cache embedded art for ${file.name}: ${e.message}")
            null
        }
    }

    /** Copies a picked audio file into the store and returns the loaded song. */
    fun import(context: Context, source: Uri): LocalSong? {
        val dir = musicDir(context).apply { mkdirs() }
        var title = "Unknown"
        val retriever = MediaMetadataRetriever()
        try {
            context.contentResolver.openFileDescriptor(source, "r")?.use { pfd ->
                retriever.setDataSource(pfd.fileDescriptor)
                title = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_TITLE)
                    ?: source.lastPathSegment?.substringAfterLast("/")?.removeSuffix(".mp3")
                    ?: "Unknown"
            }
        } catch (_: Exception) {
        } finally {
            try { retriever.release() } catch (_: Exception) { }
        }

        val fileName = "${title.replace(Regex("[^a-zA-Z0-9._-]"), "_")}_${System.currentTimeMillis()}.mp3"
        val dest = File(dir, fileName)
        return try {
            context.contentResolver.openInputStream(source)?.use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            buildSong(context, dest, null)
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}")
            dest.delete()
            null
        }
    }

    /**
     * Saves user edits for [fileName]. Blank text fields fall back to the file's tags.
     * [newCover]: a picked image to copy in; [removeCover]: drop the custom cover
     * (falls back to embedded art). Returns the reloaded song.
     */
    fun saveDetails(
        context: Context,
        fileName: String,
        title: String,
        artist: String,
        coverArtists: String,
        newCover: Uri?,
        removeCover: Boolean
    ): LocalSong? {
        val audio = File(musicDir(context), fileName)
        if (!audio.exists()) return null
        val meta = readMeta(context)
        val old = meta[fileName]
        var coverFile = old?.coverFile

        if (newCover != null || removeCover) {
            coverFile?.let { File(coversDir(context), it).delete() }
            coverFile = null
        }
        if (newCover != null) {
            // Unique name per save so image loaders don't serve a stale cached cover.
            val name = "$fileName.${System.currentTimeMillis()}.img"
            val dest = File(coversDir(context).apply { mkdirs() }, name)
            try {
                context.contentResolver.openInputStream(newCover)?.use { input ->
                    dest.outputStream().use { output -> input.copyTo(output) }
                }
                if (dest.exists() && dest.length() > 0) coverFile = name
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy cover: ${e.message}")
                dest.delete()
            }
        }

        meta[fileName] = Overrides(
            title = title.trim().ifBlank { null },
            artist = artist.trim().ifBlank { null },
            coverArtists = coverArtists.trim().ifBlank { null },
            coverFile = coverFile
        )
        writeMeta(context, meta)
        return buildSong(context, audio, meta[fileName])
    }

    /** Deletes the audio file plus its metadata and covers. */
    fun delete(context: Context, fileName: String) {
        File(musicDir(context), fileName).delete()
        coversDir(context).listFiles()
            ?.filter { it.name.startsWith("$fileName.") }
            ?.forEach { it.delete() }
        val meta = readMeta(context)
        if (meta.remove(fileName) != null) writeMeta(context, meta)
    }
}
