@file:OptIn(ExperimentalMaterial3Api::class)

package dev.savar.aria.ui.screens

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.toMutableStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import dev.savar.aria.data.SongArt
import dev.savar.aria.data.artistText
import dev.savar.aria.data.songUri
import dev.savar.aria.data.titleText
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.QueueEntry
import dev.savar.aria.ui.components.ArtImage
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * The play queue. Drag a row by its handle to reorder; the list scrolls itself near the edges.
 * Rows are keyed by their original position, so they keep their identity while you drag.
 */
@Composable
fun QueueSheet(player: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val currentPos = player.queue.indexOfFirst { it.index == player.currentIndex }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = (currentPos - 1).coerceAtLeast(0))
    // Local copy so rows move under your finger instantly; synced back when the player confirms.
    val rows = remember(player.queue) { player.queue.toMutableStateList() }
    var draggingKey by remember { mutableStateOf<Int?>(null) }
    var dragStartIndex by remember { mutableStateOf(-1) }
    var dragOffset by remember { mutableFloatStateOf(0f) }
    var autoScroll by remember { mutableFloatStateOf(0f) }
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current

    fun moveIfNeeded() {
        val key = draggingKey ?: return
        val info = listState.layoutInfo.visibleItemsInfo
        val dragged = info.firstOrNull { it.key == key } ?: return
        val center = dragged.offset + dragOffset + dragged.size / 2f
        val target = info.firstOrNull { it.key != key && center.toInt() in it.offset until it.offset + it.size } ?: return
        val from = rows.indexOfFirst { it.index == key }
        val to = target.index
        if (from < 0 || to !in rows.indices) return
        rows.add(to, rows.removeAt(from))
        dragOffset += dragged.offset - target.offset
        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
    }

    // Edge auto-scroll while dragging.
    LaunchedEffect(draggingKey) {
        if (draggingKey == null) return@LaunchedEffect
        while (isActive) {
            if (autoScroll != 0f) {
                val used = listState.scrollBy(autoScroll)
                dragOffset += used
                moveIfNeeded()
            }
            delay(16)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Text(
            "Up next · ${rows.size}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            itemsIndexed(rows, key = { _, e -> e.index }) { _, entry ->
                val isDragging = entry.index == draggingKey
                val elevation by animateDpAsState(if (isDragging) 8.dp else 0.dp, label = "dragElevation")
                QueueRow(
                    entry = entry,
                    isCurrent = entry.index == player.currentIndex,
                    elevation = elevation,
                    onClick = { vm.skipTo(entry.index) },
                    onRemove = { vm.removeAt(entry.index) },
                    handle = Modifier.pointerInput(entry.index) {
                        val edge = 64.dp.toPx()
                        detectDragGestures(
                            onDragStart = {
                                draggingKey = entry.index
                                dragStartIndex = rows.indexOfFirst { it.index == entry.index }
                                dragOffset = 0f
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                            },
                            onDragEnd = {
                                val to = rows.indexOfFirst { it.index == entry.index }
                                if (dragStartIndex >= 0 && to >= 0 && to != dragStartIndex) vm.moveItem(dragStartIndex, to)
                                draggingKey = null
                                dragOffset = 0f
                                autoScroll = 0f
                            },
                            onDragCancel = {
                                draggingKey = null
                                dragOffset = 0f
                                autoScroll = 0f
                            },
                        ) { change, amount ->
                            change.consume()
                            dragOffset += amount.y
                            moveIfNeeded()
                            // Scroll when the dragged row nears the top or bottom of the list.
                            val dragged = listState.layoutInfo.visibleItemsInfo.firstOrNull { it.key == entry.index }
                            autoScroll = if (dragged == null) 0f else {
                                val top = dragged.offset + dragOffset
                                val bottom = top + dragged.size
                                when {
                                    top < listState.layoutInfo.viewportStartOffset + edge -> -18f
                                    bottom > listState.layoutInfo.viewportEndOffset - edge -> 18f
                                    else -> 0f
                                }
                            }
                        }
                    },
                    modifier = if (isDragging) {
                        Modifier.zIndex(1f).graphicsLayer { translationY = dragOffset }
                    } else {
                        Modifier.animateItem()
                    },
                )
            }
        }
    }
}

@Composable
private fun QueueRow(
    entry: QueueEntry,
    isCurrent: Boolean,
    elevation: androidx.compose.ui.unit.Dp,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    handle: Modifier,
    modifier: Modifier,
) {
    val meta = entry.item.mediaMetadata
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = when {
            elevation > 0.dp -> MaterialTheme.colorScheme.surfaceContainerHigh
            isCurrent -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
            else -> Color.Transparent
        },
        shadowElevation = elevation,
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArtImage(
                entry.item.mediaId.toLongOrNull()?.let { SongArt(songUri(it), 0, 160) },
                Modifier.size(44.dp),
                RoundedCornerShape(10.dp),
            )
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    meta.titleText,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    meta.artistText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (!isCurrent) {
                IconButton(onClick = onRemove) {
                    Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            // Grab here to drag.
            Box(handle.size(48.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.DragHandle, "Reorder", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
