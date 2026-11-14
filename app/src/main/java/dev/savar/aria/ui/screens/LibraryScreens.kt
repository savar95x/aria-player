@file:OptIn(ExperimentalMaterial3Api::class)

package dev.savar.aria.ui.screens

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import dev.savar.aria.ui.components.GridFastScroller
import dev.savar.aria.ui.components.ListFastScroller
import dev.savar.aria.ui.components.scrollInitial
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.animation.AnimatedContent
import dev.savar.aria.ui.Motion
import dev.savar.aria.ui.sharedArt
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.savar.aria.AppContainer
import dev.savar.aria.data.Album
import dev.savar.aria.data.Artist
import dev.savar.aria.data.Song
import dev.savar.aria.data.SongSort
import dev.savar.aria.data.sortSongs
import dev.savar.aria.ui.LocalBottomPadding
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.components.ArtImage
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.components.SongRow
import dev.savar.aria.ui.components.formatDuration

enum class HomeTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists") }

@Composable
private fun listBottomPadding(): PaddingValues = PaddingValues(bottom = LocalBottomPadding.current)

@Composable
private fun NavBarSpacer() = Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))

@Composable
fun HomeScreen(
    container: AppContainer,
    player: PlayerUiState,
    vm: PlayerViewModel,
    actions: SongActions,
    onSearch: () -> Unit,
    onSettings: () -> Unit,
    onAlbum: (Long) -> Unit,
    onArtist: (String) -> Unit,
    tab: HomeTab,
) {
    val songs by container.library.songs.collectAsStateWithLifecycle()
    val albums by container.library.albums.collectAsStateWithLifecycle()
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val sort = settings?.songSort ?: SongSort.Title
    val sorted = remember(songs, sort) { songs.orEmpty().sortSongs(sort) }

    // Hoisted so each tab keeps its scroll position when you switch away and back.
    val songsList = rememberLazyListState()
    val albumsGrid = rememberLazyGridState()
    val artistsList = rememberLazyListState()
    val bg = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = bg,
        topBar = {
            TopAppBar(
                title = { Text(tab.label, fontWeight = FontWeight.SemiBold) },
                actions = {
                    IconButton(onClick = onSearch) { Icon(Icons.Rounded.Search, "Search") }
                    IconButton(onClick = onSettings) { Icon(Icons.Rounded.Settings, "Settings") }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = bg),
            )
        },
    ) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            when {
                songs == null -> CircularProgressIndicator(Modifier.align(Alignment.Center))
                songs!!.isEmpty() -> EmptyLibrary(onSettings)
                // Material "fade through" between top-level destinations.
                else -> AnimatedContent(targetState = tab, transitionSpec = { Motion.fadeThrough() }, label = "homeTab") { t ->
                    when (t) {
                        HomeTab.Songs -> SongsTab(songsList, sorted, sort, player, vm, actions, onSort = container.settings::setSongSort)
                        HomeTab.Albums -> AlbumsTab(
                            albumsGrid,
                            albums,
                            settings?.pinnedAlbums.orEmpty(),
                            onAlbum,
                            container.settings::togglePinned,
                        )
                        HomeTab.Artists -> ArtistsTab(artistsList, artists, onArtist)
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyLibrary(onSettings: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("No music here yet", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(8.dp))
        Text(
            "Copy some songs to your phone, or check which folders are included in settings.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(16.dp))
        FilledTonalButton(onClick = onSettings) { Text("Folder settings") }
    }
}

@Composable
private fun SongsTab(
    state: LazyListState,
    songs: List<Song>,
    sort: SongSort,
    player: PlayerUiState,
    vm: PlayerViewModel,
    actions: SongActions,
    onSort: (SongSort) -> Unit,
) {
    val label: (Int) -> String? = { i ->
        songs.getOrNull(i - 1)?.let { s ->
            when (sort) {
                SongSort.Title -> s.title.scrollInitial()
                SongSort.Artist -> s.artist.scrollInitial()
                SongSort.Album -> s.album.scrollInitial()
                SongSort.DateAdded -> null
            }
        }
    }
    Box(Modifier.fillMaxSize()) {
    LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = listBottomPadding()) {
        item(key = "header") {
            Row(
                Modifier.fillMaxWidth().padding(start = 16.dp, end = 4.dp, top = 4.dp, bottom = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(onClick = { vm.play(songs, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle")
                }
                Spacer(Modifier.width(12.dp))
                Text(
                    "${songs.size} songs",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
                SortMenu(sort, onSort)
            }
        }
        itemsIndexed(songs, key = { _, s -> s.id }) { index, song ->
            SongRow(
                song = song,
                isCurrent = song.id == player.currentId,
                actions = actions,
                onClick = { vm.play(songs, index) },
                modifier = Modifier.animateItem(),
            )
        }
        item { NavBarSpacer() }
    }
    ListFastScroller(state, label, scrollerModifier())
    }
}

@Composable
private fun BoxScope.scrollerModifier(): Modifier = Modifier
    .align(Alignment.TopEnd)
    .padding(top = 4.dp, bottom = LocalBottomPadding.current)
    .navigationBarsPadding()

@Composable
private fun SortMenu(sort: SongSort, onSort: (SongSort) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { open = true }) { Icon(Icons.AutoMirrored.Rounded.Sort, "Sort") }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            SongSort.entries.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option.label) },
                    onClick = { open = false; onSort(option) },
                    trailingIcon = { if (option == sort) Icon(Icons.Rounded.Check, null) },
                )
            }
        }
    }
}

private sealed interface GridEntry {
    data class Header(val title: String) : GridEntry
    data class Item(val album: Album, val pinned: Boolean) : GridEntry
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AlbumsTab(
    state: LazyGridState,
    albums: List<Album>,
    pinnedKeys: Set<String>,
    onAlbum: (Long) -> Unit,
    onTogglePin: (Album) -> Unit,
) {
    val entries = remember(albums, pinnedKeys) {
        val (pinned, rest) = albums.partition { it.pinKey in pinnedKeys }
        if (pinned.isEmpty()) {
            rest.map { GridEntry.Item(it, false) }
        } else {
            listOf(GridEntry.Header("Pinned")) + pinned.map { GridEntry.Item(it, true) } +
                listOf(GridEntry.Header("All albums")) + rest.map { GridEntry.Item(it, false) }
        }
    }
    val haptics = LocalHapticFeedback.current
    Box(Modifier.fillMaxSize()) {
    LazyVerticalGrid(
        state = state,
        columns = GridCells.Adaptive(150.dp),
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = LocalBottomPadding.current),
        horizontalArrangement = Arrangement.spacedBy(14.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        items(
            entries,
            key = { e -> if (e is GridEntry.Item) e.album.id else "h_" + (e as GridEntry.Header).title },
            span = { e -> if (e is GridEntry.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1) },
        ) { e ->
            when (e) {
                is GridEntry.Header -> Text(
                    e.title,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.animateItem().padding(top = 4.dp),
                )
                is GridEntry.Item -> Column(
                    Modifier
                        .animateItem()
                        .clip(RoundedCornerShape(18.dp))
                        .combinedClickable(
                            onClick = { onAlbum(e.album.id) },
                            onLongClick = {
                                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                                onTogglePin(e.album)
                            },
                        ),
                ) {
                    Box {
                        ArtImage(
                            e.album.art.copy(sizePx = 400),
                            Modifier.sharedArt("album-${e.album.id}").fillMaxWidth().aspectRatio(1f),
                            RoundedCornerShape(18.dp),
                        )
                        if (e.pinned) {
                            Icon(
                                Icons.Rounded.PushPin,
                                "Pinned",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier
                                    .align(Alignment.TopEnd)
                                    .padding(8.dp)
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primaryContainer)
                                    .padding(5.dp),
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(e.album.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        e.album.artist,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        item(span = { GridItemSpan(maxLineSpan) }) { NavBarSpacer() }
    }
    GridFastScroller(
        state,
        label = { i -> (entries.getOrNull(i) as? GridEntry.Item)?.album?.title?.scrollInitial() },
        modifier = scrollerModifier(),
    )
    }
}

@Composable
private fun ArtistsTab(state: LazyListState, artists: List<Artist>, onArtist: (String) -> Unit) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = listBottomPadding()) {
            items(artists, key = { it.name }) { artist -> ArtistRow(artist) { onArtist(artist.name) } }
            item { NavBarSpacer() }
        }
        ListFastScroller(state, { i -> artists.getOrNull(i)?.name?.scrollInitial() }, scrollerModifier())
    }
}

@Composable
private fun ArtistRow(artist: Artist, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtImage(artist.art.copy(sizePx = 160), Modifier.sharedArt("artist-${artist.name}").size(52.dp), CircleShape)
        Spacer(Modifier.width(14.dp))
        Column {
            Text(artist.name, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                "${artist.songs.size} songs · ${artist.albumCount} albums",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun BackTopBar(title: String = "", onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}

@Composable
private fun PlayShuffleRow(onPlay: () -> Unit, onShuffle: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        Button(onClick = onPlay) {
            Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
            Spacer(Modifier.width(6.dp))
            Text("Play")
        }
        FilledTonalButton(onClick = onShuffle) {
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
    val album = albums.firstOrNull { it.id == albumId }
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
                        RoundedCornerShape(24.dp),
                        placeholder = album.art.copy(sizePx = 400),
                    )
                    Spacer(Modifier.height(20.dp))
                    Text(album.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
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
) {
    val artists by container.library.artists.collectAsStateWithLifecycle()
    val artist = artists.firstOrNull { it.name == name }
    Scaffold(containerColor = MaterialTheme.colorScheme.background, topBar = { BackTopBar(onBack = onBack) }) { padding ->
        if (artist == null) return@Scaffold
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(), contentPadding = listBottomPadding()) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    ArtImage(
                        artist.art.copy(sizePx = 480),
                        Modifier.sharedArt("artist-${artist.name}").size(168.dp),
                        CircleShape,
                        placeholder = artist.art.copy(sizePx = 160),
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
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                title = {
                    TextField(
                        value = query,
                        onValueChange = { query = it },
                        placeholder = { Text("Songs, albums, artists") },
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
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        LazyColumn(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize(), contentPadding = listBottomPadding()) {
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
                        ArtImage(album.art.copy(sizePx = 160), Modifier.sharedArt("album-${album.id}").size(52.dp))
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
private fun SectionHeader(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 16.dp, top = 16.dp, bottom = 4.dp),
    )
}
