package dev.savar.aria.ui.theme

import android.app.Activity
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import dev.savar.aria.data.AppSettings
import dev.savar.aria.data.ThemeMode

private val AriaDark = darkColorScheme(
    primary = Color(0xFFC5B8FF),
    onPrimary = Color(0xFF2B1D6B),
    primaryContainer = Color(0xFF433589),
    onPrimaryContainer = Color(0xFFE6DEFF),
    secondary = Color(0xFFFFB1C1),
    onSecondary = Color(0xFF5E1129),
    secondaryContainer = Color(0xFF4A3A5E),
    onSecondaryContainer = Color(0xFFEBDCFF),
    tertiary = Color(0xFF8FD3E8),
)

private val AriaLight = lightColorScheme(
    primary = Color(0xFF5B4DA6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6DEFF),
    onPrimaryContainer = Color(0xFF170362),
    secondary = Color(0xFF9C3D58),
    secondaryContainer = Color(0xFFEBDCFF),
    onSecondaryContainer = Color(0xFF2B1D3F),
    tertiary = Color(0xFF00677D),
)

/** Pure black surfaces, but keep a hint of the accent hue in the raised containers. */
private fun ColorScheme.amoled(): ColorScheme = copy(
    background = Color.Black,
    surface = Color.Black,
    surfaceDim = Color.Black,
    surfaceContainerLowest = Color.Black,
    surfaceContainerLow = lerp(Color.Black, surfaceContainerLow, 0.45f),
    surfaceContainer = lerp(Color.Black, surfaceContainer, 0.55f),
    surfaceContainerHigh = lerp(Color.Black, surfaceContainerHigh, 0.65f),
    surfaceContainerHighest = lerp(Color.Black, surfaceContainerHighest, 0.75f),
    surfaceVariant = lerp(Color.Black, surfaceVariant, 0.7f),
)

@Composable
fun AriaTheme(settings: AppSettings, content: @Composable () -> Unit) {
    val dark = when (settings.themeMode) {
        ThemeMode.System -> isSystemInDarkTheme()
        ThemeMode.Light -> false
        ThemeMode.Dark -> true
    }
    val context = LocalContext.current
    val base = when {
        settings.dynamicColor && dark -> dynamicDarkColorScheme(context)
        settings.dynamicColor -> dynamicLightColorScheme(context)
        dark -> AriaDark
        else -> AriaLight
    }
    val scheme = if (dark && settings.amoled) base.amoled() else base
    val typography = remember(settings.font) { typographyFor(settings.font) }

    val view = LocalView.current
    if (!view.isInEditMode) {
        SideEffect {
            val window = (view.context as Activity).window
            WindowCompat.getInsetsController(window, view).apply {
                isAppearanceLightStatusBars = !dark
                isAppearanceLightNavigationBars = !dark
            }
        }
    }

    MaterialTheme(colorScheme = scheme, typography = typography, content = content)
}
