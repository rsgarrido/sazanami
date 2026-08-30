package io.github.rsgarrido.sazanami.ui.statistics

import android.net.Uri
import io.github.rsgarrido.sazanami.data.AlbumListeningStats
import io.github.rsgarrido.sazanami.data.ArtistListeningStats
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.TrackListeningStats
import io.github.rsgarrido.sazanami.data.normalizeArtistName
import java.util.Locale

/**
 * UI-only bridge from listening-history snapshots back to the current library's visual assets.
 * Statistics remain keyed and ordered entirely by the listening repository; this index is used
 * only to decorate ranking rows with artwork that is already available in the library.
 */
internal class StatisticsArtworkIndex(songs: List<Song>) {
    private val artworkByContentUri = mutableMapOf<String, Uri>()
    private val trackArtwork = mutableMapOf<TrackMetadataKey, Uri>()
    private val artistFallbackArtwork = mutableMapOf<String, Uri>()
    private val albumArtwork = mutableMapOf<AlbumMetadataKey, Uri>()
    private val fallbackAlbumArtwork: Map<String, Uri>

    init {
        val albumTitleArtwork = mutableMapOf<String, MutableSet<Uri>>()
        songs.forEach { song ->
            val artwork = song.albumArtUri ?: return@forEach
            artworkByContentUri.putIfAbsent(song.uri.toString(), artwork)
            trackArtwork.putIfAbsent(
                TrackMetadataKey(
                    title = normalizeStatisticsText(song.title),
                    artist = normalizeArtistName(song.artist),
                    album = normalizeStatisticsText(song.album)
                ),
                artwork
            )
            artistFallbackArtwork.putIfAbsent(normalizeArtistName(song.artist), artwork)
            albumArtwork.putIfAbsent(
                AlbumMetadataKey(
                    album = normalizeStatisticsText(song.album),
                    albumArtist = normalizeArtistName(song.albumArtist.ifBlank { song.artist })
                ),
                artwork
            )
            albumTitleArtwork
                .getOrPut(normalizeStatisticsText(song.album)) { linkedSetOf() }
                .add(artwork)
        }
        fallbackAlbumArtwork = albumTitleArtwork.mapNotNull { (album, artwork) ->
            artwork.singleOrNull()?.let { album to it }
        }.toMap()
    }

    fun trackArtwork(stats: TrackListeningStats): Uri? {
        stats.knownBindings.forEach { binding ->
            binding.contentUri?.let(artworkByContentUri::get)?.let { return it }
        }
        stats.binding?.contentUri?.let(artworkByContentUri::get)?.let { return it }
        return trackArtwork[
            TrackMetadataKey(
                title = normalizeStatisticsText(stats.title),
                artist = normalizeArtistName(stats.artist),
                album = normalizeStatisticsText(stats.album)
            )
        ]
    }

    fun artistFallbackArtwork(stats: ArtistListeningStats): Uri? =
        artistFallbackArtwork[normalizeArtistName(stats.artist)]

    fun albumArtwork(stats: AlbumListeningStats): Uri? {
        val exact = albumArtwork[
            AlbumMetadataKey(
                album = normalizeStatisticsText(stats.album),
                albumArtist = normalizeArtistName(stats.albumArtist)
            )
        ]
        return exact ?: fallbackAlbumArtwork[normalizeStatisticsText(stats.album)]
    }
}

private data class TrackMetadataKey(
    val title: String,
    val artist: String,
    val album: String
)

private data class AlbumMetadataKey(
    val album: String,
    val albumArtist: String
)

private fun normalizeStatisticsText(value: String): String = value
    .trim()
    .replace(Regex("\\s+"), " ")
    .lowercase(Locale.ROOT)
