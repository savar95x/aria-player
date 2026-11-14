package dev.savar.aria.ui

import android.app.Application
import android.content.ComponentName
import android.os.Bundle
import android.widget.Toast
import androidx.compose.runtime.Immutable
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.savar.aria.data.Links
import dev.savar.aria.data.Song
import dev.savar.aria.playback.PlaybackService
import dev.savar.aria.playback.Remote
import dev.savar.aria.playback.SleepTimer
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.launch
import kotlin.random.Random

@Immutable
data class QueueEntry(val index: Int, val item: MediaItem)

@Immutable
data class PlayerUiState(
    val connected: Boolean = false,
    val current: MediaItem? = null,
    /** Merged metadata: our tags plus anything the stream reports (e.g. radio "now playing"). */
    val meta: MediaMetadata = MediaMetadata.EMPTY,
    val currentIndex: Int = C.INDEX_UNSET,
    val playing: Boolean = false,
    val shuffle: Boolean = false,
    val repeatMode: Int = Player.REPEAT_MODE_OFF,
    val durationMs: Long = 0,
    /** Items in actual play order (respects shuffle). */
    val queue: List<QueueEntry> = emptyList(),
) {
    val currentId: Long? get() = current?.mediaId?.toLongOrNull()
}

/**
 * The UI's handle on the player, which runs in a separate process. Talks to it through a
 * [MediaController]; if the player process goes away (e.g. "Stop player"), the next action reconnects.
 */
class PlayerViewModel(private val app: Application) : AndroidViewModel(app) {
    private var controller: MediaController? = null
    private var connecting: Deferred<MediaController?>? = null

    private val _state = MutableStateFlow(PlayerUiState())
    val state: StateFlow<PlayerUiState> = _state.asStateFlow()

    private val _sleep = MutableStateFlow(SleepTimer.State())
    val sleep: StateFlow<SleepTimer.State> = _sleep.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            refresh(
                queueChanged = events.containsAny(
                    Player.EVENT_TIMELINE_CHANGED,
                    Player.EVENT_SHUFFLE_MODE_ENABLED_CHANGED,
                ),
            )
        }

        override fun onPlayerError(error: PlaybackException) {
            val what = if (Links.isLink(controller?.currentMediaItem?.mediaId.orEmpty())) "this link" else "this song"
            Toast.makeText(app, "Couldn't play $what (${error.errorCodeName})", Toast.LENGTH_LONG).show()
        }
    }

    private val controllerListener = object : MediaController.Listener {
        override fun onDisconnected(controller: MediaController) {
            this@PlayerViewModel.controller = null
            _state.value = PlayerUiState()
            _sleep.value = SleepTimer.State()
        }

        override fun onExtrasChanged(controller: MediaController, extras: Bundle) {
            _sleep.value = Remote.sleepFromExtras(extras)
        }
    }

    init {
        viewModelScope.launch { connect() }
    }

    private suspend fun connect(): MediaController? {
        controller?.let { return it }
        val pending = connecting ?: viewModelScope.async {
            val token = SessionToken(app, ComponentName(app, PlaybackService::class.java))
            runCatching {
                MediaController.Builder(app, token).setListener(controllerListener).buildAsync().await()
            }.getOrNull()?.also { c ->
                controller = c
                c.addListener(listener)
                _sleep.value = Remote.sleepFromExtras(c.sessionExtras)
                refresh(queueChanged = true)
            }
        }.also { connecting = it }
        return pending.await().also { connecting = null }
    }

    /** Runs [block] on the controller, reconnecting first if the player process isn't running. */
    private fun act(block: (MediaController) -> Unit) {
        val c = controller
        if (c != null) block(c) else viewModelScope.launch { connect()?.let(block) }
    }

    private fun refresh(queueChanged: Boolean) {
        val c = controller ?: return
        _state.update { s ->
            s.copy(
                connected = true,
                current = c.currentMediaItem,
                meta = c.mediaMetadata,
                currentIndex = c.currentMediaItemIndex,
                playing = c.playWhenReady && c.playbackState != Player.STATE_ENDED && c.playbackState != Player.STATE_IDLE,
                shuffle = c.shuffleModeEnabled,
                repeatMode = c.repeatMode,
                durationMs = c.duration.takeIf { it != C.TIME_UNSET }?.coerceAtLeast(0) ?: 0,
                queue = if (queueChanged) playOrder(c) else s.queue,
            )
        }
    }

    private fun playOrder(c: Player): List<QueueEntry> {
        val timeline = c.currentTimeline
        if (timeline.isEmpty) return emptyList()
        val out = ArrayList<QueueEntry>(timeline.windowCount)
        var i = timeline.getFirstWindowIndex(c.shuffleModeEnabled)
        while (i != C.INDEX_UNSET && out.size < timeline.windowCount) {
            out += QueueEntry(i, c.getMediaItemAt(i))
            i = timeline.getNextWindowIndex(i, Player.REPEAT_MODE_OFF, c.shuffleModeEnabled)
        }
        return out
    }

    fun position(): Long = controller?.currentPosition ?: 0

    fun play(songs: List<Song>, startIndex: Int = 0, shuffle: Boolean = false) {
        if (songs.isEmpty()) return
        act { c ->
            c.shuffleModeEnabled = shuffle
            val start = if (shuffle) Random.nextInt(songs.size) else startIndex.coerceIn(songs.indices)
            c.setMediaItems(songs.map { it.toMediaItem() }, start, 0)
            c.prepare()
            c.play()
        }
    }

    fun playUrl(url: String) = act { c ->
        c.shuffleModeEnabled = false
        c.setMediaItem(Links.mediaItem(url))
        c.prepare()
        c.play()
    }

    fun playNext(song: Song) = act { c ->
        if (c.mediaItemCount == 0) play(listOf(song))
        else c.addMediaItem(c.currentMediaItemIndex + 1, song.toMediaItem())
    }

    fun addToQueue(song: Song) = act { c ->
        if (c.mediaItemCount == 0) play(listOf(song)) else c.addMediaItem(song.toMediaItem())
    }

    fun togglePlay() = act { c ->
        if (c.playWhenReady && c.playbackState != Player.STATE_ENDED) {
            c.pause()
        } else {
            if (c.playbackState == Player.STATE_IDLE) c.prepare()
            if (c.playbackState == Player.STATE_ENDED) c.seekToDefaultPosition()
            c.play()
        }
    }

    fun next() = act { it.seekToNext() }
    fun previous() = act { it.seekToPrevious() }
    fun seekTo(ms: Long) = act { it.seekTo(ms) }
    fun skipTo(index: Int) = act { it.seekTo(index, 0); it.play() }
    fun removeAt(index: Int) = act { it.removeMediaItem(index) }
    fun toggleShuffle() = act { it.shuffleModeEnabled = !it.shuffleModeEnabled }

    fun cycleRepeat() = act { c ->
        c.repeatMode = when (c.repeatMode) {
            Player.REPEAT_MODE_OFF -> Player.REPEAT_MODE_ALL
            Player.REPEAT_MODE_ALL -> Player.REPEAT_MODE_ONE
            else -> Player.REPEAT_MODE_OFF
        }
    }

    private fun send(command: String, args: Bundle = Bundle.EMPTY) =
        act { it.sendCustomCommand(Remote.command(command), args) }

    fun startSleep(minutes: Int, finishSong: Boolean) = send(
        Remote.SLEEP_START,
        Bundle().apply { putInt(Remote.ARG_MINUTES, minutes); putBoolean(Remote.ARG_FINISH_SONG, finishSong) },
    )

    fun extendSleep(minutes: Int) = send(Remote.SLEEP_EXTEND, Bundle().apply { putInt(Remote.ARG_MINUTES, minutes) })

    fun setSleepFinishSong(on: Boolean) {
        _sleep.update { it.copy(finishSong = on) } // instant toggle feedback
        send(Remote.SLEEP_FINISH_SONG, Bundle().apply { putBoolean(Remote.ARG_FINISH_SONG, on) })
    }

    fun cancelSleep() = send(Remote.SLEEP_CANCEL)

    override fun onCleared() {
        controller?.removeListener(listener)
        controller?.release()
        controller = null
    }
}
