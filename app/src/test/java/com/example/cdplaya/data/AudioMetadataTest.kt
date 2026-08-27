package com.example.cdplaya.data

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
    fun `library enrichment keeps embedded genre values distinct`() {
        val enriched = song("Title", "Artist", "Album").withEmbeddedLibraryMetadata(
            AudioMetadata(
                date = "2026-03-14",
                genres = listOf("Rock", "R&B / Soul")
            )
        )

        assertEquals(listOf("Rock", "R&B / Soul"), enriched.genres)
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
