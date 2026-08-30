package io.github.rsgarrido.sazanami.data

data class LibraryFolder(
    val path: String,
    val name: String,
    /** Songs in this folder and all of its descendants. */
    val songCount: Int,
    /** Songs stored directly in this folder, excluding descendants. */
    val directSongCount: Int = songCount,
    val parentPath: String? = null,
    val depth: Int = 0,
    val hasChildren: Boolean = false
)
