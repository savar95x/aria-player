package dev.savar.aria.widget

import android.content.ComponentName
import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.coerceAtMost
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionParametersOf
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import dev.savar.aria.MainActivity
import dev.savar.aria.R
import dev.savar.aria.playback.PlaybackService
import dev.savar.aria.playback.RemoteAction
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext

class AriaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AriaWidget()
}

private val Small = DpSize(110.dp, 110.dp)
private val Wide = DpSize(220.dp, 110.dp)
private val Tall = DpSize(220.dp, 230.dp)

class AriaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(Small, Wide, Tall))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        WidgetState.ensureLoaded(context)
        provideContent {
            GlanceTheme {
                val data by WidgetState.data.collectAsState()
                Content(data ?: WidgetState.Data())
            }
        }
    }
}

private val White = ColorProvider(Color.White)
private val WhiteDim = ColorProvider(Color.White.copy(alpha = 0.72f))
private val Black = ColorProvider(Color.Black)

@Composable
private fun Content(data: WidgetState.Data) {
    val size = LocalSize.current
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(28.dp)
            .background(Color(0xFF16161C))
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        // Blurred artwork backdrop + scrim so white text always reads.
        data.backdrop?.let {
            Image(
                provider = ImageProvider(it),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = GlanceModifier.fillMaxSize(),
            )
        }
        Box(modifier = GlanceModifier.fillMaxSize().background(Color.Black.copy(alpha = if (data.backdrop != null) 0.38f else 0f))) {}

        when {
            size.width < Wide.width -> SmallLayout(data)
            size.height < Tall.height -> WideLayout(data)
            else -> TallLayout(data)
        }
    }
}

@Composable
private fun SmallLayout(data: WidgetState.Data) {
    Box(modifier = GlanceModifier.fillMaxSize()) {
        Artwork(data, GlanceModifier.fillMaxSize(), corner = 28.dp)
        Box(
            modifier = GlanceModifier.fillMaxSize().padding(10.dp),
            contentAlignment = Alignment.BottomEnd,
        ) {
            PlayButton(data, width = 48.dp, height = 48.dp)
        }
    }
}

@Composable
private fun WideLayout(data: WidgetState.Data) {
    val artSize = (LocalSize.current.height - 28.dp).coerceAtMost(140.dp)
    Row(
        modifier = GlanceModifier.fillMaxSize().padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Artwork(data, GlanceModifier.size(artSize), corner = 18.dp)
        Spacer(GlanceModifier.width(14.dp))
        Column(
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Titles(data)
            Spacer(GlanceModifier.height(10.dp))
            Controls(data)
        }
    }
}

@Composable
private fun TallLayout(data: WidgetState.Data) {
    Column(
        modifier = GlanceModifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Artwork(data, GlanceModifier.fillMaxWidth().defaultWeight(), corner = 20.dp)
        Spacer(GlanceModifier.height(12.dp))
        Titles(data, modifier = GlanceModifier.fillMaxWidth())
        Spacer(GlanceModifier.height(10.dp))
        Controls(data)
    }
}

@Composable
private fun Artwork(data: WidgetState.Data, modifier: GlanceModifier, corner: Dp) {
    val art = data.art
    if (art != null) {
        Image(
            provider = ImageProvider(art),
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = modifier.cornerRadius(corner),
        )
    } else {
        Box(
            modifier = modifier.cornerRadius(corner).background(Color(0xFF2A2A33)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                provider = ImageProvider(R.drawable.ic_note),
                contentDescription = null,
                colorFilter = ColorFilter.tint(WhiteDim),
                modifier = GlanceModifier.size(36.dp),
            )
        }
    }
}

@Composable
private fun Titles(data: WidgetState.Data, modifier: GlanceModifier = GlanceModifier) {
    Column(modifier = modifier) {
        Text(
            text = data.title.ifEmpty { "Aria" },
            maxLines = 1,
            style = TextStyle(color = White, fontSize = 16.sp, fontWeight = FontWeight.Bold),
        )
        Text(
            text = data.artist.ifEmpty { "Tap to open" },
            maxLines = 1,
            style = TextStyle(color = WhiteDim, fontSize = 13.sp),
        )
    }
}

@Composable
private fun Controls(data: WidgetState.Data) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(R.drawable.ic_previous, RemoteAction.Previous)
        Spacer(GlanceModifier.width(6.dp))
        PlayButton(data, width = 60.dp, height = 44.dp)
        Spacer(GlanceModifier.width(6.dp))
        IconButton(R.drawable.ic_next, RemoteAction.Next)
    }
}

@Composable
private fun IconButton(icon: Int, action: RemoteAction) {
    Box(
        modifier = GlanceModifier
            .size(40.dp)
            .cornerRadius(20.dp)
            .clickable(actionRunCallback<MediaActionCallback>(actionParametersOf(ActionKey to action.name))),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(icon),
            contentDescription = action.name,
            colorFilter = ColorFilter.tint(White),
            modifier = GlanceModifier.size(26.dp),
        )
    }
}

@Composable
private fun PlayButton(data: WidgetState.Data, width: Dp, height: Dp) {
    Box(
        modifier = GlanceModifier
            .size(width, height)
            .cornerRadius(height / 2)
            .background(Color.White)
            .clickable(
                actionRunCallback<MediaActionCallback>(actionParametersOf(ActionKey to RemoteAction.PlayPause.name)),
            ),
        contentAlignment = Alignment.Center,
    ) {
        Image(
            provider = ImageProvider(if (data.isPlaying) R.drawable.ic_pause else R.drawable.ic_play),
            contentDescription = if (data.isPlaying) "Pause" else "Play",
            colorFilter = ColorFilter.tint(Black),
            modifier = GlanceModifier.size(26.dp),
        )
    }
}

private val ActionKey = ActionParameters.Key<String>("action")

class MediaActionCallback : ActionCallback {
    override suspend fun onAction(context: Context, glanceId: GlanceId, parameters: ActionParameters) {
        val action = parameters[ActionKey]?.let { runCatching { RemoteAction.valueOf(it) }.getOrNull() } ?: return
        withContext(Dispatchers.Main) {
            val running = PlaybackService.instance
            if (running != null) {
                running.handle(action)
                return@withContext
            }
            // Service is dead: binding a controller starts it (and restores the last queue).
            val token = SessionToken(context, ComponentName(context, PlaybackService::class.java))
            val controller = MediaController.Builder(context, token).buildAsync().await()
            PlaybackService.instance?.handle(action)
            // Hold the binding until playback has promoted the service to the foreground.
            delay(4_000)
            controller.release()
        }
    }
}
