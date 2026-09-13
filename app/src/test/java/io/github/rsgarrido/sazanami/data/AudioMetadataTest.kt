package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.jaudiotagger.tag.vorbiscomment.VorbisCommentTag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class AudioMetadataTest {
    @Test
    fun `ID3-preferred merge fills only missing values from INFO`() {
        val id3 = AudioMetadata(
            title = "ID3 title",
            artists = listOf("ID3 artist"),
            album = null,
            comment = "ID3 comment"
        )
        val info = AudioMetadata(
            title = "INFO title",
            artists = listOf("INFO artist"),
            album = "INFO album",
            comment = "INFO comment"
        )

        val merged = id3.mergeMissingFrom(info)

        assertEquals("ID3 title", merged.title)
        assertEquals(listOf("ID3 artist"), merged.artists)
        assertEquals("INFO album", merged.album)
        assertEquals("ID3 comment", merged.comment)
    }

    @Test
    fun `editable patch contains only fields changed by the user`() {
        val original = EditableSongTags("Title", "Artist", "Album", "1", "2025")

        val edits = original.copy(trackNumber = "", year = "2026")
            .changedFieldsFrom(original)

        assertEquals(setOf(FieldKey.TRACK, FieldKey.YEAR), edits.keys)
        assertTrue(requireNotNull(edits[FieldKey.TRACK]).isClear)
        assertEquals(listOf("2026"), edits[FieldKey.YEAR]?.values)
    }

    @Test
    fun `embedded WAV metadata overrides MediaStore only when available`() {
        val mediaStore = song(
            title = "filename",
            artist = "Unknown Artist",
            album = "MediaStore album"
        )

        val merged = mergeWavEmbeddedMetadata(
            mediaStore,
            AudioMetadata(
                title = "Embedded title",
                artists = listOf("Embedded artist")
            )
        )

        assertEquals("Embedded title", merged.title)
        assertEquals("Embedded artist", merged.artist)
        assertEquals("MediaStore album", merged.album)
    }

    @Test
    fun `WAV without embedded metadata retains MediaStore filename fallbacks`() {
        val mediaStore = song(title = "filename", artist = "Unknown Artist", album = "")

        assertEquals(mediaStore, mergeWavEmbeddedMetadata(mediaStore, AudioMetadata()))
    }

    @Test
    fun `library enrichment keeps Smart Playlist metadata typed and distinct`() {
        val enriched = song("Title", "Artist", "Album").withEmbeddedLibraryMetadata(
            AudioMetadata(
                date = "2026-03-14",
                genres = listOf("Rock", "R&B / Soul"),
                composers = listOf("Composer One", "Composer Two"),
                publisher = "Label",
                bpm = "128",
                discNumber = "2/3"
            )
        )

        assertEquals(listOf("Rock", "R&B / Soul"), enriched.genres)
        assertEquals(listOf("Composer One", "Composer Two"), enriched.composers)
        assertEquals("Label", enriched.publisher)
        assertEquals(128, enriched.bpm)
        assertEquals(2, enriched.discNumber)
        assertEquals(3, enriched.discTotal)
        assertEquals(2026, enriched.year)
        assertEquals(
            CURRENT_EMBEDDED_METADATA_ENRICHMENT_VERSION,
            enriched.embeddedMetadataEnrichmentVersion
        )
    }

    @Test
    fun `metadata year parsing uses one bounded four digit interpretation`() {
        assertEquals(1994, parseMetadataYear("1994"))
        assertEquals(2004, parseMetadataYear("2004-09-21"))
        assertEquals(null, parseMetadataYear("unknown"))
        assertEquals(null, parseMetadataYear("999"))
        assertEquals(null, parseMetadataYear("3000"))
    }

    @Test
    fun `metadata bpm parsing accepts only bounded whole positive values`() {
        assertEquals(128, parseMetadataBpm(" 128 "))
        assertEquals(null, parseMetadataBpm(null))
        assertEquals(null, parseMetadataBpm("120.5"))
        assertEquals(null, parseMetadataBpm("0"))
        assertEquals(null, parseMetadataBpm("1000"))
    }

    @Test
    fun `disc metadata parsing accepts split and combined tag forms`() {
        assertEquals(2, parseMetadataDiscNumber(" 2 "))
        assertEquals(2, parseMetadataDiscNumber("2/3"))
        assertEquals(3, parseMetadataDiscTotal("2/3", null))
        assertEquals(4, parseMetadataDiscTotal("2/3", "4"))
        assertEquals(null, parseMetadataDiscNumber("0/2"))
        assertEquals(null, parseMetadataDiscTotal(null, "unknown"))
    }

    @Test
    fun `WAV merge promotes embedded disc metadata`() {
        val merged = mergeWavEmbeddedMetadata(
            song("Title", "Artist", "Album"),
            AudioMetadata(discNumber = "2/3")
        )

        assertEquals(2, merged.discNumber)
        assertEquals(3, merged.discTotal)
    }

    @Test
    fun `generic library merge preserves MediaStore core fields and adds embedded fields`() {
        val merged = mergeEmbeddedLibraryMetadata(
            song("MediaStore title", "MediaStore artist", "MediaStore album"),
            EmbeddedMetadataReadResult(
                metadata = AudioMetadata(
                    title = "Embedded title",
                    artists = listOf("Embedded artist"),
                    album = "Embedded album",
                    albumArtists = listOf("Embedded album artist"),
                    genres = listOf("Alternative Metal"),
                    publisher = "Label"
                ),
                format = AudioMetadataFormat.FLAC
            )
        )

        assertEquals("MediaStore title", merged.title)
        assertEquals("MediaStore artist", merged.artist)
        assertEquals("MediaStore album", merged.album)
        assertEquals("Embedded album artist", merged.albumArtist)
        assertEquals(listOf("Alternative Metal"), merged.genres)
        assertEquals("Label", merged.publisher)
    }

    @Test
    fun `WAV library merge applies core and generic metadata in one result`() {
        val merged = mergeEmbeddedLibraryMetadata(
            song("filename", "<unknown>", "<unknown>"),
            EmbeddedMetadataReadResult(
                metadata = AudioMetadata(
                    title = "Dust to Dust",
                    artists = listOf("The Warning"),
                    album = "Queen of the Murder Scene",
                    albumArtists = listOf("The Warning"),
                    genres = listOf("Hard Rock"),
                    bpm = "128"
                ),
                format = AudioMetadataFormat.WAV
            )
        )

        assertEquals("Dust to Dust", merged.title)
        assertEquals("The Warning", merged.artist)
        assertEquals("Queen of the Murder Scene", merged.album)
        assertEquals("The Warning", merged.albumArtist)
        assertEquals(listOf("Hard Rock"), merged.genres)
        assertEquals(128, merged.bpm)
        assertEquals(
            CURRENT_EMBEDDED_METADATA_ENRICHMENT_VERSION,
            merged.embeddedMetadataEnrichmentVersion
        )
    }

    @Test
    fun `writable extensions match formats backed by configured jaudiotagger writers`() {
        assertEquals(
            setOf("mp3", "flac", "m4a", "mp4", "ogg", "wav", "aif", "aiff"),
            metadataWritableExtensions
        )
    }

    @Test
    fun `single field FLAC mutation preserves ReplayGain comment`() {
        val tag = FlacTag()
        tag.setField(tag.createField("REPLAYGAIN_TRACK_GAIN", "-7.25 dB"))
        tag.setField(tag.createField("REPLAYGAIN_TRACK_PEAK", "0.9123"))

        applyMetadataTextEdits(
            tag,
            mapOf(FieldKey.GENRE to MetadataTextEdit(listOf("Jazz")))
        )

        assertEquals("Jazz", tag.getFirst(FieldKey.GENRE))
        assertEquals("-7.25 dB", tag.getFirst("REPLAYGAIN_TRACK_GAIN"))
        assertEquals("0.9123", tag.getFirst("REPLAYGAIN_TRACK_PEAK"))
    }

    @Test
    fun `field patches preserve unrelated metadata in each exposed tag family`() {
        val tags: List<Tag> = listOf(
            ID3v24Tag(), // MP3 and AIFF
            FlacTag(),
            Mp4Tag(),
            VorbisCommentTag.createNewTag() // OGG Vorbis
        )

        tags.forEach { tag ->
            tag.setField(FieldKey.TITLE, "Keep title")
            tag.setField(FieldKey.COMMENT, "Keep comment")

            applyMetadataTextEdits(
                tag,
                mapOf(FieldKey.GENRE to MetadataTextEdit(listOf("Jazz")))
            )

            assertEquals("Jazz", tag.getFirst(FieldKey.GENRE))
            assertEquals("Keep title", tag.getFirst(FieldKey.TITLE))
            assertEquals("Keep comment", tag.getFirst(FieldKey.COMMENT))
        }
    }

    private fun song(title: String, artist: String, album: String) = Song(
        id = 1L,
        title = title,
        artist = artist,
        album = album,
        trackNumber = 0,
        duration = 1_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/example.wav",
        folderPath = "/music",
        albumArtUri = null
    )
}
