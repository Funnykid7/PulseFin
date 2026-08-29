package com.pulsefin.app.ui.playlist

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.core.common.result.PulseResult
import com.pulsefin.core.domain.repository.MediaRepository
import kotlinx.coroutines.launch
import org.koin.compose.koinInject

/** Bottom sheet for adding [songId] to an existing playlist, or a brand-new one. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddToPlaylistSheet(
    songId: String,
    onDismiss: () -> Unit,
    repository: MediaRepository = koinInject(),
) {
    val playlists by repository.observePlaylists().collectAsStateWithLifecycle(emptyList())
    val scope = rememberCoroutineScope()
    var showCreateDialog by remember { mutableStateOf(false) }
    // Guards against a fast double-tap firing addToPlaylist/createPlaylist twice for the same
    // request before the in-flight call dismisses this sheet.
    var isSubmitting by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    val actionErrorMessage = stringResource(R.string.error_action_failed)
    val sheetState = rememberBottomSheetState(initialValue = SheetValue.Hidden)

    ModalBottomSheet(
        // Swipe/scrim/back dismissal must not cancel an in-flight addToPlaylist/createPlaylist —
        // that would cancel this composable's own rememberCoroutineScope mid-request, per the
        // comment below on the request itself.
        onDismissRequest = { if (!isSubmitting) onDismiss() },
        sheetState = sheetState,
    ) {
        Text(
            text = stringResource(R.string.song_menu_add_to_playlist),
            style = MaterialTheme.typography.titleLarge,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
        )
        errorMessage?.let {
            Text(
                text = it,
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )
        }
        ListItem(
            headlineContent = { Text(stringResource(R.string.playlist_new)) },
            leadingContent = { Icon(Icons.Filled.Add, contentDescription = null) },
            modifier = Modifier.bouncyClickable(enabled = !isSubmitting, onClick = { showCreateDialog = true }),
        )
        playlists.forEach { playlist ->
            ListItem(
                headlineContent = { Text(playlist.name) },
                leadingContent = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                modifier = Modifier.bouncyClickable(
                    enabled = !isSubmitting,
                    onClick = {
                        // Dismissing removes this composable (and its rememberCoroutineScope) from
                        // composition, which would cancel addToPlaylist() mid-flight if dismissal
                        // happened first — await it, then dismiss.
                        isSubmitting = true
                        errorMessage = null
                        scope.launch {
                            when (repository.addToPlaylist(playlist.id.value, listOf(songId))) {
                                is PulseResult.Success -> onDismiss()
                                is PulseResult.Failure -> {
                                    isSubmitting = false
                                    errorMessage = actionErrorMessage
                                }
                            }
                        }
                    },
                ),
            )
        }
        Spacer(Modifier.size(16.dp))
    }

    if (showCreateDialog) {
        NewPlaylistDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                showCreateDialog = false
                isSubmitting = true
                errorMessage = null
                scope.launch {
                    when (repository.createPlaylist(name, listOf(songId))) {
                        is PulseResult.Success -> onDismiss()
                        is PulseResult.Failure -> {
                            isSubmitting = false
                            errorMessage = actionErrorMessage
                        }
                    }
                }
            },
        )
    }
}
