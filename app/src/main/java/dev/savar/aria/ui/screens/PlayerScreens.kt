@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)

package dev.savar.aria.ui.screens

import android.os.SystemClock
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.runtime.CompositionLocalProvider
import dev.savar.aria.ui.LocalAnimatedScope
import dev.savar.aria.ui.Motion
import dev.savar.aria.ui.sharedArt
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil3.SingletonImageLoader
import coil3.request.ImageRequest
import coil3.request.SuccessResult
import coil3.request.allowHardware
import coil3.toBitmap
import dev.savar.aria.AppContainer
import dev.savar.aria.data.SongArt
import dev.savar.aria.data.songUri
import dev.savar.aria.playback.SleepTimer
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.components.ArtImage
import dev.savar.aria.ui.components.formatDuration
import kotlinx.coroutines.delay

private const val NowPlayingArtKey = "now-playing-art"

private fun AppContainer.artFor(id: Long?): SongArt? =
    id?.let { library.song(it)?.art ?: SongArt(songUri(it), version = 0) }

/** Wall-clock that ticks once a second while [active]; read it to recompose countdowns. */
@Composable
private fun rememberTicker(active: Boolean): Long {
    var now by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(active) {
        while (active) { now = SystemClock.elapsedRealtime(); delay(1000) }
    }
    return now
}

@Composable
fun MiniPlayer(
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    artVisible: Boolean,
    onClick: () -> Unit,
) {
    val meta = player.current?.mediaMetadata
    var progress by remember { mutableFloatStateOf(0f) }
    LaunchedEffect(player.currentId, player.playing, player.durationMs) {
        while (true) {
            progress = if (player.durationMs > 0) (vm.position().toFloat() / player.durationMs).coerceIn(0f, 1f) else 0f
            if (!player.playing) break
            delay(500)
        }
    }
    // No container of its own: it lives inside the bottom dock, sharing the nav bar's surface.
    Column(Modifier.fillMaxWidth().clickable(onClick = onClick)) {
            Row(
                Modifier.padding(start = 14.dp, end = 6.dp, top = 10.dp, bottom = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                // Fixed slot so the row doesn't reflow while the art flies up to the now-playing page.
                Box(Modifier.size(48.dp)) {
                    AnimatedVisibility(visible = artVisible, enter = fadeIn(), exit = fadeOut()) {
                        CompositionLocalProvider(LocalAnimatedScope provides this) {
                            ArtImage(
                                container.artFor(player.currentId)?.copy(sizePx = 160),
                                Modifier.sharedArt(NowPlayingArtKey).fillMaxSize(),
                                RoundedCornerShape(12.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.width(12.dp))
                AnimatedContent(
                    targetState = meta?.title?.toString().orEmpty() to meta?.artist?.toString().orEmpty(),
                    transitionSpec = { Motion.trackChange() },
                    modifier = Modifier.weight(1f),
                    label = "miniTitle",
                ) { (title, artist) ->
                    Column {
                        Text(
                            title,
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            artist,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = vm::togglePlay) {
                    Icon(if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, "Play/pause")
                }
                IconButton(onClick = { vm.next() }) { Icon(Icons.Rounded.SkipNext, "Next") }
            }
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp).height(2.dp),
                trackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
    }
}

/** Accent colour pulled from the artwork, used to tint the now-playing backdrop. */
@Composable
private fun rememberArtColor(art: SongArt?): Color {
    val context = LocalContext.current
    val fallback = MaterialTheme.colorScheme.primary
    var color by remember { mutableStateOf(fallback) }
    LaunchedEffect(art) {
        if (art == null) { color = fallback; return@LaunchedEffect }
        val request = ImageRequest.Builder(context).data(art.copy(sizePx = 160)).allowHardware(false).build()
        val result = SingletonImageLoader.get(context).execute(request)
        color = (result as? SuccessResult)?.image?.toBitmap()?.let { bmp ->
            val palette = Palette.from(bmp).generate()
            val swatch = palette.vibrantSwatch ?: palette.dominantSwatch ?: palette.mutedSwatch
            swatch?.rgb?.let { Color(it) }
        } ?: fallback
    }
    return animateColorAsState(color, tween(600), label = "artColor").value
}

@Composable
fun NowPlayingScreen(
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    onCollapse: () -> Unit,
    onOpenAlbum: (Long) -> Unit,
    onOpenArtist: (String) -> Unit,
    onEditTags: (Long) -> Unit,
) {
    val meta = player.current?.mediaMetadata
    val art = container.artFor(player.currentId)
    val accent = rememberArtColor(art)
    val bg = MaterialTheme.colorScheme.background
    var showQueue by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    val sleep by container.sleepTimer.state.collectAsStateWithLifecycle()
    val now = rememberTicker(sleep.active)
    val collapseThreshold = with(LocalDensity.current) { 120.dp.toPx() }

    Box(
        Modifier
            .fillMaxSize()
            .background(bg)
            .background(Brush.verticalGradient(0f to accent.copy(alpha = 0.42f), 0.75f to bg))
            // Swallow taps so they don't reach the library underneath; drag down to collapse.
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .pointerInput(Unit) {
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onDragEnd = { if (total > collapseThreshold) onCollapse() },
                ) { _, dy -> total += dy }
            },
    ) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Top bar
            Row(Modifier.fillMaxWidth().padding(top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onCollapse) { Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", Modifier.size(30.dp)) }
                Text(
                    "Now playing",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                )
                NowPlayingMenu(
                    onAlbum = {
                        val id = player.currentId ?: return@NowPlayingMenu
                        container.library.song(id)?.let { onOpenAlbum(it.albumId) }
                    },
                    onArtist = { meta?.artist?.toString()?.let(onOpenArtist) },
                    onEditTags = { player.currentId?.let(onEditTags) },
                )
            }

            Spacer(Modifier.weight(0.6f))
            ArtImage(
                art?.copy(sizePx = 1024),
                Modifier.sharedArt(NowPlayingArtKey).fillMaxWidth().aspectRatio(1f),
                RoundedCornerShape(28.dp),
                placeholder = art?.copy(sizePx = 160),
            )
            Spacer(Modifier.weight(0.5f))

            // Titles
            AnimatedContent(
                targetState = meta?.title?.toString().orEmpty() to meta?.artist?.toString().orEmpty(),
                transitionSpec = { Motion.trackChange() },
                modifier = Modifier.fillMaxWidth(),
                label = "npTitle",
            ) { (title, artist) ->
            Column(Modifier.fillMaxWidth()) {
                Text(
                    title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
                Text(
                    artist,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    modifier = Modifier.basicMarquee(),
                )
            }
            }
            Spacer(Modifier.height(16.dp))
            SeekBar(player, vm)
            Spacer(Modifier.height(8.dp))
            Controls(player, vm)
            Spacer(Modifier.weight(0.4f))

            // Bottom actions
            Row(Modifier.fillMaxWidth().padding(bottom = 8.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { showSleep = true }) {
                    Icon(Icons.Rounded.Bedtime, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (sleep.active) sleepLabel(sleep, now) else "Sleep")
                }
                TextButton(onClick = { showQueue = true }) {
                    Icon(Icons.AutoMirrored.Rounded.QueueMusic, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Queue")
                }
            }
        }
    }

    if (showQueue) QueueSheet(player, vm) { showQueue = false }
    if (showSleep) SleepTimerSheet(container.sleepTimer) { showSleep = false }
}

@Composable
private fun NowPlayingMenu(onAlbum: () -> Unit, onArtist: () -> Unit, onEditTags: () -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.Rounded.MoreVert, "More") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Go to album") }, onClick = { open = false; onAlbum() })
            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { open = false; onArtist() })
            DropdownMenuItem(text = { Text("Edit tags") }, onClick = { open = false; onEditTags() })
        }
    }
}

@Composable
private fun SeekBar(player: PlayerUiState, vm: PlayerViewModel) {
    var position by remember { mutableLongStateOf(0L) }
    var dragging by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(player.currentId, player.playing) {
        while (true) {
            if (dragging == null) position = vm.position()
            if (!player.playing) break
            delay(250)
        }
    }
    val duration = player.durationMs.coerceAtLeast(1)
    Column(Modifier.fillMaxWidth()) {
        Slider(
            value = dragging ?: (position.toFloat() / duration).coerceIn(0f, 1f),
            onValueChange = { dragging = it },
            onValueChangeFinished = {
                dragging?.let {
                    val target = (it * duration).toLong()
                    vm.seekTo(target)
                    position = target
                }
                dragging = null
            },
            colors = SliderDefaults.colors(inactiveTrackColor = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.15f)),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            val shown = dragging?.let { (it * duration).toLong() } ?: position
            Text(formatDuration(shown), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(formatDuration(player.durationMs), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun Controls(player: PlayerUiState, vm: PlayerViewModel) {
    val active = MaterialTheme.colorScheme.primary
    val idle = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = { vm.toggleShuffle() }) {
            Icon(Icons.Rounded.Shuffle, "Shuffle", tint = if (player.shuffle) active else idle)
        }
        IconButton(onClick = { vm.previous() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipPrevious, "Previous", Modifier.size(36.dp))
        }
        // Squircle when paused, rounder while playing.
        val corner by animateDpAsState(if (player.playing) 40.dp else 26.dp, label = "playCorner")
        Box(
            Modifier
                .size(80.dp)
                .clip(RoundedCornerShape(corner))
                .background(MaterialTheme.colorScheme.primary)
                .clickable(onClick = vm::togglePlay),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                if (player.playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                if (player.playing) "Pause" else "Play",
                tint = MaterialTheme.colorScheme.onPrimary,
                modifier = Modifier.size(40.dp),
            )
        }
        IconButton(onClick = { vm.next() }, modifier = Modifier.size(56.dp)) {
            Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(36.dp))
        }
        IconButton(onClick = { vm.cycleRepeat() }) {
            Icon(
                if (player.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                "Repeat",
                tint = if (player.repeatMode == Player.REPEAT_MODE_OFF) idle else active,
            )
        }
    }
}

@Composable
private fun QueueSheet(player: PlayerUiState, vm: PlayerViewModel, onDismiss: () -> Unit) {
    val currentPos = player.queue.indexOfFirst { it.index == player.currentIndex }.coerceAtLeast(0)
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = currentPos)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Text(
            "Up next · ${player.queue.size}",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
        )
        LazyColumn(state = listState, modifier = Modifier.fillMaxWidth()) {
            items(player.queue, key = { "${it.index}_${it.item.mediaId}" }) { entry ->
                val isCurrent = entry.index == player.currentIndex
                val meta = entry.item.mediaMetadata
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { vm.skipTo(entry.index) }
                        .background(if (isCurrent) MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f) else Color.Transparent)
                        .padding(start = 20.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    ArtImage(
                        entry.item.mediaId.toLongOrNull()?.let { SongArt(songUri(it), 0, 160) },
                        Modifier.size(44.dp),
                        RoundedCornerShape(10.dp),
                    )
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            meta.title?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyLarge,
                            color = if (isCurrent) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            meta.artist?.toString().orEmpty(),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (!isCurrent) {
                        IconButton(onClick = { vm.removeAt(entry.index) }) {
                            Icon(Icons.Rounded.Close, "Remove", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

private fun sleepLabel(state: SleepTimer.State, now: Long): String {
    if (state.waitingForSongEnd) return "After this song"
    val remaining = ((state.endsAt ?: 0) - now).coerceAtLeast(0)
    return formatDuration(remaining)
}

@Composable
private fun SleepTimerSheet(timer: SleepTimer, onDismiss: () -> Unit) {
    val state by timer.state.collectAsStateWithLifecycle()
    val now = rememberTicker(state.active)
    ModalBottomSheet(onDismissRequest = onDismiss, containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp)) {
            Text("Sleep timer", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(4.dp))
            if (state.active) {
                Text(
                    if (state.waitingForSongEnd) "Stopping when this song ends" else "Stopping in ${sleepLabel(state, now)}",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.primary,
                )
            } else {
                Text("Pause playback after", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.height(16.dp))
            val presets = listOf(10, 15, 30, 45, 60, 90)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.take(3).forEach { m -> PresetChip(m, Modifier.weight(1f)) { timer.start(m, state.finishSong) } }
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                presets.drop(3).forEach { m -> PresetChip(m, Modifier.weight(1f)) { timer.start(m, state.finishSong) } }
            }
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Finish current song", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "Wait for the track to end instead of fading out",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(checked = state.finishSong, onCheckedChange = timer::setFinishSong)
            }
            if (state.active) {
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(onClick = { timer.cancel() }, modifier = Modifier.weight(1f)) { Text("Turn off") }
                    FilledTonalButton(onClick = { timer.extend(10) }, modifier = Modifier.weight(1f), enabled = !state.waitingForSongEnd) {
                        Text("+10 min")
                    }
                }
            }
        }
    }
}

@Composable
private fun PresetChip(minutes: Int, modifier: Modifier, onClick: () -> Unit) {
    FilterChip(
        selected = false,
        onClick = onClick,
        label = { Text(if (minutes < 60) "$minutes min" else "${minutes / 60}h${if (minutes % 60 > 0) " ${minutes % 60}m" else ""}") },
        modifier = modifier,
    )
}
