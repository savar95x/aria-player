package dev.savar.aria.data

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata

/**
 * Playing a URL: direct audio files (mp3, m4a, flac, ogg…), HLS (.m3u8) and internet radio
 * (Icecast/Shoutcast). Stream metadata, like a radio's "now playing", fills in the title.
 */
object Links {
    const val PREFIX = "url:"
    private val urlRegex = Regex("""https?://[^\s<>"]+""")

    fun find(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']')

    /** YouTube needs a stream extractor; plain links to its pages can't be played directly. */
    fun isYouTube(url: String): Boolean {
        val host = Uri.parse(url).host.orEmpty().removePrefix("www.").removePrefix("m.").removePrefix("music.")
        return host == "youtube.com" || host == "youtu.be"
    }

    fun isLink(mediaId: String) = mediaId.startsWith(PREFIX)

    fun urlOf(mediaId: String) = mediaId.removePrefix(PREFIX)

    fun mediaItem(url: String): MediaItem {
        val uri = Uri.parse(url)
        val fileName = uri.lastPathSegment
            ?.substringBeforeLast('.')
            ?.let { Uri.decode(it).replace('_', ' ').trim() }
            ?.takeIf { it.isNotBlank() }
        return MediaItem.Builder()
            .setMediaId(PREFIX + url)
            .setUri(url)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    // Title left unset on purpose: tags or radio metadata from the stream fill it in.
                    .setDisplayTitle(fileName ?: uri.host ?: url)
                    .setStation(uri.host)
                    .setIsPlayable(true)
                    .setIsBrowsable(false)
                    .build(),
            )
            .build()
    }
}

/** Best title for display: stream/file title, else our fallback. */
val MediaMetadata.titleText: String get() = (title ?: displayTitle)?.toString().orEmpty()

/** Best subtitle: artist, else album artist, else the stream's host. */
val MediaMetadata.artistText: String get() = (artist ?: albumArtist ?: station)?.toString().orEmpty()
