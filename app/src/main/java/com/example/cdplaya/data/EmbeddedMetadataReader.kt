package com.example.cdplaya.data

import org.jaudiotagger.audio.AudioFileIO
import org.jaudiotagger.tag.FieldKey
import org.jaudiotagger.tag.Tag
import org.jaudiotagger.tag.wav.WavTag
import java.io.File

/** Reads embedded tags without applying MediaStore or filename fallbacks. */
class EmbeddedMetadataReader {
    fun read(file: File): EmbeddedMetadataReadResult {
        val audioFile = AudioFileIO.read(file)
        val tag = audioFile.tag
        val extension = file.extension.lowercase()

        if (tag is WavTag) {
            return readWavMetadata(tag)
        }

        return EmbeddedMetadataReadResult(
            metadata = tag?.toAudioMetadata() ?: AudioMetadata(),
            format = extension.toAudioMetadataFormat()
        )
    }

    fun readOrNull(file: File): EmbeddedMetadataReadResult? = try {
        read(file)
    } catch (_: Exception) {
        null
    } catch (_: LinkageError) {
        null
    }
}

/**
 * WAV precedence is ID3 first, then RIFF INFO for fields absent from ID3.
 *
 * This is deliberately a read merge only. Merely scanning a file never rewrites or synchronizes
 * conflicting representations.
 */
internal fun readWavMetadata(tag: WavTag): EmbeddedMetadataReadResult {
    val representations = buildSet {
        if (tag.isExistingInfoTag()) add(WavMetadataRepresentation.RIFF_INFO)
        if (tag.isExistingId3Tag()) add(WavMetadataRepresentation.ID3)
    }
    val id3Metadata = if (tag.isExistingId3Tag()) {
        tag.getID3Tag().toAudioMetadata()
    } else {
        AudioMetadata()
    }
    val infoMetadata = if (tag.isExistingInfoTag()) {
        tag.getInfoTag().toAudioMetadata()
    } else {
        AudioMetadata()
    }

    return EmbeddedMetadataReadResult(
        metadata = id3Metadata.mergeMissingFrom(infoMetadata),
        format = AudioMetadataFormat.WAV,
        wavRepresentations = representations
    )
}

internal fun Tag.toAudioMetadata(): AudioMetadata = AudioMetadata(
    title = firstOrNull(FieldKey.TITLE),
    artists = allNonBlank(FieldKey.ARTIST),
    album = firstOrNull(FieldKey.ALBUM),
    albumArtist = firstOrNull(FieldKey.ALBUM_ARTIST),
    trackNumber = firstOrNull(FieldKey.TRACK),
    trackTotal = firstOrNull(FieldKey.TRACK_TOTAL),
    discNumber = firstOrNull(FieldKey.DISC_NO),
    discTotal = firstOrNull(FieldKey.DISC_TOTAL),
    date = firstOrNull(FieldKey.YEAR),
    genre = firstOrNull(FieldKey.GENRE),
    composer = firstOrNull(FieldKey.COMPOSER),
    comment = firstOrNull(FieldKey.COMMENT),
    publisher = firstOrNull(FieldKey.RECORD_LABEL),
    copyright = firstOrNull(FieldKey.COPYRIGHT),
    bpm = firstOrNull(FieldKey.BPM),
    artwork = artworkList.mapNotNull { artwork ->
        val bytes = artwork.binaryData ?: return@mapNotNull null
        if (bytes.isEmpty()) return@mapNotNull null
        AudioMetadataArtwork(
            binaryData = bytes,
            mimeType = artwork.mimeType.orEmpty(),
            description = artwork.description.orEmpty(),
            pictureType = artwork.pictureType
        )
    }
)

private fun Tag.firstOrNull(fieldKey: FieldKey): String? = try {
    getFirst(fieldKey).cleanMetadataValue()
} catch (_: Exception) {
    null
}

private fun Tag.allNonBlank(fieldKey: FieldKey): List<String> = try {
    getAll(fieldKey).mapNotNull(String::cleanMetadataValue)
} catch (_: Exception) {
    firstOrNull(fieldKey)?.let(::listOf).orEmpty()
}

private fun String.cleanMetadataValue(): String? =
    trim().trimEnd('\u0000').trim().takeIf { it.isNotEmpty() }

private fun String.toAudioMetadataFormat(): AudioMetadataFormat = when (this) {
    "mp3" -> AudioMetadataFormat.MP3
    "flac" -> AudioMetadataFormat.FLAC
    "m4a", "mp4", "m4b", "m4p" -> AudioMetadataFormat.MP4
    "ogg", "oga" -> AudioMetadataFormat.OGG
    "wav" -> AudioMetadataFormat.WAV
    "aif", "aifc", "aiff" -> AudioMetadataFormat.AIFF
    else -> AudioMetadataFormat.UNKNOWN
}
