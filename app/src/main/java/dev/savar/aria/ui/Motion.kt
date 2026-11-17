@file:OptIn(ExperimentalSharedTransitionApi::class)

package dev.savar.aria.ui

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.ContentTransform
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect

/** Material 3 motion tokens. */
object Motion {
    val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

    /** How the now-playing sheet finishes after a tap or a released drag (carries the flick's speed). */
    val SheetSpring = spring(dampingRatio = 1f, stiffness = 420f, visibilityThreshold = SheetThreshold)

    /** Sheet position is 0..1 over ~2000px, so stop only when the remainder is well under a pixel. */
    const val SheetThreshold = 0.0002f

    /** Art flying between screens (shared elements). */
    // Critically damped and stiffer: same glide, but no slow settling tail. Navigation counts a page
    // as "still animating" until its shared artwork stops, and a back swipe can't preview until then.
    val ArtBounds = BoundsTransform { _: Rect, _: Rect -> spring(dampingRatio = 1f, stiffness = 1000f) }

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
fun Modifier.sharedArt(key: String, scope: AnimatedVisibilityScope? = null): Modifier {
    val shared = LocalSharedTransitionScope.current ?: return this
    val animated = scope ?: LocalAnimatedScope.current ?: return this
    return with(shared) {
        this@sharedArt.sharedElement(
            sharedContentState = rememberSharedContentState(key),
            animatedVisibilityScope = animated,
            boundsTransform = Motion.ArtBounds,
        )
    }
}
