package com.pulsefin.app.download

import com.pulsefin.core.data.local.DownloadDao
import com.pulsefin.core.data.local.DownloadEntity
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Bridges the provider-agnostic [DownloadRepository] (Media3-backed, in :core:playback) to Room's
 * [DownloadDao] (in :core:data), so downloaded-state UI can read from Room like the rest of the
 * library. Lives in :app rather than either core module because :core:playback and :core:data may
 * not depend on each other — mirrors how [com.pulsefin.app.playback.PlaybackScrobbler] bridges
 * PlaybackController to MediaRepository for scrobbling.
 */
class DownloadStateSync(
    private val downloadRepository: DownloadRepository,
    private val downloadDao: DownloadDao,
    private val mediaRepository: MediaRepository,
) {
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    fun start() {
        scope.launch {
            var previousSongIds = emptySet<String>()
            // combine() reuses each flow's latest emitted value rather than re-querying Room on
            // every download progress tick (which fired multiple times per second on Main).
            combine(
                downloadRepository.observeDownloads(),
                mediaRepository.observeSongs(),
            ) { downloads, songs -> downloads to songs.associateBy { it.id.value } }
                .collect { (downloads, songsById) ->
                    downloads.forEach { (songId, download) ->
                        val song = songsById[songId]
                        downloadDao.upsert(
                            DownloadEntity(
                                songId,
                                song?.title.orEmpty(),
                                song?.artistName.orEmpty(),
                                song?.artworkUrl,
                                download.state.name,
                                download.progressPercent,
                                download.bytesDownloaded,
                                download.totalBytes,
                                System.currentTimeMillis(),
                            ),
                        )
                    }
                    (previousSongIds - downloads.keys).forEach { removedSongId ->
                        downloadDao.delete(removedSongId)
                    }
                    previousSongIds = downloads.keys
                }
        }
    }
}
