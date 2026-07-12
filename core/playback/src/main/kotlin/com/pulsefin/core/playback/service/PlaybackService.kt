package com.pulsefin.core.playback.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pulsefin.core.domain.repository.StreamUrlResolver
import com.pulsefin.core.playback.R
import com.pulsefin.core.playback.queue.QueueStateStore
import com.pulsefin.core.playback.queue.toMediaItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject

/**
 * All transport controls run through this [MediaSessionService] so playback survives the
 * UI and integrates with the lock screen, Android Auto and Wear OS. This is the transport
 * shell; direct-play stream resolution and audio focus land in the playback increment.
 */
class PlaybackService : MediaSessionService() {

    private val queueStateStore: QueueStateStore by inject()
    private val cacheDataSourceFactory: CacheDataSource.Factory by inject()
    private val streamUrlResolver: StreamUrlResolver by inject()
    private val serviceScope = CoroutineScope(Dispatchers.Main + SupervisorJob())

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(cacheDataSourceFactory))
            .build()
        val sessionActivity = PendingIntent.getActivity(
            this,
            0,
            Intent().setComponent(ComponentName(packageName, "com.pulsefin.app.MainActivity")),
            PendingIntent.FLAG_IMMUTABLE,
        )
        mediaSession = MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivity)
            .build()

        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.playback_notification_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_notification) },
        )

        // Restore the last queue so a relaunch after process death (not just a fresh app start)
        // has something to resume — prepared but not auto-played.
        serviceScope.launch {
            val restored = queueStateStore.load() ?: return@launch
            // Capture by mediaId before resolving: a failed resolution can drop an item, which
            // would otherwise shift restored.currentIndex onto the wrong song.
            val currentMediaId = restored.items.getOrNull(restored.currentIndex)?.mediaId
            val items = restored.items.mapNotNull { it.toMediaItem(streamUrlResolver) }
            if (items.isEmpty()) return@launch
            val startIndex = items.indexOfFirst { it.mediaId == currentMediaId }.takeIf { it >= 0 } ?: 0
            player.setMediaItems(items, startIndex, restored.positionMs)
            player.prepare()
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        serviceScope.cancel()
        super.onDestroy()
    }
}
