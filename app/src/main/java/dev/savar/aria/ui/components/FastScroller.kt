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

// Position is fractional (first visible index + how far it's scrolled past), and the scrollable
// range is estimated from row height and viewport, not the visible-item count: integer indices and a
// count that flickers as rows enter/leave made the thumb step and jitter.

@Composable
fun ListFastScroller(state: LazyListState, label: (Int) -> String?, modifier: Modifier = Modifier) {
    fun range(): Pair<Float, Float> { // (scrollable items, item size px)
        val info = state.layoutInfo
        val size = (info.visibleItemsInfo.firstOrNull()?.size ?: 1).coerceAtLeast(1).toFloat()
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        return (info.totalItemsCount - viewport / size).coerceAtLeast(1f) to size
    }
    FastScroller(
        total = remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }.value,
        scrolling = state.isScrollInProgress,
        progress = {
            val (scrollable, size) = range()
            ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / size) / scrollable).coerceIn(0f, 1f)
        },
        indexAt = { f -> (f * range().first).toInt() },
        onScrollTo = { f ->
            val (scrollable, size) = range()
            val exact = f * scrollable
            val index = exact.toInt()
            state.scrollToItem(index, ((exact - index) * size).roundToInt())
        },
        label = label,
        modifier = modifier,
    )
}

@Composable
fun GridFastScroller(state: LazyGridState, label: (Int) -> String?, modifier: Modifier = Modifier) {
    fun range(): Triple<Float, Float, Int> { // (scrollable items, row height px, columns)
        val info = state.layoutInfo
        val first = info.visibleItemsInfo.firstOrNull()
        val height = (first?.size?.height ?: 1).coerceAtLeast(1).toFloat()
        val columns = first?.let { f -> info.visibleItemsInfo.count { it.row == f.row } }?.coerceAtLeast(1) ?: 1
        val viewport = (info.viewportEndOffset - info.viewportStartOffset).toFloat()
        return Triple((info.totalItemsCount - viewport / height * columns).coerceAtLeast(1f), height, columns)
    }
    FastScroller(
        total = remember(state) { derivedStateOf { state.layoutInfo.totalItemsCount } }.value,
        scrolling = state.isScrollInProgress,
        progress = {
            val (scrollable, height, columns) = range()
            ((state.firstVisibleItemIndex + state.firstVisibleItemScrollOffset / height * columns) / scrollable).coerceIn(0f, 1f)
        },
        indexAt = { f -> (f * range().first).toInt() },
        onScrollTo = { f ->
            val (scrollable, height, columns) = range()
            val exact = f * scrollable
            val row = (exact / columns).toInt()
            state.scrollToItem(row * columns, ((exact / columns - row) * height).roundToInt())
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
