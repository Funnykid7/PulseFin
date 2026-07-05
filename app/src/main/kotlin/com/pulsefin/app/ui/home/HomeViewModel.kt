package com.pulsefin.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    val songs: StateFlow<List<Song>> = repository.observeSongs()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    var isRefreshing by mutableStateOf(false)
        private set

    init { refresh() }

    fun refresh() {
        viewModelScope.launch {
            isRefreshing = true
            repository.refreshLibrary()
            isRefreshing = false
        }
    }

    fun onSongClick(index: Int) = playbackController.play(songs.value, index)
}
