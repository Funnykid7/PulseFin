package com.pulsefin.app.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.PlaylistAdd
import androidx.compose.material.icons.automirrored.filled.QueueMusic
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DownloadDone
import androidx.compose.material.icons.filled.Downloading
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pulsefin.app.R
import com.pulsefin.core.domain.model.DownloadState

/** 3-dot overflow used on song rows across Home/Search/etc. — play next, queue, add to playlist, download. */
@Composable
fun SongOverflowMenu(
    downloadState: DownloadState,
    onPlayNext: () -> Unit,
    onAddToQueue: () -> Unit,
    onAddToPlaylist: () -> Unit,
    onToggleDownload: () -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(Icons.Filled.MoreVert, contentDescription = stringResource(R.string.cd_more_options))
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_menu_play_next)) },
                leadingIcon = { Icon(Icons.Filled.SkipNext, contentDescription = null) },
                onClick = { expanded = false; onPlayNext() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_menu_add_to_queue)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.QueueMusic, contentDescription = null) },
                onClick = { expanded = false; onAddToQueue() },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.song_menu_add_to_playlist)) },
                leadingIcon = { Icon(Icons.AutoMirrored.Filled.PlaylistAdd, contentDescription = null) },
                onClick = { expanded = false; onAddToPlaylist() },
            )
            DropdownMenuItem(
                text = {
                    Text(
                        stringResource(
                            if (downloadState == DownloadState.COMPLETED) {
                                R.string.action_remove_download
                            } else {
                                R.string.action_download
                            },
                        ),
                    )
                },
                leadingIcon = {
                    when (downloadState) {
                        DownloadState.COMPLETED -> Icon(Icons.Filled.DownloadDone, contentDescription = null)
                        DownloadState.DOWNLOADING, DownloadState.QUEUED ->
                            Icon(Icons.Filled.Downloading, contentDescription = null)
                        else -> Icon(Icons.Filled.Download, contentDescription = null)
                    }
                },
                onClick = { expanded = false; onToggleDownload() },
            )
        }
    }
}

/** Small trailing-row indicator showing a song's current offline-download state. */
@Composable
fun DownloadStateIndicator(state: DownloadState) {
    when (state) {
        DownloadState.DOWNLOADING, DownloadState.QUEUED ->
            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
        DownloadState.COMPLETED ->
            Icon(Icons.Filled.DownloadDone, contentDescription = stringResource(R.string.cd_downloaded), modifier = Modifier.size(18.dp))
        DownloadState.FAILED ->
            Icon(
                Icons.Filled.ErrorOutline,
                contentDescription = stringResource(R.string.cd_download_failed),
                tint = MaterialTheme.colorScheme.error,
                modifier = Modifier.size(18.dp),
            )
        DownloadState.NONE -> {}
    }
}
