package dev.savar.aria.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** "Corner roundness" from settings: 0 = square, 1 = default, 2 = twice as round. */
val LocalCornerScale = staticCompositionLocalOf { 1f }

/** A corner radius from the design, scaled by the roundness setting. */
@Composable
@ReadOnlyComposable
fun cornerDp(radius: Dp): Dp = radius * LocalCornerScale.current

@Composable
@ReadOnlyComposable
fun rounded(radius: Dp): RoundedCornerShape = RoundedCornerShape(cornerDp(radius))

/** Material's own component shapes (sheets, menus, fields, buttons…), scaled the same way. */
fun scaledShapes(scale: Float) = Shapes(
    extraSmall = RoundedCornerShape(4.dp * scale),
    small = RoundedCornerShape(8.dp * scale),
    medium = RoundedCornerShape(12.dp * scale),
    large = RoundedCornerShape(16.dp * scale),
    extraLarge = RoundedCornerShape(28.dp * scale),
)

/**
 * Corners that follow the item's size: [stops] pairs a width with the radius it should have.
 * Use the same stops on both ends of a shared-element morph (mini player <-> now playing), and the
 * radius eases between them as the artwork grows or shrinks, instead of snapping to the
 * destination's radius at the start of the animation.
 */
@Composable
fun morphingRounded(vararg stops: Pair<Dp, Dp>): Shape {
    val scale = LocalCornerScale.current
    return remember(scale, stops.toList()) {
        SizeMorphShape(stops.map { (w, r) -> w.value to r.value * scale }.sortedBy { it.first })
    }
}

private data class SizeMorphShape(val stops: List<Pair<Float, Float>>) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val w = size.minDimension / density.density
        val r = when {
            w <= stops.first().first -> stops.first().second
            w >= stops.last().first -> stops.last().second
            else -> {
                val i = stops.indexOfFirst { it.first >= w }
                val (w0, r0) = stops[i - 1]
                val (w1, r1) = stops[i]
                r0 + (r1 - r0) * (w - w0) / (w1 - w0)
            }
        }
        val px = (r * density.density).coerceAtMost(size.minDimension / 2)
        return Outline.Rounded(RoundRect(0f, 0f, size.width, size.height, CornerRadius(px)))
    }
}

/** Now-playing cover: 12dp in the mini player / lyrics header, 28dp full size. */
@Composable
fun playerArtShape(): Shape = morphingRounded(52.dp to 12.dp, 280.dp to 28.dp)

/** Album cover: 12dp in rows, 18dp as a grid tile, 24dp on the album page. */
@Composable
fun albumArtShape(): Shape = morphingRounded(52.dp to 12.dp, 160.dp to 18.dp, 240.dp to 24.dp)

/**
 * The big play/pause button. From 50% roundness up it ignores the slider and keeps Material's
 * squircle (paused) <-> circle (playing) morph; below 50% it follows the slider with one fixed
 * shape, because two slightly different small radii just looked like a glitch.
 */
@Composable
@ReadOnlyComposable
fun playButtonCorner(playing: Boolean): Dp {
    val scale = LocalCornerScale.current
    return when {
        scale >= 0.5f -> if (playing) 40.dp else 26.dp
        else -> 26.dp * scale
    }
}

/**
 * Pills and circles (buttons, the search bar, segmented buttons): fully round at the
 * default roundness and above, squaring off as the slider goes down (square at 0).
 */
@Composable
@ReadOnlyComposable
fun pill(): RoundedCornerShape = RoundedCornerShape(percent = (50 * LocalCornerScale.current.coerceIn(0f, 1f)).toInt())
