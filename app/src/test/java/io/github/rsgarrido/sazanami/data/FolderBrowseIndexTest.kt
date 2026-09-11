package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class FolderBrowseIndexTest {
    @Test
    fun simpleRelativePathBuildsFolderHierarchy() {
        val song = song(1, relativePath = "Music/Artist/Album/")

        val index = buildFolderBrowseIndex(listOf(song))

        val music = index.node(PRIMARY_VOLUME, "music")
        val artist = index.node(PRIMARY_VOLUME, "music/artist")
        val album = index.node(PRIMARY_VOLUME, "music/artist/album")
        assertEquals(listOf(music.id), index.rootIds)
        assertEquals(music.id, artist.parentId)
        assertEquals(artist.id, album.parentId)
        assertSame(song, album.directSongs.single())
        assertEquals(1, music.songCount)
    }

    @Test
    fun nestedFoldersKeepRecursiveCountsAndDirectMembershipSeparate() {
        val rootSong = song(1, relativePath = "Music/")
        val childSong = song(2, relativePath = "Music/Artist/")
        val leafSong = song(3, relativePath = "Music/Artist/Album/")

        val index = buildFolderBrowseIndex(listOf(leafSong, rootSong, childSong))

        val music = index.node(PRIMARY_VOLUME, "music")
        val artist = index.node(PRIMARY_VOLUME, "music/artist")
        assertEquals(listOf(rootSong), music.directSongs)
        assertEquals(listOf(childSong), artist.directSongs)
        assertEquals(3, music.songCount)
        assertEquals(2, artist.songCount)
    }

    @Test
    fun equivalentPathsOnDifferentVolumesRemainDistinct() {
        val internal = song(1, volumeName = PRIMARY_VOLUME, relativePath = "Music/Album/")
        val removable = song(2, volumeName = "1234-5678", relativePath = "Music/Album/")

        val index = buildFolderBrowseIndex(listOf(internal, removable))

        assertEquals(2, index.roots.size)
        assertTrue(index.nodesById.containsKey(folderBrowseId(PRIMARY_VOLUME, "music/album")))
        assertTrue(index.nodesById.containsKey(folderBrowseId("1234-5678", "music/album")))
        assertSame(
            internal,
            index.node(PRIMARY_VOLUME, "music/album").directSongs.single()
        )
        assertSame(
            removable,
            index.node("1234-5678", "music/album").directSongs.single()
        )
    }

    @Test
    fun independentTopLevelPathsBecomeMultipleRoots() {
        val music = song(1, relativePath = "Music/Album/")
        val audioBooks = song(2, relativePath = "Audiobooks/Author/")

        val index = buildFolderBrowseIndex(listOf(music, audioBooks))

        assertEquals(
            listOf("Audiobooks", "Music"),
            index.roots.map(FolderBrowseNode::displayName)
        )
        assertSame(
            audioBooks,
            index.node(PRIMARY_VOLUME, "audiobooks/author").directSongs.single()
        )
        assertSame(music, index.node(PRIMARY_VOLUME, "music/album").directSongs.single())
    }

    @Test
    fun duplicateNamesUnderDifferentParentsRemainDistinct() {
        val artistLive = song(1, relativePath = "Music/Artist/Live/")
        val otherLive = song(2, relativePath = "Music/Other/Live/")

        val index = buildFolderBrowseIndex(listOf(artistLive, otherLive))

        val first = index.node(PRIMARY_VOLUME, "music/artist/live")
        val second = index.node(PRIMARY_VOLUME, "music/other/live")
        assertEquals("Live", first.displayName)
        assertEquals("Live", second.displayName)
        assertTrue(first.id != second.id)
        assertTrue(first.parentId != second.parentId)
    }

    @Test
    fun folderCanContainDirectSongsAndChildFolders() {
        val direct = song(1, relativePath = "Music/Mixes/")
        val nested = song(2, relativePath = "Music/Mixes/Live/")

        val index = buildFolderBrowseIndex(listOf(nested, direct))

        val mixes = index.node(PRIMARY_VOLUME, "music/mixes")
        assertEquals(listOf(direct), mixes.directSongs)
        assertEquals(
            listOf("Live"),
            index.childrenOf(mixes.id).map(FolderBrowseNode::displayName)
        )
        assertEquals(2, mixes.songCount)
    }

    @Test
    fun songsAtAVisibleRootStayDirectlyOnThatRoot() {
        val rootSong = song(1, relativePath = "Music/")

        val index = buildFolderBrowseIndex(listOf(rootSong))

        val root = index.roots.single()
        assertEquals("Music", root.displayName)
        assertNull(root.parentId)
        assertSame(rootSong, root.directSongs.single())
    }

    @Test
    fun relativePathWorksWhenMediaStoreDataPathIsMissing() {
        val song = song(
            id = 1,
            filePath = "",
            folderPath = "Music/Artist",
            relativePath = "Music/Artist/"
        )

        val index = buildFolderBrowseIndex(listOf(song))

        assertSame(song, index.node(PRIMARY_VOLUME, "music/artist").directSongs.single())
    }

    @Test
    fun folderPathProvidesFallbackWhenRelativePathIsUnavailable() {
        val song = song(
            id = 1,
            folderPath = "/storage/emulated/0/Music/Artist",
            relativePath = ""
        )

        val index = buildFolderBrowseIndex(listOf(song))

        val music = index.node(PRIMARY_VOLUME, "storage/emulated/0/music")
        val artist = index.node(PRIMARY_VOLUME, "storage/emulated/0/music/artist")
        assertEquals(listOf(music.id), index.rootIds)
        assertEquals(music.id, artist.parentId)
        assertSame(song, artist.directSongs.single())
    }

    @Test
    fun slashCaseAndVolumeNormalizationMergeEquivalentFolders() {
        val first = song(
            id = 1,
            volumeName = " External_Primary ",
            relativePath = " Music\\Artist\\Album/// "
        )
        val second = song(
            id = 2,
            volumeName = "external_primary",
            relativePath = "music//artist/album/"
        )

        val index = buildFolderBrowseIndex(listOf(second, first))

        val album = index.node(PRIMARY_VOLUME, "MUSIC/ARTIST/ALBUM/")
        assertEquals("Album", album.displayName)
        assertEquals(
            listOf(first, second).map(Song::id).toSet(),
            album.directSongs.map(Song::id).toSet()
        )
        assertEquals(3, index.nodesById.size)
    }

    @Test
    fun childFoldersAndDirectSongsUseDeterministicV1Ordering() {
        val songs = listOf(
            song(1, title = "Zulu", relativePath = "Music/"),
            song(2, title = "alpha", relativePath = "Music/"),
            song(3, title = "Beta", relativePath = "Music/"),
            song(4, relativePath = "Music/zeta/"),
            song(5, relativePath = "Music/Alpha/"),
            song(6, relativePath = "Music/beta/")
        )

        val forward = buildFolderBrowseIndex(songs)
        val reversed = buildFolderBrowseIndex(songs.reversed())
        val forwardRoot = forward.node(PRIMARY_VOLUME, "music")
        val reversedRoot = reversed.node(PRIMARY_VOLUME, "music")

        assertEquals(
            listOf("Alpha", "beta", "zeta"),
            forward.childrenOf(forwardRoot.id).map(FolderBrowseNode::displayName)
        )
        assertEquals(
            listOf("alpha", "Beta", "Zulu"),
            forwardRoot.directSongs.map(Song::title)
        )
        assertEquals(forwardRoot.childFolderIds, reversedRoot.childFolderIds)
        assertEquals(
            forwardRoot.directSongs.map(Song::id),
            reversedRoot.directSongs.map(Song::id)
        )
    }

    @Test
    fun constructionFromFilteredLibraryDataContainsOnlyAdmittedSongs() {
        val admitted = song(
            id = 1,
            folderPath = "/storage/emulated/0/Music/Album",
            relativePath = "Music/Album/"
        )
        val excluded = song(
            id = 2,
            folderPath = "/storage/emulated/0/Music/WhatsApp/Audio",
            relativePath = "Music/WhatsApp/Audio/"
        )
        val library = buildMusicLibraryData(
            allSongs = listOf(admitted, excluded),
            folderSelection = FolderSelection(
                mode = FolderSelectionMode.CUSTOM,
                customFolders = setOf("/storage/emulated/0/Music"),
                excludedFolders = setOf("/storage/emulated/0/Music/WhatsApp")
            )
        )

        val index = buildFolderBrowseIndex(library.songs)

        assertSame(admitted, index.node(PRIMARY_VOLUME, "music/album").directSongs.single())
        assertFalse(index.nodesById.keys.any { it.normalizedPath.contains("whatsapp") })
    }

    @Test
    fun rebuildingDropsFoldersThatNoLongerHaveAdmittedSongs() {
        val retained = song(1, relativePath = "Music/Retained/")
        val removed = song(2, relativePath = "Music/Removed/")
        val initial = buildFolderBrowseIndex(listOf(retained, removed))

        val refreshed = buildFolderBrowseIndex(listOf(retained))
        val empty = buildFolderBrowseIndex(emptyList())

        assertTrue(
            initial.nodesById.containsKey(folderBrowseId(PRIMARY_VOLUME, "music/removed"))
        )
        assertFalse(
            refreshed.nodesById.containsKey(folderBrowseId(PRIMARY_VOLUME, "music/removed"))
        )
        assertEquals(1, refreshed.node(PRIMARY_VOLUME, "music").songCount)
        assertTrue(empty.rootIds.isEmpty())
        assertTrue(empty.nodesById.isEmpty())
    }

    private fun FolderBrowseIndex.node(volumeName: String, path: String): FolderBrowseNode =
        requireNotNull(this[folderBrowseId(volumeName, path)]) {
            "Missing folder $volumeName:$path"
        }

    private fun song(
        id: Long,
        title: String = "Song $id",
        volumeName: String = PRIMARY_VOLUME,
        relativePath: String,
        folderPath: String = relativePath.replace('\\', '/').trim().trimEnd('/'),
        filePath: String = "$folderPath/song-$id.flac"
    ): Song = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = id.toInt(),
        duration = 1_000L,
        uri = mock(Uri::class.java),
        filePath = filePath,
        folderPath = folderPath,
        albumArtUri = null,
        volumeName = volumeName,
        displayName = "song-$id.flac",
        relativePath = relativePath
    )

    private companion object {
        const val PRIMARY_VOLUME = "external_primary"
    }
}
