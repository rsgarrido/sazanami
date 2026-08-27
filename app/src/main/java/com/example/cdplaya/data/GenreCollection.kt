package com.example.cdplaya.data

import java.util.Locale

const val UNKNOWN_GENRE_NAME = "Unknown Genre"
const val UNKNOWN_GENRE_KEY = "unknown:genre"

data class GenreCollection(
    val key: String,
    val name: String,
    val songs: List<Song>
)

/**
 * Builds Genre membership from the distinct embedded tag values already carried by [Song].
 * Comparison is trimmed and case-insensitive, while a sensible tag spelling is retained for
 * display. A song is counted only once per normalized genre even if its tags repeat that value.
 */
fun buildGenreCollections(songs: List<Song>): List<GenreCollection> {
    data class GenreAccumulator(
        var name: String,
        val songsByIdentity: LinkedHashMap<String, Song> = linkedMapOf()
    )

    val knownGroups = linkedMapOf<String, GenreAccumulator>()
    val unknownSongs = linkedMapOf<String, Song>()

    songs.forEach { song ->
        val songGenres = song.genres
            .mapNotNull(::cleanGenreDisplayValue)
            .distinctBy(::normalizedGenreKey)

        if (songGenres.isEmpty()) {
            unknownSongs[song.membershipKey()] = song
        } else {
            songGenres.forEach { genre ->
                val key = genreCollectionKey(genre)
                val group = knownGroups.getOrPut(key) { GenreAccumulator(name = genre) }
                group.name = preferableGenreDisplayName(group.name, genre)
                group.songsByIdentity[song.membershipKey()] = song
            }
        }
    }

    val known = knownGroups.map { (key, accumulator) ->
        GenreCollection(
            key = key,
            name = accumulator.name,
            songs = accumulator.songsByIdentity.values.toList()
        )
    }.sortedWith { left, right ->
        String.CASE_INSENSITIVE_ORDER.compare(left.name, right.name)
    }

    return if (unknownSongs.isEmpty()) {
        known
    } else {
        known + GenreCollection(
            key = UNKNOWN_GENRE_KEY,
            name = UNKNOWN_GENRE_NAME,
            songs = unknownSongs.values.toList()
        )
    }
}

fun normalizedGenreKey(value: String): String =
    value.trim().lowercase(Locale.ROOT)

/** Authoritative normalized identities used by both Genre browsing and Smart Playlists. */
fun normalizedKnownGenreKeys(values: List<String>): List<String> = values
    .mapNotNull(::cleanGenreDisplayValue)
    .map(::normalizedGenreKey)
    .distinct()

private fun genreCollectionKey(value: String): String = "known:${normalizedGenreKey(value)}"

private fun preferableGenreDisplayName(current: String, candidate: String): String =
    if (genreDisplayQuality(candidate) > genreDisplayQuality(current)) candidate else current

private fun genreDisplayQuality(value: String): Int {
    val letters = value.filter(Char::isLetter)
    return when {
        letters.isEmpty() -> 1
        letters.any(Char::isUpperCase) && letters.any(Char::isLowerCase) -> 3
        letters.all(Char::isLowerCase) -> 2
        else -> 1
    }
}

private fun cleanGenreDisplayValue(value: String): String? =
    value.trim().takeIf { cleaned ->
        cleaned.isNotEmpty() &&
                !cleaned.equals("<unknown>", ignoreCase = true) &&
                !cleaned.equals(UNKNOWN_GENRE_NAME, ignoreCase = true)
    }
