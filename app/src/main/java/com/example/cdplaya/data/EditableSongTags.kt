package com.example.cdplaya.data

import org.jaudiotagger.tag.FieldKey

data class EditableSongTags(
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: String,
    val year: String,
    val albumArtist: String = "",
    val trackTotal: String = "",
    val discNumber: String = "",
    val discTotal: String = "",
    val genre: String = "",
    val composer: String = "",
    val comment: String = "",
    val publisher: String = "",
    val copyright: String = "",
    val bpm: String = "",
    val capabilities: MetadataFormatCapabilities = MetadataFormatCapabilities.ADVANCED_EDITOR
)

internal enum class MetadataTextOperation {
    SET,
    CLEAR
}

internal data class MetadataTextEdit(
    val values: List<String>,
    val operation: MetadataTextOperation = if (values.isEmpty()) {
        MetadataTextOperation.CLEAR
    } else {
        MetadataTextOperation.SET
    }
) {
    val isClear: Boolean
        get() = operation == MetadataTextOperation.CLEAR
}

internal fun AudioMetadata.toEditableSongTags(
    song: Song,
    capabilities: MetadataFormatCapabilities = MetadataFormatCapabilities.ADVANCED_EDITOR
): EditableSongTags = EditableSongTags(
    title = title ?: song.title,
    artist = artists.toEditableMultiValue().ifBlank { song.artist },
    album = album ?: song.album,
    trackNumber = trackNumber ?: song.trackNumber.takeIf { it > 0 }?.toString().orEmpty(),
    year = date ?: song.year?.toString().orEmpty(),
    albumArtist = albumArtists.toEditableMultiValue().ifBlank { song.albumArtist },
    trackTotal = trackTotal.orEmpty(),
    discNumber = discNumber.orEmpty(),
    discTotal = discTotal.orEmpty(),
    genre = genres.toEditableMultiValue(),
    composer = composers.toEditableMultiValue(),
    comment = comment.orEmpty(),
    publisher = publisher.orEmpty(),
    copyright = copyright.orEmpty(),
    bpm = bpm.orEmpty(),
    capabilities = capabilities
)

internal fun EditableSongTags.changedFieldsFrom(
    original: EditableSongTags
): Map<FieldKey, MetadataTextEdit> = buildMap {
    putSingleIfChanged(FieldKey.TITLE, original.title, title)
    putMultiIfChanged(FieldKey.ARTIST, original.artist, artist)
    putSingleIfChanged(FieldKey.ALBUM, original.album, album)
    putSingleIfChanged(FieldKey.TRACK, original.trackNumber, trackNumber)
    putSingleIfChanged(FieldKey.YEAR, original.year, year)
    putMultiIfChanged(FieldKey.ALBUM_ARTIST, original.albumArtist, albumArtist)
    putSingleIfChanged(FieldKey.TRACK_TOTAL, original.trackTotal, trackTotal)
    putSingleIfChanged(FieldKey.DISC_NO, original.discNumber, discNumber)
    putSingleIfChanged(FieldKey.DISC_TOTAL, original.discTotal, discTotal)
    putMultiIfChanged(FieldKey.GENRE, original.genre, genre)
    putMultiIfChanged(FieldKey.COMPOSER, original.composer, composer)
    putSingleIfChanged(FieldKey.COMMENT, original.comment, comment)
    putSingleIfChanged(FieldKey.RECORD_LABEL, original.publisher, publisher)
    putSingleIfChanged(FieldKey.COPYRIGHT, original.copyright, copyright)
    putSingleIfChanged(FieldKey.BPM, original.bpm, bpm)
}

internal fun String.parseEditableMultiValue(): List<String> =
    split(';').map(String::trim).filter(String::isNotEmpty)

internal fun List<String>.toEditableMultiValue(): String =
    map(String::trim).filter(String::isNotEmpty).joinToString(MULTI_VALUE_SEPARATOR)

internal fun String.isValidMetadataBpm(): Boolean {
    val cleaned = trim()
    if (cleaned.isEmpty()) return true
    return cleaned.toIntOrNull() in 1..999
}

private fun MutableMap<FieldKey, MetadataTextEdit>.putSingleIfChanged(
    fieldKey: FieldKey,
    original: String,
    edited: String
) {
    val originalValue = original.trim()
    val editedValue = edited.trim()
    if (originalValue != editedValue) {
        put(fieldKey, MetadataTextEdit(editedValue.takeIf(String::isNotEmpty)?.let(::listOf).orEmpty()))
    }
}

private fun MutableMap<FieldKey, MetadataTextEdit>.putMultiIfChanged(
    fieldKey: FieldKey,
    original: String,
    edited: String
) {
    if (original.trim() != edited.trim()) {
        put(fieldKey, MetadataTextEdit(edited.parseEditableMultiValue()))
    }
}

private const val MULTI_VALUE_SEPARATOR = "; "
