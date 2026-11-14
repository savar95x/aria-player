package dev.savar.aria.playback

import android.os.Bundle
import android.os.SystemClock
import androidx.media3.session.SessionCommand

/**
 * The player runs in its own process (":playback") so it survives the UI being killed.
 * Everything that isn't a standard player command goes over these custom session commands,
 * and the sleep-timer state comes back as session extras.
 */
object Remote {
    const val ACTION = "aria.action" // ARG_ACTION = RemoteAction.name
    const val SLEEP_START = "aria.sleep.start" // ARG_MINUTES, ARG_FINISH_SONG
    const val SLEEP_EXTEND = "aria.sleep.extend" // ARG_MINUTES
    const val SLEEP_FINISH_SONG = "aria.sleep.finishSong" // ARG_FINISH_SONG
    const val SLEEP_CANCEL = "aria.sleep.cancel"
    const val SHUTDOWN = "aria.shutdown"

    const val ARG_ACTION = "action"
    const val ARG_MINUTES = "minutes"
    const val ARG_FINISH_SONG = "finishSong"

    private const val EXTRA_SLEEP_ENDS_AT = "sleep.endsAt"
    private const val EXTRA_SLEEP_FINISH = "sleep.finishSong"
    private const val EXTRA_SLEEP_WAITING = "sleep.waiting"

    val commands = listOf(ACTION, SLEEP_START, SLEEP_EXTEND, SLEEP_FINISH_SONG, SLEEP_CANCEL, SHUTDOWN)

    fun command(action: String) = SessionCommand(action, Bundle.EMPTY)

    // elapsedRealtime is system-wide, so the deadline means the same thing in both processes.
    fun sleepToExtras(state: SleepTimer.State) = Bundle().apply {
        putLong(EXTRA_SLEEP_ENDS_AT, state.endsAt ?: -1L)
        putBoolean(EXTRA_SLEEP_FINISH, state.finishSong)
        putBoolean(EXTRA_SLEEP_WAITING, state.waitingForSongEnd)
    }

    fun sleepFromExtras(extras: Bundle): SleepTimer.State {
        val endsAt = extras.getLong(EXTRA_SLEEP_ENDS_AT, -1L)
        return SleepTimer.State(
            endsAt = endsAt.takeIf { it > 0 && (it > SystemClock.elapsedRealtime() || extras.getBoolean(EXTRA_SLEEP_WAITING)) },
            finishSong = extras.getBoolean(EXTRA_SLEEP_FINISH),
            waitingForSongEnd = extras.getBoolean(EXTRA_SLEEP_WAITING),
        )
    }
}
