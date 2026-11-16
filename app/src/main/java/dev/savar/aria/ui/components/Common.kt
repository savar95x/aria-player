package dev.savar.aria.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.runtime.collectAsState
import dev.savar.aria.data.ArtistPhotos
import coil3.request.crossfade
import androidx.compose.runtime.produceState
import androidx.compose.foundation.shape.CircleShape
import dev.savar.aria.data.Artist
import dev.savar.aria.AriaApp
import dev.savar.aria.ui.theme.rounded
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import dev.savar.aria.data.Song
import dev.savar.aria.data.SongArt

/** Artwork with a quiet placeholder underneath (shows through when a song has no art). */
@Composable
fun ArtImage(
    art: SongArt?,
    modifier: Modifier = Modifier,
    shape: Shape = rounded(12.dp),
    /** A smaller copy that's likely cached (e.g. from the list); shown while the big one loads. */
    placeholder: SongArt? = null,
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(MaterialTheme.colorScheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.MusicNote,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f),
            modifier = Modifier.fillMaxSize(0.4f),
        )
        if (art != null) {
            val context = LocalContext.current
            key(art) {
            AsyncImage(
                model = remember(art, placeholder) {
                    ImageRequest.Builder(context)
                        .data(art)
                        .placeholderMemoryCacheKey(placeholder?.let { "${it.uri}#${it.version}#${it.sizePx}" })
                        // Fades in when decoded; images already in memory still appear instantly.
                        .crossfade(150)
                        .build()
                },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            }
        }
    }
}

data class SongActions(
    val onPlayNext: (Song) -> Unit,
    val onAddToQueue: (Song) -> Unit,
    val onOpenAlbum: (Song) -> Unit,
    val onOpenArtist: (Song) -> Unit,
    val onEditTags: (Song) -> Unit,
)

@Composable
fun SongRow(
    song: Song,
    isCurrent: Boolean,
    actions: SongActions,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    leading: (@Composable () -> Unit)? = null,
) {
    // The playing song sits in a tinted pill with an equaliser mark, like Google's own apps:
    // colour alone (our primary is a soft sage) was too easy to miss.
    val rowShape = rounded(18.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp)
            .clip(rowShape)
            .then(if (isCurrent) Modifier.background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.32f)) else Modifier)
            .clickable(onClick = onClick)
            .padding(start = 8.dp, end = 0.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        when {
            isCurrent && leading != null -> Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.GraphicEq, "Playing", Modifier.size(22.dp), tint = MaterialTheme.colorScheme.primary)
            }
            leading != null -> leading()
            else -> Box(contentAlignment = Alignment.Center) {
                ArtImage(song.art.copy(sizePx = 160), Modifier.size(48.dp))
                if (isCurrent) Box(
                    Modifier.size(48.dp).clip(rounded(12.dp)).background(Color.Black.copy(alpha = 0.45f)),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.GraphicEq, "Playing", Modifier.size(24.dp), tint = Color.White) }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                "${song.artist} · ${formatDuration(song.durationMs)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        SongMenu(song, actions)
    }
}

@Composable
fun SongMenu(song: Song, actions: SongActions) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) {
            Icon(Icons.Rounded.MoreVert, contentDescription = "More")
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            DropdownMenuItem(text = { Text("Play next") }, onClick = { open = false; actions.onPlayNext(song) })
            DropdownMenuItem(text = { Text("Add to queue") }, onClick = { open = false; actions.onAddToQueue(song) })
            DropdownMenuItem(text = { Text("Go to album") }, onClick = { open = false; actions.onOpenAlbum(song) })
            DropdownMenuItem(text = { Text("Go to artist") }, onClick = { open = false; actions.onOpenArtist(song) })
            DropdownMenuItem(text = { Text("Edit tags") }, onClick = { open = false; actions.onEditTags(song) })
        }
    }
}

fun formatDuration(ms: Long): String {
    val total = (ms / 1000).coerceAtLeast(0)
    val h = total / 3600
    val m = (total % 3600) / 60
    val s = total % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}

/**
 * An artist's photo (fetched once, then kept on the phone), falling back to their album art.
 * Cached photos show straight away; a newly fetched one fades in over the album art.
 */
@Composable
fun ArtistImage(
    artist: Artist,
    modifier: Modifier = Modifier,
    sizePx: Int = 160,
    placeholderPx: Int? = null,
) {
    val context = LocalContext.current
    val photos = remember { (context.applicationContext as AriaApp).container.artistPhotos }
    val hints = remember(artist) { artist.photoHints() }
    val version by photos.changes.collectAsState()
    val photo by produceState(photos.cached(artist.name), artist.name, version) {
        value = photos.cached(artist.name) ?: photos.get(artist.name, hints)
    }
    val file = photo
    val shape = CircleShape // Artist photos are always circles, whatever the roundness setting.
    Box(modifier.clip(shape)) {
        if (file == null) {
            ArtImage(
                artist.art.copy(sizePx = sizePx),
                Modifier.fillMaxSize(),
                shape,
                placeholder = placeholderPx?.let { artist.art.copy(sizePx = it) },
            )
        } else {
            Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainerHighest))
            // The list-size copy (already decoded for the Artists tab) sits underneath, so a bigger
            // header never shows an empty frame or the music-note placeholder while it decodes.
            AsyncImage(
                model = remember(file) { ImageRequest.Builder(context).data(file).size(160).crossfade(true).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
            if (sizePx > 160) AsyncImage(
                model = remember(file, sizePx) { ImageRequest.Builder(context).data(file).size(sizePx).build() },
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/** Your own albums and songs by this artist: lets the photo lookup find the right person. */
fun Artist.photoHints() = ArtistPhotos.Hints(
    albums = songs.map { it.album }.filter { it.isNotBlank() && !it.contains("unknown", ignoreCase = true) }
        .groupingBy { it }.eachCount().entries.sortedByDescending { it.value }.map { it.key }.take(2),
    titles = songs.map { it.title }.filter { it.isNotBlank() }.take(1),
)
