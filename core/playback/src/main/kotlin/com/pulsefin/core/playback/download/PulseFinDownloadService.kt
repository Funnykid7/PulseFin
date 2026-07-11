package com.pulsefin.core.playback.download

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build
import androidx.media3.exoplayer.offline.Download
import androidx.media3.exoplayer.offline.DownloadManager
import androidx.media3.exoplayer.offline.DownloadNotificationHelper
import androidx.media3.exoplayer.offline.DownloadService
import androidx.media3.exoplayer.scheduler.PlatformScheduler
import androidx.media3.exoplayer.scheduler.Scheduler
import com.pulsefin.core.playback.R
import org.koin.android.ext.android.inject

class PulseFinDownloadService : DownloadService(
    NOTIFICATION_ID,
    DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL_MS,
    CHANNEL_ID,
    R.string.download_notification_channel,
    0,
) {
    private val downloadManagerDep: DownloadManager by inject()
    private val notificationHelper by lazy { DownloadNotificationHelper(this, CHANNEL_ID) }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID, getString(R.string.download_notification_channel),
                NotificationManager.IMPORTANCE_LOW,
            )
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
    }

    override fun getDownloadManager(): DownloadManager = downloadManagerDep

    override fun getScheduler(): Scheduler = PlatformScheduler(this, JOB_ID)

    override fun getForegroundNotification(downloads: MutableList<Download>, notMetRequirements: Int): Notification =
        notificationHelper.buildProgressNotification(
            this, R.drawable.ic_notification, null, null, downloads, notMetRequirements,
        )

    companion object {
        private const val NOTIFICATION_ID = 2
        private const val CHANNEL_ID = "pulsefin_downloads"
        private const val JOB_ID = 1
        private const val DEFAULT_FOREGROUND_NOTIFICATION_UPDATE_INTERVAL_MS = 1000L
    }
}
