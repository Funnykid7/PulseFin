package com.pulsefin.core.playback.download

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadRequest
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.Requirements
import com.pulsefin.core.common.dispatchers.AppDispatchers
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.model.SongDownload
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.StreamUrlResolver
import java.io.IOException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext

private const val TAG = "DownloadRepository"

class DownloadRepositoryImpl(
    private val context: Context,
    private val downloadManager: DownloadManager,
    private val streamUrlResolver: StreamUrlResolver,
    private val dispatchers: AppDispatchers,
) : DownloadRepository {

    private val _downloads = MutableStateFlow<Map<String, SongDownload>>(emptyMap())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    // Ids the listener has already reported on. The cold-start disk-index seed below runs async
    // and can resolve after a live listener update for the same id — without this, the seed's now-
    // stale snapshot would overwrite the fresher listener-driven state (last-write-wins by finish
    // order, not by data freshness). Registering the listener first (synchronously, before the seed
    // even starts) plus this skip-if-already-seen check makes the merge safe in either order.
    private val listenerUpdatedIds = java.util.concurrent.ConcurrentHashMap.newKeySet<String>()

    init {
        // Downloads require some network connection, but the user no longer chooses whether
        // cellular counts — a downloaded song is always preferred over streaming during normal
        // playback anyway (see PlaybackController's shared cache), so this is just "has network".
        downloadManager.requirements = Requirements(Requirements.NETWORK)
        downloadManager.addListener(object : DownloadManager.Listener {
            override fun onDownloadChanged(dm: DownloadManager, download: Download, finalException: Exception?) {
                listenerUpdatedIds += download.request.id
                updateFrom(download)
            }
            override fun onDownloadRemoved(dm: DownloadManager, download: Download) {
                listenerUpdatedIds += download.request.id
                _downloads.update { it - download.request.id }
            }
        })
        // Media3's own index survives process death; seed from it so cold-start UI is correct
        // even before any DownloadManager.Listener callback fires. Use downloadIndex.getDownloads()
        // (not currentDownloads, which excludes COMPLETED/FAILED) so terminal states are seeded too.
        // getDownloads() declares throws IOException; if the index is unreadable, fall back to the
        // empty default and let DownloadManager.Listener populate state as downloads progress.
        // Run off the main thread — this is a synchronous disk read and DownloadRepositoryImpl is
        // a Koin single that can otherwise be constructed on whichever thread first injects it.
        scope.launch {
            // Disk read on IO; the check-and-write below deliberately stays on this launch's own
            // dispatcher (Main, same as the listener callbacks above) instead of running inside
            // withContext(dispatchers.io) — doing the "already seen?" check and the updateFrom()
            // write on IO left a window where a listener update for the same id could land between
            // them (Main and IO running concurrently), letting this seed's now-stale snapshot
            // overwrite the fresher listener-driven state. Confined to Main, the two can't interleave.
            val downloads = withContext(dispatchers.io) {
                try {
                    downloadManager.downloadIndex.getDownloads().use { cursor ->
                        buildList { while (cursor.moveToNext()) add(cursor.download) }
                    }
                } catch (e: IOException) {
                    // Ignore; _downloads stays empty and will be populated by the listener above.
                    emptyList()
                }
            }
            downloads.forEach { download ->
                if (download.request.id !in listenerUpdatedIds) updateFrom(download)
            }
            migrateTokenBearingDownloads()
        }
    }

    /**
     * One-time-per-item cleanup for downloads persisted by older builds of this app, which baked
     * the session's auth token into the DownloadRequest's URI (that URI is stored verbatim in
     * Media3's on-disk, unencrypted download index). Re-adding with a freshly-resolved token-free
     * URI overwrites just that row's metadata — [DownloadRequest.customCacheKey] is unchanged, so
     * the already-downloaded audio bytes in the shared cache are reused, not re-fetched. Naturally
     * self-limiting: once a download's stored URI no longer contains a token, later scans skip it,
     * so no persisted "migration done" flag is needed.
     */
    private suspend fun migrateTokenBearingDownloads() {
        val staleIds = withContext(dispatchers.io) {
            try {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    buildList {
                        while (cursor.moveToNext()) {
                            val download = cursor.download
                            if (download.request.uri.toString().contains("api_key=", ignoreCase = true)) {
                                add(download.request.id)
                            }
                        }
                    }
                }
            } catch (e: IOException) {
                emptyList()
            }
        }
        staleIds.forEach { songId ->
            val baseUri = streamUrlResolver.resolveBaseStreamUrl(songId) ?: return@forEach
            val request = DownloadRequest.Builder(songId, Uri.parse(baseUri)).setCustomCacheKey(songId).build()
            // Not foreground: this is a silent metadata re-registration for already-complete
            // downloads, not a new user-visible "downloading" notification.
            DownloadService.sendAddDownload(context, PulseFinDownloadService::class.java, request, false)
        }
    }

    override fun observeDownloads(): Flow<Map<String, SongDownload>> = _downloads.asStateFlow()

    override fun observeTotalDownloadedBytes(): Flow<Long> =
        _downloads.map { it.values.sumOf { d -> d.bytesDownloaded } }

    override suspend fun download(song: Song) {
        // Token-free: this URI is persisted verbatim in Media3's on-disk (unencrypted) download
        // index, so it must never carry the session's auth token. A fresh token is attached at
        // actual-fetch time instead, via the ResolvingDataSource wired in PlaybackModule.
        val uri = streamUrlResolver.resolveBaseStreamUrl(song.id.value)
        if (uri == null) {
            // No user-visible error by design (Unit-returning contract) — at least make the
            // failure observable in logcat instead of a silent no-op with zero trace.
            Log.w(TAG, "download: couldn't resolve stream URL for song ${song.id.value}")
            return
        }
        val request = DownloadRequest.Builder(song.id.value, Uri.parse(uri))
            // Matches the custom cache key used for direct playback (PlaybackController) so a
            // session-token refresh (which changes the resolved URI) doesn't orphan this download.
            .setCustomCacheKey(song.id.value)
            .build()
        DownloadService.sendAddDownload(context, PulseFinDownloadService::class.java, request, true)
    }

    override suspend fun downloadAll(songs: List<Song>) {
        // supervisorScope + per-song runCatching so one song's resolver/service failure doesn't
        // cancel the sibling downloads still in flight.
        supervisorScope {
            songs.map { song ->
                async {
                    runCatching { download(song) }
                        .onFailure { Log.w(TAG, "downloadAll: failed for song ${song.id.value}", it) }
                }
            }.awaitAll()
        }
    }

    override suspend fun remove(songId: String) {
        DownloadService.sendRemoveDownload(context, PulseFinDownloadService::class.java, songId, true)
    }

    override suspend fun removeAll(songIds: List<String>) {
        songIds.forEach { remove(it) }
    }

    override suspend fun clearAllDownloads() {
        // Read the on-disk Media3 index directly rather than the in-memory _downloads snapshot,
        // which may not yet be fully seeded (or may be missing entries the listener hasn't fired
        // for yet) — clearing against a stale/incomplete snapshot would silently leave files on disk.
        val ids = withContext(dispatchers.io) {
            try {
                downloadManager.downloadIndex.getDownloads().use { cursor ->
                    buildList { while (cursor.moveToNext()) add(cursor.download.request.id) }
                }
            } catch (e: IOException) {
                _downloads.value.keys.toList()
            }
        }
        removeAll(ids)
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
        // contentLength is -1 when unknown (e.g. before the first HTTP response) — coerce so a
        // progress-fraction UI never divides by/displays a negative total.
        val totalBytes = download.contentLength.coerceAtLeast(0L)
        _downloads.update {
            it + (download.request.id to SongDownload(
                download.request.id, state, percent, download.bytesDownloaded, totalBytes,
            ))
        }
    }
}
