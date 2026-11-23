package dev.savar.aria.data

import android.content.Context
import android.net.Uri
import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileNotFoundException
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.abs

@Immutable
data class LyricLine(val timeMs: Long, val text: String)

sealed interface Lyrics {
    @Immutable data class Synced(val lines: List<LyricLine>) : Lyrics
    @Immutable data class Plain(val text: String) : Lyrics
    data object Instrumental : Lyrics
}

sealed interface LyricsState {
    data object Loading : LyricsState
    data class Found(val lyrics: Lyrics) : LyricsState
    /** [query] pre-fills the manual search box. */
    data class NotFound(val query: String) : LyricsState
    data class Failed(val query: String) : LyricsState
}

/** What we know about the track we want lyrics for. */
data class LyricsRequest(
    val key: String,
    val title: String,
    val artist: String,
    val album: String?,
    val durationMs: Long,
    val path: String?,
)

/**
 * Lyrics, in order: our own cache → tags embedded in the file → LRCLIB (free, synced lyrics).
 * Online results are cached, so each song is looked up once and works offline afterwards.
 */
class LyricsRepository(private val context: Context) {
    private val dir = File(context.filesDir, "lyrics").apply { mkdirs() }

    init {
        // v2 prefers synced lyrics over plain ones from the file: re-check songs cached as plain once.
        val marker = File(dir, ".v2")
        if (!marker.exists()) {
            dir.listFiles { f -> f.extension == "txt" }.orEmpty().forEach { it.delete() }
            marker.writeText("")
        }
    }

    suspend fun load(req: LyricsRequest): LyricsState = withContext(Dispatchers.IO) {
        cached(req.key)?.let { return@withContext LyricsState.Found(it) }
        // Synced lyrics in the file win outright. Plain ones (often pasted from Genius, with
        // "Read More" junk) are only a fallback: try LRCLIB for synced first.
        val inFile = embedded(req.path)
        if (inFile != null && inFile !is Lyrics.Plain) return@withContext LyricsState.Found(inFile.also { save(req.key, it) })
        val light = stripPrefixes(req.title, req.artist)
        val title = cleanTitle(req.title, req.artist)
        val artist = primaryArtist(req.artist)
        try {
            // Exact match with the title as tagged, then with brackets/feat removed, then fuzzy search.
            val found = fetchExact(light, artist, req.album, req.durationMs)
                ?: (if (title != light) fetchExact(title, artist, req.album, req.durationMs) else null)
                ?: fetchSearch(title, artist, req.durationMs)
            val best = when {
                found != null && found !is Lyrics.Plain -> found
                inFile != null -> inFile
                else -> found
            }
            if (best != null) {
                save(req.key, best)
                LyricsState.Found(best)
            } else {
                LyricsState.NotFound("$title $artist".trim())
            }
        } catch (e: Exception) {
            // Offline: the file's own lyrics are better than nothing (not cached, so we retry online later).
            if (inFile != null) LyricsState.Found(inFile) else LyricsState.Failed("$title $artist".trim())
        }
    }

    /** Free-text search, for when automatic matching misses. */
    suspend fun search(req: LyricsRequest, query: String): LyricsState = withContext(Dispatchers.IO) {
        try {
            val results = JSONArray(String(get("https://lrclib.net/api/search?q=" + Uri.encode(query))))
            pickBest(results, req.durationMs, strict = false)?.let { save(req.key, it); LyricsState.Found(it) }
                ?: LyricsState.NotFound(query)
        } catch (e: Exception) {
            LyricsState.Failed(query)
        }
    }

    // ---- cache -------------------------------------------------------------------------------

    private fun cached(key: String): Lyrics? {
        File(dir, "$key.lrc").takeIf { it.exists() }?.let { return parseLrc(it.readText()) }
        File(dir, "$key.txt").takeIf { it.exists() }?.let { return Lyrics.Plain(it.readText()) }
        if (File(dir, "$key.inst").exists()) return Lyrics.Instrumental
        return null
    }

    private fun save(key: String, lyrics: Lyrics) {
        runCatching {
            when (lyrics) {
                is Lyrics.Synced -> File(dir, "$key.lrc").writeText(
                    lyrics.lines.joinToString("\n") { "[${formatTime(it.timeMs)}]${it.text}" },
                )
                is Lyrics.Plain -> File(dir, "$key.txt").writeText(lyrics.text)
                Lyrics.Instrumental -> File(dir, "$key.inst").writeText("")
            }
        }
    }

    // ---- embedded ----------------------------------------------------------------------------

    private fun embedded(path: String?): Lyrics? {
        if (path == null) return null
        return runCatching {
            TagOptionSingleton.getInstance().isAndroid = true
            val text = AudioFileIO.read(File(path)).tag?.getFirst(FieldKey.LYRICS)?.trim().orEmpty()
            if (text.isEmpty()) null else fromText(text)
        }.getOrNull()
    }

    // ---- LRCLIB ------------------------------------------------------------------------------

    private fun fetchExact(title: String, artist: String, album: String?, durationMs: Long): Lyrics? {
        // LRCLIB's exact lookup needs album and duration; without them go straight to search.
        if (album.isNullOrBlank() || durationMs <= 0 || artist.isBlank()) return null
        val url = buildString {
            append("https://lrclib.net/api/get?track_name=").append(Uri.encode(title))
            append("&artist_name=").append(Uri.encode(artist))
            if (!album.isNullOrBlank()) append("&album_name=").append(Uri.encode(album))
            if (durationMs > 0) append("&duration=").append(durationMs / 1000)
        }
        return try {
            fromResult(JSONObject(String(get(url))))
        } catch (e: FileNotFoundException) {
            null // 404: no exact match
        }
    }

    private fun fetchSearch(title: String, artist: String, durationMs: Long): Lyrics? {
        val url = "https://lrclib.net/api/search?track_name=" + Uri.encode(title) +
            (if (artist.isNotBlank()) "&artist_name=" + Uri.encode(artist) else "")
        return try {
            pickBest(JSONArray(String(get(url))), durationMs, strict = true)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    /** Closest duration wins, synced preferred; [strict] rejects matches more than 8s off. */
    private fun pickBest(results: JSONArray, durationMs: Long, strict: Boolean): Lyrics? {
        val candidates = (0 until results.length()).map { results.getJSONObject(it) }
            .filter { fromResult(it) != null }
            .sortedWith(
                compareBy<JSONObject>(
                    { if (durationMs > 0) abs(it.optDouble("duration", 0.0) * 1000 - durationMs) else 0.0 },
                    { if (it.isNull("syncedLyrics") || it.optString("syncedLyrics").isBlank()) 1 else 0 },
                ),
            )
        val best = candidates.firstOrNull() ?: return null
        if (strict && durationMs > 0 && abs(best.optDouble("duration", 0.0) * 1000 - durationMs) > 8_000) return null
        return fromResult(best)
    }

    private fun fromResult(o: JSONObject): Lyrics? {
        val synced = if (o.isNull("syncedLyrics")) "" else o.optString("syncedLyrics")
        val plain = if (o.isNull("plainLyrics")) "" else o.optString("plainLyrics")
        return when {
            synced.isNotBlank() -> parseLrc(synced)
            plain.isNotBlank() -> Lyrics.Plain(plain.trim())
            o.optBoolean("instrumental") -> Lyrics.Instrumental
            else -> null
        }
    }

    private fun get(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Aria (https://github.com/savar95x/aria-player)")
        try {
            // 4xx = no match / bad query: callers treat it as "not found", not as a failure.
            if (conn.responseCode in 400..499) throw FileNotFoundException(url)
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private val timeTag = Regex("""\[(\d{1,3}):(\d{1,2}(?:[.:]\d{1,3})?)]""")
        private val offsetTag = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        fun fromText(text: String): Lyrics = if (timeTag.containsMatchIn(text)) parseLrc(text) else Lyrics.Plain(text)

        fun parseLrc(text: String): Lyrics {
            val offset = offsetTag.find(text)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            val lines = text.lineSequence().flatMap { raw ->
                val stamps = timeTag.findAll(raw).toList()
                if (stamps.isEmpty()) return@flatMap emptySequence()
                val words = cleanLine(raw.substring(stamps.last().range.last + 1))
                stamps.asSequence().map { m ->
                    val min = m.groupValues[1].toLong()
                    val sec = m.groupValues[2].replace(':', '.').toDouble()
                    LyricLine(((min * 60 + sec) * 1000).toLong() - offset, words)
                }
            }.sortedBy { it.timeMs }.toList()
            return if (lines.isEmpty()) Lyrics.Plain(text) else Lyrics.Synced(lines)
        }

        // "Enhanced LRC" (karaoke files, often embedded in tags): a <mm:ss.xx> stamp before every word
        // and a voice label ("v1:", "v2:", "bg:") at the start. Aria syncs by line, so both are dropped.
        private val wordTag = Regex("""<\d{1,3}:\d{1,2}(?:[.:]\d{1,3})?>""")
        private val voiceTag = Regex("""^\s*(?:v\d+|bg)\s*:\s*""", RegexOption.IGNORE_CASE)

        private fun cleanLine(text: String): String =
            text.replace(wordTag, "").replace(voiceTag, "").replace(Regex("""\s+"""), " ").trim()

        private fun formatTime(ms: Long): String {
            val t = ms.coerceAtLeast(0)
            return "%02d:%02d.%02d".format(t / 60_000, (t / 1000) % 60, (t % 1000) / 10)
        }

        /** "01 - J. Cole - Cole Summer (Prod. X) [Official]" → "Cole Summer". */
        fun cleanTitle(title: String, artist: String): String {
            val t = stripPrefixes(title, artist).replace(Regex("""\s*[(\[][^)\]]*[)\]]"""), "")
                .replace(Regex("""\s+(feat\.?|ft\.?|featuring)\s+.*$""", RegexOption.IGNORE_CASE), "")
                .trim()
            return t.ifEmpty { title.trim() }
        }

        /** Drops a leading track number and "Artist - " prefix, keeps the rest as tagged. */
        fun stripPrefixes(title: String, artist: String): String {
            var t = title.replace(Regex("""^\s*\d{1,3}\s*[-._)]\s*"""), "")
            primaryArtist(artist).takeIf { it.isNotBlank() }?.let { a ->
                t = t.replace(Regex("""^\s*${Regex.escape(a)}\s*[-–:]\s*""", RegexOption.IGNORE_CASE), "")
            }
            return t.trim().ifEmpty { title.trim() }
        }

        /** "Kanye West/Ty Dolla \$ign" → "Kanye West". */
        fun primaryArtist(artist: String): String {
            if (artist.startsWith("Unknown", ignoreCase = true)) return ""
            return artist.split("/", ",", " & ", " x ", " feat", " ft.").first().trim()
        }
    }
}
