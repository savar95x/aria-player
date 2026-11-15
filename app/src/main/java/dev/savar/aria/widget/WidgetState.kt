package dev.savar.aria.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import androidx.core.graphics.ColorUtils
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
        /** Play-button colour: a light tint of the cover's main colour. */
        val accent: Int = Color.WHITE,
    )

    private val _data = MutableStateFlow<Data?>(null)
    val data: StateFlow<Data?> = _data.asStateFlow()

    /** Called (via [WidgetUpdateReceiver]) whenever the player's song or play state changes. */
    suspend fun apply(context: Context, id: Long?, title: String, artist: String, isPlaying: Boolean) {
        val app = context.applicationContext
        val current = _data.value
        val next = if (current != null && current.songId == id) {
            current.copy(isPlaying = isPlaying, title = title, artist = artist)
        } else {
            artData(app, id).copy(songId = id, title = title, artist = artist, isPlaying = isPlaying)
        }
        if (next != current) {
            _data.value = next
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
        val song = withContext(Dispatchers.IO) {
            val saved = app.container.queueStore.load()
            val id = saved?.ids?.getOrNull(saved.index)
            id?.let { MediaStoreQuery.songsByIds(app, listOf(it)).firstOrNull() }
        }
        if (_data.value != null) return
        _data.value = artData(app, song?.id).copy(
            songId = song?.id,
            title = song?.title.orEmpty(),
            artist = song?.artist.orEmpty(),
        )
    }

    private suspend fun artData(context: Context, id: Long?): Data = withContext(Dispatchers.IO) {
        val raw = id?.let { runCatching { EmbeddedArt.bitmap(context, songUri(it), 480) }.getOrNull() }
            ?: return@withContext Data()
        val art = squareCrop(raw, 400)
        val tiny = art.scale(12, 12, filter = true)
        Data(art = art, backdrop = blur(tiny), accent = accentOf(tiny))
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

    /** Average colour of the cover, lifted into a soft pastel that dark icons read well on. */
    private fun accentOf(tiny: Bitmap): Int {
        var r = 0L; var g = 0L; var b = 0L
        val px = IntArray(tiny.width * tiny.height)
        tiny.getPixels(px, 0, tiny.width, 0, 0, tiny.width, tiny.height)
        px.forEach { r += Color.red(it); g += Color.green(it); b += Color.blue(it) }
        val n = px.size.coerceAtLeast(1)
        val hsl = FloatArray(3)
        ColorUtils.RGBToHSL((r / n).toInt(), (g / n).toInt(), (b / n).toInt(), hsl)
        hsl[1] = (hsl[1] * 1.4f).coerceIn(0f, 0.55f)
        hsl[2] = 0.86f
        return ColorUtils.HSLToColor(hsl)
    }
}
