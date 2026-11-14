package dev.savar.aria.data

import android.content.Context
import android.database.Cursor
import android.provider.MediaStore
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Stateless MediaStore reads, usable from both the UI process and the player process. */
object MediaStoreQuery {
    suspend fun songsByIds(context: Context, ids: List<Long>): List<Song> = withContext(Dispatchers.IO) {
        if (ids.isEmpty()) return@withContext emptyList()
        val byId = ids.chunked(500).flatMap { chunk ->
            query(context, "${MediaStore.Audio.Media._ID} IN (${chunk.joinToString(",")})")
        }.associateBy { it.id }
        ids.mapNotNull { byId[it] }
    }

    fun query(context: Context, extraSelection: String?): List<Song> {
        val projection = arrayOf(
            MediaStore.Audio.Media._ID,
            MediaStore.Audio.Media.TITLE,
            MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM,
            MediaStore.Audio.Media.ALBUM_ID,
            MediaStore.Audio.Media.ALBUM_ARTIST,
            MediaStore.Audio.Media.GENRE,
            MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.DATA,
            MediaStore.Audio.Media.TRACK,
            MediaStore.Audio.Media.YEAR,
            MediaStore.Audio.Media.DATE_ADDED,
            MediaStore.Audio.Media.DATE_MODIFIED,
        )
        val selection = buildString {
            append("${MediaStore.Audio.Media.IS_MUSIC} != 0")
            if (extraSelection != null) append(" AND ($extraSelection)")
        }
        return try {
            context.contentResolver.query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, projection, selection, null, null,
            )?.use { it.readSongs() }.orEmpty()
        } catch (e: SecurityException) {
            Log.w("Aria", "No audio permission yet", e)
            emptyList()
        }
    }

    private fun Cursor.readSongs(): List<Song> {
        val id = getColumnIndexOrThrow(MediaStore.Audio.Media._ID)
        val title = getColumnIndexOrThrow(MediaStore.Audio.Media.TITLE)
        val artist = getColumnIndexOrThrow(MediaStore.Audio.Media.ARTIST)
        val album = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM)
        val albumId = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ID)
        val albumArtist = getColumnIndexOrThrow(MediaStore.Audio.Media.ALBUM_ARTIST)
        val genre = getColumnIndexOrThrow(MediaStore.Audio.Media.GENRE)
        val duration = getColumnIndexOrThrow(MediaStore.Audio.Media.DURATION)
        val data = getColumnIndexOrThrow(MediaStore.Audio.Media.DATA)
        val track = getColumnIndexOrThrow(MediaStore.Audio.Media.TRACK)
        val year = getColumnIndexOrThrow(MediaStore.Audio.Media.YEAR)
        val added = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_ADDED)
        val modified = getColumnIndexOrThrow(MediaStore.Audio.Media.DATE_MODIFIED)
        val out = ArrayList<Song>(count)
        while (moveToNext()) {
            val path = getString(data) ?: continue
            out += Song(
                id = getLong(id),
                title = getString(title)?.takeIf { it.isNotBlank() } ?: path.substringAfterLast('/').substringBeforeLast('.'),
                artist = getString(artist).cleanUnknown() ?: "Unknown artist",
                album = getString(album).cleanUnknown() ?: "Unknown album",
                albumId = getLong(albumId),
                albumArtist = getString(albumArtist).cleanUnknown(),
                genre = getString(genre).cleanUnknown(),
                durationMs = getLong(duration),
                path = path,
                // MediaStore encodes disc as thousands (1003 = disc 1, track 3); keep for ordering.
                track = getInt(track),
                year = getInt(year),
                dateAdded = getLong(added),
                dateModified = getLong(modified),
            )
        }
        return out
    }

    private fun String?.cleanUnknown(): String? =
        this?.trim()?.takeIf { it.isNotEmpty() && it != MediaStore.UNKNOWN_STRING }
}
