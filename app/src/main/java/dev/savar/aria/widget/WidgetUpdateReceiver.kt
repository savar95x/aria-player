package dev.savar.aria.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import dev.savar.aria.container
import dev.savar.aria.data.artistText
import dev.savar.aria.data.titleText
import kotlinx.coroutines.launch

/**
 * Bridge from the player process to the widget, which lives in the UI process
 * (Glance needs WorkManager, which only runs in the main process).
 */
class WidgetUpdateReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val pending = goAsync()
        context.container.scope.launch {
            try {
                WidgetState.apply(
                    context,
                    id = intent.getLongExtra(EXTRA_ID, -1L).takeIf { it >= 0 },
                    title = intent.getStringExtra(EXTRA_TITLE).orEmpty(),
                    artist = intent.getStringExtra(EXTRA_ARTIST).orEmpty(),
                    isPlaying = intent.getBooleanExtra(EXTRA_PLAYING, false),
                )
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        private const val EXTRA_ID = "id"
        private const val EXTRA_TITLE = "title"
        private const val EXTRA_ARTIST = "artist"
        private const val EXTRA_PLAYING = "playing"

        fun send(context: Context, item: MediaItem?, meta: MediaMetadata?, isPlaying: Boolean) {
            context.sendBroadcast(
                Intent(context, WidgetUpdateReceiver::class.java)
                    .putExtra(EXTRA_ID, item?.mediaId?.toLongOrNull() ?: -1L)
                    .putExtra(EXTRA_TITLE, meta?.titleText.orEmpty())
                    .putExtra(EXTRA_ARTIST, meta?.artistText.orEmpty())
                    .putExtra(EXTRA_PLAYING, isPlaying),
            )
        }
    }
}
