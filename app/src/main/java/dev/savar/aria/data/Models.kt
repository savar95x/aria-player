package dev.savar.aria.data

import android.content.ContentUris
import android.net.Uri
import android.provider.MediaStore
import androidx.compose.runtime.Immutable
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

@Immutable
data class Song(
    val id: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String?,
    val genre: String?,
    val durationMs: Long,
    val path: String,
    val track: Int,
    val year: Int,
    val dateAdded: Long,
    val dateModified: Long,
) {
    val uri: Uri get() = songUri(id)
    val folder: String get() = path.substringBeforeLast('/', "")
    val art: SongArt get() = SongArt(uri, dateModified)

    fun toMediaItem(): MediaItem = MediaItem.Builder()
        .setMediaId(id.toString())
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setArtist(artist)
                .setAlbumTitle(album)
                .setAlbumArtist(albumArtist)
                // Resolved by SongArtBitmapLoader via MediaStore thumbnails.
                .setArtworkUri(uri)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .build(),
        )
        .build()
}

fun songUri(id: Long): Uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id)

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val art: SongArt get() = songs.first().art
}

@Immutable
data class Artist(
    val name: String,
    val songs: List<Song>,
    val albumCount: Int,
) {
    val art: SongArt get() = songs.first().art
}

/** Coil model for embedded/album art of a song; [version] busts the cache after tag edits. */
data class SongArt(val uri: Uri, val version: Long, val sizePx: Int = 512)

enum class SongSort(val label: String) {
    Title("Title"),
    Artist("Artist"),
    Album("Album"),
    DateAdded("Recently added"),
}

fun List<Song>.sortSongs(sort: SongSort): List<Song> = when (sort) {
    SongSort.Title -> sortedBy { it.title.lowercase() }
    SongSort.Artist -> sortedWith(compareBy({ it.artist.lowercase() }, { it.album.lowercase() }, { it.track }))
    SongSort.Album -> sortedWith(compareBy({ it.album.lowercase() }, { it.track }))
    SongSort.DateAdded -> sortedByDescending { it.dateAdded }
}
