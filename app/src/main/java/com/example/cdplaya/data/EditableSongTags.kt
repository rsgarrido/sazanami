package com.example.cdplaya.data

data class EditableSongTags(
    val title: String,
    val artist: String,
    val album: String,
    val trackNumber: String,
    val year: String
)

internal fun AudioMetadata.toEditableSongTags(song: Song): EditableSongTags = EditableSongTags(
    title = title ?: song.title,
    artist = primaryArtist ?: song.artist,
    album = album ?: song.album,
    trackNumber = trackNumber ?: song.trackNumber.takeIf { it > 0 }?.toString().orEmpty(),
    year = date ?: song.year?.toString().orEmpty()
)

internal fun EditableSongTags.changedFieldsFrom(
    original: EditableSongTags
): Map<org.jaudiotagger.tag.FieldKey, String?> = buildMap {
    putIfChanged(org.jaudiotagger.tag.FieldKey.TITLE, original.title, title)
    putIfChanged(org.jaudiotagger.tag.FieldKey.ARTIST, original.artist, artist)
    putIfChanged(org.jaudiotagger.tag.FieldKey.ALBUM, original.album, album)
    putIfChanged(org.jaudiotagger.tag.FieldKey.TRACK, original.trackNumber, trackNumber)
    putIfChanged(org.jaudiotagger.tag.FieldKey.YEAR, original.year, year)
}

private fun MutableMap<org.jaudiotagger.tag.FieldKey, String?>.putIfChanged(
    fieldKey: org.jaudiotagger.tag.FieldKey,
    original: String,
    edited: String
) {
    val originalValue = original.trim()
    val editedValue = edited.trim()
    if (originalValue != editedValue) {
        put(fieldKey, editedValue.takeIf(String::isNotEmpty))
    }
}
