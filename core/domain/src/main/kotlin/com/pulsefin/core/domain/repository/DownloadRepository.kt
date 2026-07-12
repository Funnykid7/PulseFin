package com.pulsefin.core.domain.repository

import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.model.SongDownload
import kotlinx.coroutines.flow.Flow

interface DownloadRepository {
    fun observeDownloads(): Flow<Map<String, SongDownload>>
    fun observeDownload(songId: String): Flow<SongDownload?>
    fun observeTotalDownloadedBytes(): Flow<Long>

    suspend fun download(song: Song)
    suspend fun downloadAll(songs: List<Song>)
    suspend fun remove(songId: String)
    suspend fun removeAll(songIds: List<String>)
    suspend fun clearAllDownloads()
}
