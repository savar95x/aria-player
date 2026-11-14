package dev.savar.aria.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri

/**
 * Artwork embedded in the file itself, and nothing else.
 *
 * MediaStore thumbnails can "helpfully" borrow a cover from other files that share a guessed album
 * (untagged files get their folder name as album), so untagged songs ended up with someone else's
 * art. Reading the file's own picture means no art is shown rather than the wrong art.
 */
object EmbeddedArt {

    fun bytes(context: Context, uri: Uri): ByteArray? {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, uri)
            retriever.embeddedPicture
        } catch (e: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    fun bitmap(context: Context, uri: Uri, maxPx: Int): Bitmap? {
        val data = bytes(context, uri) ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxPx && bounds.outHeight / (sample * 2) >= maxPx) sample *= 2
        return BitmapFactory.decodeByteArray(data, 0, data.size, BitmapFactory.Options().apply { inSampleSize = sample })
    }
}
