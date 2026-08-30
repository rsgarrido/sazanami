package io.github.rsgarrido.sazanami.data

/**
 * Creates the conservative first-run default. Only conventional top-level Music roots are
 * included automatically; other roots remain opt-in even when MediaStore reports audio there.
 */
internal fun defaultInitialLibraryFolderSelection(
    folders: Collection<LibraryFolder>
): FolderSelection = FolderSelection(
    mode = FolderSelectionMode.CUSTOM,
    customFolders = folders
        .asSequence()
        .filter { folder ->
            folder.depth == 0 && folder.name.equals(CONVENTIONAL_MUSIC_FOLDER_NAME, true)
        }
        .mapTo(linkedSetOf(), LibraryFolder::path)
)

/**
 * Keeps discovery metadata for every root while replacing only selected songs with their current
 * selected-library versions. The normal library remains filtered by [selection].
 */
internal fun buildInitialSelectedLibraryData(
    discoveredSongs: List<Song>,
    refreshedSelectedSongs: List<Song>,
    selection: FolderSelection
): MusicLibraryData {
    val refreshedByMembership = refreshedSelectedSongs.associateBy(Song::membershipKey)
    val mergedSongs = discoveredSongs.map { discovered ->
        refreshedByMembership[discovered.membershipKey()] ?: discovered
    }
    return buildMusicLibraryData(
        allSongs = mergedSongs,
        folderSelection = selection
    )
}

internal data class InitialCoreLibraryBuild(
    val libraryData: MusicLibraryData,
    val refreshResult: LibraryRefreshResult
)

/** Reconciles reusable cached rows without invoking any file metadata or artwork dependency. */
internal fun buildInitialSelectedCoreLibrary(
    discoveredSongs: List<Song>,
    cachedSongs: List<Song>,
    selection: FolderSelection
): InitialCoreLibraryBuild {
    val selectedRows = discoveredSongs.filter { song -> selection.includes(song.folderPath) }
    val selectedCachedSongs = cachedSongs.filter { song -> selection.includes(song.folderPath) }
    val refreshResult = LibraryRefreshEngine.refresh(
        cachedSongs = selectedCachedSongs,
        indexSongs = selectedRows,
        requiresEnrichment = { _, _ -> false },
        enrich = { row -> row }
    )
    return InitialCoreLibraryBuild(
        libraryData = buildInitialSelectedLibraryData(
            discoveredSongs = discoveredSongs,
            refreshedSelectedSongs = refreshResult.songs,
            selection = selection
        ),
        refreshResult = refreshResult
    )
}

private const val CONVENTIONAL_MUSIC_FOLDER_NAME = "Music"
