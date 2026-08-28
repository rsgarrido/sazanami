package com.example.cdplaya.player

import androidx.media3.common.MediaItem
import java.util.Locale

internal data class AlbumTransitionTrack(
    val album: String,
    val albumArtist: String,
    val trackArtist: String,
    val folderPath: String,
    val rawTrackNumber: Int,
    val discNumber: Int? = null
)

internal data class AlbumTransitionDecision(
    val preserve: Boolean,
    val reason: String
)

internal object AlbumTransitionMetadata {
    const val RAW_TRACK_NUMBER = "com.example.cdplaya.transition.raw_track_number"
    const val FOLDER_PATH = "com.example.cdplaya.transition.folder_path"
    const val DISC_NUMBER = "com.example.cdplaya.transition.disc_number"

    fun from(item: MediaItem): AlbumTransitionTrack? {
        val evidence = item.listeningEvidence() ?: return null
        val extras = item.mediaMetadata.extras ?: return null
        if (!extras.containsKey(RAW_TRACK_NUMBER)) return null
        return AlbumTransitionTrack(
            album = evidence.reference.album,
            albumArtist = evidence.reference.albumArtist,
            trackArtist = evidence.reference.artist,
            folderPath = extras.getString(FOLDER_PATH).orEmpty(),
            rawTrackNumber = extras.getInt(RAW_TRACK_NUMBER),
            discNumber = extras.getInt(DISC_NUMBER).takeIf { it > 0 }
        )
    }
}

/** Conservative evidence rule: uncertain album continuity remains crossfade-eligible. */
internal object NaturalAlbumTransitionPolicy {
    fun isConfidentContinuation(
        playlist: List<MediaItem>,
        currentIndex: Int,
        nextIndex: Int
    ): Boolean = evaluate(
        playlist = playlist,
        currentIndex = currentIndex,
        nextIndex = nextIndex
    ).preserve

    fun evaluate(
        playlist: List<MediaItem>,
        currentIndex: Int,
        nextIndex: Int
    ): AlbumTransitionDecision = evaluateTracks(
        playlist = playlist.map(AlbumTransitionMetadata::from),
        currentIndex = currentIndex,
        nextIndex = nextIndex
    )

    internal fun isConfidentContinuationTracks(
        playlist: List<AlbumTransitionTrack?>,
        currentIndex: Int,
        nextIndex: Int
    ): Boolean = evaluateTracks(playlist, currentIndex, nextIndex).preserve

    internal fun evaluateTracks(
        playlist: List<AlbumTransitionTrack?>,
        currentIndex: Int,
        nextIndex: Int
    ): AlbumTransitionDecision {
        if (currentIndex !in playlist.indices || nextIndex !in playlist.indices) {
            return AlbumTransitionDecision(false, "invalid_queue_index")
        }
        if (nextIndex != currentIndex + 1) {
            return AlbumTransitionDecision(false, "not_queue_adjacent")
        }
        val outgoing = playlist[currentIndex]
            ?: return AlbumTransitionDecision(false, "outgoing_metadata_missing")
        val incoming = playlist[nextIndex]
            ?: return AlbumTransitionDecision(false, "incoming_metadata_missing")
        strongAlbumIdentityConflict(outgoing, incoming)?.let { reason ->
            return AlbumTransitionDecision(false, reason)
        }

        val from = decodeTrackNumber(outgoing)
            ?: return AlbumTransitionDecision(false, "outgoing_track_number_invalid")
        val to = decodeTrackNumber(incoming)
            ?: return AlbumTransitionDecision(false, "incoming_track_number_invalid")
        if (from.discAware != to.discAware) {
            return AlbumTransitionDecision(false, "track_number_encoding_mismatch")
        }
        val albumNumbers = playlist.asSequence()
            .filterNotNull()
            .filter { candidate -> strongAlbumIdentityConflict(outgoing, candidate) == null }
            .mapNotNull(::decodeTrackNumber)
            .toList()
        if (albumNumbers.count { it == from } != 1 || albumNumbers.count { it == to } != 1) {
            return AlbumTransitionDecision(false, "duplicate_track_number")
        }
        if (from.disc == to.disc) {
            return if (to.track == from.track + 1) {
                AlbumTransitionDecision(true, "sequential_track")
            } else {
                AlbumTransitionDecision(false, "non_sequential_track")
            }
        }
        if (!from.discAware || to.disc != from.disc + 1 || to.track != 1) {
            return AlbumTransitionDecision(false, "multi_disc_sequence_not_confident")
        }

        val outgoingDiscTracks = albumNumbers.asSequence()
            .filter { number -> number.discAware && number.disc == from.disc }
            .map { number -> number.track }
            .toList()
        if (outgoingDiscTracks.size < 2 || from.track != outgoingDiscTracks.maxOrNull()) {
            return AlbumTransitionDecision(false, "multi_disc_boundary_not_confident")
        }
        return AlbumTransitionDecision(true, "sequential_disc_boundary")
    }

    private fun strongAlbumIdentityConflict(
        first: AlbumTransitionTrack,
        second: AlbumTransitionTrack
    ): String? {
        val album = first.album.normalized()
        val folder = first.folderPath.normalizedPath()
        val artist = first.albumArtist.ifBlank { first.trackArtist }.normalized()
        if (album.isEmpty() || folder.isEmpty() || artist.isEmpty()) {
            return "strong_identity_missing"
        }
        if (album != second.album.normalized()) return "different_album"
        if (artist != second.albumArtist.ifBlank { second.trackArtist }.normalized()) {
            return "different_artist"
        }

        val secondFolder = second.folderPath.normalizedPath()
        if (folder != secondFolder) {
            val firstDisc = knownDiscNumber(first)
            val secondDisc = knownDiscNumber(second)
            val sameParent = folder.parentPath() == secondFolder.parentPath()
            if (
                firstDisc == null ||
                secondDisc == null ||
                firstDisc == secondDisc ||
                !sameParent
            ) {
                return "different_folder"
            }
        }
        return null
    }

    private data class TrackNumber(
        val disc: Int,
        val track: Int,
        val discAware: Boolean
    )

    private fun decodeTrackNumber(track: AlbumTransitionTrack): TrackNumber? {
        val raw = track.rawTrackNumber
        if (raw <= 0) return null

        val encodedDisc = if (raw >= 1_000) raw / 1_000 else null
        val encodedTrack = if (raw >= 1_000) raw % 1_000 else raw
        if (encodedTrack <= 0) return null

        val explicitDisc = track.discNumber?.takeIf { it > 0 }
        if (explicitDisc != null && encodedDisc != null && explicitDisc != encodedDisc) return null
        val resolvedDisc = explicitDisc ?: encodedDisc ?: 1
        if (resolvedDisc <= 0) return null
        return TrackNumber(
            disc = resolvedDisc,
            track = encodedTrack,
            discAware = explicitDisc != null || encodedDisc != null
        )
    }

    private fun knownDiscNumber(track: AlbumTransitionTrack): Int? {
        track.discNumber?.takeIf { it > 0 }?.let { return it }
        val raw = track.rawTrackNumber
        if (raw < 1_000 || raw % 1_000 <= 0) return null
        return (raw / 1_000).takeIf { it > 0 }
    }

    private fun String.normalized(): String = trim().lowercase(Locale.ROOT)

    private fun String.normalizedPath(): String =
        trim().replace('\\', '/').trimEnd('/').lowercase(Locale.ROOT)

    private fun String.parentPath(): String =
        substringBeforeLast('/', missingDelimiterValue = "")
}
