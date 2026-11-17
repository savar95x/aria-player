package dev.savar.aria.widget

import android.content.BroadcastReceiver
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.savar.aria.container
import dev.savar.aria.playback.PlaybackService
import dev.savar.aria.playback.Remote
import dev.savar.aria.playback.RemoteAction
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch

/** Widget buttons. A tap on a widget lets the app start its media service from the background. */
class WidgetActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val name = intent.action?.removePrefix(ACTION_PREFIX) ?: return
        val action = RemoteAction.entries.firstOrNull { it.name == name } ?: return
        val app = context.applicationContext
        val pending = goAsync()
        app.container.scope.launch {
            try {
                // Flip the icon right away; the player confirms (or corrects) a moment later.
                if (action == RemoteAction.PlayPause) WidgetState.togglePlayingOptimistically(app)

                val running = PlaybackService.instance
                if (running != null) {
                    running.perform(action)
                } else {
                    // Cold start: connecting creates the service, which restores the last queue first.
                    val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
                    val controller = runCatching { MediaController.Builder(app, token).buildAsync().await() }.getOrNull()
                    if (controller != null) {
                        runCatching {
                            controller.sendCustomCommand(
                                Remote.command(Remote.ACTION),
                                Bundle().apply { putString(Remote.ARG_ACTION, action.name) },
                            ).await()
                        }
                        // Hold the binding until playback has promoted the service to the foreground.
                        delay(3_000)
                        controller.release()
                    }
                }
                // Reconcile: if the tap changed nothing (empty queue, player refused, service never
                // came up), the optimistic icon would otherwise stay wrong.
                delay(1_200)
                PlaybackService.instance?.pushWidget() ?: WidgetState.refresh(app)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_PREFIX = "dev.savar.aria.widget."
    }
}
