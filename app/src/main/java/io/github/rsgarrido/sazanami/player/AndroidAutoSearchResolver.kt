package io.github.rsgarrido.sazanami.player

import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.library.buildLibraryAlbumGroups
import io.github.rsgarrido.sazanami.ui.library.buildLibraryArtistGroups
import java.text.Normalizer
import java.util.Locale

data class AndroidAutoSearchRequest(
    val query: String? = null,
    val title: String? = null,
    val artist: String? = null,
    val album: String? = null,
    val playlist: String? = null,
    val genre: String? = null
)

data class AndroidAutoPlaybackMatch(
    val songs: List<Song>,
    val startIndex: Int
) {
    val selectedSong: Song get() = songs[startIndex]
}

object AndroidAutoSearchResolver {
    fun resolveSongSelection(
        songId: Long,
        catalog: AndroidAutoCatalogSnapshot
    ): AndroidAutoPlaybackMatch? = catalog.songs
        .firstOrNull { song -> song.id == songId }
        ?.let { song -> contextForSong(song, catalog.songs) }

    fun resolvePlayback(
        request: AndroidAutoSearchRequest,
        catalog: AndroidAutoCatalogSnapshot,
        preferredSongId: Long? = null
    ): AndroidAutoPlaybackMatch? {
        if (catalog.songs.isEmpty()) return null

        val hasExplicitSearchTerm = listOf(
            request.query,
            request.title,
            request.artist,
            request.album,
            request.playlist,
            request.genre
        ).any { value -> value.cleanQuery() != null }

        request.playlist.cleanQuery()?.let { requestedPlaylist ->
            catalog.playlists.bestNamedMatch(requestedPlaylist)?.let { playlist ->
                return playlist.songs.toPlaybackMatch(preferredSongId)
            }
        }

        val requestedTitle = request.title.cleanQuery()
        val requestedArtist = request.artist.cleanQuery()
        if (requestedTitle != null) {
            bestSongMatch(
                songs = catalog.songs,
                title = requestedTitle,
                artist = requestedArtist
            )?.let { song ->
                return contextForSong(song, catalog.songs)
            }
        }

        request.album.cleanQuery()?.let { requestedAlbum ->
            val album = buildLibraryAlbumGroups(catalog.songs)
                .filter { group -> requestedArtist == null || textMatches(group.artistText, requestedArtist) }
                .bestNamedMatch(requestedAlbum) { group -> group.title }
            if (album != null) return album.songs.toPlaybackMatch(preferredSongId)
        }

        if (requestedArtist != null) {
            val artist = buildLibraryArtistGroups(catalog.songs)
                .bestNamedMatch(requestedArtist) { group -> group.name }
            if (artist != null) return artist.songs.toPlaybackMatch(preferredSongId)
        }

        request.genre.cleanQuery()?.let { requestedGenre ->
            val genreSongs = catalog.songs.filter { song ->
                song.genres.any { genre -> textMatches(genre, requestedGenre) }
            }
            if (genreSongs.isNotEmpty()) return genreSongs.toPlaybackMatch(preferredSongId)
        }

        val rawQuery = request.query.cleanQuery()?.stripVoicePlayPrefix()
        if (rawQuery != null) {
            if (rawQuery.isGenericLibraryRequest()) {
                return catalog.songs.toPlaybackMatch(preferredSongId)
            }

            parseTitleByArtist(rawQuery)?.let { (title, artist) ->
                bestSongMatch(catalog.songs, title, artist)?.let { song ->
                    return contextForSong(song, catalog.songs)
                }
            }

            // Voice ranking is deliberately separate from durable identity resolution.
            // Exact title > exact playlist > exact album > exact artist > exact genre;
            // a partial collection name must never steal an exact match of another kind.
            catalog.songs
                .filter { song -> normalized(song.title) == normalized(rawQuery) }
                .sortedWith(songSearchTieBreaker)
                .firstOrNull()
                ?.let { song -> return contextForSong(song, catalog.songs) }

            catalog.playlists.exactNamedMatch(rawQuery) { it.name }?.let { playlist ->
                return playlist.songs.toPlaybackMatch(preferredSongId)
            }

            val albums = buildLibraryAlbumGroups(catalog.songs)
            val artists = buildLibraryArtistGroups(catalog.songs)
            albums.exactNamedMatch(rawQuery) { group -> group.title }
                ?.let { album -> return album.songs.toPlaybackMatch(preferredSongId) }

            artists.exactNamedMatch(rawQuery) { group -> group.name }
                ?.let { artist -> return artist.songs.toPlaybackMatch(preferredSongId) }

            val genreSongs = catalog.songs.filter { song ->
                song.genres.any { normalized(it) == normalized(rawQuery) }
            }
            if (genreSongs.isNotEmpty()) return genreSongs.toPlaybackMatch(preferredSongId)

            catalog.playlists.bestNamedMatch(rawQuery)?.let {
                return it.songs.toPlaybackMatch(preferredSongId)
            }
            albums.bestNamedMatch(rawQuery) { it.title }?.let {
                return it.songs.toPlaybackMatch(preferredSongId)
            }
            artists.bestNamedMatch(rawQuery) { it.name }?.let {
                return it.songs.toPlaybackMatch(preferredSongId)
            }

            searchSongs(rawQuery, catalog, limit = 1).firstOrNull()?.let { song ->
                return contextForSong(song, catalog.songs)
            }
        }

        if (hasExplicitSearchTerm) return null

        val preferredIndex = preferredSongId?.let { id ->
            catalog.songs.indexOfFirst { song -> song.id == id }.takeIf { it >= 0 }
        } ?: 0
        return AndroidAutoPlaybackMatch(catalog.songs, preferredIndex)
    }

    fun searchSongs(
        query: String,
        catalog: AndroidAutoCatalogSnapshot,
        limit: Int = 100
    ): List<Song> {
        val cleaned = query.cleanQuery()?.stripVoicePlayPrefix() ?: return emptyList()
        if (cleaned.isGenericLibraryRequest()) return catalog.songs.take(limit.coerceAtLeast(0))
        val titleByArtist = parseTitleByArtist(cleaned)
        val queryTokens = normalized(cleaned).split(' ').filter(String::isNotBlank)
        val playlistSongs = catalog.playlists.bestNamedMatch(cleaned)?.songs.orEmpty().map { it.id }.toSet()
        return catalog.songs
            .map { song -> song to maxOf(
                searchScore(song, cleaned, titleByArtist, queryTokens),
                if (song.id in playlistSongs) 60 else 0,
                if (song.genres.any { textMatches(it, cleaned) }) 60 else 0
            ) }
            .filter { (_, score) -> score > 0 }
            .sortedWith(
                compareByDescending<Pair<Song, Int>> { (_, score) -> score }
                    .thenBy { (song, _) -> song.title.lowercase(Locale.ROOT) }
                    .thenBy { (song, _) -> song.artist.lowercase(Locale.ROOT) }
                    .thenBy { (song, _) -> song.id }
            )
            .take(limit.coerceAtLeast(0))
            .map(Pair<Song, Int>::first)
    }

    private fun searchScore(
        song: Song,
        query: String,
        titleByArtist: Pair<String, String>?,
        queryTokens: List<String>
    ): Int {
        val title = normalized(song.title)
        val artist = normalized(song.artist)
        val album = normalized(song.album)
        val normalizedQuery = normalized(query)

        if (titleByArtist != null) {
            val requestedTitle = normalized(titleByArtist.first)
            val requestedArtist = normalized(titleByArtist.second)
            if (title == requestedTitle && artist == requestedArtist) return 120
            if (title == requestedTitle && artist.contains(requestedArtist)) return 115
        }
        if (title == normalizedQuery) return 110
        if ("$title by $artist" == normalizedQuery) return 108
        if (artist == normalizedQuery) return 100
        if (album == normalizedQuery) return 95
        if (title.startsWith(normalizedQuery)) return 90
        if (title.contains(normalizedQuery)) return 80
        if (artist.contains(normalizedQuery)) return 70
        if (album.contains(normalizedQuery)) return 65

        val searchable = "$title $artist $album"
        if (queryTokens.isNotEmpty() && queryTokens.all(searchable::contains)) return 55
        return 0
    }

    private fun bestSongMatch(
        songs: List<Song>,
        title: String,
        artist: String?
    ): Song? {
        val titleNorm = normalized(title)
        val artistNorm = artist?.let(::normalized)
        return songs
            .filter { song -> normalized(song.title) == titleNorm }
            .filter { song -> artistNorm == null || textMatches(song.artist, artistNorm) }
            .sortedWith(songSearchTieBreaker)
            .firstOrNull()
            ?: songs
                .filter { song -> normalized(song.title).contains(titleNorm) }
                .filter { song -> artistNorm == null || textMatches(song.artist, artistNorm) }
                .sortedWith(songSearchTieBreaker)
                .firstOrNull()
    }

    private fun contextForSong(song: Song, songs: List<Song>): AndroidAutoPlaybackMatch {
        buildLibraryAlbumGroups(songs)
            .firstOrNull { album -> album.songs.any { candidate -> candidate.id == song.id } }
            ?.songs
            ?.takeIf { context -> context.size > 1 }
            ?.let { context -> return requireNotNull(context.toPlaybackMatch(song.id)) }

        buildLibraryArtistGroups(songs)
            .firstOrNull { artist -> artist.songs.any { candidate -> candidate.id == song.id } }
            ?.songs
            ?.takeIf { context -> context.size > 1 }
            ?.let { context -> return requireNotNull(context.toPlaybackMatch(song.id)) }

        return listOf(song).toPlaybackMatch(song.id)!!
    }

    private fun parseTitleByArtist(query: String): Pair<String, String>? {
        val normalizedSpacing = query.trim().replace(Regex("\\s+"), " ")
        val marker = Regex("\\s+by\\s+", RegexOption.IGNORE_CASE)
        val match = marker.find(normalizedSpacing) ?: return null
        val title = normalizedSpacing.substring(0, match.range.first).trim()
        val artist = normalizedSpacing.substring(match.range.last + 1).trim()
        return if (title.isNotBlank() && artist.isNotBlank()) title to artist else null
    }

    private fun String.stripVoicePlayPrefix(): String =
        replace(Regex("^(please\\s+)?(play|listen\\s+to)(\\s+me)?\\s+", RegexOption.IGNORE_CASE), "")
            .trim()

    private fun String.isGenericLibraryRequest(): Boolean = normalized(this) in setOf(
        "music",
        "my music",
        "songs",
        "all songs",
        "my songs",
        "library",
        "my library"
    )

    private fun textMatches(actual: String, requested: String): Boolean {
        val actualNorm = normalized(actual)
        val requestedNorm = normalized(requested)
        return actualNorm == requestedNorm || actualNorm.contains(requestedNorm)
    }

    private fun normalized(value: String): String = Normalizer.normalize(
        value.trim().lowercase(Locale.ROOT),
        Normalizer.Form.NFKD
    )
        .replace(Regex("\\p{M}+"), "")
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    private fun String?.cleanQuery(): String? =
        this?.trim()?.takeIf(String::isNotEmpty)

    private fun List<Song>.toPlaybackMatch(preferredSongId: Long?): AndroidAutoPlaybackMatch? {
        if (isEmpty()) return null
        val index = preferredSongId?.let { id ->
            indexOfFirst { song -> song.id == id }.takeIf { it >= 0 }
        } ?: 0
        return AndroidAutoPlaybackMatch(this, index)
    }

    private fun List<AutoPlaylistEntry>.bestNamedMatch(query: String): AutoPlaylistEntry? =
        bestNamedMatch(query) { playlist -> playlist.name }

    private fun <T> List<T>.bestNamedMatch(query: String, name: (T) -> String): T? {
        val requested = normalized(query)
        val ordered = sortedBy { normalized(name(it)) }
        return ordered.firstOrNull { item -> normalized(name(item)) == requested }
            ?: ordered.firstOrNull { item -> normalized(name(item)).startsWith(requested) }
            ?: ordered.firstOrNull { item -> normalized(name(item)).contains(requested) }
    }

    private fun <T> List<T>.exactNamedMatch(query: String, name: (T) -> String): T? =
        firstOrNull { normalized(name(it)) == normalized(query) }

    private val songSearchTieBreaker = compareBy<Song>(
        { song -> song.album.lowercase(Locale.ROOT) },
        { song -> song.trackNumber },
        { song -> song.title.lowercase(Locale.ROOT) },
        Song::id
    )
}
