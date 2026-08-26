package com.example.cdplaya.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AlbumTransitionPolicyTest {
    @Test
    fun sequentialStrongAlbumIdentityIsPreserved() {
        assertTrue(policy(track(1), track(2)))
    }

    @Test
    fun differentAlbumNonSequentialMissingAndReorderedMetadataRemainEligible() {
        assertFalse(policy(track(1), track(2, album = "Other")))
        assertFalse(policy(track(1), track(3)))
        assertFalse(policy(track(0), track(1)))
        assertFalse(policy(track(1), track(2), extra = listOf(track(2))))
        assertFalse(
            NaturalAlbumTransitionPolicy.isConfidentContinuationTracks(
                playlist = listOf(track(2), track(1)),
                currentIndex = 0,
                nextIndex = 1
            )
        )
    }

    @Test
    fun repeatedAlbumTitleWithoutFolderOrArtistIdentityIsNotPreserved() {
        assertFalse(policy(track(1), track(2, folder = "Other/Album")))
        assertFalse(policy(track(1), track(2, artist = "Other Artist")))
    }

    @Test
    fun diagnosticDecisionExplainsWhyUnrelatedTracksRemainCrossfadeEligible() {
        val differentAlbum = NaturalAlbumTransitionPolicy.evaluateTracks(
            playlist = listOf(track(1), track(2, album = "Other")),
            currentIndex = 0,
            nextIndex = 1
        )
        val differentArtist = NaturalAlbumTransitionPolicy.evaluateTracks(
            playlist = listOf(track(1), track(2, artist = "Other Artist")),
            currentIndex = 0,
            nextIndex = 1
        )
        val differentFolder = NaturalAlbumTransitionPolicy.evaluateTracks(
            playlist = listOf(track(1), track(2, folder = "Other/Album")),
            currentIndex = 0,
            nextIndex = 1
        )

        assertEquals("different_album", differentAlbum.reason)
        assertEquals("different_artist", differentArtist.reason)
        assertEquals("different_folder", differentFolder.reason)
        assertFalse(differentAlbum.preserve)
        assertFalse(differentArtist.preserve)
        assertFalse(differentFolder.preserve)
    }

    @Test
    fun encodedFinalDiscTrackToNextDiscTrackOneIsPreservedOnlyWithEvidence() {
        val playlist = listOf(track(1_001), track(1_010), track(2_001))
        assertTrue(
            NaturalAlbumTransitionPolicy.isConfidentContinuationTracks(
                playlist = playlist,
                currentIndex = 1,
                nextIndex = 2
            )
        )
        assertFalse(policy(track(1_009), track(2_001), extra = listOf(track(1_010))))
        assertFalse(policy(track(10), track(2_001)))
    }

    private fun policy(
        outgoing: AlbumTransitionTrack,
        incoming: AlbumTransitionTrack,
        extra: List<AlbumTransitionTrack> = emptyList()
    ): Boolean = NaturalAlbumTransitionPolicy.isConfidentContinuationTracks(
        playlist = listOf(outgoing, incoming) + extra,
        currentIndex = 0,
        nextIndex = 1
    )

    private fun track(
        number: Int,
        album: String = "Album",
        folder: String = "Music/Artist/Album",
        artist: String = "Album Artist"
    ) = AlbumTransitionTrack(
        album = album,
        albumArtist = artist,
        trackArtist = artist,
        folderPath = folder,
        rawTrackNumber = number
    )
}
