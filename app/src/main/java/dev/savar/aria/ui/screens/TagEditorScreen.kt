@file:OptIn(ExperimentalMaterial3Api::class)

package dev.savar.aria.ui.screens

import dev.savar.aria.ui.theme.pill
import dev.savar.aria.ui.theme.rounded
import android.app.Activity
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.ExperimentalMaterial3ExpressiveApi
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil3.compose.AsyncImage
import dev.savar.aria.AppContainer
import dev.savar.aria.data.Song
import dev.savar.aria.metadata.EditableTags
import dev.savar.aria.metadata.MetadataSearch
import dev.savar.aria.metadata.TagWriter
import dev.savar.aria.metadata.TrackMatch
import dev.savar.aria.ui.LocalBottomPadding
import dev.savar.aria.ui.components.ArtImage
import kotlinx.coroutines.launch

private fun Song.toTags() = EditableTags(
    title = title,
    artist = artist.takeUnless { it == "Unknown artist" }.orEmpty(),
    album = album.takeUnless { it == "Unknown album" }.orEmpty(),
    albumArtist = albumArtist.orEmpty(),
    year = year.takeIf { it > 0 }?.toString().orEmpty(),
    genre = genre.orEmpty(),
    track = (track % 1000).takeIf { it > 0 }?.toString().orEmpty(),
)

@Composable
fun TagEditorScreen(songId: Long, container: AppContainer, onBack: () -> Unit) {
    val song = remember(songId) { container.library.song(songId) }
    if (song == null) {
        LaunchedEffect(Unit) { onBack() }
        return
    }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbar = remember { SnackbarHostState() }

    var tags by remember { mutableStateOf(song.toTags()) }
    var newArtUrl by remember { mutableStateOf<String?>(null) }
    var newArtThumb by remember { mutableStateOf<String?>(null) }
    var saving by remember { mutableStateOf(false) }
    var showSearch by remember { mutableStateOf(false) }

    fun save() {
        saving = true
        scope.launch {
            val result = runCatching {
                val art = newArtUrl?.let { MetadataSearch.download(it) }
                TagWriter.write(context, song, tags, art)
            }
            saving = false
            if (result.isSuccess) {
                onBack()
            } else {
                snackbar.showSnackbar("Couldn't save: ${result.exceptionOrNull()?.message ?: "unknown error"}")
            }
        }
    }

    // Android asks the user once per file before another app's audio can be modified.
    val writeRequest = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
        if (it.resultCode == Activity.RESULT_OK) save()
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbar) },
        topBar = {
            TopAppBar(
                title = { Text("Edit tags") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") } },
                actions = {
                    if (saving) {
                        CircularProgressIndicator(Modifier.size(24.dp).padding(end = 4.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(16.dp))
                    } else {
                        Button(shape = pill(), 
                            onClick = {
                                val pi = MediaStore.createWriteRequest(context.contentResolver, listOf(song.uri))
                                writeRequest.launch(IntentSenderRequest.Builder(pi.intentSender).build())
                            },
                            enabled = TagWriter.canWrite(song),
                            modifier = Modifier.padding(end = 8.dp),
                        ) { Text("Save") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
            )
        },
    ) { padding ->
        Column(
            Modifier
                .padding(top = padding.calculateTopPadding())
                .fillMaxSize()
                .imePadding()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(112.dp).clip(rounded(18.dp))) {
                    // Old cover stays underneath until the new one has loaded, so it never blanks.
                    ArtImage(song.art.copy(sizePx = 320), Modifier.fillMaxSize(), rounded(18.dp))
                    if (newArtThumb != null) {
                        AsyncImage(newArtThumb, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                    }
                }
                Spacer(Modifier.width(16.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        song.path.substringAfterLast('/'),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Spacer(Modifier.height(8.dp))
                    FilledTonalButton(shape = pill(), onClick = { showSearch = true }) {
                        Icon(Icons.Rounded.CloudDownload, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Find online")
                    }
                }
            }
            if (!TagWriter.canWrite(song)) {
                Text(
                    "This file type can't be tagged yet.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            TagField("Title", tags.title) { tags = tags.copy(title = it) }
            TagField("Artist", tags.artist) { tags = tags.copy(artist = it) }
            TagField("Album", tags.album) { tags = tags.copy(album = it) }
            TagField("Album artist", tags.albumArtist) { tags = tags.copy(albumArtist = it) }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TagField("Year", tags.year, Modifier.weight(1f), numeric = true) { tags = tags.copy(year = it) }
                TagField("Track", tags.track, Modifier.weight(1f), numeric = true) { tags = tags.copy(track = it) }
            }
            TagField("Genre", tags.genre) { tags = tags.copy(genre = it) }
            Spacer(Modifier.height(LocalBottomPadding.current + 16.dp))
        }
    }

    if (showSearch) {
        MatchSheet(
            initialQuery = MetadataSearch.cleanQuery(song.title, song.artist),
            onDismiss = { showSearch = false },
            onPick = { m ->
                tags = EditableTags(m.title, m.artist, m.album, m.albumArtist, m.year, m.genre, m.track)
                newArtUrl = m.artworkUrl
                newArtThumb = m.thumbnailUrl
                showSearch = false // the sheet has already slid away
            },
        )
    }
}

@Composable
private fun TagField(
    label: String,
    value: String,
    modifier: Modifier = Modifier.fillMaxWidth(),
    numeric: Boolean = false,
    onChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
            imeAction = ImeAction.Next,
        ),
        modifier = modifier,
    )
}

/**
 * Online match picker. Fixed height and fully expanded from the start, so the sheet never jumps
 * when results arrive; previous results stay put (dimmed) while a new search runs; picking a
 * result slides the sheet away before the editor updates.
 */
@OptIn(ExperimentalMaterial3ExpressiveApi::class)
@Composable
private fun MatchSheet(initialQuery: String, onDismiss: () -> Unit, onPick: (TrackMatch) -> Unit) {
    var query by remember { mutableStateOf(initialQuery) }
    var results by remember { mutableStateOf<List<TrackMatch>?>(null) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val keyboard = LocalSoftwareKeyboardController.current
    val listState = rememberLazyListState()

    fun search() {
        if (query.isBlank()) return
        keyboard?.hide()
        loading = true
        error = null
        scope.launch {
            runCatching { MetadataSearch.search(query) }
                .onSuccess { results = it; listState.scrollToItem(0) }
                .onFailure { error = "Search failed. Check your connection." }
            loading = false
        }
    }
    LaunchedEffect(Unit) { search() }

    fun pick(m: TrackMatch) {
        keyboard?.hide()
        scope.launch { sheetState.hide() }.invokeOnCompletion { onPick(m) }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f).imePadding()) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                singleLine = true,
                label = { Text("Search") },
                trailingIcon = { IconButton(onClick = ::search) { Icon(Icons.Rounded.Search, "Search") } },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { search() }),
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
            )
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().weight(1f)) {
                val list = results
                when {
                    error != null && list.isNullOrEmpty() -> Text(
                        error!!,
                        color = MaterialTheme.colorScheme.error,
                        modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
                    )
                    list != null && list.isEmpty() && !loading -> Text(
                        "No matches. Try fewer words.",
                        modifier = Modifier.align(Alignment.TopCenter).padding(24.dp),
                    )
                    !list.isNullOrEmpty() -> LazyColumn(
                        state = listState,
                        contentPadding = PaddingValues(bottom = 24.dp),
                        modifier = Modifier.fillMaxSize().graphicsLayer { alpha = if (loading) 0.4f else 1f },
                    ) {
                        itemsIndexed(list, key = { i, m -> "$i|${m.title}|${m.album}" }) { _, m ->
                            MatchRow(m, enabled = !loading) { pick(m) }
                        }
                    }
                }
                if (loading) LoadingIndicator(Modifier.align(Alignment.Center))
            }
        }
    }
}

@Composable
private fun MatchRow(m: TrackMatch, enabled: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(enabled = enabled, onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier.size(56.dp).clip(rounded(12.dp)).background(MaterialTheme.colorScheme.surfaceContainerHighest),
        ) {
            AsyncImage(m.thumbnailUrl, null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(m.title, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(
                m.artist,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                listOf(m.album, m.year).filter { it.isNotBlank() }.joinToString(" · "),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
