package dev.savar.aria.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.runtime.Composable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
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
