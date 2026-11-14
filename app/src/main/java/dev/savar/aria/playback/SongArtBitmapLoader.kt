package dev.savar.aria.playback

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.provider.MediaStore
import androidx.annotation.OptIn
import androidx.media3.common.util.BitmapLoader
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSourceBitmapLoader
import com.google.common.util.concurrent.ListenableFuture
import dev.savar.aria.data.EmbeddedArt
import com.google.common.util.concurrent.MoreExecutors
import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Notification / lock-screen artwork: song URIs go through MediaStore thumbnails. */
@OptIn(UnstableApi::class)
class SongArtBitmapLoader(private val context: Context) : BitmapLoader {
    private val executor = MoreExecutors.listeningDecorator(Executors.newSingleThreadExecutor())
    private val fallback = DataSourceBitmapLoader(context)

    override fun supportsMimeType(mimeType: String): Boolean = fallback.supportsMimeType(mimeType)

    override fun decodeBitmap(data: ByteArray): ListenableFuture<Bitmap> = fallback.decodeBitmap(data)

    override fun loadBitmap(uri: Uri): ListenableFuture<Bitmap> =
        if (uri.authority == MediaStore.AUTHORITY && uri.path.orEmpty().contains("/audio/media/")) {
            executor.submit(Callable { EmbeddedArt.bitmap(context, uri, 720) ?: error("No embedded art") })
        } else {
            fallback.loadBitmap(uri)
        }
}
