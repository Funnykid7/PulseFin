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

    // Falls back to the last metadata we actually saw for a song when a later join misses (e.g.
    // the song was dropped from Room by a subsequent refreshLibrary() while still downloaded) —
    // otherwise a transient miss would REPLACE an already-good row with blank title/artist/art.
    private data class SongMetadata(val title: String, val artist: String, val artworkUrl: String?)
    private val lastKnownMetadata = mutableMapOf<String, SongMetadata>()

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
                        val metadata = if (song != null) {
                            SongMetadata(song.title, song.artistName, song.artworkUrl)
                                .also { lastKnownMetadata[songId] = it }
                        } else {
                            lastKnownMetadata[songId]
                        }
                        downloadDao.upsert(
                            DownloadEntity(
                                songId,
                                metadata?.title.orEmpty(),
                                metadata?.artist.orEmpty(),
                                metadata?.artworkUrl,
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
                        lastKnownMetadata.remove(removedSongId)
                    }
                    previousSongIds = downloads.keys
                }
        }
    }
}
