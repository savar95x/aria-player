package dev.savar.aria.data

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

data class SavedQueue(
    val ids: List<Long>,
    val index: Int,
    val positionMs: Long,
    val shuffle: Boolean,
    val repeatMode: Int,
)

/** Persists the queue so playback (and the widget) survive the app being killed. */
class QueueStore(context: Context) {
    private val file = File(context.filesDir, "queue.txt")

    suspend fun save(queue: SavedQueue) = withContext(Dispatchers.IO) {
        val tmp = File(file.parentFile, "queue.tmp")
        tmp.writeText(
            listOf(
                queue.index.toString(),
                queue.positionMs.toString(),
                queue.shuffle.toString(),
                queue.repeatMode.toString(),
                queue.ids.joinToString(","),
            ).joinToString("\n"),
        )
        tmp.renameTo(file)
    }

    suspend fun load(): SavedQueue? = withContext(Dispatchers.IO) {
        runCatching {
            val lines = file.readLines()
            SavedQueue(
                index = lines[0].toInt(),
                positionMs = lines[1].toLong(),
                shuffle = lines[2].toBoolean(),
                repeatMode = lines[3].toInt(),
                ids = lines.getOrNull(4).orEmpty().split(',').mapNotNull { it.toLongOrNull() },
            )
        }.getOrNull()?.takeIf { it.ids.isNotEmpty() }
    }
}
