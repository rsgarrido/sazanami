package io.github.rsgarrido.sazanami.data

data class Playlist(
    val playlistId: Long,
    val name: String,
    val songCount: Int,
    val totalDuration: Long = 0L,
    val type: PlaylistType = PlaylistType.MANUAL,
    val artworkMode: PlaylistArtworkMode = PlaylistArtworkMode.AUTOMATIC,
    val artworkReference: String? = null,
    val folderId: Long? = null,
    val createdAt: Long = 0L,
    val modifiedAt: Long = 0L,
    val membershipBehavior: PlaylistMembershipBehavior = PlaylistMembershipBehavior.MANUAL,
    val generatedTemplateKey: String? = null,
    val generatedLastRefreshedAt: Long? = null,
    val smartResolutionError: String? = null,
    /** Resolved library identities used to prevent duplicate chooser additions. */
    val songMembershipKeys: Set<String> = emptySet(),
    /** Resolved in playlist order and reduced to distinct albums for automatic artwork. */
    val automaticArtworkSongs: List<Song> = emptyList()
)

enum class PlaylistType {
    MANUAL,
    SMART;

    companion object {
        fun fromStorage(value: String): PlaylistType =
            entries.firstOrNull { it.name == value } ?: MANUAL
    }
}

enum class PlaylistArtworkMode {
    AUTOMATIC,
    CUSTOM;

    companion object {
        fun fromStorage(value: String): PlaylistArtworkMode =
            entries.firstOrNull { it.name == value } ?: AUTOMATIC
    }
}
