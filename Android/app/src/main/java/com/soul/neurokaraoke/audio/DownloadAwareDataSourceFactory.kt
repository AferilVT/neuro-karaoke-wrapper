package com.soul.neurokaraoke.audio

import android.net.Uri
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.FileDataSource
import androidx.media3.datasource.TransferListener
import com.soul.neurokaraoke.data.repository.DownloadRepository

/**
 * DataSource.Factory that checks DownloadRepository for local files before
 * delegating to the upstream (network) factory. Transparent to ExoPlayer.
 */
@UnstableApi
class DownloadAwareDataSourceFactory(
    private val upstreamFactory: DataSource.Factory
) : DataSource.Factory {

    override fun createDataSource(): DataSource {
        return DownloadAwareDataSource(upstreamFactory.createDataSource())
    }
}

@UnstableApi
private class DownloadAwareDataSource(
    private val upstream: DataSource
) : DataSource {

    private var activeSource: DataSource? = null
    private val transferListeners = mutableListOf<TransferListener>()

    override fun open(dataSpec: DataSpec): Long {
        // Close previous source if any (should have been closed already)
        close()

        val uri = dataSpec.uri.toString()
        val localPath = DownloadRepository.getLocalAudioPath(uri)

        return if (localPath != null) {
            val fileSource = FileDataSource()
            for (listener in transferListeners) {
                fileSource.addTransferListener(listener)
            }
            val localSpec = dataSpec.buildUpon()
                .setUri(Uri.fromFile(java.io.File(localPath)))
                .build()
            activeSource = fileSource
            fileSource.open(localSpec)
        } else {
            activeSource = upstream
            upstream.open(dataSpec)
        }
    }

    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        return activeSource?.read(buffer, offset, length)
            ?: throw IllegalStateException("DataSource not opened")
    }

    override fun addTransferListener(transferListener: TransferListener) {
        transferListeners.add(transferListener)
        upstream.addTransferListener(transferListener)
    }

    override fun getUri(): Uri? = activeSource?.uri

    override fun getResponseHeaders(): Map<String, List<String>> {
        return activeSource?.responseHeaders ?: emptyMap()
    }

    override fun close() {
        activeSource?.close()
        activeSource = null
    }
}
