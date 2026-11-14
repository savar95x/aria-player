package dev.savar.aria

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.savar.aria.data.MusicRepository
import dev.savar.aria.data.QueueStore
import dev.savar.aria.data.SettingsStore
import dev.savar.aria.data.SongArtFetcher
import dev.savar.aria.data.SongArtKeyer
import dev.savar.aria.playback.SleepTimer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class AriaApp : Application(), SingletonImageLoader.Factory {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SongArtKeyer())
                add(SongArtFetcher.Factory())
            }
            .crossfade(true)
            .build()
}

/** Hand-rolled DI: everything lives for the process lifetime. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings = SettingsStore(context, scope)
    val library = MusicRepository(context, settings, scope)
    val queueStore = QueueStore(context)
    val sleepTimer = SleepTimer(scope)

    /** A link shared/opened into Aria, waiting for the player to pick it up. */
    val pendingLink = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as AriaApp).container
