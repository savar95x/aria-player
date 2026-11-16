package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.pill
import dev.savar.aria.ui.theme.rounded
import android.os.SystemClock
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.collectIsDraggedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import dev.savar.aria.data.LyricLine
import dev.savar.aria.data.Lyrics
import dev.savar.aria.data.LyricsState
import dev.savar.aria.ui.PlayerViewModel
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
fun LyricsPane(
    state: LyricsState,
    vm: PlayerViewModel,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.fillMaxWidth().fadingEdges()) {
        when (state) {
            LyricsState.Loading -> LoadingIndicator(Modifier.align(Alignment.Center))
            is LyricsState.Found -> when (val lyrics = state.lyrics) {
                is Lyrics.Synced -> SyncedLyrics(lyrics.lines, vm)
                is Lyrics.Plain -> Text(
                    lyrics.text,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 32.dp),
                )
                Lyrics.Instrumental -> Text(
                    "♪  Instrumental",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.align(Alignment.Center),
                )
            }
            is LyricsState.NotFound -> ManualSearch("No lyrics found for this one", state.query, onSearch, Modifier.align(Alignment.Center))
            is LyricsState.Failed -> ManualSearch("Couldn't reach the lyrics service", state.query, onSearch, Modifier.align(Alignment.Center))
        }
    }
}

/** Soft fade at the top and bottom so lines drift in and out instead of being cut. */
private fun Modifier.fadingEdges(): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        drawRect(
            Brush.verticalGradient(0f to Color.Transparent, 0.08f to Color.Black, 0.88f to Color.Black, 1f to Color.Transparent),
            blendMode = BlendMode.DstIn,
        )
    }

@Composable
private fun SyncedLyrics(lines: List<LyricLine>, vm: PlayerViewModel) {
    var position by remember { mutableLongStateOf(vm.position()) }
    LaunchedEffect(Unit) {
        while (true) {
            position = vm.position()
            delay(120)
        }
    }
    // Lead by a moment so the highlight lands as the line is sung, not after.
    val current by remember(lines) { derivedStateOf { lines.indexOfLast { it.timeMs <= position + 250 } } }

    // Open already on the line being sung (not at the top, waiting for the next line to scroll).
    val listState = rememberLazyListState(initialFirstVisibleItemIndex = current.coerceAtLeast(0))
    val dragged by listState.interactionSource.collectIsDraggedAsState()
    var lastTouch by remember { mutableLongStateOf(0L) }
    // Only a real scroll pauses following; the first composition (dragged = false) must not.
    LaunchedEffect(dragged) { if (dragged || lastTouch != 0L) lastTouch = SystemClock.uptimeMillis() }
    // Follow the song, unless you've just scrolled yourself.
    LaunchedEffect(current) {
        if (current >= 0 && !dragged && SystemClock.uptimeMillis() - lastTouch > 2500) {
            listState.animateScrollToItem(current)
        }
    }

    BoxWithConstraints(Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            // The active line sits about a third of the way down.
            contentPadding = PaddingValues(top = maxHeight * 0.3f, bottom = maxHeight * 0.6f),
        ) {
            itemsIndexed(lines) { i, line ->
                val active = i == current
                val onSurface = MaterialTheme.colorScheme.onSurface
                val color by animateColorAsState(
                    when {
                        active -> onSurface
                        i < current -> onSurface.copy(alpha = 0.3f)
                        else -> onSurface.copy(alpha = 0.45f)
                    },
                    label = "lyricColor",
                )
                val scale by animateFloatAsState(if (active) 1f else 0.94f, label = "lyricScale")
                Text(
                    line.text.ifBlank { "♪" },
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = color,
                    modifier = Modifier
                        .fillMaxWidth()
                        .graphicsLayer {
                            scaleX = scale
                            scaleY = scale
                            transformOrigin = TransformOrigin(0f, 0.5f)
                        }
                        .clip(rounded(12.dp))
                        .clickable {
                            vm.seekTo(line.timeMs)
                            lastTouch = 0L
                        }
                        .padding(vertical = 10.dp, horizontal = 4.dp),
                )
            }
        }
    }
}

@Composable
private fun ManualSearch(title: String, initial: String, onSearch: (String) -> Unit, modifier: Modifier) {
    var query by remember(initial) { mutableStateOf(initial) }
    Column(
        modifier.fillMaxWidth().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            singleLine = true,
            label = { Text("Song and artist") },
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSearch(query) }),
            modifier = Modifier.fillMaxWidth(),
        )
        FilledTonalButton(shape = pill(), onClick = { onSearch(query) }, enabled = query.isNotBlank()) { Text("Search lyrics") }
    }
}
