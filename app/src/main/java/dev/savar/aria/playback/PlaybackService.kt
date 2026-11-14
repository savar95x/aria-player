package dev.savar.aria.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.savar.aria.MainActivity
import dev.savar.aria.container
import dev.savar.aria.data.SavedQueue
import dev.savar.aria.data.songUri
import dev.savar.aria.widget.WidgetState
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RemoteAction { PlayPause, Next, Previous }

@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    lateinit var player: ExoPlayer
        private set
    private var session: MediaSession? = null
    private val scope = MainScope()
    private var restoreJob: Job? = null

    override fun onCreate() {
        super.onCreate()
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_LOCAL)
            .build()

        session = MediaSession.Builder(this, player)
            .setCallback(SessionCallback())
            .setBitmapLoader(CacheBitmapLoader(SongArtBitmapLoader(this)))
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0,
                    Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                    )
                ) {
                    pushWidget()
                }
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                        Player.EVENT_REPEAT_MODE_CHANGED,
                    )
                ) {
                    saveQueue()
                }
            }
        })

        instance = this
        restoreJob = scope.launch { restoreQueue() }
        // Keep the saved position fresh so a crash or kill loses at most a few seconds.
        scope.launch {
            while (isActive) {
                delay(10_000)
                if (player.isPlaying) saveQueue()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onTaskRemoved(rootIntent: Intent?) {
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        instance = null
        container.sleepTimer.cancel()
        WidgetState.update(this, player.currentMediaItem, isPlaying = false)
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Entry point for the home-screen widget buttons. */
    fun handle(action: RemoteAction) {
        scope.launch {
            restoreJob?.join()
            when (action) {
                RemoteAction.PlayPause -> if (player.isPlaying) {
                    player.pause()
                } else {
                    if (player.playbackState == Player.STATE_IDLE) player.prepare()
                    if (player.playbackState == Player.STATE_ENDED) player.seekToDefaultPosition()
                    player.play()
                }
                RemoteAction.Next -> player.seekToNext()
                RemoteAction.Previous -> player.seekToPrevious()
            }
        }
    }

    private fun pushWidget() {
        WidgetState.update(this, player.currentMediaItem, player.isPlaying || player.playWhenReady)
    }

    private fun saveQueue() {
        if (player.mediaItemCount == 0) return
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        val queue = SavedQueue(
            ids = ids,
            index = player.currentMediaItemIndex,
            positionMs = player.currentPosition,
            shuffle = player.shuffleModeEnabled,
            repeatMode = player.repeatMode,
        )
        container.scope.launch { container.queueStore.save(queue) }
    }

    private suspend fun restoreQueue() {
        if (player.mediaItemCount > 0) return
        val saved = container.queueStore.load() ?: return
        val items = loadItems(saved)
        if (items.mediaItems.isEmpty() || player.mediaItemCount > 0) return
        player.shuffleModeEnabled = saved.shuffle
        player.repeatMode = saved.repeatMode
        player.setMediaItems(items.mediaItems, items.startIndex, items.startPositionMs)
        player.prepare()
    }

    private suspend fun loadItems(saved: SavedQueue): MediaSession.MediaItemsWithStartPosition {
        val currentId = saved.ids.getOrNull(saved.index)
        val songs = container.library.songsByIds(saved.ids)
        val index = songs.indexOfFirst { it.id == currentId }
        return MediaSession.MediaItemsWithStartPosition(
            songs.map { it.toMediaItem() },
            index.coerceAtLeast(0),
            if (index >= 0) saved.positionMs else 0,
        )
    }

    private inner class SessionCallback : MediaSession.Callback {
        // Controllers send items without their URI; rebuild it from the MediaStore id.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                if (item.localConfiguration != null) item
                else item.buildUpon().setUri(songUri(item.mediaId.toLong())).build()
            }.toMutableList(),
        )

        // Headset / Bluetooth / system "resume" with nothing loaded: bring back the last queue.
        override fun onPlaybackResumption(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): ListenableFuture<MediaSession.MediaItemsWithStartPosition> = scope.future {
            val saved = container.queueStore.load()
                ?: throw IllegalStateException("Nothing to resume")
            loadItems(saved)
        }
    }

    companion object {
        /** Same-process shortcut for the widget and sleep timer. */
        @Volatile
        var instance: PlaybackService? = null
            private set
    }
}
