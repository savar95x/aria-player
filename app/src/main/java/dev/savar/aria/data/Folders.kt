package dev.savar.aria.data

enum class FolderRule { None, Include, Exclude }

/**
 * Whitelist + blacklist of folders (absolute paths).
 *
 * - The most specific rule wins, so you can include `Music` but exclude `Music/Voice notes`,
 *   or exclude `Download` but include `Download/Albums`.
 * - If anything is included, folders with no matching rule are hidden.
 */
data class FolderFilter(
    val include: Set<String> = emptySet(),
    val exclude: Set<String> = emptySet(),
) {
    val isEmpty get() = include.isEmpty() && exclude.isEmpty()

    fun ruleFor(path: String): FolderRule = when (path) {
        in include -> FolderRule.Include
        in exclude -> FolderRule.Exclude
        else -> FolderRule.None
    }

    fun allows(folder: String): Boolean {
        if (isEmpty) return true
        val inc = include.filter { folder.isUnder(it) }.maxOfOrNull { it.length } ?: -1
        val exc = exclude.filter { folder.isUnder(it) }.maxOfOrNull { it.length } ?: -1
        return when {
            inc < 0 && exc < 0 -> include.isEmpty()
            else -> inc > exc
        }
    }
}

private fun String.isUnder(parent: String) = this == parent || startsWith("$parent/")

data class FolderNode(
    val path: String,
    val name: String,
    val depth: Int,
    val songCount: Int,
    val volumeLabel: String,
)

private val emulatedRoot = Regex("^/storage/emulated/\\d+")
private val volumeRoot = Regex("^/storage/[^/]+")

private fun storageRoot(path: String): String =
    emulatedRoot.find(path)?.value ?: volumeRoot.find(path)?.value ?: ""

/** Every folder that holds music (directly or below), with recursive song counts. */
fun buildFolderTree(songs: List<Song>, extraPaths: Collection<String>): List<FolderNode> {
    val counts = HashMap<String, Int>()
    for (song in songs) {
        val root = storageRoot(song.folder)
        var dir = song.folder
        while (dir.length > root.length && dir.isNotEmpty()) {
            counts[dir] = (counts[dir] ?: 0) + 1
            dir = dir.substringBeforeLast('/', "")
        }
    }
    for (p in extraPaths) counts.putIfAbsent(p, 0)
    return counts.entries
        .map { (path, count) ->
            val root = storageRoot(path)
            val rel = path.removePrefix(root).trimStart('/')
            FolderNode(
                path = path,
                name = rel.substringAfterLast('/'),
                depth = rel.count { it == '/' },
                songCount = count,
                volumeLabel = if (emulatedRoot.matches(root)) "Internal" else "SD card",
            )
        }
        .sortedBy { it.path.lowercase() }
}
