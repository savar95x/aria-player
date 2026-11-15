package dev.savar.aria.data

import android.content.Context
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap

/**
 * Artist photos from Deezer's public catalogue (no key needed), saved on the phone so each
 * artist is looked up once. Only an exact name match is used: no photo beats the wrong photo.
 * Artists Deezer has no picture for are remembered too, and retried after a couple of weeks.
 */
class ArtistPhotos(context: Context) {
    private val dir = File(context.filesDir, "artists").apply { mkdirs() }

    /** name key -> photo file (or null = checked, nothing found). Filled from disk on first use. */
    private val known: ConcurrentHashMap<String, Result> by lazy {
        val map = ConcurrentHashMap<String, Result>()
        val now = System.currentTimeMillis()
        dir.listFiles().orEmpty().forEach { f ->
            when (f.extension) {
                "jpg" -> map[f.nameWithoutExtension] = Result(f)
                "none" -> if (now - f.lastModified() < RETRY_MS) map[f.nameWithoutExtension] = Result(null)
            }
        }
        map
    }
    private val inFlight = ConcurrentHashMap<String, CompletableDeferred<File?>>()
    private val gate = Semaphore(3)

    private class Result(val file: File?)

    /** Instant answer from what's already on disk (so cached photos never flash album art first). */
    fun cached(name: String): File? = known[key(name)]?.file

    /** Looks the artist up if we haven't before. Null when there's no photo or no network. */
    suspend fun get(name: String): File? {
        if (normalize(name).isEmpty() || name.contains("unknown", ignoreCase = true)) return null
        val k = key(name)
        known[k]?.let { return it.file }
        val mine = CompletableDeferred<File?>()
        val running = inFlight.putIfAbsent(k, mine)
        if (running != null) return running.await()
        val result = runCatching { gate.withPermit { withContext(Dispatchers.IO) { fetch(name, k) } } }.getOrNull()
        mine.complete(result)
        inFlight.remove(k)
        return result
    }

    private fun fetch(name: String, k: String): File? {
        val q = URLEncoder.encode(name, "UTF-8")
        val json = JSONObject(String(http("https://api.deezer.com/search/artist?q=$q&limit=10")))
        val hits = json.optJSONArray("data")
        var url: String? = null
        if (hits != null) for (i in 0 until hits.length()) {
            val hit = hits.getJSONObject(i)
            if (normalize(hit.optString("name")) != normalize(name)) continue
            val pic = hit.optString("picture_xl").ifBlank { hit.optString("picture_big") }
            if (pic.isNotBlank() && !isPlaceholder(pic)) { url = pic; break }
        }
        if (url == null) {
            File(dir, "$k.none").writeText("")
            known[k] = Result(null)
            return null
        }
        // 500px is plenty for the 168dp header and keeps the cache small.
        val bytes = http(url.replace("1000x1000", "500x500"))
        val tmp = File(dir, "$k.tmp").apply { writeBytes(bytes) }
        val out = File(dir, "$k.jpg")
        tmp.renameTo(out)
        File(dir, "$k.none").delete()
        known[k] = Result(out)
        return out
    }

    private fun http(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 12_000
        conn.setRequestProperty("User-Agent", "Aria (https://github.com/savar95x/aria-player)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    private fun key(name: String): String =
        MessageDigest.getInstance("SHA-1").digest(normalize(name).toByteArray())
            .joinToString("") { "%02x".format(it) }

    private companion object {
        const val RETRY_MS = 14L * 24 * 60 * 60 * 1000
        /** Deezer's grey silhouette: empty image hash, or the md5 of an empty string. */
        fun isPlaceholder(url: String) = "/artist//" in url || "d41d8cd98f00b204e9800998ecf8427e" in url
        fun normalize(s: String) = s.lowercase().replace("&", "and").filter { it.isLetterOrDigit() }
    }
}
