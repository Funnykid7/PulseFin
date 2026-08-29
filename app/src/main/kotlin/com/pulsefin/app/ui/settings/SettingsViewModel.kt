package com.pulsefin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.app.playback.PlaybackScrobbler
import com.pulsefin.core.data.local.Settings
import com.pulsefin.core.data.local.SettingsStore
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val downloadRepository: DownloadRepository,
    private val authRepository: AuthRepository,
    private val mediaRepository: MediaRepository,
    private val playbackController: PlaybackController,
    private val playbackScrobbler: PlaybackScrobbler,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val totalDownloadedBytes: StateFlow<Long> = downloadRepository.observeTotalDownloadedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { settingsStore.setDarkTheme(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun setHapticsEnabled(enabled: Boolean) = viewModelScope.launch { settingsStore.setHapticsEnabled(enabled) }

    fun clearAllDownloads() = viewModelScope.launch { downloadRepository.clearAllDownloads() }

    fun downloadAllSongs() = viewModelScope.launch {
        downloadRepository.downloadAll(mediaRepository.observeSongs().first())
    }

    fun signOut() = viewModelScope.launch {
        // Clear Media3 downloads/cache (:core:playback) alongside the session + Room wipe
        // (:core:data) — the two modules don't depend on each other, so this bridges them.
        // Stopping playback lives here for the same reason: without it, audio, the notification,
        // and the media session all keep running under the just-wiped session.
        playbackController.stop()
        downloadRepository.clearAllDownloads()
        authRepository.logout()
        playbackScrobbler.resetSession()
    }
}
