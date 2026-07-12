package com.pulsefin.core.domain.repository

import kotlinx.coroutines.flow.Flow

/**
 * Exposes user download requirement preferences. Exists so :core:playback can observe
 * settings without depending on :core:data.
 */
interface DownloadRequirementsProvider {
    fun observePreferDownloadsOnCellular(): Flow<Boolean>
}
