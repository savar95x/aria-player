package dev.savar.aria.data

import android.content.Context
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
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
    val folders: FolderFilter = FolderFilter(),
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
        val include = stringSetPreferencesKey("folders_include")
        val exclude = stringSetPreferencesKey("folders_exclude")
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
        folders = FolderFilter(this[Keys.include].orEmpty(), this[Keys.exclude].orEmpty()),
    )

    private fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        scope.launch { store.edit { block(it) } }
    }

    fun setTheme(mode: ThemeMode) = edit { it[Keys.theme] = mode.name }
    fun setAmoled(on: Boolean) = edit { it[Keys.amoled] = on }
    fun setDynamicColor(on: Boolean) = edit { it[Keys.dynamic] = on }
    fun setFont(name: String?) = edit { if (name.isNullOrBlank()) it.remove(Keys.font) else it[Keys.font] = name.trim() }
    fun setSongSort(sort: SongSort) = edit { it[Keys.sort] = sort.name }

    fun setFolderRule(path: String, rule: FolderRule) = edit {
        val include = it[Keys.include].orEmpty() - path
        val exclude = it[Keys.exclude].orEmpty() - path
        it[Keys.include] = if (rule == FolderRule.Include) include + path else include
        it[Keys.exclude] = if (rule == FolderRule.Exclude) exclude + path else exclude
    }

    fun clearFolderRules() = edit {
        it.remove(Keys.include)
        it.remove(Keys.exclude)
    }
}
