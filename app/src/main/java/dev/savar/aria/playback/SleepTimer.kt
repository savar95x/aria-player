package dev.savar.aria.playback

import android.os.SystemClock
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

/**
 * Pauses after a delay. Either fades out over ~8s, or (with [finishSong]) lets the current
 * track end first so you don't fall asleep to a song cut in half.
 */
class SleepTimer(private val scope: CoroutineScope) {

    data class State(
        /** [SystemClock.elapsedRealtime] deadline, null when off. */
        val endsAt: Long? = null,
        val finishSong: Boolean = false,
        val waitingForSongEnd: Boolean = false,
    ) {
        val active get() = endsAt != null
    }

    private val _state = MutableStateFlow(State())
    val state: StateFlow<State> = _state.asStateFlow()
    private var job: Job? = null

    fun start(minutes: Int, finishSong: Boolean) {
        job?.cancel()
        val endsAt = SystemClock.elapsedRealtime() + minutes * 60_000L
        _state.value = State(endsAt = endsAt, finishSong = finishSong)
        job = scope.launch {
            delay(endsAt - SystemClock.elapsedRealtime())
            fire(finishSong)
        }
    }

    fun extend(minutes: Int) {
        val s = _state.value
        val endsAt = s.endsAt ?: return
        if (s.waitingForSongEnd) return
        val remaining = endsAt - SystemClock.elapsedRealtime()
        start(((remaining / 60_000L).toInt() + minutes).coerceAtLeast(1), s.finishSong)
    }

    fun setFinishSong(on: Boolean) {
        _state.value = _state.value.copy(finishSong = on)
    }

    fun cancel() {
        job?.cancel()
        job = null
        PlaybackService.instance?.player?.pauseAtEndOfMediaItems = false
        _state.value = State()
    }

    private suspend fun fire(finishSongAtStart: Boolean) {
        val player = PlaybackService.instance?.player
        val finishSong = _state.value.finishSong || finishSongAtStart
        if (player == null || !player.isPlaying) {
            _state.value = State()
            return
        }
        if (finishSong) {
            _state.value = _state.value.copy(waitingForSongEnd = true)
            player.pauseAtEndOfMediaItems = true
            try {
                while (player.isPlaying) delay(500)
            } finally {
                player.pauseAtEndOfMediaItems = false
            }
        } else {
            val startVolume = player.volume
            try {
                val steps = 32
                repeat(steps) { i ->
                    player.volume = startVolume * (1f - (i + 1f) / steps)
                    delay(250)
                }
                player.pause()
            } finally {
                player.volume = startVolume
            }
        }
        _state.value = State()
    }
}
