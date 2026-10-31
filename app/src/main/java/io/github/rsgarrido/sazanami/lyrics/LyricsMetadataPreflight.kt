package io.github.rsgarrido.sazanami.lyrics

import java.io.File
import java.io.IOException
import java.io.RandomAccessFile

/**
 * Bound declared metadata before jaudiotagger allocates tag buffers. Audio payloads are skipped,
 * never copied. This is not a replacement container parser: library internals may still allocate
 * within the budget. Reject malformed/oversized envelopes rather than attempt recovery here.
 */
internal object LyricsMetadataPreflight {
    const val METADATA_BYTES = 16L * 1024 * 1024
    private const val MAX_HEADERS = 16_384

    fun check(file: File, extension: String, checkCancelled: () -> Unit, onOpened: () -> Unit = {}) {
        RandomAccessFile(file, "r").use { input ->
            onOpened()
            when (extension) {
                "mp3" -> id3(input)
                "flac" -> flac(input, checkCancelled)
                "ogg", "oga" -> ogg(input, checkCancelled)
                "wav" -> chunks(input, littleEndian = true, checkCancelled = checkCancelled)
                "aif", "aiff", "aifc" -> chunks(input, littleEndian = false, checkCancelled = checkCancelled)
                else -> mp4(input, checkCancelled)
            }
        }
    }

    private fun id3(input: RandomAccessFile) {
        if (input.length() < 10) throw IOException("Truncated audio")
        if (input.ascii(3) != "ID3") return
        input.skipBytes(3)
        var size = 0L
        repeat(4) {
            val value = input.readUnsignedByte()
            if (value and 0x80 != 0) throw IOException("Invalid ID3 size")
            size = (size shl 7) or value.toLong()
        }
        budget(size)
        if (size > input.length() - 10) throw IOException("Truncated ID3")
    }

    private fun flac(input: RandomAccessFile, checkCancelled: () -> Unit) {
        if (input.ascii(4) != "fLaC") throw IOException("Not native FLAC")
        var total = 0L
        repeat(MAX_HEADERS) {
            checkCancelled()
            val header = input.readUnsignedByte()
            val size = (input.readUnsignedByte().toLong() shl 16) or
                (input.readUnsignedByte().toLong() shl 8) or input.readUnsignedByte().toLong()
            total += size + 4
            budget(total)
            input.skipChecked(size)
            if (header and 0x80 != 0) return
        }
        throw LyricsInputLimitException("FLAC metadata headers")
    }

    private fun chunks(input: RandomAccessFile, littleEndian: Boolean, checkCancelled: () -> Unit) {
        if (input.ascii(4) != if (littleEndian) "RIFF" else "FORM") throw IOException("Unsupported chunk container")
        input.skipBytes(4)
        val type = input.ascii(4)
        if (if (littleEndian) type != "WAVE" else type !in setOf("AIFF", "AIFC")) throw IOException("Unsupported audio container")
        var total = 0L
        repeat(MAX_HEADERS) {
            checkCancelled()
            if (input.filePointer == input.length()) return
            val kind = input.ascii(4)
            val raw = input.readInt()
            val size = (if (littleEndian) Integer.reverseBytes(raw) else raw).toLong() and 0xffffffffL
            if (kind !in setOf("data", "SSND")) {
                total += size + 8
                budget(total)
            }
            input.skipChecked(size + (size and 1L))
        }
        throw LyricsInputLimitException("audio chunks")
    }

    private fun mp4(input: RandomAccessFile, checkCancelled: () -> Unit) {
        var total = 0L
        repeat(MAX_HEADERS) {
            checkCancelled()
            val start = input.filePointer
            if (start == input.length()) return
            var size = input.readInt().toLong() and 0xffffffffL
            val kind = input.ascii(4)
            var header = 8L
            if (size == 1L) {
                size = input.readLong()
                header = 16L
            } else if (size == 0L) size = input.length() - start
            if (size < header || size > input.length() - start) throw IOException("Invalid MP4 atom")
            // Metadata reader loads moov. Bound other non-audio atoms conservatively as well.
            if (kind !in setOf("mdat", "free", "skip")) {
                total += size
                budget(total)
            }
            input.skipChecked(size - header)
        }
        throw LyricsInputLimitException("MP4 atoms")
    }

    private fun ogg(input: RandomAccessFile, checkCancelled: () -> Unit) {
        var total = 0L
        var completedPackets = 0
        repeat(MAX_HEADERS) {
            checkCancelled()
            if (input.ascii(4) != "OggS" || input.readUnsignedByte() != 0) throw IOException("Invalid Ogg page")
            input.skipBytes(21)
            val segments = input.readUnsignedByte()
            var payload = 0L
            repeat(segments) {
                val size = input.readUnsignedByte()
                payload += size
                if (size < 255) completedPackets++
            }
            total += 27 + segments + payload
            budget(total)
            input.skipChecked(payload)
            if (completedPackets >= 3) return // Vorbis identification, comments and setup.
        }
        throw LyricsInputLimitException("Ogg header pages")
    }

    private fun budget(size: Long) {
        if (size > METADATA_BYTES) throw LyricsInputLimitException("metadata bytes")
    }

    private fun RandomAccessFile.ascii(count: Int): String {
        val bytes = ByteArray(count)
        readFully(bytes)
        return String(bytes, Charsets.ISO_8859_1)
    }

    private fun RandomAccessFile.skipChecked(size: Long) {
        if (size < 0 || size > length() - filePointer) throw IOException("Truncated metadata")
        seek(filePointer + size)
    }
}
