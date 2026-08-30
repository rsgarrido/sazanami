package io.github.rsgarrido.sazanami.data.playlistfile

import java.util.Locale

data class PlaylistImportResult(
    val playlistName: String?,
    val importedSongCount: Int,
    val unmatchedEntryCount: Int
)

fun defaultImportedPlaylistName(sourceDisplayName: String?): String {
    val displayName = sourceDisplayName.orEmpty().trim()
    val lowercaseName = displayName.lowercase(Locale.ROOT)

    val nameWithoutExtension = when {
        lowercaseName.endsWith(".m3u8") -> displayName.dropLast(".m3u8".length)
        lowercaseName.endsWith(".m3u") -> displayName.dropLast(".m3u".length)
        else -> displayName
    }.trim()

    return nameWithoutExtension.ifBlank { "Imported Playlist" }
}
