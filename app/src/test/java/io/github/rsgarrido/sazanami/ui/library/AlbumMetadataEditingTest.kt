package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import io.github.rsgarrido.sazanami.data.BatchArtworkReference
import io.github.rsgarrido.sazanami.data.BatchArtworkValue
import io.github.rsgarrido.sazanami.data.BatchEditIntent
import io.github.rsgarrido.sazanami.data.BatchInitialValue
import io.github.rsgarrido.sazanami.data.BatchMetadataField
import io.github.rsgarrido.sazanami.data.BatchMetadataValue
import io.github.rsgarrido.sazanami.data.EditableSongTags
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.deriveBatchMetadataEditorState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class AlbumMetadataEditingTest {
    @Test
    fun `album edit targets are the exact folder-backed album songs`() {
        val firstAlbum = listOf(
            song(1, "Greatest Hits", "/music/artist-a/album", 1),
            song(2, "Greatest Hits", "/music/artist-a/album", 2)
        )
        val sameTitleDifferentAlbum = song(
            3,
            "Greatest Hits",
            "/music/artist-b/album",
            1
        )

        val groups = buildLibraryAlbumGroups(firstAlbum + sameTitleDifferentAlbum)
        val selected = groups.single { group -> group.key == "/music/artist-a/album" }

        assertEquals(listOf(1L, 2L), selected.metadataEditingSongs().map(Song::id))
        assertFalse(selected.metadataEditingSongs().any { it.id == sameTitleDifferentAlbum.id })
    }

    @Test
    fun `album and manual entry derive the same batch plan structure`() {
        val songs = listOf(
            song(11, "Album", "/music/album", 1),
            song(12, "Album", "/music/album", 2)
        )
        val album = buildLibraryAlbumGroups(songs).single()
        val tags = songs.associate { song -> song.id to tags(albumArtist = "Artist", genre = "Rock") }

        val albumState = deriveBatchMetadataEditorState(album.metadataEditingSongs()) {
            tags.getValue(it.id)
        }
        val manualState = deriveBatchMetadataEditorState(songs) { tags.getValue(it.id) }

        assertEquals(manualState.plan(), albumState.plan())
        assertEquals(0, albumState.plan().changeCount)
    }

    @Test
    fun `common and mixed album metadata remain distinguishable`() {
        val songs = listOf(
            song(21, "Album", "/music/album", 1),
            song(22, "Album", "/music/album", 2)
        )
        val tags = mapOf(
            21L to tags(
                albumArtist = "Shared Artist",
                genre = "Rock",
                composer = "Composer One",
                discNumber = "1"
            ),
            22L to tags(
                albumArtist = "Shared Artist",
                genre = "Rock",
                composer = "Composer Two",
                discNumber = "2"
            )
        )

        val state = deriveBatchMetadataEditorState(songs) { tags.getValue(it.id) }

        assertEquals(
            BatchInitialValue.Common(BatchMetadataValue.MultiValue(listOf("Shared Artist"))),
            state.fields.getValue(BatchMetadataField.ALBUM_ARTIST).initial
        )
        assertEquals(
            BatchInitialValue.Common(BatchMetadataValue.MultiValue(listOf("Rock"))),
            state.fields.getValue(BatchMetadataField.GENRE).initial
        )
        assertEquals(
            BatchInitialValue.Mixed,
            state.fields.getValue(BatchMetadataField.COMPOSER).initial
        )
        assertEquals(
            BatchInitialValue.Mixed,
            state.fields.getValue(BatchMetadataField.DISC_NUMBER).initial
        )
    }

    @Test
    fun `album rename does not touch titles track numbers or mixed disc numbers`() {
        val songs = listOf(
            song(31, "Old Album", "/music/album", 1),
            song(32, "Old Album", "/music/album", 2)
        )
        val tags = mapOf(
            31L to tags(album = "Old Album", discNumber = "1"),
            32L to tags(album = "Old Album", discNumber = "2")
        )

        val plan = deriveBatchMetadataEditorState(songs) { tags.getValue(it.id) }
            .set(BatchMetadataField.ALBUM, "New Album")
            .plan()

        assertEquals(setOf(BatchMetadataField.ALBUM), plan.fieldChanges.keys)
        assertEquals(listOf("Track 1", "Track 2"), plan.selectedTargets.map { it.title })
        assertFalse(plan.fieldChanges.containsKey(BatchMetadataField.DISC_NUMBER))
        assertTrue(isAlbumGroupAvailable("/music/album", songs.map { it.copy(album = "New Album") }))
    }

    @Test
    fun `album artist and artwork changes remain explicit operations`() {
        val songs = listOf(
            song(41, "Album", "/music/album", 1),
            song(42, "Album", "/music/album", 2)
        )
        val state = deriveBatchMetadataEditorState(songs) { tags(albumArtist = it.albumArtist) }
        val artistPlan = state
            .set(BatchMetadataField.ALBUM_ARTIST, "New Album Artist")
            .plan()
        val artwork = BatchArtworkReference("replacement-hash", "content://artwork/new")
        val artworkPlan = state.replaceArtwork(artwork).plan()

        assertEquals(setOf(BatchMetadataField.ALBUM_ARTIST), artistPlan.fieldChanges.keys)
        assertEquals(
            BatchEditIntent.Set(BatchArtworkValue.Present(artwork)),
            artworkPlan.artworkChange?.intent
        )
        assertEquals(songs.size, artworkPlan.selectedTrackCount)
    }

    @Test
    fun `missing folder-backed group returns safely to albums`() {
        val renamedInPlace = song(51, "New Album", "/music/album", 1)

        assertTrue(isAlbumGroupAvailable("/music/album", listOf(renamedInPlace)))
        assertFalse(isAlbumGroupAvailable("/music/missing", listOf(renamedInPlace)))
    }

    private fun tags(
        album: String = "Album",
        albumArtist: String = "",
        genre: String = "",
        composer: String = "",
        discNumber: String = ""
    ) = EditableSongTags(
        title = "Title",
        artist = "Artist",
        album = album,
        trackNumber = "1",
        year = "2026",
        albumArtist = albumArtist,
        genre = genre,
        composer = composer,
        discNumber = discNumber,
        discTotal = "2"
    )

    private fun song(
        id: Long,
        album: String,
        folderPath: String,
        trackNumber: Int
    ): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = "Track $trackNumber",
            artist = "Artist",
            album = album,
            trackNumber = trackNumber,
            duration = 180_000,
            uri = uri,
            filePath = "$folderPath/$trackNumber.flac",
            folderPath = folderPath,
            albumArtUri = null,
            albumArtist = "Album Artist",
            volumeName = "external",
            displayName = "$trackNumber.flac",
            relativePath = folderPath.substringAfter("/music/")
        )
    }
}
