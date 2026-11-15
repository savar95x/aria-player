package dev.savar.aria.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ThemeMode { System, Light, Dark }

data class AppSettings(
    val themeMode: ThemeMode = ThemeMode.System,
    val amoled: Boolean = true,
    val dynamicColor: Boolean = true,
    /** Google Fonts family name, or null for the system font. */
    val font: String? = null,
    val songSort: SongSort = SongSort.Title,
    val albumSort: AlbumSort = AlbumSort.Title,
    val artistSort: ArtistSort = ArtistSort.Name,
    val folders: FolderFilter = FolderFilter(),
    /** [Album.pinKey]s, shown first in the Albums tab. */
    val pinnedAlbums: Set<String> = emptySet(),
    /** Corner roundness: 0 = square, 1 = default, 2 = extra round. */
    val cornerScale: Float = 1f,
    /** Text size multiplier (1 = default). */
    val textScale: Float = 1f,
    /** Added to every text style's weight, in steps of 100 (e.g. -100 = a notch lighter). */
    val weightDelta: Int = 0,
    /** Extra space between characters, in sp ("px" in the UI), -5..+5 (0 = the font's own spacing). */
    val letterSpacing: Float = 0f,
)

private val Context.dataStore by preferencesDataStore("settings")

class SettingsStore(context: Context, private val scope: CoroutineScope) {
    private val store = context.applicationContext.dataStore

    private object Keys {
        val theme = stringPreferencesKey("theme")
        val amoled = booleanPreferencesKey("amoled")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val font = stringPreferencesKey("font")
        val sort = stringPreferencesKey("song_sort")
        val albumSort = stringPreferencesKey("album_sort")
        val artistSort = stringPreferencesKey("artist_sort")
        val include = stringSetPreferencesKey("folders_include")
        val exclude = stringSetPreferencesKey("folders_exclude")
        val pinned = stringSetPreferencesKey("pinned_albums")
        val corners = floatPreferencesKey("corner_scale")
        val textScale = floatPreferencesKey("text_scale")
        val weightDelta = intPreferencesKey("weight_delta")
        val letterSpacing = floatPreferencesKey("letter_spacing_sp")
    }

    /** Null until the first read finishes, so the UI can avoid a theme flash. */
    val settings: StateFlow<AppSettings?> = store.data
        .map { it.toSettings() }
        .stateIn(scope, SharingStarted.Eagerly, null)

    val folderFilter = store.data
        .map { FolderFilter(it[Keys.include].orEmpty(), it[Keys.exclude].orEmpty()) }
        .distinctUntilChanged()

    private fun Preferences.toSettings() = AppSettings(
        themeMode = this[Keys.theme]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: ThemeMode.System,
        amoled = this[Keys.amoled] ?: true,
        dynamicColor = this[Keys.dynamic] ?: true,
        font = this[Keys.font]?.takeIf { it.isNotBlank() },
        songSort = this[Keys.sort]?.let { runCatching { SongSort.valueOf(it) }.getOrNull() } ?: SongSort.Title,
        albumSort = this[Keys.albumSort]?.let { runCatching { AlbumSort.valueOf(it) }.getOrNull() } ?: AlbumSort.Title,
        artistSort = this[Keys.artistSort]?.let { runCatching { ArtistSort.valueOf(it) }.getOrNull() } ?: ArtistSort.Name,
        folders = FolderFilter(this[Keys.include].orEmpty(), this[Keys.exclude].orEmpty()),
        pinnedAlbums = this[Keys.pinned].orEmpty(),
        cornerScale = this[Keys.corners] ?: 1f,
        textScale = this[Keys.textScale] ?: 1f,
        weightDelta = this[Keys.weightDelta] ?: 0,
        letterSpacing = this[Keys.letterSpacing] ?: 0f,
    )

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { store.edit { block(it) } }
    }

    fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    fun setAmoled(on: Boolean) = edit { it[Keys.amoled] = on }
    fun setDynamicColor(on: Boolean) = edit { it[Keys.dynamic] = on }
    fun setFont(name: String?) = edit { if (name.isNullOrBlank()) it.remove(Keys.font) else it[Keys.font] = name.trim() }
    fun setSongSort(sort: SongSort) = edit { it[Keys.sort] = sort.name }
    fun setAlbumSort(sort: AlbumSort) = edit { it[Keys.albumSort] = sort.name }
    fun setArtistSort(sort: ArtistSort) = edit { it[Keys.artistSort] = sort.name }
    fun setCornerScale(scale: Float) = edit { it[Keys.corners] = scale.coerceIn(0f, 2f) }
    fun setTextScale(scale: Float) = edit { it[Keys.textScale] = scale.coerceIn(0.8f, 1.3f) }
    fun setWeightDelta(delta: Int) = edit { it[Keys.weightDelta] = delta.coerceIn(-300, 300) }
    fun setLetterSpacing(sp: Float) = edit { it[Keys.letterSpacing] = sp.coerceIn(-5f, 5f) }
    /** Typeface, size, weight and letter spacing back to defaults. */
    fun resetFontSettings() = edit { it.remove(Keys.font); it.remove(Keys.textScale); it.remove(Keys.weightDelta); it.remove(Keys.letterSpacing) }

    fun setFolderRule(path: String, rule: FolderRule) = edit {
        val include = it[Keys.include].orEmpty() - path
        val exclude = it[Keys.exclude].orEmpty() - path
        it[Keys.include] = if (rule == FolderRule.Include) include + path else include
        it[Keys.exclude] = if (rule == FolderRule.Exclude) exclude + path else exclude
    }

    fun togglePinned(album: Album) = edit {
        val pinned = it[Keys.pinned].orEmpty()
        it[Keys.pinned] = if (album.pinKey in pinned) pinned - album.pinKey else pinned + album.pinKey
    }

    fun clearFolderRules() = edit {
        it.remove(Keys.include)
        it.remove(Keys.exclude)
    }
}
