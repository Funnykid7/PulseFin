package com.pulsefin.core.data.local

import com.pulsefin.core.domain.repository.DownloadRequirementsProvider
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class DownloadRequirementsProviderImpl(
    private val settingsStore: SettingsStore,
) : DownloadRequirementsProvider {
    override fun observePreferDownloadsOnCellular(): Flow<Boolean> =
        settingsStore.settings.map { it.preferDownloadsOnCellular }
}
