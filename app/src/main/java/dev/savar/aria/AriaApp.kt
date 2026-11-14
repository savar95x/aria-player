package dev.savar.aria

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.savar.aria.data.LyricsRepository
import dev.savar.aria.data.MusicRepository
import dev.savar.aria.data.QueueStore
import dev.savar.aria.data.SettingsStore
import dev.savar.aria.data.SongArtFetcher
import dev.savar.aria.data.SongArtKeyer
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow

class AriaApp : Application(), SingletonImageLoader.Factory {
    /** Lazy: the player process only touches the bits it needs (saved queue), never settings/library. */
    val container: AppContainer by lazy { AppContainer(this) }

    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(SongArtKeyer())
                add(SongArtFetcher.Factory())
            }
            .crossfade(true)
            .build()
}

/** Hand-rolled DI: everything lives for the process lifetime, created on first use. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(context, scope) }
    val library by lazy { MusicRepository(context, settings, scope) }
    val queueStore by lazy { QueueStore(context) }
    val lyrics by lazy { LyricsRepository(context) }

    /** A link shared/opened into Aria, waiting for the player to pick it up. */
    val pendingLink = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as AriaApp).container
