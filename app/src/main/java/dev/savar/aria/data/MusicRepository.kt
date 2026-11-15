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
            .groupBy { albumKey(it) }
            .map { (_, tracks) ->
                val sorted = tracks.sortedWith(compareBy({ it.track }, { it.title.lowercase() }))
                Album(
                    // MediaStore can split one album into several ids (e.g. files in different
                    // folders); the merged album keeps the id most of its songs have.
                    id = tracks.groupingBy { it.albumId }.eachCount().maxBy { it.value }.key,
                    title = sorted.first().album,
                    artist = sorted.firstNotNullOfOrNull { it.albumArtist }
                        ?: sorted.groupingBy { it.artist }.eachCount().maxBy { it.value }.key,
                    year = sorted.maxOf { it.year },
                    songs = sorted,
                )
            }
            .sortedBy { it.title.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * Which album a song belongs to: album name + album artist (or the lead artist when there's no
     * album-artist tag), not MediaStore's album id, which splits albums whose files live in
     * different folders. Untitled albums stay apart by id so they don't merge into one heap.
     */
    private fun albumKey(song: Song): String {
        val title = song.album.trim().lowercase()
        if (title.isEmpty() || title == "unknown album" || title == "<unknown>") return "id:${song.albumId}"
        val by = song.albumArtist?.takeIf { it.isNotBlank() }
            ?: song.artist.split('/', ';', ',', '&').first().split(leadArtistSplit).first()
        return title + "\u0000" + by.trim().lowercase()
    }


    /** Every full artist tag in the library, lower-cased: used to tell real artists from joined credits. */
    @Volatile
    private var knownArtists: Set<String> = emptySet()

    /**
     * Artists tab only: songs are grouped under their main artist, so "Frank Ocean/Earl Sweatshirt"
     * and "J. Cole & Future" sit under Frank Ocean and J. Cole. Everywhere else shows the full tag.
     */
    val artists: StateFlow<List<Artist>> = songs.map { list ->
        val all = list.orEmpty()
        knownArtists = all.mapTo(HashSet()) { it.artist.trim().lowercase() }
        all.groupBy { mainArtist(it.artist).lowercase() }
            .map { (_, tracks) ->
                // Most common spelling of the main artist wins ("Kanye West" over "kanye west").
                val name = tracks.groupingBy { mainArtist(it.artist) }.eachCount().maxBy { it.value }.key
                Artist(
                    name = name,
                    songs = tracks.sortedWith(compareBy({ it.album.lowercase() }, { it.track })),
                    albumCount = tracks.distinctBy { albumKey(it) }.size,
                )
            }
            .sortedBy { it.name.lowercase() }
    }.flowOn(Dispatchers.Default).stateIn(scope, SharingStarted.Eagerly, emptyList())

    /**
     * The main artist of a tag. "/" and ";" always separate credits. Commas, "&", "x" and "feat."
     * only do when the first name is itself an artist in your library, so "Simon & Garfunkel" or
     * "Tyler, The Creator" stay whole while "J. Cole & Future" goes under J. Cole.
     */
    fun mainArtist(raw: String): String {
        val tag = raw.trim()
        val bySlash = tag.split('/', ';').first().trim()
        if (bySlash.isNotEmpty() && bySlash != tag) return bySlash
        val first = tag.split(joinedCredit).first().trim()
        return if (first.isNotEmpty() && first != tag && first.lowercase() in knownArtists) first else tag
    }


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

    private companion object {
        val leadArtistSplit = Regex("\\s+(feat\\.?|ft\\.?|featuring|x)\\s+", RegexOption.IGNORE_CASE)
        val joinedCredit = Regex("""\s*(?:,|&|\bx\b|\bfeat\.?|\bft\.?|\bfeaturing\b)\s*""", RegexOption.IGNORE_CASE)
    }
}
