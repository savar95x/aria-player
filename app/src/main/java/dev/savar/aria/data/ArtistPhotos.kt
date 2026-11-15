package dev.savar.aria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist photos, kept on the phone so each artist is looked up once.
 *
 * Automatic pick: search Deezer for the artist *together with one of your own albums or songs*,
 * so "Adele" means the Adele whose albums you have, not the first Adele in the catalogue. If that
 * finds nothing, fall back to the most-followed artist with exactly that name. No photo beats a
 * wrong photo, so anything without an exact name match is skipped.
 *
 * The user can override it: another online photo, one from the gallery, or "use album art".
 * Overrides are never replaced automatically.
 *
 * Files in filesDir/artists, `<key>` = sha1 of the normalised name:
 *   `<key>.<time>.jpg` automatic photo, `<key>.<time>.pick.jpg` chosen by the user,
 *   `<key>.none` looked up, nothing found (retried after two weeks), `<key>.off` user wants album art.
 * A new file name per change means image caches never show a stale photo.
 */
class ArtistPhotos(private val context: Context) {
    data class Hints(val albums: List<String>, val titles: List<String>)

    /** A photo the user can pick: [url] is the full image, [thumb] a small one for the grid. */
    data class Candidate(val url: String, val thumb: String, val source: String, val label: String)

    private sealed interface Entry {
        data class Photo(val file: File, val picked: Boolean) : Entry
        data class NoPhoto(val picked: Boolean) : Entry
    }

    private val dir = File(context.filesDir, "artists").apply { mkdirs() }
    private val _changes = MutableStateFlow(0L)

    /** Bumps whenever a photo is added, replaced or removed; the UI re-reads [cached] on change. */
    val changes: StateFlow<Long> = _changes.asStateFlow()

    private val known: ConcurrentHashMap<String, Entry> by lazy { loadIndex() }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File?>>()
    private val gate = Semaphore(3)

    /** Instant answer from disk, so a saved photo never flashes album art first. */
    fun cached(name: String): File? = (known[key(name)] as? Entry.Photo)?.file

    /** True if the user picked this photo themselves (or chose album art). */
    fun isUserChoice(name: String): Boolean = when (val e = known[key(name)]) {
        is Entry.Photo -> e.picked
        is Entry.NoPhoto -> e.picked
        null -> false
    }

    /** Looks the artist up if we haven't before. Null when there's no photo or no network. */
    suspend fun get(name: String, hints: Hints): File? {
        if (!lookupable(name)) return null
        val k = key(name)
        known[k]?.let { return (it as? Entry.Photo)?.file }
        val mine = CompletableDeferred<File?>()
        val running = inFlight.putIfAbsent(k, mine)
        if (running != null) return running.await()
        val result = runCatching {
            gate.withPermit { withContext(Dispatchers.IO) { autoFetch(name, k, hints) } }
        }.getOrNull()
        mine.complete(result)
        inFlight.remove(k)
        return result
    }

    /** Photos to choose from: Deezer (matched through your albums, then by name) and Wikipedia. */
    suspend fun candidates(name: String, hints: Hints): List<Candidate> = withContext(Dispatchers.IO) {
        coroutineScope {
            val deezer = async { runCatching { deezerCandidates(name, hints) }.getOrDefault(emptyList()) }
            val wiki = async { runCatching { wikiCandidates(name) }.getOrDefault(emptyList()) }
            (deezer.await() + wiki.await()).distinctBy { it.url }
        }
    }

    suspend fun choose(name: String, candidate: Candidate): Boolean = withContext(Dispatchers.IO) {
        runCatching { save(key(name), shrink(http(candidate.url)), picked = true) }.isSuccess
    }

    suspend fun chooseFromGallery(name: String, uri: Uri): Boolean = withContext(Dispatchers.IO) {
        runCatching {
            val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(context.contentResolver, uri)) { decoder, info, _ ->
                val longest = maxOf(info.size.width, info.size.height)
                if (longest > MAX_SIDE) {
                    val f = MAX_SIDE.toFloat() / longest
                    decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
                }
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            }
            save(key(name), jpeg(bitmap), picked = true)
        }.isSuccess
    }

    /** Show album art for this artist, and never look a photo up again. */
    fun useAlbumArt(name: String) {
        val k = key(name)
        clear(k)
        File(dir, "$k.off").writeText("")
        known[k] = Entry.NoPhoto(picked = true)
        _changes.value++
    }

    /** Forget any choice; the automatic photo is looked up again. */
    fun automatic(name: String) {
        val k = key(name)
        clear(k)
        known.remove(k)
        _changes.value++
    }

    // ---- automatic lookup ----

    private fun autoFetch(name: String, k: String, hints: Hints): File? {
        val url = matchThroughLibrary(name, hints) ?: mostFollowed(name)
        if (url == null) {
            clear(k)
            File(dir, "$k.none").writeText("")
            known[k] = Entry.NoPhoto(picked = false)
            return null
        }
        return save(k, http(url), picked = false)
    }

    /** The artist as credited on one of your own albums / songs: the right person, not a namesake. */
    private fun matchThroughLibrary(name: String, hints: Hints): String? {
        for (album in hints.albums.take(2)) {
            pictureOf(deezer("search/album", "artist:\"$name\" album:\"$album\""), name)?.let { return it }
        }
        for (title in hints.titles.take(1)) {
            pictureOf(deezer("search/track", "artist:\"$name\" track:\"$title\""), name)?.let { return it }
        }
        return null
    }

    private fun pictureOf(results: List<JSONObject>, name: String): String? =
        results.asSequence()
            .mapNotNull { it.optJSONObject("artist") }
            .filter { sameName(it.optString("name"), name) }
            .mapNotNull { picture(it) }
            .firstOrNull()

    private fun mostFollowed(name: String): String? =
        deezer("search/artist", name)
            .filter { sameName(it.optString("name"), name) }
            .sortedByDescending { it.optLong("nb_fan") }
            .firstNotNullOfOrNull { picture(it) }

    // ---- candidates for the picker ----

    private fun deezerCandidates(name: String, hints: Hints): List<Candidate> {
        val out = mutableListOf<Candidate>()
        matchThroughLibrary(name, hints)?.let {
            out += Candidate(it, it.replace("1000x1000", "250x250"), "Deezer", "Matched to your albums")
        }
        deezer("search/artist", name).take(8).forEach { a ->
            val pic = picture(a) ?: return@forEach
            val fans = a.optLong("nb_fan")
            out += Candidate(pic, pic.replace("1000x1000", "250x250"), "Deezer", "${a.optString("name")} · ${compact(fans)} fans")
        }
        return out
    }

    private fun wikiCandidates(name: String): List<Candidate> {
        val q = enc("\"$name\" music")
        val url = "https://en.wikipedia.org/w/api.php?action=query&format=json&formatversion=2" +
            "&generator=search&gsrsearch=$q&gsrlimit=8&prop=pageimages&piprop=thumbnail&pithumbsize=1000"
        val pages = JSONObject(String(http(url))).optJSONObject("query")?.optJSONArray("pages") ?: return emptyList()
        return (0 until pages.length()).map { pages.getJSONObject(it) }
            .sortedBy { it.optInt("index") }
            .mapNotNull { p ->
                val src = p.optJSONObject("thumbnail")?.optString("source")?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                if (src.endsWith(".svg.png", ignoreCase = true)) return@mapNotNull null // logos, maps, icons
                Candidate(src, src, "Wikipedia", p.optString("title"))
            }
    }

    // ---- storage ----

    private fun loadIndex(): ConcurrentHashMap<String, Entry> {
        // v1 (first release) matched by name only and picked some namesakes; redo automatic picks once.
        val marker = File(dir, ".v2")
        if (!marker.exists()) {
            dir.listFiles().orEmpty().forEach { f -> if (!f.name.endsWith(".pick.jpg") && !f.name.endsWith(".off")) f.delete() }
            marker.writeText("")
        }
        val map = ConcurrentHashMap<String, Entry>()
        val now = System.currentTimeMillis()
        dir.listFiles().orEmpty().sortedBy { it.name }.forEach { f ->
            val k = f.name.substringBefore('.')
            if (k.isEmpty()) return@forEach
            val entry: Entry? = when {
                f.name.endsWith(".pick.jpg") -> Entry.Photo(f, picked = true)
                f.name.endsWith(".jpg") -> Entry.Photo(f, picked = false)
                f.name.endsWith(".off") -> Entry.NoPhoto(picked = true)
                f.name.endsWith(".none") -> if (now - f.lastModified() < RETRY_MS) Entry.NoPhoto(picked = false) else null
                f.name.endsWith(".tmp") -> { f.delete(); null }
                else -> null
            }
            if (entry != null && (map[k] == null || isPicked(entry))) map[k] = entry
        }
        return map
    }

    private fun isPicked(e: Entry) = (e as? Entry.Photo)?.picked == true || (e as? Entry.NoPhoto)?.picked == true

    private fun save(k: String, bytes: ByteArray, picked: Boolean): File {
        val tmp = File(dir, "$k.tmp").apply { writeBytes(bytes) }
        val out = File(dir, "$k.${System.currentTimeMillis()}" + if (picked) ".pick.jpg" else ".jpg")
        clear(k)
        tmp.renameTo(out)
        known[k] = Entry.Photo(out, picked)
        _changes.value++
        return out
    }

    private fun clear(k: String) {
        dir.listFiles { f -> f.name.substringBefore('.') == k && !f.name.endsWith(".tmp") }.orEmpty().forEach { it.delete() }
    }

    /** Big downloads (Wikipedia originals can be huge) are scaled to [MAX_SIDE]. */
    private fun shrink(bytes: ByteArray): ByteArray {
        if (bytes.size < 600_000) return bytes
        val bitmap = ImageDecoder.decodeBitmap(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes))) { decoder, info, _ ->
            val longest = maxOf(info.size.width, info.size.height)
            if (longest > MAX_SIDE) {
                val f = MAX_SIDE.toFloat() / longest
                decoder.setTargetSize((info.size.width * f).toInt(), (info.size.height * f).toInt())
            }
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
        }
        return jpeg(bitmap)
    }

    private fun jpeg(bitmap: Bitmap): ByteArray =
        ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }.toByteArray()

    // ---- network ----

    private fun deezer(path: String, query: String): List<JSONObject> {
        val json = JSONObject(String(http("https://api.deezer.com/$path?q=${enc(query)}&limit=10")))
        val data = json.optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { data.getJSONObject(it) }
    }

    /** Deezer's 1000px picture, or null for its grey "no photo" silhouette. */
    private fun picture(artist: JSONObject): String? {
        val pic = artist.optString("picture_xl").ifBlank { artist.optString("picture_big") }
        return pic.takeIf { it.isNotBlank() && "/artist//" !in it && EMPTY_HASH !in it }
    }

    private fun http(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Aria/1.0 (https://github.com/savar95x/aria-player)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun key(name: String): String =
        MessageDigest.getInstance("SHA-1").digest(normalize(name).toByteArray()).joinToString("") { "%02x".format(it) }

    private companion object {
        const val RETRY_MS = 14L * 24 * 60 * 60 * 1000
        const val MAX_SIDE = 1200
        /** md5 of the empty string: Deezer's placeholder image hash. */
        const val EMPTY_HASH = "d41d8cd98f00b204e9800998ecf8427e"

        fun enc(s: String): String = URLEncoder.encode(s, "UTF-8")
        fun normalize(s: String) = s.lowercase().removePrefix("the ").replace("&", "and").filter { it.isLetterOrDigit() }
        fun sameName(a: String, b: String) = normalize(a).isNotEmpty() && normalize(a) == normalize(b)
        fun lookupable(name: String) = normalize(name).isNotEmpty() && !name.contains("unknown", ignoreCase = true)
        fun compact(n: Long): String = when {
            n >= 1_000_000 -> "%.1fM".format(n / 1_000_000.0).replace(".0M", "M")
            n >= 1_000 -> "${n / 1_000}K"
            else -> n.toString()
        }
    }
}
