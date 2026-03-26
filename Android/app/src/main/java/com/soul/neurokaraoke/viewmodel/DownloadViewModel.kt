package com.soul.neurokaraoke.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.soul.neurokaraoke.data.model.Song
import com.soul.neurokaraoke.data.repository.DownloadRepository
import com.soul.neurokaraoke.data.repository.DownloadedSong
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class DownloadViewModel(application: Application) : AndroidViewModel(application) {

    init {
        DownloadRepository.initialize(application)
    }

    val downloads: StateFlow<List<DownloadedSong>> = DownloadRepository.downloads
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val downloadProgress: StateFlow<Map<String, Float>> = DownloadRepository.downloadProgress
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    fun isDownloaded(songId: String): Boolean = DownloadRepository.isDownloaded(songId)

    fun downloadSong(song: Song) {
        viewModelScope.launch {
            DownloadRepository.downloadSong(song)
        }
    }

    fun removeSong(songId: String) {
        DownloadRepository.removeSong(songId)
    }

    fun removeAll() {
        DownloadRepository.removeAll()
    }

    fun downloadPlaylistSongs(songs: List<Song>) {
        viewModelScope.launch {
            songs.forEach { song ->
                launch { DownloadRepository.downloadSong(song) }
            }
        }
    }

    fun getTotalSizeFormatted(): String {
        val bytes = DownloadRepository.getTotalSizeBytes()
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${"%.1f".format(bytes / 1024f)} KB"
            bytes < 1024 * 1024 * 1024 -> "${"%.1f".format(bytes / (1024f * 1024f))} MB"
            else -> "${"%.2f".format(bytes / (1024f * 1024f * 1024f))} GB"
        }
    }
}
