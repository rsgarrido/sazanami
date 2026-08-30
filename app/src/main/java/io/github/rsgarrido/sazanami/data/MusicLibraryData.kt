package io.github.rsgarrido.sazanami.data

import java.io.File

data class MusicLibraryData(
    val songs: List<Song>,
    val libraryFolders: List<LibraryFolder>,
    /** Complete snapshot used for identity reconciliation even when folder filtering is active. */
    val referenceSongs: List<Song> = songs
)

fun buildMusicLibraryData(
    allSongs: List<Song>,
    folderSelection: FolderSelection = FolderSelection.All
): MusicLibraryData {
    val filteredSongs = allSongs.filter { song -> folderSelection.includes(song.folderPath) }

    return MusicLibraryData(
        songs = filteredSongs,
        libraryFolders = buildLibraryFolders(allSongs),
        referenceSongs = allSongs
    )
}

fun buildMusicLibraryData(
    allSongs: List<Song>,
    selectedFolders: Set<String>
): MusicLibraryData = buildMusicLibraryData(
    allSongs = allSongs,
    folderSelection = FolderSelection.fromStored(null, selectedFolders)
)

fun buildLibraryFolders(songs: List<Song>): List<LibraryFolder> {
    if (songs.isEmpty()) return emptyList()

    data class FolderAccumulator(
        val path: String,
        val name: String,
        val parentPath: String?,
        var songCount: Int = 0,
        var directSongCount: Int = 0
    )

    val accumulators = linkedMapOf<String, FolderAccumulator>()
    songs.groupBy { song -> normalizeLibraryFolderPath(song.folderPath) }
        .filterKeys(String::isNotBlank)
        .forEach { (leafPath, folderSongs) ->
            val hierarchy = folderHierarchyPaths(
                folderPath = leafPath,
                relativePath = folderSongs.firstNotNullOfOrNull { song ->
                    song.relativePath.takeIf(String::isNotBlank)
                }.orEmpty()
            )
            hierarchy.forEachIndexed { index, path ->
                val accumulator = accumulators.getOrPut(path) {
                    FolderAccumulator(
                        path = path,
                        name = File(path).name.ifBlank { path },
                        parentPath = hierarchy.getOrNull(index - 1)
                    )
                }
                accumulator.songCount += folderSongs.size
                if (index == hierarchy.lastIndex) {
                    accumulator.directSongCount += folderSongs.size
                }
            }
        }

    val childrenByParent = accumulators.values.groupBy(FolderAccumulator::parentPath)
    val ordered = mutableListOf<LibraryFolder>()

    fun appendChildren(parentPath: String?, depth: Int) {
        childrenByParent[parentPath]
            .orEmpty()
            .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { folder -> folder.name })
            .forEach { folder ->
                val children = childrenByParent[folder.path].orEmpty()
                ordered += LibraryFolder(
                    path = folder.path,
                    name = folder.name,
                    songCount = folder.songCount,
                    directSongCount = folder.directSongCount,
                    parentPath = folder.parentPath,
                    depth = depth,
                    hasChildren = children.isNotEmpty()
                )
                appendChildren(folder.path, depth + 1)
            }
    }

    appendChildren(parentPath = null, depth = 0)
    return ordered
}

internal fun folderHierarchyPaths(folderPath: String, relativePath: String): List<String> {
    val normalizedPath = normalizeLibraryFolderPath(folderPath)
    if (normalizedPath.isBlank()) return emptyList()
    val absolute = normalizedPath.startsWith('/')
    val segments = normalizedPath.trim('/').split('/').filter(String::isNotBlank)
    if (segments.isEmpty()) return listOf(normalizedPath)

    val relativeSegments = normalizeLibraryFolderPath(relativePath)
        .trim('/')
        .split('/')
        .filter(String::isNotBlank)
    val relativeMatchesTail = relativeSegments.isNotEmpty() &&
            relativeSegments.size <= segments.size &&
            segments.takeLast(relativeSegments.size)
                .map(String::lowercase) == relativeSegments.map(String::lowercase)
    val firstVisibleIndex = when {
        relativeMatchesTail -> segments.size - relativeSegments.size
        segments.size >= 4 && segments[0].equals("storage", true) &&
                segments[1].equals("emulated", true) && segments[2].all(Char::isDigit) -> 3
        segments.size >= 3 && segments[0].equals("storage", true) -> 2
        segments.size >= 2 && segments[0].equals("sdcard", true) -> 1
        segments.size >= 4 && segments[0].equals("mnt", true) &&
                segments[1].equals("media_rw", true) -> 3
        segments.size >= 4 && segments[0].equals("data", true) &&
                segments[1].equals("media", true) && segments[2].all(Char::isDigit) -> 3
        absolute -> segments.lastIndex
        else -> 0
    }.coerceIn(0, segments.lastIndex)

    return (firstVisibleIndex..segments.lastIndex).map { index ->
        buildString {
            if (absolute) append('/')
            append(segments.take(index + 1).joinToString("/"))
        }
    }
}
