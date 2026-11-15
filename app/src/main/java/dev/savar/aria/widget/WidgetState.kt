package dev.savar.aria.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import dev.savar.aria.container
import dev.savar.aria.data.EmbeddedArt
import dev.savar.aria.data.MediaStoreQuery
import dev.savar.aria.data.songUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

/** What the widget shows. Lives in memory; rebuilt from the saved queue after a cold start. */
object WidgetState {

    data class Data(
        val songId: Long? = null,
        val title: String = "",
        val artist: String = "",
        val isPlaying: Boolean = false,
        val art: Bitmap? = null,
        val backdrop: Bitmap? = null,
    )

    private val _data = MutableStateFlow<Data?>(null)
    val data: StateFlow<Data?> = _data.asStateFlow()

    /** Called (via [WidgetUpdateReceiver]) whenever the player's song or play state changes. */
    suspend fun apply(context: Context, id: Long?, title: String, artist: String, isPlaying: Boolean) {
        val app = context.applicationContext
        if (id == null && title.isEmpty()) {
            // Player shutting down or between queues: keep showing the last song, paused.
            ensureLoaded(app)
            val last = _data.value ?: return
            if (last.isPlaying) { _data.value = last.copy(isPlaying = false); AriaWidget.render(app) }
            return
        }
        val current = _data.value
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
        if (next != current) {
            _data.value = next
            snapshot(app, next)
            AriaWidget.render(app, next)
        }
    }

    /** Instant feedback for the widget's play button, before the player has answered. */
    suspend fun togglePlayingOptimistically(context: Context) {
        ensureLoaded(context)
        val current = _data.value ?: return
        val next = current.copy(isPlaying = !current.isPlaying)
        _data.value = next
        AriaWidget.render(context, next)
    }

    /** Fills in the last song from the saved queue when the process starts cold (e.g. after a reboot). */
    suspend fun ensureLoaded(context: Context) {
        if (_data.value != null) return
        val app = context.applicationContext
        // 1. Our own snapshot of the last song shown (survives the app being killed).
        val prefs = app.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val lastId = prefs.getLong(KEY_ID, -1L).takeIf { it >= 0 }
        var title = prefs.getString(KEY_TITLE, null).orEmpty()
        var artist = prefs.getString(KEY_ARTIST, null).orEmpty()
        var id = lastId
        // 2. Otherwise the player's saved queue.
        if (id == null || title.isEmpty()) {
            val saved = withContext(Dispatchers.IO) { runCatching { app.container.queueStore.load() }.getOrNull() }
            val qid = saved?.ids?.getOrNull(saved.index)
            songFor(app, qid ?: id)?.let { id = it.id; title = it.title; artist = it.artist }
        }
        if (_data.value != null) return
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
        val raw = id?.let { runCatching { EmbeddedArt.bitmap(context, songUri(it), 480) }.getOrNull() }
            ?: return@withContext Data()
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
