package dev.savar.aria.playback

import android.app.PendingIntent
import android.content.Intent
import android.os.Bundle
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.CacheBitmapLoader
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dev.savar.aria.MainActivity
import dev.savar.aria.container
import dev.savar.aria.data.Links
import dev.savar.aria.data.MediaStoreQuery
import dev.savar.aria.data.SavedQueue
import dev.savar.aria.data.artistText
import dev.savar.aria.data.songUri
import dev.savar.aria.data.titleText
import dev.savar.aria.widget.WidgetUpdateReceiver
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.future
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class RemoteAction { PlayPause, Next, Previous }

/**
 * Media3 playback service. The UI, widget and sleep-timer controls reach it through the media
 * session (custom commands in [Remote]), so it doesn't depend on sharing memory with the UI.
 */
@OptIn(UnstableApi::class)
class PlaybackService : MediaSessionService() {

    lateinit var player: ExoPlayer
        private set
    private var session: MediaSession? = null
    private val scope = MainScope()
    private var restoreJob: Job? = null
    private val sleepTimer by lazy { SleepTimer(scope) }

    override fun onCreate() {
        super.onCreate()
        instance = this
        player = ExoPlayer.Builder(this)
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setHandleAudioBecomingNoisy(true)
            .setWakeMode(C.WAKE_MODE_NETWORK)
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

        // Streams may only have a fallback display title until their own metadata arrives.
        setMediaNotificationProvider(
            object : DefaultMediaNotificationProvider(this) {
                override fun getNotificationContentTitle(metadata: MediaMetadata): CharSequence? =
                    metadata.titleText.ifEmpty { null }

                override fun getNotificationContentText(metadata: MediaMetadata): CharSequence? =
                    metadata.artistText.ifEmpty { null }
            },
        )

        player.addListener(object : Player.Listener {
            override fun onEvents(player: Player, events: Player.Events) {
                if (events.containsAny(
                        Player.EVENT_MEDIA_ITEM_TRANSITION,
                        Player.EVENT_IS_PLAYING_CHANGED,
                        Player.EVENT_PLAY_WHEN_READY_CHANGED,
                        Player.EVENT_TIMELINE_CHANGED,
                        Player.EVENT_MEDIA_METADATA_CHANGED,
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

        // Sleep-timer state is published to every connected controller as session extras.
        scope.launch {
            sleepTimer.state.collect { session?.setSessionExtras(Remote.sleepToExtras(it)) }
        }

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
        // Keep playing when the app is swiped away; only shut down if nothing is playing.
        if (!player.playWhenReady || player.mediaItemCount == 0) {
            stopSelf()
        }
    }

    override fun onDestroy() {
        saveQueue()
        instance = null
        sleepTimer.cancel()
        WidgetUpdateReceiver.send(this, player.currentMediaItem, player.mediaMetadata, isPlaying = false)
        session?.release()
        session = null
        player.release()
        scope.cancel()
        super.onDestroy()
    }

    /** Widget buttons, when the service is already running. */
    fun perform(action: RemoteAction) = handle(action)

    private fun handle(action: RemoteAction) {
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
        WidgetUpdateReceiver.send(this, player.currentMediaItem, player.mediaMetadata, player.isPlaying || player.playWhenReady)
    }

    private fun saveQueue() {
        if (player.mediaItemCount == 0) return
        // Only local songs are restored; links are one-offs.
        val ids = (0 until player.mediaItemCount).mapNotNull { player.getMediaItemAt(it).mediaId.toLongOrNull() }
        if (ids.isEmpty()) return
        val currentId = player.currentMediaItem?.mediaId?.toLongOrNull()
        val queue = SavedQueue(
            ids = ids,
            index = ids.indexOf(currentId).coerceAtLeast(0),
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
        val songs = MediaStoreQuery.songsByIds(this, saved.ids)
        val index = songs.indexOfFirst { it.id == currentId }
        return MediaSession.MediaItemsWithStartPosition(
            songs.map { it.toMediaItem() },
            index.coerceAtLeast(0),
            if (index >= 0) saved.positionMs else 0,
        )
    }

    private inner class SessionCallback : MediaSession.Callback {
        override fun onConnect(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
        ): MediaSession.ConnectionResult {
            val commands = MediaSession.ConnectionResult.DEFAULT_SESSION_COMMANDS.buildUpon()
                .apply { Remote.commands.forEach { add(Remote.command(it)) } }
                .build()
            return MediaSession.ConnectionResult.AcceptedResultBuilder(session)
                .setAvailableSessionCommands(commands)
                .build()
        }

        override fun onCustomCommand(
            session: MediaSession,
            controller: MediaSession.ControllerInfo,
            customCommand: SessionCommand,
            args: Bundle,
        ): ListenableFuture<SessionResult> {
            when (customCommand.customAction) {
                Remote.ACTION -> args.getString(Remote.ARG_ACTION)
                    ?.let { runCatching { RemoteAction.valueOf(it) }.getOrNull() }
                    ?.let(::handle)
                Remote.SLEEP_START -> sleepTimer.start(args.getInt(Remote.ARG_MINUTES, 30), args.getBoolean(Remote.ARG_FINISH_SONG))
                Remote.SLEEP_EXTEND -> sleepTimer.extend(args.getInt(Remote.ARG_MINUTES, 10))
                Remote.SLEEP_FINISH_SONG -> sleepTimer.setFinishSong(args.getBoolean(Remote.ARG_FINISH_SONG))
                Remote.SLEEP_CANCEL -> sleepTimer.cancel()
            }
            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
        }

        // Controllers send items without their URI; rebuild it from the media id.
        override fun onAddMediaItems(
            mediaSession: MediaSession,
            controller: MediaSession.ControllerInfo,
            mediaItems: MutableList<MediaItem>,
        ): ListenableFuture<MutableList<MediaItem>> = Futures.immediateFuture(
            mediaItems.map { item ->
                when {
                    item.localConfiguration != null -> item
                    Links.isLink(item.mediaId) -> item.buildUpon().setUri(Links.urlOf(item.mediaId)).build()
                    else -> item.buildUpon().setUri(songUri(item.mediaId.toLong())).build()
                }
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
        /** Only meaningful inside the player process (used by the sleep timer). */
        @Volatile
        var instance: PlaybackService? = null
            private set
    }
}
