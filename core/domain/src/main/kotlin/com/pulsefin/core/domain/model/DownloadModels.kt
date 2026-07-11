package com.pulsefin.core.domain.model

enum class DownloadState { NONE, QUEUED, DOWNLOADING, COMPLETED, FAILED }

data class SongDownload(
    val songId: String,
    val state: DownloadState,
    val progressPercent: Int = 0,
    val bytesDownloaded: Long = 0L,
    val totalBytes: Long = 0L,
)
