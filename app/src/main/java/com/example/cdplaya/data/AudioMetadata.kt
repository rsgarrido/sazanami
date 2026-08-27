package com.example.cdplaya.data

/**
 * Format-independent embedded audio metadata.
 *
 * File-format concepts (ID3 frames, Vorbis comments, MP4 atoms and RIFF INFO fields) stay in
 * the metadata adapters. UI models should be derived from this model rather than from a concrete
 * tagging-library type.
 */
data class AudioMetadata(
    val title: String? = null,
    val artists: List<String> = emptyList(),
    val album: String? = null,
    val albumArtists: List<String> = emptyList(),
    val trackNumber: String? = null,
    val trackTotal: String? = null,
    val discNumber: String? = null,
    val discTotal: String? = null,
    val date: String? = null,
    val genres: List<String> = emptyList(),
    val composers: List<String> = emptyList(),
    val comment: String? = null,
    val publisher: String? = null,
    val copyright: String? = null,
    val bpm: String? = null,
    val artwork: List<AudioMetadataArtwork> = emptyList()
) {
    val primaryArtist: String?
        get() = artists.firstOrNull { it.isNotBlank() }

    val primaryAlbumArtist: String?
        get() = albumArtists.firstOrNull { it.isNotBlank() }

    /** Keep values from this instance and fill only missing values from [fallback]. */
    fun mergeMissingFrom(fallback: AudioMetadata): AudioMetadata = copy(
        title = title ?: fallback.title,
        artists = artists.ifEmpty { fallback.artists },
        album = album ?: fallback.album,
        albumArtists = albumArtists.ifEmpty { fallback.albumArtists },
        trackNumber = trackNumber ?: fallback.trackNumber,
        trackTotal = trackTotal ?: fallback.trackTotal,
        discNumber = discNumber ?: fallback.discNumber,
        discTotal = discTotal ?: fallback.discTotal,
        date = date ?: fallback.date,
        genres = genres.ifEmpty { fallback.genres },
        composers = composers.ifEmpty { fallback.composers },
        comment = comment ?: fallback.comment,
        publisher = publisher ?: fallback.publisher,
        copyright = copyright ?: fallback.copyright,
        bpm = bpm ?: fallback.bpm,
        artwork = artwork.ifEmpty { fallback.artwork }
    )
}

class AudioMetadataArtwork(
    val binaryData: ByteArray,
    val mimeType: String,
    val description: String,
    val pictureType: Int
)

enum class AudioMetadataFormat {
    MP3,
    FLAC,
    MP4,
    OGG,
    WAV,
    AIFF,
    UNKNOWN
}

enum class WavMetadataRepresentation {
    RIFF_INFO,
    ID3
}

data class EmbeddedMetadataReadResult(
    val metadata: AudioMetadata,
    val format: AudioMetadataFormat,
    val wavRepresentations: Set<WavMetadataRepresentation> = emptySet()
)

enum class EditableMetadataField {
    ALBUM_ARTIST,
    TRACK_TOTAL,
    DISC_NUMBER,
    DISC_TOTAL,
    GENRE,
    COMPOSER,
    COMMENT,
    PUBLISHER,
    COPYRIGHT,
    BPM
}

data class MetadataFormatCapabilities(
    val supportedFields: Set<EditableMetadataField>
) {
    fun supports(field: EditableMetadataField): Boolean = field in supportedFields

    companion object {
        val NONE = MetadataFormatCapabilities(emptySet())
        val ADVANCED_EDITOR = MetadataFormatCapabilities(EditableMetadataField.entries.toSet())
    }
}

internal fun AudioMetadataFormat.editorCapabilities(): MetadataFormatCapabilities = when (this) {
    AudioMetadataFormat.MP3,
    AudioMetadataFormat.FLAC,
    AudioMetadataFormat.MP4,
    AudioMetadataFormat.OGG,
    AudioMetadataFormat.WAV,
    AudioMetadataFormat.AIFF -> MetadataFormatCapabilities.ADVANCED_EDITOR

    AudioMetadataFormat.UNKNOWN -> MetadataFormatCapabilities.NONE
}
