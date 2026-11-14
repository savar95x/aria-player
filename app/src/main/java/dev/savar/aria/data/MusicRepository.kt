package dev.savar.aria.data

import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The local library, straight from MediaStore, filtered by the folder rules. */
@OptIn(FlowPreview::class)
class MusicRepository(
    private val context: Context,
    settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _allSongs = MutableStateFlow<List<Song>?>(null)

    /** Every song MediaStore knows about, ignoring folder rules (used by the folder picker). */
    val allSongs: StateFlow<List<Song>?> = _allSongs.asStateFlow()

    /** Songs that pass the folder rules. Null while the first scan is running. */
    val songs: StateFlow<List<Song>?> = combine(_allSongs, settings.folderFilter) { all, filter ->
        all?.filter { filter.allows(it.folder) }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, null)

    val albums: StateFlow<List<Album>> = songs.map { list ->
        list.orEmpty()
            .groupBy { it.albumId }
            .map { (id, tracks) ->
                val sorted = tracks.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
                Album(
                    id = id,
                    title = sorted.first().album,
                    artist = sorted.firstNotNullOfOrNull { it.albumArtist }
                        ?: sorted.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                    year = sorted.maxOf { it.year },
                    songs = sorted,
                )
            }
            .sortedBy { it.title.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    val artists: StateFlow<List<Artist>> = songs.map { list ->
        list.orEmpty()
            .groupBy { it.artist }
            .map { (name, tracks) ->
                Artist(
                    name = name,
                    songs = tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.track })),
                    albumCount = tracks.distinctBy { it.albumId }.size,
                )
            }
            .sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    private val refreshRequests = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    private var observing = false

    init {
        scope.launch {
            refreshRequests.debounce(400).collect {
                _allSongs.value = withContext(Dispatchers.IO) { MediaStoreQuery.query(context, null) }
            }
        }
    }

    /** Call once the audio permission is granted; afterwards MediaStore changes refresh automatically. */
    fun refresh() {
        if (!observing) {
            observing = true
            context.contentResolver.registerContentObserver(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                true,
                object : ContentObserver(Handler(Looper.getMainLooper())) {
                    override fun onChange(selfChange: Boolean) {
                        refreshRequests.tryEmit(Unit)
                    }
                },
            )
            scope.launch { _allSongs.value = withContext(Dispatchers.IO) { MediaStoreQuery.query(context, null) } }
            return
        }
        refreshRequests.tryEmit(Unit)
    }

    fun song(id: Long): Song? = _allSongs.value?.firstOrNull { it.id == id }

    /** Direct lookup, independent of the cached library (used when restoring the queue). */
    suspend fun songsByIds(ids: List<Long>): List<Song> = MediaStoreQuery.songsByIds(context, ids)
}
