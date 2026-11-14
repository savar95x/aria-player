package dev.savar.aria.metadata

import androidx.compose.runtime.Immutable
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.Locale

@Immutable
data class TrackMatch(
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String,
    val year: String,
    val genre: String,
    val track: String,
    val artworkUrl: String?,
    val thumbnailUrl: String?,
)

/**
 * Song lookup via the iTunes Search API: free, no key, good coverage (including Indian
 * catalogues when searched in the IN store), and 1200px+ cover art.
 */
object MetadataSearch {

    suspend fun search(term: String): List<TrackMatch> = withContext(Dispatchers.IO) {
        val country = Locale.getDefault().country.takeIf { it.length == 2 } ?: "US"
        val local = query(term, country)
        if (local.isNotEmpty() || country == "US") local else query(term, "US")
    }

    private fun query(term: String, country: String): List<TrackMatch> {
        val url = "https://itunes.apple.com/search?media=music&entity=song&limit=25" +
            "&country=$country&term=" + URLEncoder.encode(term, "UTF-8")
        val json = JSONObject(String(fetch(url)))
        val results = json.optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).map { i ->
            val r = results.getJSONObject(i)
            val art100 = r.optString("artworkUrl100").takeIf { it.isNotBlank() }
            val trackNo = r.optInt("trackNumber", 0)
            TrackMatch(
                title = r.optString("trackName"),
                artist = r.optString("artistName"),
                album = r.optString("collectionName"),
                albumArtist = r.optString("collectionArtistName").ifBlank { r.optString("artistName") },
                year = r.optString("releaseDate").take(4),
                genre = r.optString("primaryGenreName"),
                track = if (trackNo > 0) trackNo.toString() else "",
                artworkUrl = art100?.replace("100x100bb", "1200x1200bb"),
                thumbnailUrl = art100?.replace("100x100bb", "200x200bb"),
            )
        }
    }

    suspend fun download(url: String): ByteArray = withContext(Dispatchers.IO) { fetch(url) }

    private fun fetch(url: String): ByteArray {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 10_000
        conn.readTimeout = 15_000
        conn.setRequestProperty("User-Agent", "Aria/1.0 (+https://github.com/savar95x/aria-player)")
        try {
            if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}")
            return conn.inputStream.use { it.readBytes() }
        } finally {
            conn.disconnect()
        }
    }

    /** Turns "Artist - Song (Official Video) [4K]" style names into a useful query. */
    fun cleanQuery(title: String, artist: String): String {
        val t = title
            .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            .replace(Regex("(?i)official|video|audio|lyrics?|hd|4k|full song"), " ")
            .replace('_', ' ')
        val a = artist.takeUnless { it.startsWith("Unknown", ignoreCase = true) }.orEmpty()
        return "$t $a".replace(Regex("\\s+"), " ").trim()
    }
}
