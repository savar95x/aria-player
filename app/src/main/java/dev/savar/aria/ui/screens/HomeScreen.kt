@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalMaterial3ExpressiveApi::class)

// Home: the search pill, and the Songs / Albums / Artists tabs with their sorts and fast scrollers.
// Also the list helpers the other library pages share (listBottomPadding, NavBarSpacer, ArtistRow).

package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.pill
import dev.savar.aria.ui.theme.albumArtShape
import dev.savar.aria.ui.components.ArtistImage
import dev.savar.aria.ui.theme.rounded
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.statusBarsPadding
import dev.savar.aria.ui.sharedContainer
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Sort
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
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
import dev.savar.aria.data.Album
import dev.savar.aria.data.Artist
import dev.savar.aria.data.Song
import dev.savar.aria.data.SongSort
import dev.savar.aria.data.AlbumSort
import dev.savar.aria.data.ArtistSort
import dev.savar.aria.data.SortOption
import dev.savar.aria.ui.LocalBottomPadding
import dev.savar.aria.ui.LocalDockInset
import dev.savar.aria.ui.PlayerUiState
import dev.savar.aria.ui.PlayerViewModel
import dev.savar.aria.ui.components.ArtImage
import dev.savar.aria.ui.components.SongActions
import dev.savar.aria.ui.components.SongRow

enum class HomeTab(val label: String) { Songs("Songs"), Albums("Albums"), Artists("Artists") }

@Composable
internal fun listBottomPadding(): PaddingValues = PaddingValues(bottom = LocalBottomPadding.current)

@Composable
internal fun NavBarSpacer() = Spacer(Modifier.windowInsetsBottomHeight(WindowInsets.navigationBars))

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
    val settings by container.settings.settings.collectAsStateWithLifecycle()
    val sort = settings?.songSort ?: SongSort.Title
    val sorted by container.library.sortedSongs.collectAsStateWithLifecycle()
    val albumSort = settings?.albumSort ?: AlbumSort.Title
    val sortedAlbums by container.library.sortedAlbums.collectAsStateWithLifecycle()
    val artistSort = settings?.artistSort ?: ArtistSort.Name
    val sortedArtists by container.library.sortedArtists.collectAsStateWithLifecycle()

    // Hoisted so each tab keeps its scroll position when you switch away and back.
    val songsList = rememberLazyListState()
    val albumsGrid = rememberLazyGridState()
    val artistsList = rememberLazyListState()
    val bg = MaterialTheme.colorScheme.background

    Scaffold(
        containerColor = bg,
        topBar = { SearchPill(onSearch = onSearch, onSettings = onSettings) },
    ) { padding ->
        Box(Modifier.padding(top = padding.calculateTopPadding()).fillMaxSize()) {
            when {
                songs == null -> LoadingIndicator(Modifier.align(Alignment.Center))
                songs!!.isEmpty() -> EmptyLibrary(onSettings)
                // Material "fade through" between top-level destinations.
                else -> AnimatedContent(targetState = tab, transitionSpec = { Motion.fadeThrough() }, label = "homeTab") { t ->
                    when (t) {
                        HomeTab.Songs -> SongsTab(songsList, sorted, sort, player, vm, actions, onSort = container.settings::setSongSort)
                        HomeTab.Albums -> AlbumsTab(
                            albumsGrid,
                            sortedAlbums,
                            albumSort,
                            settings?.pinnedAlbums.orEmpty(),
                            onAlbum,
                            container.settings::togglePinned,
                            onSort = container.settings::setAlbumSort,
                        )
                        HomeTab.Artists -> ArtistsTab(artistsList, sortedArtists, artistSort, onArtist, container.settings::setArtistSort)
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
        FilledTonalButton(shape = pill(), onClick = onSettings) { Text("Folder settings") }
    }
}

/** Google-style search bar: one rounded surface, search and settings at matching weight. */
@Composable
private fun SearchPill(onSearch: () -> Unit, onSettings: () -> Unit) {
    PillRow(Modifier.clickable(onClick = onSearch)) {
        Spacer(Modifier.width(12.dp))
        Icon(Icons.Rounded.Search, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.width(14.dp))
        Text(
            "Search or paste a link",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onSettings) {
            Icon(Icons.Outlined.Settings, "Settings", tint = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/** The pill shape shared by the home search bar and the search screen, so one morphs into the other. */
@Composable
internal fun PillRow(modifier: Modifier = Modifier, content: @Composable RowScope.() -> Unit) {
    Row(
        Modifier
            .statusBarsPadding()
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .height(56.dp)
            .sharedContainer("search-pill")
            .clip(pill())
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .then(modifier)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        content = content,
    )
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
            // Light, text-weight controls: the list should be the loudest thing on screen.
            Row(
                Modifier.fillMaxWidth().padding(start = 4.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                TextButton(shape = pill(), onClick = { vm.play(songs, shuffle = true) }) {
                    Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Shuffle all")
                    Text(
                        "  ·  ${songs.size}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Spacer(Modifier.weight(1f))
                SortMenu(sort, SongSort.entries, onSort)
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
    // Track runs to the visible bottom: the dock's top edge (measured), or the system nav bar without one.
    .padding(top = 4.dp, bottom = LocalDockInset.current)
    .then(if (LocalDockInset.current == 0.dp) Modifier.navigationBarsPadding() else Modifier)

@Composable
private fun <T : SortOption> SortMenu(sort: T, options: List<T>, onSort: (T) -> Unit) {
    var open by remember { mutableStateOf(false) }
    Box {
        TextButton(shape = pill(), 
            onClick = { open = true },
            colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) {
            Text(sort.label)
            Spacer(Modifier.width(6.dp))
            Icon(Icons.AutoMirrored.Rounded.Sort, "Sort", Modifier.size(18.dp))
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            options.forEach { option ->
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
    sort: AlbumSort,
    pinnedKeys: Set<String>,
    onAlbum: (Long) -> Unit,
    onTogglePin: (Album) -> Unit,
    onSort: (AlbumSort) -> Unit,
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
        item(key = "sort", span = { GridItemSpan(maxLineSpan) }) {
            CountSortRow("${albums.size} albums") { SortMenu(sort, AlbumSort.entries, onSort) }
        }
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
                            albumArtShape(),
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
                    // Same title/subtitle styles as the song and artist rows, so the tabs read alike
                    // (titleSmall + bodySmall here looked smaller and heavier than the other tabs).
                    Text(e.album.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(
                        e.album.artist,
                        style = MaterialTheme.typography.bodyMedium,
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
        label = { i ->
            (entries.getOrNull(i - 1) as? GridEntry.Item)?.album?.let { a ->
                when (sort) {
                    AlbumSort.Title -> a.title.scrollInitial()
                    AlbumSort.Artist -> a.artist.scrollInitial()
                    AlbumSort.Year -> a.year.takeIf { it > 0 }?.toString()
                    AlbumSort.DateAdded -> null
                }
            }
        },
        modifier = scrollerModifier(),
    )
    }
}

@Composable
private fun ArtistsTab(
    state: LazyListState,
    artists: List<Artist>,
    sort: ArtistSort,
    onArtist: (String) -> Unit,
    onSort: (ArtistSort) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        LazyColumn(Modifier.fillMaxSize(), state = state, contentPadding = listBottomPadding()) {
            item(key = "sort") {
                CountSortRow("${artists.size} artists", Modifier.padding(horizontal = 16.dp)) {
                    SortMenu(sort, ArtistSort.entries, onSort)
                }
            }
            items(artists, key = { it.name }) { artist ->
                ArtistRow(artist, Modifier.animateItem()) { onArtist(artist.name) }
            }
            item { NavBarSpacer() }
        }
        ListFastScroller(
            state,
            { i -> if (sort == ArtistSort.Name) artists.getOrNull(i - 1)?.name?.scrollInitial() else null },
            scrollerModifier(),
        )
    }
}

/** Quiet header above a list: how many there are, and how they're sorted. */
@Composable
private fun CountSortRow(count: String, modifier: Modifier = Modifier, sortMenu: @Composable () -> Unit) {
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(count, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.weight(1f))
        sortMenu()
    }
}

@Composable
internal fun ArtistRow(artist: Artist, modifier: Modifier = Modifier, onClick: () -> Unit) {
    Row(
        modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        ArtistImage(artist, Modifier.sharedArt("artist-${artist.name}").size(52.dp))
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
internal fun BackTopBar(title: String = "", onBack: () -> Unit, actions: @Composable () -> Unit = {}) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
        actions = { actions() },
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
    )
}
