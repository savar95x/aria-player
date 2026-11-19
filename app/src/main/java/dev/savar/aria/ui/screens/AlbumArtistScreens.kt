@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

// The pages you drill into from the library: an album, and an artist (their albums and songs).

package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.pill
import dev.savar.aria.ui.theme.albumArtShape
import dev.savar.aria.ui.components.ArtistImage
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import dev.savar.aria.ui.sharedArt
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.geometry.Rect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.AppContainer
import dev.savar.aria.data.Artist
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.components.ArtImage
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.components.SongRow
import dev.savar.aria.ui.components.formatDuration

@Composable
private fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    // Expressive buttons: shape morphs on press.
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPlay, shapes = ButtonDefaults.shapes(shape = pill())) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle, shapes = ButtonDefaults.shapes(shape = pill())) {
            Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
            Spacer(Modifier.width(6.dp))
            Text("Shuffle")
        }
    }
}

@Composable
fun AlbumScreen(
    albumId: Long,
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    actions: SongActions,
    onBack: () -> Unit,
    onArtist: (String) -> Unit,
) {
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    // A song's own MediaStore album id may belong to a merged album with a different id.
    val album = albums.firstOrNull { it.id == albumId } ?: albums.firstOrNull { a -> a.songs.any { it.albumId == albumId } }
    val pinned = album != null && album.pinKey in settings?.pinnedAlbums.orEmpty()
    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            BackTopBar(onBack = onBack) {
                if (album != null) {
                    IconButton(onClick = { container.settings.togglePinned(album) }) {
                        Icon(
                            if (pinned) Icons.Rounded.PushPin else Icons.Outlined.PushPin,
                            if (pinned) "Unpin album" else "Pin album",
                            tint = if (pinned) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
        },
    ) { padding ->
        if (album == null) return@Scaffold
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(), contentPadding = listBottomPadding()) {
            item(key = "header") {
                Column(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    ArtImage(
                        album.art.copy(sizePx = 720),
                        Modifier.sharedArt("album-${album.id}").size(240.dp),
                        albumArtShape(),
                        placeholder = album.art.copy(sizePx = 400),
                    )
                    Spacer(Modifier.height(20.dp))
                    // One step below headline: album titles are often long, and at headline size they
                    // wrapped into a big block that dwarfed the artist line and the track list.
                    Text(
                        album.title,
                        style = MaterialTheme.typography.titleLarge,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(horizontal = 24.dp),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        album.artist,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onArtist(album.artist) }.padding(4.dp),
                    )
                    val total = album.songs.sumOf { it.durationMs }
                    Text(
                        listOfNotNull(
                            album.year.takeIf { it > 0 }?.toString(),
                            "${album.songs.size} songs",
                            formatDuration(total),
                        ).joinToString(" · "),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    PlayShuffleRow(onPlay = { vm.play(album.songs) }, onShuffle = { vm.play(album.songs, shuffle = true) })
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(album.songs, key = { _, s -> s.id }) { index, song ->
                SongRow(
                    song = song,
                    isCurrent = song.id == player.currentId,
                    actions = actions,
                    onClick = { vm.play(album.songs, index) },
                    leading = {
                        Box(Modifier.size(32.dp), contentAlignment = Alignment.Center) {
                            Text(
                                (song.track % 1000).takeIf { it > 0 }?.toString() ?: "–",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    },
                )
            }
            item { NavBarSpacer() }
        }
    }
}

@Composable
fun ArtistScreen(
    name: String,
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    actions: SongActions,
    onBack: () -> Unit,
    photoOpen: Boolean,
    onViewPhoto: (Artist, Rect) -> Unit,
) {
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.name == name }
    // Where the header photo is, so the full-screen viewer can grow out of it (and back into it).
    var photoBounds by remember { mutableStateOf(Rect.Zero) }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar(onBack = onBack) }) { padding ->
        if (artist == null) return@Scaffold
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(), contentPadding = listBottomPadding()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtistImage(
                        artist,
                        Modifier.sharedArt("artist-${artist.name}").size(168.dp)
                            .onGloballyPositioned { photoBounds = Rect(it.positionInRoot(), it.size.toSize()) }
                            // Hidden while the viewer shows it, so there's only ever one copy on screen.
                            .graphicsLayer { alpha = if (photoOpen) 0f else 1f }
                            .clip(CircleShape)
                            .clickable { onViewPhoto(artist, photoBounds) },
                        sizePx = 480,
                        placeholderPx = 160,
                    )
                    Spacer(Modifier.height(16.dp))
                    Text(artist.name, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
                    Text(
                        "${artist.songs.size} songs · ${artist.albumCount} albums",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    PlayShuffleRow(onPlay = { vm.play(artist.songs) }, onShuffle = { vm.play(artist.songs, shuffle = true) })
                    Spacer(Modifier.height(12.dp))
                }
            }
            itemsIndexed(artist.songs, key = { _, s -> s.id }) { index, song ->
                SongRow(song, song.id == player.currentId, actions, onClick = { vm.play(artist.songs, index) })
            }
            item { NavBarSpacer() }
        }
    }
}
