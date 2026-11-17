package dev.savar.aria.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * The now-playing sheet's position and gestures, modelled on Retro's sliding panel.
 *
 * One number: [position] 0 = collapsed onto the dock, 1 = fully open. A finger owns it outright
 * (it follows 1:1 both ways, and you can catch it mid-animation and throw it back); on release it
 * springs to the nearer end, or the way you flicked, carrying the flick's speed.
 *
 * Drawing lives in AriaRoot; this file is only state and touch handling.
 *
 * @param travel how far the sheet moves, in px: from the dock's top edge to the top of the screen.
 * @param onOpenChange keeps the screen's "now playing open" flag in step with where the sheet settles.
 */
@Stable
class PlayerSheetState(
    initiallyOpen: Boolean,
    private val scope: CoroutineScope,
    val travel: () -> Float,
    private val onOpenChange: (Boolean) -> Unit,
) {
    // Sub-pixel finish: the default 0.01 threshold ended every animation by snapping the last ~20px.
    private val anim = Animatable(if (initiallyOpen) 1f else 0f, visibilityThreshold = Motion.SheetThreshold)
    private var dragging by mutableStateOf(false)
    private var dragPos by mutableFloatStateOf(0f)
    private var settleJob: Job? = null

    /** False when there's nothing playing: the sheet can't be opened. */
    var enabled = true

    /**
     * 0..1. Always read the position through this: a flick's velocity can carry the spring a hair past
     * 0 or 1, and a negative corner radius crashed 1.0.11.
     */
    val position: Float get() = (if (dragging) dragPos else anim.value).coerceIn(0f, 1f)

    internal val isMoving: Boolean get() = anim.isRunning || settleJob?.isActive == true
    internal val isSettledOpen: Boolean get() = anim.value >= 1f && !anim.isRunning

    /** Animates to open/closed (from wherever it is, at [velocity] in fractions per second). */
    fun settle(open: Boolean, velocity: Float = 0f) {
        onOpenChange(open)
        settleJob?.cancel()
        settleJob = scope.launch {
            anim.snapTo(position)
            dragging = false
            anim.animateTo(if (open) 1f else 0f, Motion.SheetSpring, initialVelocity = velocity)
        }
    }

    /** Follows the open flag when something else changed it (the collapse button, back, links). */
    suspend fun follow(open: Boolean) {
        val target = if (open) 1f else 0f
        if (!dragging && settleJob?.isActive != true && anim.targetValue != target) {
            anim.animateTo(target, Motion.SheetSpring)
        }
    }

    internal fun grab() {
        settleJob?.cancel()
        dragPos = anim.value.coerceIn(0f, 1f)
        dragging = true
        scope.launch { anim.stop() }
    }

    internal fun dragBy(dy: Float) {
        dragPos = (dragPos - dy / travel()).coerceIn(0f, 1f)
    }

    internal fun release(velocityY: Float, fling: Float) {
        if (!dragging) return
        val open = when {
            velocityY < -fling -> true
            velocityY > fling -> false
            else -> dragPos > 0.5f
        }
        settle(open && enabled, -velocityY / travel())
    }

    /** The sheet's layout, for converting touches to screen space (set by [sheetDrag]). */
    internal var coords: LayoutCoordinates? = null

    /** Was the last click handled by a settle (e.g. tapping the falling mini player)? */
    internal val settling: Boolean get() = settleJob?.isActive == true
}

/** Drag up on the dock to pull the sheet open. */
fun Modifier.dockDrag(sheet: PlayerSheetState, enabled: Boolean): Modifier = pointerInput(sheet, enabled) {
    if (!enabled) return@pointerInput
    val tracker = VelocityTracker()
    val fling = 400.dp.toPx()
    detectVerticalDragGestures(
        onDragStart = {
            tracker.resetTracking()
            sheet.grab()
        },
        onDragEnd = { sheet.release(tracker.calculateVelocity().y, fling) },
        onDragCancel = { sheet.release(0f, fling) },
    ) { change, dy ->
        change.consume()
        tracker.addPosition(change.uptimeMillis, change.position)
        sheet.dragBy(dy)
    }
}

/**
 * Goes on the sheet itself, after its translation, so its touch area falls with it: above the
 * sheet's top edge the list underneath is live straight away, even mid-animation. Positions are
 * converted to screen space, because local coordinates move with the sheet.
 *
 * Touching a moving sheet catches it; drags move it, except a swipe up on the settled open player,
 * which calls [onSwipeUp] (the queue). Lyrics scrolling and the seek bar consume their own drags.
 */
fun Modifier.sheetDrag(sheet: PlayerSheetState, onSwipeUp: () -> Unit): Modifier =
    onGloballyPositioned { sheet.coords = it }.pointerInput(sheet) {
        val fling = 400.dp.toPx()
        val queueDistance = 28.dp.toPx()
        val slop = viewConfiguration.touchSlop
        fun screenY(p: Offset): Float = sheet.coords?.takeIf { it.isAttached }?.localToRoot(p)?.y ?: p.y
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false)
            val tracker = VelocityTracker()
            val startY = screenY(down.position)
            var lastY = startY
            val caught = sheet.isMoving
            var mode = if (caught) 1 else 0 // 1 = moving the sheet, -1 = swiping up for the queue
            if (caught) sheet.grab()
            tracker.addPosition(down.uptimeMillis, Offset(0f, startY))
            while (true) {
                val event = awaitPointerEvent()
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                val y = screenY(change.position)
                if (!change.pressed) break
                // A child (lyrics list, seek bar) took this drag before we claimed it.
                if (mode == 0 && change.isConsumed) return@awaitEachGesture
                val dy = y - lastY
                lastY = y
                if (mode == 0 && abs(y - startY) > slop) {
                    mode = if (y < startY && sheet.isSettledOpen) -1 else 1
                    if (mode == 1) sheet.grab()
                }
                if (mode != 0) {
                    change.consume()
                    tracker.addPosition(change.uptimeMillis, Offset(0f, y))
                    if (mode == 1) sheet.dragBy(dy)
                }
            }
            val velocity = tracker.calculateVelocity().y
            when {
                // A tap on the falling mini player: its click already started reopening (grab()
                // cancelled any earlier settle, so an active one is the click's).
                mode == 1 && sheet.settling -> Unit
                mode == 1 -> sheet.release(velocity, fling)
                mode == -1 && (lastY - startY < -queueDistance || velocity < -fling) -> onSwipeUp()
            }
        }
    }
