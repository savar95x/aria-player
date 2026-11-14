package dev.savar.aria.widget

import android.content.Context
import android.graphics.Bitmap
import androidx.core.graphics.scale
import androidx.glance.appwidget.updateAll
import androidx.media3.common.MediaItem
import dev.savar.aria.container
import dev.savar.aria.data.EmbeddedArt
import dev.savar.aria.data.songUri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
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

    fun update(context: Context, item: MediaItem?, isPlaying: Boolean) {
        val app = context.applicationContext
        app.container.scope.launch {
            val id = item?.mediaId?.toLongOrNull()
            val current = _data.value
            val next = if (current != null && current.songId == id) {
                current.copy(isPlaying = isPlaying)
            } else {
                val (art, backdrop) = loadArt(app, id)
                Data(
                    songId = id,
                    title = item?.mediaMetadata?.title?.toString().orEmpty(),
                    artist = item?.mediaMetadata?.artist?.toString().orEmpty(),
                    isPlaying = isPlaying,
                    art = art,
                    backdrop = backdrop,
                )
            }
            if (next != current) {
                _data.value = next
                AriaWidget().updateAll(app)
            }
        }
    }

    /** Called by the widget when the process starts cold (e.g. after a reboot). */
    suspend fun ensureLoaded(context: Context) {
        if (_data.value != null) return
        val app = context.applicationContext
        val saved = app.container.queueStore.load()
        val id = saved?.ids?.getOrNull(saved.index)
        val song = id?.let { app.container.library.songsByIds(listOf(it)).firstOrNull() }
        if (_data.value != null) return
        val (art, backdrop) = loadArt(app, song?.id)
        _data.value = Data(
            songId = song?.id,
            title = song?.title.orEmpty(),
            artist = song?.artist.orEmpty(),
            art = art,
            backdrop = backdrop,
        )
    }

    private suspend fun loadArt(context: Context, id: Long?): Pair<Bitmap?, Bitmap?> =
        withContext(Dispatchers.IO) {
            if (id == null) return@withContext null to null
            val art = EmbeddedArt.bitmap(context, songUri(id), 480) ?: return@withContext null to null
            art to blur(art)
        }

    /** Cheap, smooth blur: shrink hard, then let bilinear filtering spread the colour back out. */
    private fun blur(src: Bitmap): Bitmap {
        val tiny = src.scale(12, 12, filter = true)
        val small = tiny.scale(48, 48, filter = true)
        return small.scale(160, 160, filter = true)
    }
}
