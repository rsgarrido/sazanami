package io.github.rsgarrido.sazanami.data

enum class FolderSelectionMode {
    ALL,
    CUSTOM
}

enum class FolderSelectionState {
    SELECTED,
    PARTIAL,
    UNSELECTED
}

data class FolderSelection(
    val mode: FolderSelectionMode = FolderSelectionMode.ALL,
    /** Folder roots that are explicitly included. Descendants are included automatically. */
    val customFolders: Set<String> = emptySet(),
    /** Folder roots that are explicitly excluded from a broader include or ALL selection. */
    val excludedFolders: Set<String> = emptySet()
) {
    fun includes(folderPath: String): Boolean {
        val normalizedPath = normalizeLibraryFolderPath(folderPath)
        if (normalizedPath.isBlank()) return false

        val mostSpecificInclude = customFolders
            .asSequence()
            .map(::normalizeLibraryFolderPath)
            .filter { root -> root.isNotBlank() && isSameOrDescendantFolder(normalizedPath, root) }
            .maxOfOrNull(String::length)
        val mostSpecificExclude = excludedFolders
            .asSequence()
            .map(::normalizeLibraryFolderPath)
            .filter { root -> root.isNotBlank() && isSameOrDescendantFolder(normalizedPath, root) }
            .maxOfOrNull(String::length)

        return when {
            mostSpecificInclude != null &&
                    (mostSpecificExclude == null || mostSpecificInclude > mostSpecificExclude) -> true
            mostSpecificExclude != null -> false
            else -> mode == FolderSelectionMode.ALL
        }
    }

    fun stateFor(
        folderPath: String,
        availableFolders: Collection<String>
    ): FolderSelectionState {
        val subtree = availableFolders
            .asSequence()
            .map(::normalizeLibraryFolderPath)
            .filter { candidate -> isSameOrDescendantFolder(candidate, folderPath) }
            .distinct()
            .toList()
            .ifEmpty { listOf(normalizeLibraryFolderPath(folderPath)) }
        val includedCount = subtree.count(::includes)
        return when {
            includedCount == subtree.size -> FolderSelectionState.SELECTED
            includedCount > 0 -> FolderSelectionState.PARTIAL
            else -> FolderSelectionState.UNSELECTED
        }
    }

    fun effectiveFolders(availableFolders: Collection<String>): Set<String> =
        availableFolders.filterTo(mutableSetOf(), ::includes)

    fun toggle(folderPath: String, availableFolders: Collection<String>): FolderSelection {
        val normalizedPath = normalizeLibraryFolderPath(folderPath)
        if (normalizedPath.isBlank()) return this

        return when (stateFor(normalizedPath, availableFolders)) {
            FolderSelectionState.SELECTED -> exclude(normalizedPath)
            FolderSelectionState.PARTIAL,
            FolderSelectionState.UNSELECTED -> include(normalizedPath)
        }.normalized()
    }

    fun toStoredFolders(): Set<String> = buildSet {
        addAll(customFolders.map(::normalizeLibraryFolderPath).filter(String::isNotBlank))
        excludedFolders
            .map(::normalizeLibraryFolderPath)
            .filter(String::isNotBlank)
            .forEach { path -> add(EXCLUDED_FOLDER_PREFIX + path) }
    }

    private fun include(folderPath: String): FolderSelection {
        val remainingExclusions = excludedFolders.filterNotTo(mutableSetOf()) { excluded ->
            isSameOrDescendantFolder(excluded, folderPath)
        }
        val remainingIncludes = customFolders.filterNotTo(mutableSetOf()) { included ->
            isSameOrDescendantFolder(included, folderPath)
        }
        val hasExcludedAncestor = remainingExclusions.any { excluded ->
            isSameOrDescendantFolder(folderPath, excluded)
        }
        if (mode == FolderSelectionMode.CUSTOM || hasExcludedAncestor) {
            remainingIncludes += folderPath
        }
        return copy(
            customFolders = remainingIncludes,
            excludedFolders = remainingExclusions
        )
    }

    private fun exclude(folderPath: String): FolderSelection {
        val remainingIncludes = customFolders.filterNotTo(mutableSetOf()) { included ->
            isSameOrDescendantFolder(included, folderPath)
        }
        val remainingExclusions = excludedFolders.filterNotTo(mutableSetOf()) { excluded ->
            isSameOrDescendantFolder(excluded, folderPath)
        }
        remainingExclusions += folderPath
        return copy(
            customFolders = remainingIncludes,
            excludedFolders = remainingExclusions
        )
    }

    private fun normalized(): FolderSelection = copy(
        customFolders = customFolders
            .mapTo(mutableSetOf(), ::normalizeLibraryFolderPath)
            .filterTo(mutableSetOf(), String::isNotBlank),
        excludedFolders = excludedFolders
            .mapTo(mutableSetOf(), ::normalizeLibraryFolderPath)
            .filterTo(mutableSetOf(), String::isNotBlank)
    )

    companion object {
        val All = FolderSelection()

        fun fromStored(
            storedMode: String?,
            storedFolders: Set<String>
        ): FolderSelection {
            val explicitMode = storedMode?.let { value ->
                FolderSelectionMode.entries.firstOrNull { it.name == value }
            }
            val included = storedFolders
                .filterNot { value -> value.startsWith(EXCLUDED_FOLDER_PREFIX) }
                .mapTo(mutableSetOf(), ::normalizeLibraryFolderPath)
            val excluded = storedFolders
                .filter { value -> value.startsWith(EXCLUDED_FOLDER_PREFIX) }
                .mapTo(mutableSetOf()) { value ->
                    normalizeLibraryFolderPath(value.removePrefix(EXCLUDED_FOLDER_PREFIX))
                }
            val mode = explicitMode ?: if (included.isEmpty()) {
                FolderSelectionMode.ALL
            } else {
                FolderSelectionMode.CUSTOM
            }
            return FolderSelection(mode, included, excluded).normalized()
        }
    }
}

internal fun normalizeLibraryFolderPath(path: String): String {
    val replaced = path.trim().replace('\\', '/')
    if (replaced == "/") return replaced
    return replaced.trimEnd('/')
}

internal fun isSameOrDescendantFolder(candidatePath: String, rootPath: String): Boolean {
    val candidate = normalizeLibraryFolderPath(candidatePath)
    val root = normalizeLibraryFolderPath(rootPath)
    if (candidate.isBlank() || root.isBlank()) return false
    return candidate.equals(root, ignoreCase = true) ||
            candidate.startsWith("$root/", ignoreCase = true)
}

private const val EXCLUDED_FOLDER_PREFIX = "__internal_folder_exclude__:"
