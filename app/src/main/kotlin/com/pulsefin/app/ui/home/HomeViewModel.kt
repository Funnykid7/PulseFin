package com.pulsefin.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.core.domain.model.DownloadState
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.DownloadRepository
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
    private val downloadRepository: DownloadRepository,
) : ViewModel() {

    val songs: StateFlow<List<Song>> = repository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val downloadStates: StateFlow<Map<String, DownloadState>> = downloadRepository.observeDownloads()
        .map { downloads -> downloads.mapValues { it.value.state } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyMap())

    var isRefreshing by mutableStateOf(false)
        private set

    init { sync(force = false) }

    fun refresh() = sync(force = true)

    private fun sync(force: Boolean) {
        viewModelScope.launch {
            // Spinner only for user-initiated pulls; the silent startup sync shouldn't flash it.
            if (force) isRefreshing = true
            repository.refreshLibrary(force = force)
            // Only this call's own isRefreshing=true should be cleared by it — otherwise a
            // concurrently-running non-forced sync can clear the spinner for a still-in-progress
            // forced (pull-to-refresh) one.
            if (force) isRefreshing = false
        }
    }

    // Resolve by id against the current list at click time rather than trusting a captured list
    // index — a concurrent library sync that reorders songs between render and click would
    // otherwise play the wrong track.
    fun onSongClick(song: Song) = viewModelScope.launch {
        val index = songs.value.indexOfFirst { it.id.value == song.id.value }
        if (index >= 0) playbackController.play(songs.value, index)
    }

    fun playNext(song: Song) = viewModelScope.launch { playbackController.playNext(song) }

    fun addToQueue(song: Song) = viewModelScope.launch { playbackController.addToQueue(song) }

    fun toggleDownload(song: Song) = viewModelScope.launch {
        if (downloadStates.value[song.id.value] == DownloadState.COMPLETED) {
            downloadRepository.remove(song.id.value)
        } else {
            downloadRepository.download(song)
        }
    }
}
