package dev.savar.aria

import android.app.Application
import android.content.Context
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.request.crossfade
import dev.savar.aria.data.ArtistPhotos
import dev.savar.aria.data.LyricsRepository
import dev.savar.aria.data.MusicRepository
import dev.savar.aria.data.QueueStore
import dev.savar.aria.data.SettingsStore
import dev.savar.aria.data.SongArtFetcher
import dev.savar.aria.data.SongArtKeyer
import dev.savar.aria.update.Updater
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import dev.savar.aria.widget.AriaWidget
import dev.savar.aria.widget.WidgetState

class AriaApp : Application(), SingletonImageLoader.Factory {
    /** Lazy: the player process only touches the bits it needs (saved queue), never settings/library. */
    val container: AppContainer by lazy { AppContainer(this) }

    // As early as possible: library start-up code runs before Application.onCreate.
    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(base)
        CrashActivity.install(this)
    }

    override fun onCreate() {
        super.onCreate()
        // A killed process can't tell the widget it stopped playing; whenever the app comes back
        // (UI, service or a widget tap), redraw it from the truth. Not in the crash-screen process.
        if (!getProcessName().endsWith(":crash")) {
            container.scope.launch { if (AriaWidget.hasWidgets(this@AriaApp)) WidgetState.refresh(this@AriaApp) }
        }
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

/** Hand-rolled DI: everything lives for the process lifetime, created on first use. */
class AppContainer(context: Context) {
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val settings by lazy { SettingsStore(context, scope) }
    val library by lazy { MusicRepository(context, settings, scope) }
    val queueStore by lazy { QueueStore(context) }
    val lyrics by lazy { LyricsRepository(context) }
    val artistPhotos by lazy { ArtistPhotos(context) }
    val updater by lazy { Updater(context, scope) }

    /** A link shared/opened into Aria, waiting for the player to pick it up. */
    val pendingLink = MutableStateFlow<String?>(null)
}

val Context.container: AppContainer get() = (applicationContext as AriaApp).container
