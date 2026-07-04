package com.pulsefin.app.ui.home

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.model.Song
import com.pulsefin.core.domain.repository.MediaRepository
import com.pulsefin.core.playback.controller.PlaybackController
import kotlinx.coroutines.launch

data class HomeUiState(
    val isLoading: Boolean = true,
    val songs: List<Song> = emptyList(),
    val error: String? = null,
)

class HomeViewModel(
    private val mediaRepository: MediaRepository,
    private val playbackController: PlaybackController,
) : ViewModel() {

    var uiState by mutableStateOf(HomeUiState())
        private set

    init {
        load()
    }

    fun load() {
        uiState = uiState.copy(isLoading = true, error = null)
        viewModelScope.launch {
            uiState = when (val result = mediaRepository.songs()) {
                is PulseResult.Success -> HomeUiState(isLoading = false, songs = result.data)
                is PulseResult.Failure -> HomeUiState(
                    isLoading = false,
                    error = result.error.message ?: "Couldn't load your library",
                )
            }
        }
    }

    fun onSongClick(index: Int) {
        playbackController.play(uiState.songs, index)
    }
}
