package com.pulsefin.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.pulsefin.core.data.local.Settings
import com.pulsefin.core.data.local.SettingsStore
import com.pulsefin.core.domain.repository.AuthRepository
import com.pulsefin.core.domain.repository.DownloadRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(
    private val settingsStore: SettingsStore,
    private val downloadRepository: DownloadRepository,
    private val authRepository: AuthRepository,
) : ViewModel() {

    val settings: StateFlow<Settings> = settingsStore.settings
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), Settings())

    val totalDownloadedBytes: StateFlow<Long> = downloadRepository.observeTotalDownloadedBytes()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), 0L)

    fun setDarkTheme(enabled: Boolean) = viewModelScope.launch { settingsStore.setDarkTheme(enabled) }

    fun setDynamicColor(enabled: Boolean) = viewModelScope.launch { settingsStore.setDynamicColor(enabled) }

    fun setPreferDownloadsOnCellular(enabled: Boolean) =
        viewModelScope.launch { settingsStore.setPreferDownloadsOnCellular(enabled) }

    fun clearAllDownloads() = viewModelScope.launch { downloadRepository.clearAllDownloads() }

    fun signOut() = viewModelScope.launch { authRepository.logout() }
}
