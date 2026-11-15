package dev.savar.aria.data

import android.content.Context
import dev.savar.aria.ui.theme.FeaturedFonts
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/** One Google Fonts family. [popularity]: 1 = most used. */
data class FontEntry(val name: String, val category: FontCategory, val popularity: Int)

enum class FontCategory(val label: String) {
    Sans("Sans"), Serif("Serif"), Display("Display"), Handwriting("Handwriting"), Mono("Mono");

    companion object {
        fun of(raw: String): FontCategory = when (raw.lowercase()) {
            "serif" -> Serif
            "display" -> Display
            "handwriting" -> Handwriting
            "monospace" -> Mono
            else -> Sans
        }
    }
}

/**
 * The whole Google Fonts catalogue (about 1,800 families), from Google's public font list, no API key.
 * Cached on the phone and refreshed weekly; offline before the first fetch it falls back to the
 * hand-picked [FeaturedFonts].
 */
class FontCatalog(context: Context) {
    private val file = File(context.filesDir, "fonts.json")

    suspend fun load(): List<FontEntry> = withContext(Dispatchers.IO) {
        val fresh = file.exists() && System.currentTimeMillis() - file.lastModified() < WEEK_MS
        if (!fresh) runCatching { file.writeText(download()) }
        runCatching { parse(file.readText()) }.getOrNull()?.takeIf { it.isNotEmpty() } ?: fallback()
    }

    private fun download(): String {
        val conn = URL("https://fonts.google.com/metadata/fonts").openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 20_000
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            val text = conn.inputStream.bufferedReader().use { it.readText() }
            parse(text).also { require(it.size > 100) } // sanity check before caching
            return text
        } finally {
            conn.disconnect()
        }
    }

    private fun parse(text: String): List<FontEntry> {
        // The response starts with an anti-JSON-hijacking prefix: skip to the first '{'.
        val json = JSONObject(text.substring(text.indexOf('{')))
        val list = json.getJSONArray("familyMetadataList")
        return (0 until list.length()).map { list.getJSONObject(it) }
            .map { FontEntry(it.getString("family"), FontCategory.of(it.optString("category")), it.optInt("popularity", Int.MAX_VALUE)) }
            .sortedBy { it.popularity }
    }

    private fun fallback() = FeaturedFonts.mapIndexed { i, name ->
        FontEntry(name, if (name in setOf("Fraunces", "Playfair Display")) FontCategory.Serif else if (name == "JetBrains Mono") FontCategory.Mono else FontCategory.Sans, i)
    }

    private companion object {
        const val WEEK_MS = 7L * 24 * 60 * 60 * 1000
    }
}
