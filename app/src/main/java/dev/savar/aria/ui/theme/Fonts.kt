package dev.savar.aria.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
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
        listOf(FontWeight.Normal, FontWeight.Medium, FontWeight.SemiBold, FontWeight.Bold).map {
            Font(googleFont = font, fontProvider = provider, weight = it)
        },
    )
}

/** Material's default sizes read a bit large on a phone this dense; scale every style down. */
private const val TextScale = 0.92f

fun typographyFor(font: String?): Typography {
    val family = font?.let { googleFontFamily(it) }
    val base = Typography()
    fun TextStyle.f() = copy(
        fontFamily = family ?: fontFamily,
        fontSize = fontSize * TextScale,
        lineHeight = if (lineHeight.isSpecified) lineHeight * TextScale else lineHeight,
    )
    return Typography(
        displayLarge = base.displayLarge.f(),
        displayMedium = base.displayMedium.f(),
        displaySmall = base.displaySmall.f(),
        headlineLarge = base.headlineLarge.f(),
        headlineMedium = base.headlineMedium.f(),
        headlineSmall = base.headlineSmall.f(),
        titleLarge = base.titleLarge.f(),
        titleMedium = base.titleMedium.f(),
        titleSmall = base.titleSmall.f(),
        bodyLarge = base.bodyLarge.f(),
        bodyMedium = base.bodyMedium.f(),
        bodySmall = base.bodySmall.f(),
        labelLarge = base.labelLarge.f(),
        labelMedium = base.labelMedium.f(),
        labelSmall = base.labelSmall.f(),
    )
}
