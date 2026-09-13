package io.github.rsgarrido.sazanami.data

import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.flac.FlacTag
import org.jaudiotagger.tag.id3.ID3v24Tag
import org.jaudiotagger.tag.images.ArtworkFactory
import org.jaudiotagger.tag.mp4.Mp4Tag
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AdvancedMetadataEditingTest {
    @Test
    fun `advanced fields map through normalized metadata`() {
        val tag = FlacTag()
        applyMetadataTextEdits(
            tag,
            mapOf(
                FieldKey.GENRE to edit("Rock", "Alternative"),
                FieldKey.COMPOSER to edit("Composer One", "Composer Two"),
                FieldKey.COMMENT to edit("Session note"),
                FieldKey.RECORD_LABEL to edit("Local Label"),
                FieldKey.COPYRIGHT to edit("Copyright 2026"),
                FieldKey.BPM to edit("128")
            )
        )

        val metadata = tag.toAudioMetadata()

        assertEquals(listOf("Rock", "Alternative"), metadata.genres)
        assertEquals(listOf("Composer One", "Composer Two"), metadata.composers)
        assertEquals("Session note", metadata.comment)
        assertEquals("Local Label", metadata.publisher)
        assertEquals("Copyright 2026", metadata.copyright)
        assertEquals("128", metadata.bpm)
    }

    @Test
    fun `blank changed advanced fields clear only their own keys`() {
        val advancedFields = listOf(
            FieldKey.GENRE,
            FieldKey.COMPOSER,
            FieldKey.COMMENT,
            FieldKey.RECORD_LABEL,
            FieldKey.COPYRIGHT,
            FieldKey.BPM
        )

        advancedFields.forEach { fieldToClear ->
            val tag = FlacTag().apply {
                setField(FieldKey.TITLE, "Keep title")
                advancedFields.forEach { setField(it, "Existing ${it.name}") }
            }

            applyMetadataTextEdits(tag, mapOf(fieldToClear to MetadataTextEdit(emptyList())))

            assertTrue(tag.getAll(fieldToClear).isEmpty())
            assertEquals("Keep title", tag.getFirst(FieldKey.TITLE))
            advancedFields.filterNot { it == fieldToClear }.forEach { untouched ->
                assertEquals("Existing ${untouched.name}", tag.getFirst(untouched))
            }
        }
    }

    @Test
    fun `composer-only edit preserves multiple genres ReplayGain and custom fields`() {
        val tag = FlacTag().apply {
            setField(FieldKey.GENRE, "Rock")
            addField(FieldKey.GENRE, "Alternative")
            setField(FieldKey.COMPOSER, "Old composer")
            setField(createField("REPLAYGAIN_TRACK_GAIN", "-6.75 dB"))
            setField(createField("REPLAYGAIN_TRACK_PEAK", "0.9234"))
            setField(createField("CUSTOM_VENDOR_FIELD", "keep-me"))
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.COMPOSER to edit("New composer")))

        assertEquals(listOf("Rock", "Alternative"), tag.getAll(FieldKey.GENRE))
        assertEquals("New composer", tag.getFirst(FieldKey.COMPOSER))
        assertEquals("-6.75 dB", tag.getFirst("REPLAYGAIN_TRACK_GAIN"))
        assertEquals("0.9234", tag.getFirst("REPLAYGAIN_TRACK_PEAK"))
        assertEquals("keep-me", tag.getFirst("CUSTOM_VENDOR_FIELD"))
    }

    @Test
    fun `untouched multi-values create no patch while explicit edit uses semicolon policy`() {
        val original = EditableSongTags(
            title = "Title",
            artist = "Artist One; Artist Two",
            album = "Album",
            trackNumber = "1",
            year = "2026",
            genre = "Rock; Alternative",
            composer = "Composer One; Composer Two"
        )

        assertTrue(original.changedFieldsFrom(original).isEmpty())

        val edits = original.copy(genre = "Jazz; Fusion ; ; Contemporary")
            .changedFieldsFrom(original)

        assertEquals(setOf(FieldKey.GENRE), edits.keys)
        assertEquals(
            listOf("Jazz", "Fusion", "Contemporary"),
            edits[FieldKey.GENRE]?.values
        )
    }

    @Test
    fun `album artist and track-disc totals use normalized field patches`() {
        val original = EditableSongTags("Title", "Artist", "Album", "2", "2026")
        val edits = original.copy(
            albumArtist = "Album Artist One; Album Artist Two",
            trackTotal = "12",
            discNumber = "1",
            discTotal = "2"
        ).changedFieldsFrom(original)

        assertEquals(
            listOf("Album Artist One", "Album Artist Two"),
            edits[FieldKey.ALBUM_ARTIST]?.values
        )
        assertEquals(listOf("12"), edits[FieldKey.TRACK_TOTAL]?.values)
        assertEquals(listOf("1"), edits[FieldKey.DISC_NO]?.values)
        assertEquals(listOf("2"), edits[FieldKey.DISC_TOTAL]?.values)
    }

    @Test
    fun `advanced edit preserves ID3 artwork and custom frame`() {
        val artworkBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 0xff.toByte(), 0xd9.toByte())
        val artwork = ArtworkFactory.getNew().apply {
            binaryData = artworkBytes
            mimeType = "image/jpeg"
            description = "Cover"
            pictureType = 3
        }
        val tag = ID3v24Tag().apply {
            setField(artwork)
            setField(FieldKey.CUSTOM1, "keep-custom-frame")
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.COMPOSER to edit("Composer")))

        assertArrayEquals(artworkBytes, tag.firstArtwork.binaryData)
        assertEquals("keep-custom-frame", tag.getFirst(FieldKey.CUSTOM1))
    }

    @Test
    fun `MP4 track number edit preserves existing track total`() {
        val tag = Mp4Tag().apply {
            setField(FieldKey.TRACK, "3")
            setField(FieldKey.TRACK_TOTAL, "12")
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.TRACK to edit("4")))

        assertEquals("4", tag.getFirst(FieldKey.TRACK))
        assertEquals("12", tag.getFirst(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun `MP4 track number edit remains valid without a track total`() {
        val tag = Mp4Tag().apply {
            setField(FieldKey.TRACK, "3")
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.TRACK to edit("4")))

        assertEquals("4", tag.getFirst(FieldKey.TRACK))
        assertEquals("", tag.getFirst(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun `MP4 track total edit preserves existing track number`() {
        val tag = Mp4Tag().apply {
            setField(FieldKey.TRACK, "3")
            setField(FieldKey.TRACK_TOTAL, "12")
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.TRACK_TOTAL to edit("14")))

        assertEquals("3", tag.getFirst(FieldKey.TRACK))
        assertEquals("14", tag.getFirst(FieldKey.TRACK_TOTAL))
    }

    @Test
    fun `MP4 disc edits preserve the other paired value`() {
        val discNumberEdit = Mp4Tag().apply {
            setField(FieldKey.DISC_NO, "1")
            setField(FieldKey.DISC_TOTAL, "3")
        }
        val discTotalEdit = Mp4Tag().apply {
            setField(FieldKey.DISC_NO, "1")
            setField(FieldKey.DISC_TOTAL, "3")
        }

        applyMetadataTextEdits(discNumberEdit, mapOf(FieldKey.DISC_NO to edit("2")))
        applyMetadataTextEdits(discTotalEdit, mapOf(FieldKey.DISC_TOTAL to edit("4")))

        assertEquals("2", discNumberEdit.getFirst(FieldKey.DISC_NO))
        assertEquals("3", discNumberEdit.getFirst(FieldKey.DISC_TOTAL))
        assertEquals("1", discTotalEdit.getFirst(FieldKey.DISC_NO))
        assertEquals("4", discTotalEdit.getFirst(FieldKey.DISC_TOTAL))
    }

    @Test
    fun `MP4 clearing one paired value preserves its counterpart`() {
        val track = Mp4Tag().apply {
            setField(FieldKey.TRACK, "3")
            setField(FieldKey.TRACK_TOTAL, "12")
        }
        val disc = Mp4Tag().apply {
            setField(FieldKey.DISC_NO, "1")
            setField(FieldKey.DISC_TOTAL, "2")
        }

        applyMetadataTextEdits(track, mapOf(FieldKey.TRACK_TOTAL to MetadataTextEdit(emptyList())))
        applyMetadataTextEdits(disc, mapOf(FieldKey.DISC_NO to MetadataTextEdit(emptyList())))

        assertEquals("3", track.getFirst(FieldKey.TRACK))
        assertEquals("", track.getFirst(FieldKey.TRACK_TOTAL))
        assertEquals("", disc.getFirst(FieldKey.DISC_NO))
        assertEquals("2", disc.getFirst(FieldKey.DISC_TOTAL))
    }

    @Test
    fun `MP4 text edit preserves artwork and unrelated fields`() {
        val artworkBytes = byteArrayOf(0xff.toByte(), 0xd8.toByte(), 1, 2, 3, 0xff.toByte(), 0xd9.toByte())
        val artwork = ArtworkFactory.getNew().apply {
            binaryData = artworkBytes
            mimeType = "image/jpeg"
            description = "Cover"
            pictureType = 3
        }
        val tag = Mp4Tag().apply {
            setField(FieldKey.TITLE, "Old title")
            setField(FieldKey.ALBUM, "Keep album")
            setField(artwork)
        }

        applyMetadataTextEdits(tag, mapOf(FieldKey.TITLE to edit("New title")))

        assertEquals("New title", tag.getFirst(FieldKey.TITLE))
        assertEquals("Keep album", tag.getFirst(FieldKey.ALBUM))
        assertArrayEquals(artworkBytes, tag.firstArtwork.binaryData)
    }

    @Test
    fun `capabilities expose advanced fields only for writable metadata formats`() {
        val supportedFormats = listOf(
            AudioMetadataFormat.MP3,
            AudioMetadataFormat.FLAC,
            AudioMetadataFormat.MP4,
            AudioMetadataFormat.OGG,
            AudioMetadataFormat.WAV,
            AudioMetadataFormat.AIFF
        )

        supportedFormats.forEach { format ->
            EditableMetadataField.entries.forEach { field ->
                assertTrue("$format should support $field", format.editorCapabilities().supports(field))
            }
        }
        EditableMetadataField.entries.forEach { field ->
            assertFalse(AudioMetadataFormat.UNKNOWN.editorCapabilities().supports(field))
        }
    }

    private fun edit(vararg values: String) = MetadataTextEdit(values.toList())
}
