package com.pulsefin.app.ui.player

import android.view.HapticFeedbackConstants
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.playback.controller.PlaybackController
import kotlin.math.roundToInt
import org.koin.compose.koinInject

// Queue rows always render with a subtitle (artist), which fixes MediaRow's min height at 72.dp.
private val QUEUE_ROW_HEIGHT = 72.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QueueScreen(
    onBack: () -> Unit,
    playbackController: PlaybackController = koinInject(),
) {
    val queue by playbackController.queue.collectAsStateWithLifecycle()
    val state by playbackController.state.collectAsStateWithLifecycle()

    // Stable system-bar insets, not the Scaffold's contentPadding: the bottom bar's height snaps in
    // one frame when it slides out, which would jerk this full-screen list as the transition settles.
    val topInset = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    val density = LocalDensity.current
    val rowHeightPx = with(density) { QUEUE_ROW_HEIGHT.toPx() }
    val view = LocalView.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = topInset),
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
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                itemsIndexed(
                    queue,
                    key = { i, item -> "$i-${item.mediaId}" },
                    contentType = { _, _ -> "queueItem" },
                ) { index, item ->
                    val isCurrent = index == state.currentIndex
                    val isDragged = index == draggedIndex
                    val titleColor by animateColorAsState(
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        label = "queueTitleColor",
                    )
                    MediaRow(
                        title = item.title,
                        imageModel = item.artworkUrl,
                        modifier = Modifier
                            .animateItem()
                            .graphicsLayer { translationY = if (isDragged) dragOffsetY else 0f }
                            .zIndex(if (isDragged) 1f else 0f)
                            .bouncyClickable { playbackController.playIndex(index) },
                        subtitle = item.artist,
                        imageSize = 48.dp,
                        titleStyle = MaterialTheme.typography.bodyLarge,
                        titleColor = titleColor,
                        titleWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        trailing = {
                            Icon(
                                Icons.Filled.DragHandle,
                                contentDescription = "Reorder",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.pointerInput(index, queue.size) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = {
                                            draggedIndex = index
                                            dragOffsetY = 0f
                                            view.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                                        },
                                        onDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount.y
                                        },
                                        onDragEnd = {
                                            val from = draggedIndex
                                            val moveBy = (dragOffsetY / rowHeightPx).roundToInt()
                                            val to = (from + moveBy).coerceIn(0, queue.lastIndex)
                                            if (from in queue.indices && from != to) {
                                                playbackController.moveQueueItem(from, to)
                                            }
                                            draggedIndex = -1
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedIndex = -1
                                            dragOffsetY = 0f
                                        },
                                    )
                                },
                            )
                        },
                    )
                }
            }
        }
    }
}
