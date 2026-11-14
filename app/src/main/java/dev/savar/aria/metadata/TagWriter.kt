package dev.savar.aria.metadata

import android.content.Context
import android.media.MediaScannerConnection
import dev.savar.aria.data.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.images.ArtworkFactory
import java.io.File
import java.util.logging.Level
import java.util.logging.Logger

data class EditableTags(
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val year: String = "",
    val genre: String = "",
    val track: String = "",
)

/**
 * Writes tags + cover art into the audio file. Works on a private copy (jaudiotagger needs a
 * real file), then streams it back through MediaStore, which the user approved via a write request.
 */
object TagWriter {
    val supportedExtensions = setOf("mp3", "m4a", "mp4", "aac", "flac", "ogg", "wav", "aif", "aiff", "wma", "dsf")

    init {
        TagOptionSingleton.getInstance().isAndroid = true
        Logger.getLogger("org.jaudiotagger").level = Level.OFF
    }

    fun canWrite(song: Song) = song.path.substringAfterLast('.').lowercase() in supportedExtensions

    suspend fun write(context: Context, song: Song, tags: EditableTags, artwork: ByteArray?) = withContext(Dispatchers.IO) {
        val ext = song.path.substringAfterLast('.').lowercase()
        val tmp = File(context.cacheDir, "tagedit_${song.id}.$ext")
        val resolver = context.contentResolver
        try {
            resolver.openInputStream(song.uri)!!.use { input -> tmp.outputStream().use { input.copyTo(it) } }

            val file = AudioFileIO.read(tmp)
            val tag = file.tagOrCreateAndSetDefault
            fun set(key: FieldKey, value: String) {
                val v = value.trim()
                if (v.isEmpty()) runCatching { tag.deleteField(key) } else tag.setField(key, v)
            }
            set(FieldKey.TITLE, tags.title)
            set(FieldKey.ARTIST, tags.artist)
            set(FieldKey.ALBUM, tags.album)
            set(FieldKey.ALBUM_ARTIST, tags.albumArtist)
            set(FieldKey.YEAR, tags.year)
            set(FieldKey.GENRE, tags.genre)
            set(FieldKey.TRACK, tags.track)
            if (artwork != null) {
                tag.deleteArtworkField()
                val art = ArtworkFactory.getNew()
                art.setBinaryData(artwork)
                art.setMimeType(if (artwork.isPng()) "image/png" else "image/jpeg")
                art.setPictureType(3) // front cover
                tag.setField(art)
            }
            file.commit()

            resolver.openOutputStream(song.uri, "wt")!!.use { out -> tmp.inputStream().use { it.copyTo(out) } }
        } finally {
            tmp.delete()
        }
        MediaScannerConnection.scanFile(context, arrayOf(song.path), null, null)
    }

    private fun ByteArray.isPng() = size > 4 && this[0] == 0x89.toByte() && this[1] == 'P'.code.toByte()
}
