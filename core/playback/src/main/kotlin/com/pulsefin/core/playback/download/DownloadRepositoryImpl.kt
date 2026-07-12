package com.pulsefin.core.playback.download

import android.content.Context
import android.net.Uri
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.model.SongDownload
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.StreamUrlResolver
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update

class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val streamUrlResolver: StreamUrlResolver,
) : DownloadRepository {

    private val _downloads = MutableStateFlow<Map<String, SongDownload>>(emptyMap())

    init {
        // Media3's own index survives process death; seed from it so cold-start UI is correct
        // even before any DownloadManager.Listener callback fires. Use downloadIndex.getDownloads()
        // (not currentDownloads, which excludes COMPLETED/FAILED) so terminal states are seeded too.
        downloadManager.downloadIndex.getDownloads().use { cursor ->
            while (cursor.moveToNext()) {
                updateFrom(cursor.download)
            }
        }
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                updateFrom(download)
            }
            override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                _downloads.update { it - download.request.id }
            }
        })
    }

    override fun observeDownloads(): Flow<Map<String, SongDownload>> = _downloads.asStateFlow()

    override fun observeDownload(songId: String): Flow<SongDownload?> =
        _downloads.map { it[songId] }.distinctUntilChanged()

    override suspend fun download(song: Song) {
        val uri = streamUrlResolver.resolveStreamUrl(song.id.value) ?: return
        val request = DownloadRequest.Builder(song.id.value, Uri.parse(uri)).build()
        DownloadService.sendAddDownload(context, PulseFinDownloadService::class.java, request, false)
    }

    override suspend fun downloadAll(songs: List<Song>) {
        songs.forEach { download(it) }
    }

    override suspend fun remove(songId: String) {
        DownloadService.sendRemoveDownload(context, PulseFinDownloadService::class.java, songId, false)
    }

    override suspend fun removeAll(songIds: List<String>) {
        songIds.forEach { remove(it) }
    }

    private fun updateFrom(download: Download) {
        val state = when (download.state) {
            Download.STATE_QUEUED, Download.STATE_RESTARTING -> DownloadState.QUEUED
            Download.STATE_DOWNLOADING -> DownloadState.DOWNLOADING
            Download.STATE_COMPLETED -> DownloadState.COMPLETED
            Download.STATE_FAILED -> DownloadState.FAILED
            else -> DownloadState.NONE
        }
        val percent = download.percentDownloaded.let { if (it.isNaN()) 0 else it.toInt() }
        _downloads.update {
            it + (download.request.id to SongDownload(
                download.request.id, state, percent, download.bytesDownloaded, download.contentLength,
            ))
        }
    }
}
