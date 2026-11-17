@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

// Search across songs, albums and artists; a pasted http(s) link offers to play it.

package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.albumArtShape
import dev.savar.aria.ui.theme.rounded
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.rounded.Link
import dev.savar.aria.data.Links
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import dev.savar.aria.ui.sharedArt
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.AppContainer
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.components.ArtImage
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.components.SongRow

@Composable
fun SearchScreen(
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    actions: SongActions,
    onBack: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
) {
    val songs by container.library.songs.collectAsStateWithLifecycle()
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val artists by container.library.artists.collectAsStateWithLifecycle()
    var query by rememberSaveable { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }

    val q = query.trim()
    val link = remember(q) { Links.find(q) }
    val songHits = remember(q, songs) {
        if (q.isEmpty()) emptyList() else songs.orEmpty().filter {
            it.title.contains(q, true) || it.artist.contains(q, true) || it.album.contains(q, true)
        }
    }
    val albumHits = remember(q, albums) { if (q.isEmpty()) emptyList() else albums.filter { it.title.contains(q, true) }.take(6) }
    val artistHits = remember(q, artists) { if (q.isEmpty()) emptyList() else artists.filter { it.name.contains(q, true) }.take(6) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            PillRow {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Search or paste a link") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        trailingIcon = {
                            if (query.isNotEmpty()) IconButton(onClick = { query = "" }) { Icon(Icons.Rounded.Close, "Clear") }
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        modifier = Modifier.weight(1f).focusRequester(focus),
                    )
            }
        },
    ) { padding ->
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(), contentPadding = listBottomPadding()) {
            link?.let { url ->
                item(key = "link") { LinkRow(url, onPlay = { vm.playUrl(url); onBack() }) }
            }
            if (artistHits.isNotEmpty()) {
                item { SectionHeader("Artists") }
                items(artistHits, key = { "ar_" + it.name }) { ArtistRow(it) { onArtist(it.name) } }
            }
            if (albumHits.isNotEmpty()) {
                item { SectionHeader("Albums") }
                items(albumHits, key = { "al_" + it.id }) { album ->
                    Row(
                        Modifier.fillMaxWidth().clickable { onAlbum(album.id) }.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ArtImage(album.art.copy(sizePx = 160), Modifier.sharedArt("album-${album.id}").size(52.dp), albumArtShape())
                        Spacer(Modifier.width(14.dp))
                        Column {
                            Text(album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text(album.artist, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
            if (songHits.isNotEmpty()) {
                item { SectionHeader("Songs") }
                itemsIndexed(songHits, key = { _, s -> "s_" + s.id }) { index, song ->
                    SongRow(song, song.id == player.currentId, actions, onClick = { vm.play(songHits, index) })
                }
            }
            item { NavBarSpacer() }
        }
    }
}

@Composable
private fun LinkRow(url: String, onPlay: () -> Unit) {
    val youtube = Links.isYouTube(url)
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !youtube, onClick = onPlay)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(52.dp).clip(rounded(12.dp)).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Link, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                if (youtube) "YouTube links aren't supported yet" else "Play this link",
                style = MaterialTheme.typography.bodyLarge,
                color = if (youtube) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.onSurface,
            )
            Text(
                url,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
