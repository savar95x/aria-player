package dev.savar.aria.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.SizeF
import android.view.View
import android.widget.RemoteViews
import dev.savar.aria.MainActivity
import dev.savar.aria.R
import dev.savar.aria.container
import dev.savar.aria.playback.RemoteAction
import kotlinx.coroutines.launch

/**
 * The home-screen widget, drawn with plain RemoteViews and pushed straight from the player.
 *
 * No Glance / WorkManager on purpose: one fewer moving part between "song changed" and "widget
 * shows it", nothing to break across library upgrades, and it renders even right after a reboot.
 * The class name must stay `AriaWidgetReceiver` so widgets already on home screens keep working.
 */
class AriaWidgetReceiver : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        context.container.scope.launch {
            try {
                WidgetState.ensureLoaded(context)
                AriaWidget.render(context)
            } finally {
                pending.finish()
            }
        }
    }
}

object AriaWidget {
    // The launcher picks the largest layout that fits the widget's current size.
    private val Small = SizeF(110f, 110f)
    private val Wide = SizeF(220f, 110f)
    private val Tall = SizeF(220f, 230f)

    fun render(context: Context, data: WidgetState.Data = WidgetState.data.value ?: WidgetState.Data()) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, AriaWidgetReceiver::class.java))
        if (ids.isEmpty()) return
        val views = RemoteViews(
            mapOf(
                Small to build(app, R.layout.widget_small, data, full = false),
                Wide to build(app, R.layout.widget_wide, data, full = true),
                Tall to build(app, R.layout.widget_tall, data, full = true),
            ),
        )
        runCatching { manager.updateAppWidget(ids, views) }
    }

    private fun build(context: Context, layout: Int, data: WidgetState.Data, full: Boolean): RemoteViews =
        RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(android.R.id.background, openApp(context))

            if (data.art != null) setImageViewBitmap(R.id.art, data.art) else setImageViewResource(R.id.art, R.drawable.widget_art_placeholder)

            setImageViewResource(R.id.play_icon, if (data.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            setContentDescription(R.id.play, if (data.isPlaying) "Pause" else "Play")
            setInt(R.id.play_bg, "setColorFilter", data.accent)
            setOnClickPendingIntent(R.id.play, action(context, RemoteAction.PlayPause))

            if (!full) return@apply
            val backdrop = data.backdrop
            if (backdrop != null) {
                setImageViewBitmap(R.id.backdrop, backdrop)
                setViewVisibility(R.id.backdrop, View.VISIBLE)
                setViewVisibility(R.id.scrim, View.VISIBLE)
            } else {
                setViewVisibility(R.id.backdrop, View.GONE)
                setViewVisibility(R.id.scrim, View.GONE)
            }
            setTextViewText(R.id.title, data.title.ifEmpty { "Aria" })
            setTextViewText(R.id.artist, data.artist.ifEmpty { "Tap to open" })
            setOnClickPendingIntent(R.id.prev, action(context, RemoteAction.Previous))
            setOnClickPendingIntent(R.id.next, action(context, RemoteAction.Next))
        }

    private fun openApp(context: Context): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun action(context: Context, action: RemoteAction): PendingIntent = PendingIntent.getBroadcast(
        context,
        action.ordinal + 1,
        Intent(context, WidgetActionReceiver::class.java).setAction(WidgetActionReceiver.ACTION_PREFIX + action.name),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )
}
