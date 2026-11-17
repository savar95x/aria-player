package dev.savar.aria.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.savar.aria.container
import dev.savar.aria.data.EmbeddedArt
import dev.savar.aria.data.MediaStoreQuery
import dev.savar.aria.data.songUri
import dev.savar.aria.playback.PlaybackService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * What the widget shows. Lives in memory; rebuilt from a snapshot / the saved queue after a cold start.
 *
 * Every change goes through one [Mutex], in arrival order: artwork decoding is slow, and without it
 * a quick skip A -> B -> C could finish B's decode last and leave the widget showing B.
 *
 * Rule: the widget must never be wrong. "Playing" is only shown while the player service is alive;
 * anything that might have gone stale (process killed, a tap that changed nothing) gets reconciled
 * by [refresh] or by the service pushing its real state.
 */
object WidgetState {

    data class Data(
        val songId: Long? = null,
        val title: String = "",
        val artist: String = "",
        val isPlaying: Boolean = false,
        val art: Bitmap? = null,
        val backdrop: Bitmap? = null,
    )

    private val lock = Mutex()
    private val _data = MutableStateFlow<Data?>(null)
    val data: StateFlow<Data?> = _data.asStateFlow()

    /** From the player (via [WidgetUpdateReceiver]) whenever its song, metadata or play state changes. */
    suspend fun apply(context: Context, id: Long?, title: String, artist: String, isPlaying: Boolean) = lock.withLock {
        val app = context.applicationContext
        ensureLoadedLocked(app)
        val current = _data.value
        if (id == null && title.isEmpty()) {
            // Player shutting down or between queues: keep showing the last song, paused.
            if (current != null && current.isPlaying) publish(app, current.copy(isPlaying = false))
            return@withLock
        }
        // Restored queue items can arrive before their metadata: never let that blank the titles.
        var t = title
        var a = artist
        if (t.isEmpty() && id != null) {
            if (current?.songId == id) { t = current.title; a = current.artist }
            else songFor(app, id)?.let { t = it.title; a = it.artist }
        }
        val next = if (current != null && current.songId == id) {
            current.copy(isPlaying = isPlaying, title = t, artist = a)
        } else {
            artData(app, id).copy(songId = id, title = t, artist = a, isPlaying = isPlaying)
        }
        if (next != current) publish(app, next)
    }

    /** Instant feedback for the widget's play button, before the player has answered. */
    suspend fun togglePlayingOptimistically(context: Context) = lock.withLock {
        val app = context.applicationContext
        ensureLoadedLocked(app)
        val current = _data.value ?: return@withLock
        publish(app, current.copy(isPlaying = !current.isPlaying))
    }

    /**
     * Re-draws from what we know, correcting anything stale: "playing" without a running player
     * (the process was killed mid-song), or a song whose cover never got decoded. Called when a
     * widget is added/resized, when the app process starts, and after widget taps.
     */
    suspend fun refresh(context: Context) = lock.withLock {
        val app = context.applicationContext
        ensureLoadedLocked(app)
        var d = _data.value ?: Data()
        if (d.isPlaying && PlaybackService.instance == null) d = d.copy(isPlaying = false)
        if (d.art == null && d.songId != null) {
            d = artData(app, d.songId).copy(songId = d.songId, title = d.title, artist = d.artist, isPlaying = d.isPlaying)
        }
        publish(app, d)
    }

    private fun publish(context: Context, d: Data) {
        _data.value = d
        snapshot(context, d)
        AriaWidget.render(context, d)
    }

    /** Fills in the last song when the process starts cold (e.g. after a reboot or being killed). */
    private suspend fun ensureLoadedLocked(app: Context) {
        if (_data.value != null) return
        // 1. Our own snapshot of the last song shown (survives the app being killed).
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        var id = prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 }
        var title = prefs.getString(KEY_TITLE, null).orEmpty()
        var artist = prefs.getString(KEY_ARTIST, null).orEmpty()
        // 2. Otherwise the player's saved queue.
        if (id == null || title.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { runCatching { app.container.queueStore.load() }.getOrNull() }
            val queued = saved?.ids?.getOrNull(saved.index)
            songFor(app, queued ?: id)?.let { id = it.id; title = it.title; artist = it.artist }
        }
        _data.value = artData(app, id).copy(songId = id, title = title, artist = artist)
    }

    private suspend fun songFor(context: Context, id: Long?) = id?.let {
        withContext(Dispatchers.IO) { runCatching { MediaStoreQuery.songsByIds(context, listOf(it)).firstOrNull() }.getOrNull() }
    }

    private fun snapshot(context: Context, d: Data) {
        if (d.songId == null || d.title.isEmpty()) return
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putLong(KEY_ID, d.songId).putString(KEY_TITLE, d.title).putString(KEY_ARTIST, d.artist).apply()
    }

    private const val PREFS = "widget"
    private const val KEY_ID = "id"
    private const val KEY_TITLE = "title"
    private const val KEY_ARTIST = "artist"

    private suspend fun artData(context: Context, id: Long?): Data = withContext(Dispatchers.IO) {
        // Nothing to decode for if there's no widget on any home screen.
        if (id == null || !AriaWidget.hasWidgets(context)) return@withContext Data()
        val raw = runCatching { EmbeddedArt.bitmap(context, songUri(id), 480) }.getOrNull() ?: return@withContext Data()
        val art = squareCrop(raw, 400)
        Data(art = art, backdrop = blur(art.scale(12, 12, filter = true)))
    }

    /** Centre square, so the cover keeps its shape in every widget size. */
    private fun squareCrop(src: Bitmap, size: Int): Bitmap {
        val side = minOf(src.width, src.height)
        val cropped = if (src.width == src.height) src
        else Bitmap.createBitmap(src, (src.width - side) / 2, (src.height - side) / 2, side, side)
        return if (side > size) cropped.scale(size, size, filter = true) else cropped
    }

    /** Cheap, smooth blur: shrink hard, then let bilinear filtering spread the colour back out. */
    private fun blur(tiny: Bitmap): Bitmap = tiny.scale(48, 48, filter = true).scale(160, 160, filter = true)
}
