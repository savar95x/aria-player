package dev.savar.aria.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.util.SizeF
import android.util.TypedValue
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
    override fun onAppWidgetOptionsChanged(context: Context, manager: AppWidgetManager, id: Int, options: Bundle) {
        // Resized: rebuild for the new size.
        onUpdate(context, manager, intArrayOf(id))
    }

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
    // Same breakpoints as the original design: narrower than Wide -> small, shorter than Tall -> wide.
    private const val WIDE_MIN_WIDTH = 220f
    private const val TALL_MIN_HEIGHT = 230f

    fun render(context: Context, data: WidgetState.Data = WidgetState.data.value ?: WidgetState.Data()) {
        val app = context.applicationContext
        val manager = AppWidgetManager.getInstance(app)
        val ids = manager.getAppWidgetIds(ComponentName(app, AriaWidgetReceiver::class.java))
        for (id in ids) {
            // One layout per size the widget actually has (portrait / landscape), measured exactly.
            val views = RemoteViews(sizesOf(manager, id).associateWith { build(app, it, data) })
            runCatching { manager.updateAppWidget(id, views) }
        }
    }

    private fun sizesOf(manager: AppWidgetManager, id: Int): List<SizeF> {
        val options = manager.getAppWidgetOptions(id)
        val sizes: List<SizeF>? = if (Build.VERSION.SDK_INT >= 33) {
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES, SizeF::class.java)
        } else {
            @Suppress("DEPRECATION")
            options.getParcelableArrayList(AppWidgetManager.OPTION_APPWIDGET_SIZES)
        }
        if (!sizes.isNullOrEmpty()) return sizes.distinct()
        val minW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH)
        val maxW = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH)
        val minH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
        val maxH = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT)
        if (minW <= 0 || minH <= 0) return listOf(SizeF(280f, 140f))
        return listOf(SizeF(minW.toFloat(), maxH.toFloat()), SizeF(maxW.toFloat(), minH.toFloat())).distinct()
    }

    private fun build(context: Context, size: SizeF, data: WidgetState.Data): RemoteViews {
        val layout = when {
            size.width < WIDE_MIN_WIDTH -> R.layout.widget_small
            size.height < TALL_MIN_HEIGHT -> R.layout.widget_wide
            else -> R.layout.widget_tall
        }
        return RemoteViews(context.packageName, layout).apply {
            setOnClickPendingIntent(android.R.id.background, openApp(context))

            if (data.art != null) setImageViewBitmap(R.id.art, data.art) else setImageViewResource(R.id.art, R.drawable.widget_art_placeholder)
            setImageViewResource(R.id.play_icon, if (data.isPlaying) R.drawable.ic_pause else R.drawable.ic_play)
            setContentDescription(R.id.play, if (data.isPlaying) "Pause" else "Play")
            setOnClickPendingIntent(R.id.play, action(context, RemoteAction.PlayPause))
            if (layout == R.layout.widget_small) return@apply

            if (layout == R.layout.widget_wide) {
                // Square cover filling the height (minus padding), capped like before.
                val art = (size.height - 28f).coerceIn(48f, 140f)
                setViewLayoutWidth(R.id.art, art, TypedValue.COMPLEX_UNIT_DIP)
                setViewLayoutHeight(R.id.art, art, TypedValue.COMPLEX_UNIT_DIP)
            }
            val backdrop = data.backdrop
            setViewVisibility(R.id.backdrop, if (backdrop != null) View.VISIBLE else View.GONE)
            setViewVisibility(R.id.scrim, if (backdrop != null) View.VISIBLE else View.GONE)
            if (backdrop != null) setImageViewBitmap(R.id.backdrop, backdrop)
            setTextViewText(R.id.title, data.title.ifEmpty { "Aria" })
            setTextViewText(R.id.artist, data.artist.ifEmpty { "Tap to open" })
            setOnClickPendingIntent(R.id.prev, action(context, RemoteAction.Previous))
            setOnClickPendingIntent(R.id.next, action(context, RemoteAction.Next))
        }
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
