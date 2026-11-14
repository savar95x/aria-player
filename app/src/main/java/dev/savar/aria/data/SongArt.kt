package dev.savar.aria.data

import coil3.ImageLoader
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okio.Buffer
import java.io.FileNotFoundException

/** Loads a song's embedded cover; Coil handles downsampling and memory caching. */
class SongArtFetcher(private val data: SongArt, private val options: Options) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bytes = EmbeddedArt.bytes(options.context, data.uri)
            ?: throw FileNotFoundException("No embedded art in ${data.uri}")
        SourceFetchResult(
            source = ImageSource(Buffer().write(bytes), options.fileSystem),
            mimeType = null,
            dataSource = DataSource.DISK,
        )
    }

    class Factory : Fetcher.Factory<SongArt> {
        override fun create(data: SongArt, options: Options, imageLoader: ImageLoader): Fetcher =
            SongArtFetcher(data, options)
    }
}

class SongArtKeyer : Keyer<SongArt> {
    override fun key(data: SongArt, options: Options): String = "${data.uri}#${data.version}#${data.sizePx}"
}
