package io.github.rsgarrido.sazanami.ui.library

import android.net.Uri
import io.github.rsgarrido.sazanami.data.GenreCollection
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.UNKNOWN_GENRE_KEY
import io.github.rsgarrido.sazanami.data.UNKNOWN_GENRE_NAME
import io.github.rsgarrido.sazanami.data.buildGenreCollections
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class GenreGroupingTest {
    @Test
    fun knownGenresAreTrimmedGroupedCaseInsensitivelyAndCountedOncePerSong() {
        val groups = buildGenreCollections(
            listOf(
                song(1, "First", listOf(" rock ")),
                song(2, "Second", listOf("Rock", "ROCK")),
                song(3, "Third", listOf("Jazz"))
            )
        )

        assertEquals(listOf("Jazz", "Rock"), groups.map(GenreCollection::name))
        assertEquals(2, groups.single { it.name == "Rock" }.songs.size)
        assertEquals(listOf(1L, 2L), groups.single { it.name == "Rock" }.songs.map(Song::id))
    }

    @Test
    fun missingAndBlankGenresShareAnUnknownGroupThatSortsLast() {
        val groups = buildGenreCollections(
            listOf(
                song(1, "Unknown one", emptyList()),
                song(2, "Known", listOf("Ambient")),
                song(3, "Unknown two", listOf("  ")),
                song(4, "Unknown placeholder", listOf("<unknown>"))
            )
        )

        assertEquals(listOf("Ambient", UNKNOWN_GENRE_NAME), groups.map(GenreCollection::name))
        assertEquals(UNKNOWN_GENRE_KEY, groups.last().key)
        assertEquals(setOf(1L, 3L, 4L), groups.last().songs.mapTo(mutableSetOf(), Song::id))
    }

    @Test
    fun distinctMetadataValuesCreateMultipleMembershipsWithoutParsingPunctuation() {
        val song = song(1, "Hybrid", listOf("Rock", "R&B / Soul", "Jazz, Fusion"))

        val groups = buildGenreCollections(listOf(song))

        assertEquals(listOf("Jazz, Fusion", "R&B / Soul", "Rock"), groups.map { it.name })
        assertTrue(groups.all { it.songs.single().id == song.id })
        assertFalse(groups.any { it.name == "R&B" || it.name == "Soul" || it.name == "Jazz" })
    }

    @Test
    fun recomputingAfterMetadataEditMovesSongBetweenGroups() {
        val original = song(1, "Track", listOf("Rock"))
        val before = buildGenreCollections(listOf(original))
        val after = buildGenreCollections(listOf(original.copy(genres = listOf("Punk"))))

        assertEquals(listOf("Rock"), before.map(GenreCollection::name))
        assertEquals(listOf("Punk"), after.map(GenreCollection::name))
        assertEquals(1L, after.single().songs.single().id)
    }

    private fun song(id: Long, title: String, genres: List<String>): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = title,
            artist = "Artist",
            album = "Album",
            trackNumber = id.toInt(),
            duration = 180_000,
            uri = uri,
            filePath = "/music/$id.flac",
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external",
            displayName = "$id.flac",
            genres = genres,
            embeddedMetadataEnrichmentVersion = 1
        )
    }
}
