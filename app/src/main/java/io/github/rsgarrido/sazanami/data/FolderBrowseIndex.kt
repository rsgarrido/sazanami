package io.github.rsgarrido.sazanami.data

import java.util.Locale

/** Stable folder identity within one transient admitted-library snapshot. */
data class FolderId internal constructor(
    val volumeName: String,
    val normalizedPath: String
)

data class FolderBrowseNode(
    val id: FolderId,
    val parentId: FolderId?,
    val displayName: String,
    val childFolderIds: List<FolderId>,
    val directSongs: List<Song>,
    /** Songs in this folder and all descendant folders. */
    val songCount: Int
)

/**
 * An immutable folder projection derived only from the admitted songs supplied to
 * [buildFolderBrowseIndex]. It never discovers files or consults Settings folder discovery.
 */
class FolderBrowseIndex internal constructor(
    val rootIds: List<FolderId>,
    val nodesById: Map<FolderId, FolderBrowseNode>
) {
    val roots: List<FolderBrowseNode>
        get() = rootIds.mapNotNull(nodesById::get)

    operator fun get(id: FolderId): FolderBrowseNode? = nodesById[id]

    fun childrenOf(id: FolderId): List<FolderBrowseNode> =
        nodesById[id]?.childFolderIds.orEmpty().mapNotNull(nodesById::get)

    companion object {
        val Empty = FolderBrowseIndex(emptyList(), emptyMap())
    }
}

/** Builds a fresh hierarchy from the currently admitted library songs. */
fun buildFolderBrowseIndex(admittedSongs: List<Song>): FolderBrowseIndex {
    if (admittedSongs.isEmpty()) return FolderBrowseIndex.Empty

    val mutableNodes = linkedMapOf<FolderId, MutableFolderBrowseNode>()
    admittedSongs.forEach { song ->
        val hierarchy = song.folderBrowseHierarchy()
        hierarchy.forEachIndexed { index, path ->
            val parentId = hierarchy.getOrNull(index - 1)?.id
            val node = mutableNodes.getOrPut(path.id) { MutableFolderBrowseNode(path.id) }
            node.displayNames += path.displayName
            node.parentCandidates.add(parentId)
            if (index == hierarchy.lastIndex) {
                node.directSongs += song
            }
        }
    }
    if (mutableNodes.isEmpty()) return FolderBrowseIndex.Empty

    val parentById = mutableNodes.mapValues { (_, node) -> node.resolvedParentId() }
    val childrenByParent = parentById.entries
        .groupBy(
            keySelector = { entry -> entry.value },
            valueTransform = { entry -> entry.key }
        )

    val displayNameById = mutableNodes.mapValues { (_, node) ->
        node.displayNames.minWithOrNull(folderDisplayNameComparator).orEmpty()
    }
    val orderedChildrenByParent = childrenByParent.mapValues { (_, childIds) ->
        childIds.sortedWith(folderIdComparator(displayNameById))
    }
    val countById = mutableMapOf<FolderId, Int>()

    fun recursiveSongCount(id: FolderId): Int = countById.getOrPut(id) {
        mutableNodes.getValue(id).directSongs.size +
            orderedChildrenByParent[id].orEmpty().sumOf(::recursiveSongCount)
    }

    val orderedIds = mutableNodes.keys.sortedWith(folderIdComparator(displayNameById))
    val nodesById = orderedIds.associateWithTo(linkedMapOf()) { id ->
        val mutableNode = mutableNodes.getValue(id)
        FolderBrowseNode(
            id = id,
            parentId = parentById.getValue(id),
            displayName = displayNameById.getValue(id),
            childFolderIds = orderedChildrenByParent[id].orEmpty(),
            directSongs = mutableNode.directSongs.sortedWith(folderSongComparator),
            songCount = recursiveSongCount(id)
        )
    }
    val rootIds = orderedChildrenByParent[null].orEmpty()
    return FolderBrowseIndex(rootIds = rootIds, nodesById = nodesById)
}

internal fun folderBrowseId(volumeName: String, path: String): FolderId = FolderId(
    volumeName = volumeName.identityNormalized(),
    normalizedPath = normalizedFolderBrowsePath(path)
)

private data class FolderBrowsePath(
    val id: FolderId,
    val displayName: String
)

private class MutableFolderBrowseNode(val id: FolderId) {
    val displayNames = mutableSetOf<String>()
    val parentCandidates = mutableSetOf<FolderId?>()
    val directSongs = mutableListOf<Song>()

    fun resolvedParentId(): FolderId? {
        // A visible-root interpretation wins if sparse/fallback metadata described the same path
        // at different hierarchy depths. This keeps conventional roots such as Music at the top.
        if (null in parentCandidates) return null
        return parentCandidates.filterNotNull().minWithOrNull(folderIdStableComparator)
    }
}

private fun Song.folderBrowseHierarchy(): List<FolderBrowsePath> {
    val normalizedRelativePath = normalizeLibraryFolderPath(relativePath)
    val hierarchyPaths = if (normalizedRelativePath.isNotBlank()) {
        cumulativeFolderPaths(normalizedRelativePath)
    } else {
        folderHierarchyPaths(folderPath, relativePath = "")
    }

    return hierarchyPaths.mapNotNull { path ->
        val normalizedPath = normalizedFolderBrowsePath(path)
        if (normalizedPath.isBlank()) return@mapNotNull null
        FolderBrowsePath(
            id = folderBrowseId(volumeName, normalizedPath),
            displayName = path.folderDisplayName()
        )
    }
}

private fun cumulativeFolderPaths(path: String): List<String> {
    val segments = path.replace('\\', '/').trim().trim('/').split('/')
        .filter(String::isNotBlank)
    return segments.indices.map { index -> segments.take(index + 1).joinToString("/") }
}

private fun normalizedFolderBrowsePath(path: String): String = path
    .replace('\\', '/')
    .trim()
    .trim('/')
    .split('/')
    .filter(String::isNotBlank)
    .joinToString("/")
    .lowercase(Locale.ROOT)

private fun String.folderDisplayName(): String = replace('\\', '/')
    .trim()
    .trimEnd('/')
    .substringAfterLast('/')
    .trim()

private val folderDisplayNameComparator = Comparator<String> { left, right ->
    String.CASE_INSENSITIVE_ORDER.compare(left, right).takeUnless { it == 0 }
        ?: left.compareTo(right)
}

private val folderIdStableComparator = compareBy<FolderId>(
    FolderId::volumeName,
    FolderId::normalizedPath
)

private fun folderIdComparator(displayNames: Map<FolderId, String>): Comparator<FolderId> =
    Comparator { left, right ->
        folderDisplayNameComparator.compare(
            displayNames.getValue(left),
            displayNames.getValue(right)
        ).takeUnless { it == 0 }
            ?: folderIdStableComparator.compare(left, right)
    }

private val folderSongComparator = Comparator<Song> { left, right ->
    compareFolderSongText(left.title, right.title)
        .takeUnless { it == 0 }
        ?: compareFolderSongText(left.artist, right.artist).takeUnless { it == 0 }
        ?: compareFolderSongText(left.album, right.album).takeUnless { it == 0 }
        ?: left.membershipKey().compareTo(right.membershipKey())
}

/** Mirrors the existing title-ascending library behavior: known text sorts before blank text. */
private fun compareFolderSongText(left: String, right: String): Int = when {
    left.isNotBlank() != right.isNotBlank() -> if (left.isNotBlank()) -1 else 1
    left.isBlank() -> 0
    else -> left.trim().lowercase(Locale.ROOT).compareTo(right.trim().lowercase(Locale.ROOT))
}
