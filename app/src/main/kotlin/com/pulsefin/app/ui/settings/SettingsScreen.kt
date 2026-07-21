package com.pulsefin.app.ui.settings

import android.text.format.Formatter
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.BuildConfig
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import org.koin.androidx.compose.koinViewModel

private const val PULSEFIN_WEBSITE_URL = "https://funnykid7.github.io/PulseFin/"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    viewModel: SettingsViewModel = koinViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val totalDownloadedBytes by viewModel.totalDownloadedBytes.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showClearDownloadsDialog by remember { mutableStateOf(false) }
    var showSignOutDialog by remember { mutableStateOf(false) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = { Text(stringResource(R.string.settings_title)) },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
                        interactionSource = backInteraction,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.cd_back))
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )

            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item { SectionHeader(stringResource(R.string.section_appearance)) }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_dark_theme),
                        checked = settings.darkTheme,
                        onCheckedChange = viewModel::setDarkTheme,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_dynamic_color),
                        checked = settings.dynamicColor,
                        onCheckedChange = viewModel::setDynamicColor,
                    )
                }
                item {
                    SwitchRow(
                        title = stringResource(R.string.settings_haptics),
                        checked = settings.hapticsEnabled,
                        onCheckedChange = viewModel::setHapticsEnabled,
                    )
                }

                item { SectionHeader(stringResource(R.string.section_downloads)) }
                item {
                    Text(
                        text = stringResource(
                            R.string.settings_downloaded_size,
                            Formatter.formatShortFileSize(context, totalDownloadedBytes),
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                item {
                    Row(
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        OutlinedButton(onClick = viewModel::downloadAllSongs) {
                            Text(stringResource(R.string.settings_download_all))
                        }
                        OutlinedButton(onClick = { showClearDownloadsDialog = true }) {
                            Text(stringResource(R.string.settings_clear_downloads))
                        }
                    }
                }

                item { SectionHeader(stringResource(R.string.section_about)) }
                item {
                    Text(
                        text = stringResource(R.string.settings_version, BuildConfig.VERSION_NAME),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }
                item {
                    val uriHandler = LocalUriHandler.current
                    Text(
                        text = stringResource(R.string.settings_website),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .bouncyClickable(onClick = { uriHandler.openUri(PULSEFIN_WEBSITE_URL) })
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                    )
                }

                item { HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp)) }
                item {
                    OutlinedButton(
                        onClick = { showSignOutDialog = true },
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 20.dp),
                    ) {
                        Text(stringResource(R.string.action_sign_out))
                    }
                }
            }
        }
    }

    if (showClearDownloadsDialog) {
        AlertDialog(
            onDismissRequest = { showClearDownloadsDialog = false },
            title = { Text(stringResource(R.string.settings_clear_downloads_confirm_title)) },
            text = { Text(stringResource(R.string.settings_clear_downloads_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showClearDownloadsDialog = false
                    viewModel.clearAllDownloads()
                }) { Text(stringResource(R.string.action_clear), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showClearDownloadsDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }

    if (showSignOutDialog) {
        AlertDialog(
            onDismissRequest = { showSignOutDialog = false },
            title = { Text(stringResource(R.string.settings_sign_out_confirm_title)) },
            text = { Text(stringResource(R.string.settings_sign_out_confirm_body)) },
            confirmButton = {
                TextButton(onClick = {
                    showSignOutDialog = false
                    viewModel.signOut()
                }) { Text(stringResource(R.string.action_sign_out), color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showSignOutDialog = false }) { Text(stringResource(R.string.action_cancel)) }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleLarge,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 20.dp, bottom = 8.dp),
    )
}

@Composable
private fun SwitchRow(title: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
