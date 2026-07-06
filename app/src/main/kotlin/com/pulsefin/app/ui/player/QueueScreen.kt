package com.pulsefin.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.playback.controller.PlaybackController
import org.koin.compose.koinInject

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    contentPadding: PaddingValues,
    onBack: () -> Unit,
    playbackController: PlaybackController = koinInject(),
) {
    val queue by playbackController.queue.collectAsStateWithLifecycle()
    val state by playbackController.state.collectAsStateWithLifecycle()

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = contentPadding.calculateTopPadding()),
                title = { Text("Up Next") },
                navigationIcon = {
                    val backInteraction = remember { MutableInteractionSource() }
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.pressScale(backInteraction, pressedScale = 0.9f),
                        interactionSource = backInteraction,
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
            )
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                itemsIndexed(
                    queue,
                    key = { i, item -> "$i-${item.mediaId}" },
                    contentType = { _, _ -> "queueItem" },
                ) { index, item ->
                    val isCurrent = index == state.currentIndex
                    val titleColor by animateColorAsState(
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        label = "queueTitleColor",
                    )
                    MediaRow(
                        title = item.title,
                        imageModel = item.artworkUrl,
                        modifier = Modifier
                            .animateItem()
                            .bouncyClickable { playbackController.playIndex(index) },
                        subtitle = item.artist,
                        imageSize = 48.dp,
                        titleStyle = MaterialTheme.typography.bodyLarge,
                        titleColor = titleColor,
                        titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
