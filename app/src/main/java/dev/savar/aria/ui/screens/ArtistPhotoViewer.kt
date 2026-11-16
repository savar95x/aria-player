@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.pill
import android.net.Uri
import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.shape.CornerSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
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
 *
 * Drawn in the app's own window (over everything, from AriaRoot) rather than a Dialog, so the photo
 * can grow out of the round header image and shrink back into it (a container transform) instead of
 * the dialog's fade. [source] is the header image's bounds in root coordinates.
 */
@Composable
fun ArtistPhotoViewer(artist: Artist, photos: ArtistPhotos, source: Rect, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val version by photos.changes.collectAsState()
    val photo = remember(version, artist.name) { photos.cached(artist.name) }
    var picking by remember { mutableStateOf(false) }
    val dismissY = remember { Animatable(0f) }
    val open = remember { Animatable(0f) }
    var closing by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(Unit) { open.animateTo(1f, spring(dampingRatio = 1f, stiffness = 650f)) }
    fun close() {
        if (closing) return
        closing = true
        scope.launch {
            launch { dismissY.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f)) }
            open.animateTo(0f, spring(dampingRatio = 1f, stiffness = 900f))
            onDismiss()
        }
    }
    BackHandler(enabled = !closing) { close() }

    // Light status bar icons over the black backdrop, whatever the theme.
    val view = LocalView.current
    DisposableEffect(view) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val wasLight = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = false
        onDispose { if (wasLight != null) controller?.isAppearanceLightStatusBars = wasLight }
    }

    var origin by remember { mutableStateOf(Offset.Zero) }
    val fade = (1f - abs(dismissY.value) / 1200f).coerceIn(0f, 1f)
    val p = open.value
    val chrome = ((p - 0.5f) * 2f).coerceIn(0f, 1f) * fade
    Box(
        Modifier
            .fillMaxSize()
            .onGloballyPositioned { origin = it.positionInRoot() }
            .drawBehind { drawRect(Color.Black.copy(alpha = p.coerceIn(0f, 1f) * fade)) },
    ) {
        ZoomableImage(
            model = remember(photo, artist) {
                ImageRequest.Builder(context)
                    .data(photo ?: artist.art.copy(sizePx = 1024))
                    .size(Size.ORIGINAL)
                    .crossfade(true)
                    .build()
            },
            // The header's copy is already in memory: shown at once, under the full-size one.
            placeholder = remember(photo, artist) {
                if (photo != null) ImageRequest.Builder(context).data(photo).size(480).build()
                else artist.art.copy(sizePx = 480)
            },
            source = source.translate(-origin),
            progress = { open.value },
            offsetY = { dismissY.value },
            closing = closing,
            onDrag = { dy -> if (!closing) scope.launch { dismissY.snapTo(dismissY.value + dy) } },
            onDragEnd = {
                if (!closing) scope.launch {
                    if (abs(dismissY.value) > 260f) close() else dismissY.animateTo(0f, spring())
                }
            },
            modifier = Modifier.fillMaxSize(),
        )
        Row(
            Modifier.fillMaxWidth().statusBarsPadding().padding(horizontal = 4.dp, vertical = 4.dp)
                .graphicsLayer { alpha = chrome },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = { close() }) { Icon(Icons.Rounded.Close, "Close", tint = Color.White) }
            Text(
                artist.name,
                color = Color.White,
                style = MaterialTheme.typography.titleMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        FilledTonalButton(
            shape = pill(),
            onClick = { picking = true },
            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 24.dp)
                .graphicsLayer { alpha = chrome },
        ) { Text("Change photo") }
    }
    if (picking) ArtistPhotoPicker(artist, photos) { picking = false }
}

/**
 * Square image that fits the screen; pinch, pan and double-tap zoom. Vertical drags at 1x go to [onDrag].
 * At [progress] 0 the image sits exactly over [source] as a circle; at 1 it fills the screen width.
 */
@Composable
private fun ZoomableImage(
    model: Any,
    placeholder: Any,
    source: Rect,
    progress: () -> Float,
    offsetY: () -> Float,
    closing: Boolean,
    onDrag: (Float) -> Unit,
    onDragEnd: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()

    // Closing while zoomed in: settle back to 1x as the photo flies home.
    LaunchedEffect(closing) {
        if (closing && (scale != 1f || offset != Offset.Zero)) {
            val s0 = scale
            val o0 = offset
            animate(0f, 1f, animationSpec = spring(dampingRatio = 1f, stiffness = 900f)) { t, _ ->
                scale = s0 + (1f - s0) * t
                offset = o0 * (1f - t)
            }
        }
    }

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

        Box(
            Modifier
                .aspectRatio(1f)
                .graphicsLayer {
                    // Container transform: from the header circle to the full-width square.
                    val p = progress()
                    val q = 1f - p
                    val from = if (source.width > 0f) source.width / side else 0.4f
                    val k = from + (1f - from) * p
                    scaleX = k
                    scaleY = k
                    translationX = (source.center.x - w / 2f) * q
                    translationY = (source.center.y - h / 2f) * q + offsetY()
                    // CornerSize(Float) is pixels: half the side = circle, easing to square corners.
                    shape = RoundedCornerShape(CornerSize((side / 2f * q).coerceIn(0f, side / 2f)))
                    clip = p < 0.999f
                },
        ) {
            val zoom = Modifier.fillMaxSize().graphicsLayer {
                scaleX = scale
                scaleY = scale
                translationX = offset.x
                translationY = offset.y
            }
            AsyncImage(model = placeholder, contentDescription = null, contentScale = ContentScale.Crop, modifier = zoom)
            AsyncImage(model = model, contentDescription = null, contentScale = ContentScale.Crop, modifier = zoom)
        }
        // Gestures on a full-size overlay, so you can pinch anywhere on screen.
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(w, h) {
                    detectTapGestures(onDoubleTap = { tap -> if (!closing) zoomTo(if (scale > 1.05f) 1f else 2.5f, tap) })
                }
                .pointerInput(w, h, closing) {
                    if (closing) return@pointerInput
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
                OutlinedButton(shape = pill(), onClick = { gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                    Icon(Icons.Rounded.PhotoLibrary, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Gallery")
                }
                OutlinedButton(shape = pill(), onClick = { photos.useAlbumArt(artist.name); onDone() }) {
                    Icon(Icons.Rounded.Image, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Album art")
                }
                if (photos.isUserChoice(artist.name)) {
                    OutlinedButton(shape = pill(), onClick = { photos.automatic(artist.name); onDone() }) {
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
