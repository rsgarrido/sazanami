package com.example.cdplaya.data

/**
 * Returns the explicit/encoded disc number CDPlaya can trust for this song.
 *
 * MediaStore sometimes encodes disc + track as 1001, 1002, 2001, ... . Explicit embedded
 * DISC_NO metadata is preferred when it agrees with MediaStore; conflicting evidence is treated
 * as unknown. Otherwise the legacy encoded form remains supported.
 */
fun Song.knownDiscNumber(): Int? {
    val explicitDisc = discNumber?.takeIf { it > 0 }
    val encodedDisc = encodedDiscNumber(trackNumber)
    if (explicitDisc != null && encodedDisc != null && explicitDisc != encodedDisc) return null
    return explicitDisc ?: encodedDisc
}

/** Returns the track number within a disc, preserving MediaStore's legacy encoded form. */
fun Song.trackNumberWithinDisc(): Int? {
    if (trackNumber <= 0) return null
    val encodedTrack = encodedTrackNumber(trackNumber)
    return encodedTrack ?: trackNumber
}

/**
 * Resolves per-song disc numbers for an album-sized set.
 *
 * A song with missing disc metadata may inherit a disc number only from its own folder, and only
 * when all known songs in that folder agree on exactly one disc. This is intentionally
 * conservative and is used only for album ordering/presentation after the folder itself has
 * supplied unambiguous evidence.
 */
fun resolveAlbumDiscNumbers(songs: List<Song>): Map<String, Int?> {
    val folderDiscNumbers = songs
        .groupBy(Song::folderPath)
        .mapValues { (_, folderSongs) ->
            folderSongs.mapNotNull(Song::knownDiscNumber).distinct().singleOrNull()
        }

    return songs.associate { song ->
        song.membershipKey() to (song.knownDiscNumber() ?: folderDiscNumbers[song.folderPath])
    }
}

fun parseMetadataDiscNumber(rawDiscNumber: String?): Int? =
    parsePositiveMetadataNumber(rawDiscNumber?.substringBefore('/'))

fun parseMetadataDiscTotal(rawDiscNumber: String?, rawDiscTotal: String?): Int? {
    val explicitTotal = parsePositiveMetadataNumber(rawDiscTotal?.substringBefore('/'))
    if (explicitTotal != null) return explicitTotal

    return rawDiscNumber
        ?.substringAfter('/', missingDelimiterValue = "")
        ?.let(::parsePositiveMetadataNumber)
}

private fun parsePositiveMetadataNumber(rawValue: String?): Int? = rawValue
    ?.trim()
    ?.toIntOrNull()
    ?.takeIf { value -> value in 1..999 }

private fun encodedDiscNumber(rawTrackNumber: Int): Int? {
    if (rawTrackNumber < 1_000) return null
    val disc = rawTrackNumber / 1_000
    val track = rawTrackNumber % 1_000
    return disc.takeIf { it > 0 && track > 0 }
}

private fun encodedTrackNumber(rawTrackNumber: Int): Int? {
    if (rawTrackNumber < 1_000) return null
    val track = rawTrackNumber % 1_000
    return track.takeIf { it > 0 }
}
