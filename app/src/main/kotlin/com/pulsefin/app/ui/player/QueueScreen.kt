package com.pulsefin.app.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pulsefin.app.R
import com.pulsefin.app.ui.components.HapticEffect
import com.pulsefin.app.ui.components.LocalHapticsEnabled
import com.pulsefin.app.ui.components.MediaRow
import com.pulsefin.app.ui.components.bouncyClickable
import com.pulsefin.app.ui.components.performHaptic
import com.pulsefin.app.ui.components.pressScale
import com.pulsefin.core.common.util.sizedArtUrl
import com.pulsefin.core.playback.controller.PlaybackController
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
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
    val hapticsEnabled = LocalHapticsEnabled.current
    var draggedIndex by remember { mutableIntStateOf(-1) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }

    // The index the dragged row would land on if released right now — used to live-reflow the
    // rows it's crossing over, rather than only snapping the list into place on release.
    val targetIndex by remember {
        derivedStateOf {
            if (draggedIndex < 0 || queue.isEmpty()) {
                -1
            } else {
                (draggedIndex + (dragOffsetY / rowHeightPx).roundToInt()).coerceIn(0, queue.lastIndex)
            }
        }
    }

    // Stable per-item keys: mediaId alone isn't unique (a song can legitimately appear twice in
    // the queue), and the previous "$index-$mediaId" key baked the position into the key itself,
    // which defeated animateItem()'s move-detection (every reordered row got a "new" key). Keying
    // by mediaId + stable occurrence order fixes both.
    val queueKeys = remember(queue) {
        val seen = mutableMapOf<String, Int>()
        queue.map { item ->
            val occurrence = seen.getOrDefault(item.mediaId, 0)
            seen[item.mediaId] = occurrence + 1
            "${item.mediaId}#$occurrence"
        }
    }

    val listState = rememberLazyListState()
    var highlightedMediaId by remember { mutableStateOf<String?>(null) }
    LaunchedEffect(playbackController) {
        playbackController.lastMovedMediaId.collect { mediaId ->
            if (mediaId == null) return@collect
            // mediaId alone can't disambiguate a song that appears twice in the queue. playNext()
            // always lands the moved item right after the current track, so when there's more than
            // one occurrence, the one closest to that position is the one that was actually moved.
            fun findMovedIndex() = playbackController.queue.value
                .withIndex()
                .filter { it.value.mediaId == mediaId }
                .minByOrNull { kotlin.math.abs(it.index - (state.currentIndex + 1)) }
                ?.index ?: -1
            // The queue StateFlow's own update (triggered by the same playNext() call) can land a
            // beat after this signal, since both cross the MediaController/session boundary
            // separately — retry once after a short delay if the item isn't visible yet.
            var index = findMovedIndex()
            if (index < 0) {
                delay(80)
                index = findMovedIndex()
            }
            if (index >= 0) {
                listState.animateScrollToItem(index)
                highlightedMediaId = mediaId
                delay(900)
                if (highlightedMediaId == mediaId) highlightedMediaId = null
            }
            playbackController.consumeLastMoved()
        }
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(modifier = Modifier.fillMaxSize()) {
            TopAppBar(
                modifier = Modifier.padding(top = topInset),
                title = { Text(stringResource(R.string.queue_title)) },
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
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = bottomInset),
            ) {
                itemsIndexed(
                    queue,
                    key = { i, _ -> queueKeys[i] },
                    contentType = { _, _ -> "queueItem" },
                ) { index, item ->
                    val isCurrent = index == state.currentIndex
                    val isDragged = index == draggedIndex
                    val titleColor by animateColorAsState(
                        if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                        label = "queueTitleColor",
                    )
                    // While a row is being dragged, every row it's currently crossing over shifts
                    // out of the way by one row height, so the reorder is visible mid-gesture
                    // instead of only snapping into place once the finger lifts.
                    val reflowTarget = when {
                        isDragged || draggedIndex < 0 -> 0f
                        targetIndex > draggedIndex && index in (draggedIndex + 1)..targetIndex -> -rowHeightPx
                        targetIndex < draggedIndex && index in targetIndex until draggedIndex -> rowHeightPx
                        else -> 0f
                    }
                    val reflowOffset by animateFloatAsState(reflowTarget, label = "queueReflow")
                    val highlightAlpha by animateFloatAsState(
                        if (item.mediaId == highlightedMediaId) 0.16f else 0f,
                        label = "queueHighlight",
                    )
                    val dismissState = rememberSwipeToDismissBoxState()
                    // Stabilized by index: Material3's SwipeToDismissBox internally keys a
                    // LaunchedEffect on this lambda's identity, and any PlaybackController.state
                    // change (e.g. the current track advancing) recomposes every visible row — an
                    // un-remembered lambda here would restart that effect and can fire onDismiss a
                    // second time, deleting whatever item now sits at this index.
                    val onDismiss: (SwipeToDismissBoxValue) -> Unit =
                        remember(index) { { playbackController.removeFromQueue(index) } }
                    SwipeToDismissBox(
                        state = dismissState,
                        onDismiss = onDismiss,
                        backgroundContent = {
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(MaterialTheme.colorScheme.errorContainer)
                                    .padding(horizontal = 24.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.cd_remove_from_queue),
                                    tint = MaterialTheme.colorScheme.onErrorContainer,
                                )
                            }
                        },
                    ) {
                        MediaRow(
                            title = item.title,
                            imageModel = sizedArtUrl(item.artworkUrl, 180),
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.primary.copy(alpha = highlightAlpha))
                                .animateItem()
                                .graphicsLayer { translationY = if (isDragged) dragOffsetY else reflowOffset }
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
                                    contentDescription = stringResource(R.string.cd_reorder),
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.pointerInput(index, queue.size) {
                                        detectDragGesturesAfterLongPress(
                                            onDragStart = {
                                                draggedIndex = index
                                                dragOffsetY = 0f
                                                // Medium: the start of a drag gesture is a more
                                                // deliberate engagement than a simple tap.
                                                view.performHaptic(HapticEffect.Medium, hapticsEnabled)
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
}
