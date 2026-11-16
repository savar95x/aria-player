package dev.savar.aria.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/** First letter for the scroll bubble; anything that doesn't start with a letter goes under "#". */
fun String.scrollInitial(): String {
    val c = firstOrNull { it.isLetterOrDigit() } ?: return "#"
    return if (c.isLetter()) c.uppercaseChar().toString() else "#"
}

// Progress is measured in estimated pixels: (first visible index × average item size + how far it's
// scrolled past) over (all items × average size + content padding − viewport). Averages over every
// visible item/row, so a short header row reaching the top doesn't jerk the thumb, and the content
// padding (the dock's space) is counted, so the thumb reaches the bottom. At the real ends it pins
// to 0 / 1 exactly.

private class ScrollEstimate(val itemPx: Float, val perRow: Int, val maxScroll: Float)

@Composable
fun ListFastScroller(state: LazyListState, label: (Int) -> String?, modifier: Modifier = Modifier) {
    fun estimate(): ScrollEstimate {
        val info = state.layoutInfo
        val items = info.visibleItemsInfo
        val avg = if (items.isEmpty()) 1f else (items.sumOf { it.size } + info.mainAxisItemSpacing * (items.size - 1)).toFloat() / items.size
        val content = info.totalItemsCount * avg + info.beforeContentPadding + info.afterContentPadding
        return ScrollEstimate(avg.coerceAtLeast(1f), 1, (content - info.viewportSize.height).coerceAtLeast(1f))
    }
    FastScroller(
        total = remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }.value,
        scrolling = state.isScrollInProgress,
        progress = {
            when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val e = estimate()
                    ((state.firstVisibleItemIndex * e.itemPx + state.firstVisibleItemScrollOffset) / e.maxScroll).coerceIn(0f, 1f)
                }
            }
        },
        indexAt = { f -> val e = estimate(); (f * e.maxScroll / e.itemPx).toInt() },
        onScrollTo = { f ->
            val e = estimate()
            val px = f * e.maxScroll
            val index = (px / e.itemPx).toInt()
            state.scrollToItem(index, (px - index * e.itemPx).roundToInt())
        },
        label = label,
        modifier = modifier,
    )
}

@Composable
fun GridFastScroller(state: LazyGridState, label: (Int) -> String?, modifier: Modifier = Modifier) {
    fun estimate(): ScrollEstimate {
        val info = state.layoutInfo
        val rows = info.visibleItemsInfo.groupBy { it.row }
        // Columns from the fullest visible row (headers span the whole width and count as one).
        val perRow = rows.values.maxOfOrNull { it.size }?.coerceAtLeast(1) ?: 1
        val rowPx = if (rows.isEmpty()) 1f
        else (rows.values.sumOf { r -> r.maxOf { it.size.height } } + info.mainAxisItemSpacing * (rows.size - 1)).toFloat() / rows.size
        val content = (info.totalItemsCount.toFloat() / perRow) * rowPx + info.beforeContentPadding + info.afterContentPadding
        return ScrollEstimate(rowPx.coerceAtLeast(1f), perRow, (content - info.viewportSize.height).coerceAtLeast(1f))
    }
    FastScroller(
        total = remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }.value,
        scrolling = state.isScrollInProgress,
        progress = {
            when {
                !state.canScrollBackward -> 0f
                !state.canScrollForward -> 1f
                else -> {
                    val e = estimate()
                    val rowsAbove = state.firstVisibleItemIndex.toFloat() / e.perRow
                    ((rowsAbove * e.itemPx + state.firstVisibleItemScrollOffset) / e.maxScroll).coerceIn(0f, 1f)
                }
            }
        },
        indexAt = { f -> val e = estimate(); (f * e.maxScroll / e.itemPx * e.perRow).toInt() },
        onScrollTo = { f ->
            val e = estimate()
            val px = f * e.maxScroll
            val row = (px / e.itemPx).toInt()
            state.scrollToItem(row * e.perRow, (px - row * e.itemPx).roundToInt())
        },
        label = label,
        modifier = modifier,
    )
}

/**
 * Appears while you scroll; grab the thumb to jump through the list, with a letter bubble.
 * Only the thumb takes touches, and only while it's visible, so row buttons underneath still work.
 */
@Composable
private fun FastScroller(
    total: Int,
    scrolling: Boolean,
    progress: () -> Float,
    indexAt: (Float) -> Int,
    onScrollTo: suspend (Float) -> Unit,
    label: (Int) -> String?,
    modifier: Modifier,
) {
    if (total < 40) return
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(scrolling, dragging) {
        if (scrolling || dragging) shown = true else { delay(1400); shown = false }
    }
    val alpha by animateFloatAsState(if (shown) 1f else 0f, label = "scrollerAlpha")
    val thumbWidth by animateDpAsState(if (dragging) 10.dp else 6.dp, label = "thumbWidth")
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val currentLabel by rememberUpdatedState(label)

    BoxWithConstraints(modifier.fillMaxHeight().width(44.dp)) {
        val thumbHeight = 56.dp
        val trackPx = with(LocalDensity.current) { (maxHeight - thumbHeight).toPx() }.coerceAtLeast(1f)
        // Read in layout/draw only (lambdas), so the thumb moves every frame without recomposing.
        val fraction: () -> Float = { if (dragging) dragFraction else progress() }
        val offsetPx: () -> Int = { (fraction() * trackPx).roundToInt() }

        if (alpha > 0f) {
            if (dragging) {
                label(indexAt(dragFraction))?.let { text ->
                    val bubble = 60.dp
                    Box(
                        Modifier
                            .offset {
                                IntOffset(
                                    x = -(bubble + 12.dp).roundToPx(),
                                    y = offsetPx() + ((thumbHeight - bubble) / 2).roundToPx(),
                                )
                            }
                            .size(bubble)
                            .clip(RoundedCornerShape(topStartPercent = 50, topEndPercent = 50, bottomStartPercent = 50, bottomEndPercent = 8))
                            .background(MaterialTheme.colorScheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(text, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    }
                }
            }
            Box(
                Modifier
                    .offset { IntOffset(0, offsetPx()) }
                    .size(44.dp, thumbHeight)
                    .graphicsLayer { this.alpha = alpha }
                    .pointerInput(trackPx) {
                        var lastIndex = -1
                        var lastLabel: String? = null
                        detectVerticalDragGestures(
                            onDragStart = {
                                dragFraction = progress()
                                dragging = true
                            },
                            onDragEnd = { dragging = false },
                            onDragCancel = { dragging = false },
                        ) { change, dy ->
                            change.consume()
                            dragFraction = (dragFraction + dy / trackPx).coerceIn(0f, 1f)
                            // The list follows the thumb continuously (with an in-row offset), not row by row.
                            val f = dragFraction
                            scope.launch { onScrollTo(f) }
                            val index = indexAt(f)
                            if (index != lastIndex) {
                                lastIndex = index
                                // Tick once per letter, not once per row.
                                val l = currentLabel(index)
                                if (l != lastLabel) {
                                    lastLabel = l
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                            }
                        }
                    },
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .padding(end = 6.dp)
                        .width(thumbWidth)
                        .fillMaxHeight()
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                )
            }
        }
    }
}
