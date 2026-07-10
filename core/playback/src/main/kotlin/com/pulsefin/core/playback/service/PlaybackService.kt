package com.pulsefin.core.playback.service

import android.app.PendingIntent
import android.content.ComponentName
import android.content.Intent
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.pulsefin.core.playback.R

/**
 * All transport controls run through this [MediaSessionService] so playback survives the
 * UI and integrates with the lock screen, Android Auto and Wear OS. This is the transport
 * shell; queue management, direct-play stream resolution and audio focus land in the
 * playback increment.
 */
class PlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val player = ExoPlayer.Builder(this).build()
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
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? =
        mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}
