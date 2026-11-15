@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.aria.ui.screens

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.size.Size
import dev.savar.aria.data.Artist
import dev.savar.aria.data.ArtistPhotos
import dev.savar.aria.ui.components.photoHints
import dev.savar.aria.ui.theme.rounded
import kotlinx.coroutines.launch
import kotlin.math.abs

/**
 * Full-screen artist photo: pinch or double-tap to zoom, drag to look around, swipe down to close.
 * "Change photo" opens the picker.
 */
@Composable
fun ArtistPhotoViewer(artist: Artist, photos: ArtistPhotos, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by photos.changes.collectAsState()
    val photo = remember(version, artist.name) { photos.cached(artist.name) }
    var picking by remember { mutableStateOf(false) }
    val dismissY = remember { Animatable(0f) }
    val scope = rememberCoroutineScope()

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false, decorFitsSystemWindows = false),
    ) {
        val fade = (1f - abs(dismissY.value) / 1200f).coerceIn(0f, 1f)
        Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = fade))) {
            ZoomableImage(
                model = remember(photo, artist) {
                    ImageRequest.Builder(context)
                        .data(photo ?: artist.art.copy(sizePx = 1024))
                        .size(Size.ORIGINAL)
                        .crossfade(true)
                        .build()
                },
                onDrag = { dy -> scope.launch { dismissY.snapTo(dismissY.value + dy) } },
                onDragEnd = {
                    scope.launch {
                        if (abs(dismissY.value) > 260f) onDismiss() else dismissY.animateTo(0f, spring())
                    }
                },
                modifier = Modifier.fillMaxSize().graphicsLayer { translationY = dismissY.value },
            )
            Row(
                Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
                    .graphicsLayer { alpha = fade },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                IconButton(onClick = onDismiss) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
                Text(
                    artist.name,
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            FilledTonalButton(
                onClick = { picking = true },
                modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                    .graphicsLayer { alpha = fade },
            ) { Text("Change photo") }
        }
        if (picking) ArtistPhotoPicker(artist, photos) { picking = false }
    }
}

/** Square image that fits the screen; pinch, pan and double-tap zoom. Vertical drags at 1x go to [onDrag]. */
@Composable
private fun ZoomableImage(
    model: Any,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    BoxWithConstraints(modifier, contentAlignment = Alignment.Center) {
        val w = constraints.maxWidth.toFloat()
        val h = constraints.maxHeight.toFloat()
        val side = minOf(w, h)
        fun clamp(o: Offset, s: Float): Offset {
            val maxX = ((side * s - w) / 2f).coerceAtLeast(0f)
            val maxY = ((side * s - h) / 2f).coerceAtLeast(0f)
            return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
        }
        fun zoomTo(target: Float, focus: Offset) {
            val startScale = scale
            val startOffset = offset
            val c = focus - Offset(w / 2f, h / 2f)
            scope.launch {
                animate(0f, 1f, animationSpec = spring(stiffness = 600f)) { t, _ ->
                    val s = startScale + (target - startScale) * t
                    scale = s
                    offset = clamp(c - (c - startOffset) * (s / startScale), s)
                }
            }
        }

        AsyncImage(
            model = model,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offset.x
                    translationY = offset.y
                },
        )
        // Gestures on a full-size overlay, so you can pinch anywhere on screen.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h) {
                    detectTapGestures(onDoubleTap = { tap -> zoomTo(if (scale > 1.05f) 1f else 2.5f, tap) })
                }
                .pointerInput(w, h) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        var dismissing = false
                        do {
                            val event = awaitPointerEvent()
                            val zoom = event.calculateZoom()
                            val pan = event.calculatePan()
                            val multi = event.changes.count { it.pressed } > 1
                            if (!dismissing && (multi || scale > 1.01f)) {
                                val c = event.calculateCentroid() - Offset(w / 2f, h / 2f)
                                val newScale = (scale * zoom).coerceIn(1f, 5f)
                                offset = clamp(c - (c - offset) * (newScale / scale) + pan, newScale)
                                scale = newScale
                            } else if (pan != Offset.Zero && (dismissing || abs(pan.y) > abs(pan.x))) {
                                dismissing = true
                                onDrag(pan.y)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } while (event.changes.any { it.pressed })
                        if (dismissing) onDragEnd()
                        if (scale < 1.01f) { scale = 1f; offset = Offset.Zero }
                    }
                },
        )
    }
}

/** Pick another photo: online matches, the gallery, album art, or back to automatic. */
@Composable
private fun ArtistPhotoPicker(artist: Artist, photos: ArtistPhotos, onDone: () -> Unit) {
    val scope = rememberCoroutineScope()
    val hints = remember(artist) { artist.photoHints() }
    var busy by remember { mutableStateOf(false) }
    val candidates by produceState<List<ArtistPhotos.Candidate>?>(null, artist.name) {
        value = photos.candidates(artist.name, hints)
    }
    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) scope.launch {
            busy = true
            photos.chooseFromGallery(artist.name, uri)
            onDone()
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDone,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(horizontal = 16.dp)) {
            Text("Photo for ${artist.name}", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(onClick = { photos.useAlbumArt(artist.name); onDone() }) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Album art")
                }
                if (photos.isUserChoice(artist.name)) {
                    OutlinedButton(onClick = { photos.automatic(artist.name); onDone() }) {
                        Icon(Icons.Rounded.AutoAwesome, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Auto")
                    }
                }
            }
            Spacer(Modifier.height(16.dp))
            val list = candidates
            Box(Modifier.fillMaxWidth().heightIn(min = 160.dp, max = 520.dp), contentAlignment = Alignment.Center) {
                when {
                    list == null || busy -> LoadingIndicator()
                    list.isEmpty() -> Text(
                        "No photos found online. Try one from your gallery.",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    else -> LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth().padding(bottom = 24.dp),
                    ) {
                        items(list, key = { it.url }) { c ->
                            Column(
                                Modifier.clickable {
                                    busy = true
                                    scope.launch { photos.choose(artist.name, c); onDone() }
                                },
                            ) {
                                AsyncImage(
                                    model = c.thumb,
                                    contentDescription = c.label,
                                    contentScale = ContentScale.Crop,
                                    modifier = Modifier.fillMaxWidth().aspectRatio(1f).clip(rounded(14.dp))
                                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                                )
                                Spacer(Modifier.height(4.dp))
                                Text(c.source, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                                Text(
                                    c.label,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
