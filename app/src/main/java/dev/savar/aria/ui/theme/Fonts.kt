package dev.savar.aria.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.text.googlefonts.Font
import androidx.compose.ui.text.googlefonts.GoogleFont
import dev.savar.aria.R

/** Downloadable fonts via Google Play Services; cached by the system after first use. */
private val provider = GoogleFont.Provider(
    providerAuthority = "com.google.android.gms.fonts",
    providerPackage = "com.google.android.gms",
    certificates = R.array.com_google_android_gms_fonts_certs,
)

val FeaturedFonts = listOf(
    "Inter", "Manrope", "Outfit", "DM Sans", "Plus Jakarta Sans", "Figtree",
    "Space Grotesk", "Sora", "Lexend", "Urbanist", "Rubik", "Poppins",
    "Nunito", "Work Sans", "IBM Plex Sans", "JetBrains Mono", "Fraunces", "Playfair Display",
)

private val fontCache = HashMap<String, FontFamily>()

fun googleFontFamily(name: String): FontFamily = fontCache.getOrPut(name) {
    val font = GoogleFont(name)
    FontFamily(
        // Every weight, so the Weight setting can go lighter or bolder (fonts without a weight fall back).
        (100..900 step 100).map { FontWeight(it) }.map {
            Font(googleFont = font, fontProvider = provider, weight = it)
        },
    )
}

/** Material's default sizes read a bit large on a phone this dense; scale every style down. */
private const val TextScale = 0.92f

fun typographyFor(font: String?, textScale: Float = 1f, weightDelta: Int = 0, letterSpacing: Float = 0f): Typography {
    val family = font?.let { googleFontFamily(it) }
    val base = Typography()
    val scale = TextScale * textScale
    // Material's defaults leave almost everything at Regular, so nothing stands out. Aria's hierarchy:
    // headlines Bold, titles SemiBold, primary text (song titles, row titles, buttons) Medium, secondary text Regular.
    // The Weight setting shifts all of these together.
    fun TextStyle.f(weight: Int) = copy(
        fontFamily = family ?: fontFamily,
        fontSize = fontSize * scale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * scale else lineHeight,
        fontWeight = FontWeight((weight + weightDelta).coerceIn(100, 900)),
        // Material spaces body/title/label text out (+0.1 to +0.5sp). With Medium weight that reads
        // stretched and odd next to other players, which all use natural spacing. Default to 0
        // (keeping Material's tighter tracking on the big display styles); the setting overrides.
        letterSpacing = if (letterSpacing == 0f) {
            if (this.letterSpacing.isSpecified && this.letterSpacing.value < 0f) this.letterSpacing else 0.sp
        } else letterSpacing.sp,
    )
    return Typography(
        displayLarge = base.displayLarge.f(600),
        displayMedium = base.displayMedium.f(600),
        displaySmall = base.displaySmall.f(600),
        headlineLarge = base.headlineLarge.f(700),
        headlineMedium = base.headlineMedium.f(700),
        headlineSmall = base.headlineSmall.f(700),
        titleLarge = base.titleLarge.f(600),
        titleMedium = base.titleMedium.f(500),
        titleSmall = base.titleSmall.f(600),
        bodyLarge = base.bodyLarge.f(500),
        bodyMedium = base.bodyMedium.f(400),
        bodySmall = base.bodySmall.f(400),
        labelLarge = base.labelLarge.f(500),
        labelMedium = base.labelMedium.f(500),
        labelSmall = base.labelSmall.f(500),
    )
}
