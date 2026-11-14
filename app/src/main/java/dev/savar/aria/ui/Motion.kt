@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.savar.aria.ui

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.dp

/** Material 3 motion tokens. */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** Springy but settled: used for art flying between screens. */
    val ArtBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 0.86f, stiffness = 380f) }

    // Shared X-axis: the new screen slides in a little and fades up; the old one drifts the other way.
    private const val Shift = 10

    val Enter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { it / Shift } +
            fadeIn(tween(250, delayMillis = 60))
    }
    val Exit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { -it / Shift } + fadeOut(tween(120))
    }
    val PopEnter: AnimatedContentTransitionScope<*>.() -> EnterTransition = {
        slideInHorizontally(tween(400, easing = EmphasizedDecelerate)) { -it / Shift } +
            fadeIn(tween(250, delayMillis = 60))
    }
    val PopExit: AnimatedContentTransitionScope<*>.() -> ExitTransition = {
        slideOutHorizontally(tween(250, easing = EmphasizedAccelerate)) { it / Shift } + fadeOut(tween(120))
    }

    /** Fade-through for switching bottom-nav destinations. */
    fun fadeThrough(): ContentTransform =
        (fadeIn(tween(220, delayMillis = 90)) + scaleIn(tween(220, delayMillis = 90), initialScale = 0.94f)) togetherWith
            fadeOut(tween(90))

    /** Track change: new title rises in, old one rises out. */
    fun trackChange(): ContentTransform = ContentTransform(
        targetContentEnter = slideInVertically(tween(320, easing = EmphasizedDecelerate)) { it / 2 } + fadeIn(tween(220)),
        initialContentExit = slideOutVertically(tween(200, easing = EmphasizedAccelerate)) { -it / 2 } + fadeOut(tween(120)),
        sizeTransform = SizeTransform(clip = false),
    )
}

val LocalSharedTransitionScope = compositionLocalOf<SharedTransitionScope?> { null }
val LocalAnimatedScope = compositionLocalOf<AnimatedVisibilityScope?> { null }

/** A container (e.g. the search bar) that morphs into its counterpart on the next screen. */
@Composable
fun Modifier.sharedContainer(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedContainer.sharedBounds(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = Motion.ArtBounds,
        )
    }
}

/** Marks artwork that should morph between screens sharing the same [key]. No-op outside a transition. */
@Composable
fun Modifier.sharedArt(key: String): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = LocalAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedArt.sharedElement(
            state = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = Motion.ArtBounds,
        )
    }
}

/**
 * Light vertical swipe: fires after a short drag (~28dp past touch slop) or on any quick flick.
 */
fun Modifier.verticalSwipe(
    onSwipeUp: (() -> Unit)? = null,
    onSwipeDown: (() -> Unit)? = null,
): Modifier = pointerInput(onSwipeUp != null, onSwipeDown != null) {
    val distance = 28.dp.toPx()
    val fling = 350.dp.toPx() // per second
    val tracker = VelocityTracker()
    var total = 0f
    detectVerticalDragGestures(
        onDragStart = {
            total = 0f
            tracker.resetTracking()
        },
        onDragEnd = {
            val velocity = tracker.calculateVelocity().y
            when {
                total < 0 && (total < -distance || velocity < -fling) -> onSwipeUp?.invoke()
                total > 0 && (total > distance || velocity > fling) -> onSwipeDown?.invoke()
            }
        },
    ) { change, dy ->
        change.consume()
        total += dy
        tracker.addPosition(change.uptimeMillis, change.position)
    }
}
