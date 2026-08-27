package com.example.cdplaya.data

import android.net.Uri
import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.audio.wav.WavOptions
import org.jaudiotagger.audio.wav.WavSaveOptions
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.TagOptionSingleton
import org.jaudiotagger.tag.wav.WavTag
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import java.io.ByteArrayOutputStream
import java.io.File

class WavMetadataIntegrationTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @After
    fun restoreWavOptions() {
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_UNLESS_ONLY_INFO)
            setWavSaveOptions(WavSaveOptions.SAVE_BOTH)
        }
    }

    @Test
    fun `explicit edit writes both WAV representations and preserves audio and unknown chunks`() {
        val file = minimalWav("untagged.wav")
        val originalAudio = requireChunk(file.readBytes(), "data")
        val originalUnknown = requireChunk(file.readBytes(), "Xtra")
        val originalBext = requireChunk(file.readBytes(), "bext")
        val originalIXml = requireChunk(file.readBytes(), "iXML")
        val repository = TagEditorRepository()

        val result = repository.writeTags(
            song(file),
            EditableSongTags(
                title = "Direct title",
                artist = "Direct artist",
                album = "Direct album",
                trackNumber = "7",
                year = "2026"
            )
        )

        assertTrue(result.message, result.wasSuccessful)
        val reread = AudioFileIO.read(file)
        val wavTag = reread.tag as WavTag
        assertTrue(wavTag.isExistingId3Tag())
        assertTrue(wavTag.isExistingInfoTag())
        assertEquals("Direct title", clean(wavTag.getID3Tag().getFirst(FieldKey.TITLE)))
        assertEquals("Direct title", clean(wavTag.getInfoTag().getFirst(FieldKey.TITLE)))
        assertEquals("Direct artist", clean(wavTag.getID3Tag().getFirst(FieldKey.ARTIST)))
        assertEquals("Direct artist", clean(wavTag.getInfoTag().getFirst(FieldKey.ARTIST)))
        assertArrayEquals(originalAudio, requireChunk(file.readBytes(), "data"))
        assertArrayEquals(originalUnknown, requireChunk(file.readBytes(), "Xtra"))
        assertArrayEquals(originalBext, requireChunk(file.readBytes(), "bext"))
        assertArrayEquals(originalIXml, requireChunk(file.readBytes(), "iXML"))
        assertTrue(reread.audioHeader.trackLength >= 0)
    }

    @Test
    fun `WAV read precedence is ID3 then INFO and scan does not synchronize conflicts`() {
        val file = minimalWav("both.wav")
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_ONLY)
            setWavSaveOptions(WavSaveOptions.SAVE_BOTH)
        }
        val audioFile = AudioFileIO.read(file)
        val wavTag = audioFile.tag as WavTag
        wavTag.getID3Tag().setField(FieldKey.TITLE, "ID3 title")
        wavTag.getInfoTag().setField(FieldKey.TITLE, "INFO title")
        wavTag.getInfoTag().setField(FieldKey.ARTIST, "INFO artist")
        AudioFileIO.write(audioFile)

        val beforeRead = file.readBytes()
        val result = EmbeddedMetadataReader().read(file)

        assertEquals("ID3 title", result.metadata.title)
        assertEquals("INFO artist", result.metadata.primaryArtist)
        assertEquals(
            setOf(WavMetadataRepresentation.ID3, WavMetadataRepresentation.RIFF_INFO),
            result.wavRepresentations
        )
        assertArrayEquals(beforeRead, file.readBytes())
    }

    @Test
    fun `reader supports RIFF INFO only WAV`() {
        val file = minimalWav("info-only.wav")
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_INFO_ONLY)
            setWavSaveOptions(WavSaveOptions.SAVE_ACTIVE)
        }
        val audioFile = AudioFileIO.read(file)
        (audioFile.tag as WavTag).getInfoTag().setField(FieldKey.TITLE, "INFO title")
        AudioFileIO.write(audioFile)

        val result = EmbeddedMetadataReader().read(file)

        assertEquals("INFO title", result.metadata.title)
        assertEquals(setOf(WavMetadataRepresentation.RIFF_INFO), result.wavRepresentations)
    }

    @Test
    fun `reader supports ID3 only WAV`() {
        val file = minimalWav("id3-only.wav")
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_ONLY)
            setWavSaveOptions(WavSaveOptions.SAVE_ACTIVE)
        }
        val audioFile = AudioFileIO.read(file)
        (audioFile.tag as WavTag).getID3Tag().setField(FieldKey.TITLE, "ID3 title")
        AudioFileIO.write(audioFile)

        val result = EmbeddedMetadataReader().read(file)

        assertEquals("ID3 title", result.metadata.title)
        assertEquals(setOf(WavMetadataRepresentation.ID3), result.wavRepresentations)
    }

    @Test
    fun `editing one field keeps unrelated ID3 fields`() {
        val file = minimalWav("preserve.wav")
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_ONLY)
            setWavSaveOptions(WavSaveOptions.SAVE_BOTH)
        }
        val initial = AudioFileIO.read(file)
        val initialTag = initial.tag as WavTag
        initialTag.getID3Tag().setField(FieldKey.TITLE, "Old title")
        initialTag.getID3Tag().setField(FieldKey.ARTIST, "Artist")
        initialTag.getID3Tag().setField(FieldKey.ALBUM, "Album")
        initialTag.getID3Tag().setField(FieldKey.CATALOG_NO, "CAT-001")
        AudioFileIO.write(initial)

        val repository = TagEditorRepository()
        val result = repository.writeTags(
            song(file).copy(title = "Old title", artist = "Artist", album = "Album"),
            EditableSongTags("New title", "Artist", "Album", "", "")
        )

        assertTrue(result.message, result.wasSuccessful)
        val updated = (AudioFileIO.read(file).tag as WavTag).getID3Tag()
        assertEquals("New title", clean(updated.getFirst(FieldKey.TITLE)))
        assertEquals("CAT-001", clean(updated.getFirst(FieldKey.CATALOG_NO)))
    }

    @Test
    fun `advanced WAV edit preserves rich ID3 metadata and unrelated RIFF chunks`() {
        val file = minimalWav("advanced.wav")
        TagOptionSingleton.getInstance().apply {
            setWavOptions(WavOptions.READ_ID3_ONLY)
            setWavSaveOptions(WavSaveOptions.SAVE_BOTH)
        }
        val initial = AudioFileIO.read(file)
        val initialTag = initial.tag as WavTag
        initialTag.getID3Tag().apply {
            setField(FieldKey.TITLE, "Keep title")
            setField(FieldKey.ARTIST, "Keep artist")
            setField(FieldKey.GENRE, "Rock")
            addField(FieldKey.GENRE, "Alternative")
            setField(FieldKey.COMPOSER, "Old composer")
            setField(FieldKey.CATALOG_NO, "CAT-KEEP")
        }
        initialTag.getInfoTag().apply {
            setField(FieldKey.TITLE, "Keep title")
            setField(FieldKey.ARTIST, "Keep artist")
            setField(FieldKey.GENRE, "Rock")
            addField(FieldKey.GENRE, "Alternative")
            setField(FieldKey.COMPOSER, "Old composer")
        }
        AudioFileIO.write(initial)
        val before = file.readBytes()
        val originalAudio = requireChunk(before, "data")
        val originalBext = requireChunk(before, "bext")
        val originalIXml = requireChunk(before, "iXML")
        val originalUnknown = requireChunk(before, "Xtra")

        val repository = TagEditorRepository()
        val song = song(file).copy(title = "Keep title", artist = "Keep artist")
        val edited = repository.readTags(song).copy(
            composer = "New composer",
            bpm = "123"
        )
        val result = repository.writeTags(song, edited)

        assertTrue(result.message, result.wasSuccessful)
        val updated = AudioFileIO.read(file).tag as WavTag
        assertEquals("Keep title", clean(updated.getID3Tag().getFirst(FieldKey.TITLE)))
        assertEquals(listOf("Rock", "Alternative"), updated.getID3Tag().getAll(FieldKey.GENRE))
        assertEquals("New composer", clean(updated.getID3Tag().getFirst(FieldKey.COMPOSER)))
        assertEquals("New composer", clean(updated.getInfoTag().getFirst(FieldKey.COMPOSER)))
        assertEquals("123", clean(updated.getID3Tag().getFirst(FieldKey.BPM)))
        assertTrue(runCatching { updated.getInfoTag().getFirst(FieldKey.BPM) }.getOrDefault("").isBlank())
        assertEquals("CAT-KEEP", clean(updated.getID3Tag().getFirst(FieldKey.CATALOG_NO)))
        val after = file.readBytes()
        assertArrayEquals(originalAudio, requireChunk(after, "data"))
        assertArrayEquals(originalBext, requireChunk(after, "bext"))
        assertArrayEquals(originalIXml, requireChunk(after, "iXML"))
        assertArrayEquals(originalUnknown, requireChunk(after, "Xtra"))
    }

    @Test
    fun `no-op repository save leaves WAV byte-for-byte unchanged`() {
        val file = minimalWav("no-op.wav")
        val repository = TagEditorRepository()
        val song = song(file)
        val unchangedTags = repository.readTags(song)
        val before = file.readBytes()

        val result = repository.writeTags(song, unchangedTags)

        assertTrue(result.message, result.wasSuccessful)
        assertEquals("No metadata changes to save.", result.message)
        assertArrayEquals(before, file.readBytes())
    }

    @Test
    fun `invalid BPM is rejected before WAV is written`() {
        val file = minimalWav("invalid-bpm.wav")
        val repository = TagEditorRepository()
        val song = song(file)
        val invalidTags = repository.readTags(song).copy(bpm = "0")
        val before = file.readBytes()

        val result = repository.writeTags(song, invalidTags)

        assertTrue(!result.wasSuccessful)
        assertEquals("BPM must be a whole number from 1 to 999.", result.message)
        assertArrayEquals(before, file.readBytes())
    }

    private fun song(file: File) = Song(
        id = 1L,
        title = "untagged",
        artist = "Unknown Artist",
        album = "Unknown Album",
        trackNumber = 0,
        duration = 100L,
        uri = mock(Uri::class.java),
        filePath = file.absolutePath,
        folderPath = file.parent.orEmpty(),
        albumArtUri = null,
        displayName = file.name
    )

    private fun minimalWav(name: String): File {
        val format = ByteArrayOutputStream().apply {
            writeLittleEndianShort(1)
            writeLittleEndianShort(1)
            writeLittleEndianInt(8_000)
            writeLittleEndianInt(16_000)
            writeLittleEndianShort(2)
            writeLittleEndianShort(16)
        }.toByteArray()
        val audio = ByteArray(256) { index -> (index % 31).toByte() }
        val unknown = byteArrayOf(9, 8, 7, 6, 5, 4)
        val chunks = ByteArrayOutputStream().apply {
            writeChunk("fmt ", format)
            writeChunk("bext", "minimal-broadcast-extension".toByteArray())
            writeChunk("iXML", "<BWFXML><PROJECT>fixture</PROJECT></BWFXML>".toByteArray())
            writeChunk("Xtra", unknown)
            writeChunk("data", audio)
        }.toByteArray()
        val wav = ByteArrayOutputStream().apply {
            write("RIFF".toByteArray(Charsets.US_ASCII))
            writeLittleEndianInt(chunks.size + 4)
            write("WAVE".toByteArray(Charsets.US_ASCII))
            write(chunks)
        }.toByteArray()

        return temporaryFolder.newFile(name).apply { writeBytes(wav) }
    }

    private fun requireChunk(wav: ByteArray, id: String): ByteArray {
        var offset = 12
        while (offset + 8 <= wav.size) {
            val chunkId = wav.copyOfRange(offset, offset + 4).toString(Charsets.US_ASCII)
            val size = littleEndianInt(wav, offset + 4)
            val dataStart = offset + 8
            if (chunkId == id) return wav.copyOfRange(dataStart, dataStart + size)
            offset = dataStart + size + (size and 1)
        }
        error("Missing WAV chunk $id")
    }

    private fun ByteArrayOutputStream.writeChunk(id: String, bytes: ByteArray) {
        write(id.toByteArray(Charsets.US_ASCII))
        writeLittleEndianInt(bytes.size)
        write(bytes)
        if (bytes.size % 2 != 0) write(0)
    }

    private fun ByteArrayOutputStream.writeLittleEndianShort(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
    }

    private fun ByteArrayOutputStream.writeLittleEndianInt(value: Int) {
        write(value and 0xff)
        write((value ushr 8) and 0xff)
        write((value ushr 16) and 0xff)
        write((value ushr 24) and 0xff)
    }

    private fun littleEndianInt(bytes: ByteArray, offset: Int): Int =
        (bytes[offset].toInt() and 0xff) or
            ((bytes[offset + 1].toInt() and 0xff) shl 8) or
            ((bytes[offset + 2].toInt() and 0xff) shl 16) or
            ((bytes[offset + 3].toInt() and 0xff) shl 24)

    private fun clean(value: String): String = value.trim().trimEnd('\u0000').trim()
}
