package dev.savar.aria.ui.theme

import dev.savar.kit.FontSettings
import dev.savar.kit.kitTypography

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dev.savar.aria.R

/** Any Google Font, every weight, through Play Services: the shared kit's (same in all four apps). */
fun googleFontFamily(name: String): FontFamily = dev.savar.kit.googleFontFamily(name)

/** Aria's text styles, now the shared kit's: every app uses the hierarchy tuned here. */
fun typographyFor(font: String?, textScale: Float = 1f, weightDelta: Int = 0, letterSpacing: Float = 0f): Typography =
    kitTypography(FontSettings(font, textScale, weightDelta, letterSpacing))
