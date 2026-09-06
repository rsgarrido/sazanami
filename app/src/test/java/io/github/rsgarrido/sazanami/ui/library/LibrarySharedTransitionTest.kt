package io.github.rsgarrido.sazanami.ui.library

import io.github.rsgarrido.sazanami.ui.DetailEntryOrigin
import io.github.rsgarrido.sazanami.ui.sharedArtworkSourceScope
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibrarySharedTransitionTest {
    @Test
    fun sourceReplica_requiresBothAMatchAndSubduedArtworkTreatment() {
        assertEquals(
            false,
            shouldDrawSharedArtworkSourceReplica(
                matchFound = false,
                treatment = LibrarySharedArtworkSourceSlotTreatment.SUBDUED_ARTWORK
            )
        )
        assertEquals(
            false,
            shouldDrawSharedArtworkSourceReplica(
                matchFound = true,
                treatment = LibrarySharedArtworkSourceSlotTreatment.NEUTRAL_SURFACE
            )
        )
        assertEquals(
            true,
            shouldDrawSharedArtworkSourceReplica(
                matchFound = true,
                treatment = LibrarySharedArtworkSourceSlotTreatment.SUBDUED_ARTWORK
            )
        )
    }

    @Test
    fun artworkKeysRemainEntityAndIdentityIsolated() {
        val scope = LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION

        assertNotEquals(
            LibrarySharedArtworkKey.Album("42", scope),
            LibrarySharedArtworkKey.Artist("42", scope)
        )
        assertNotEquals(
            LibrarySharedArtworkKey.Artist("42", scope),
            LibrarySharedArtworkKey.Playlist(42L, scope)
        )
        assertNotEquals(
            LibrarySharedArtworkKey.Playlist(42L, scope),
            LibrarySharedArtworkKey.Playlist(43L, scope)
        )
    }

    @Test
    fun homeAndLibraryKeysAreIncompatibleWhileEachDetailPairRemainsCompatible() {
        val home = LibrarySharedArtworkSourceScope.HOME_PINNED
        val library = LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION

        assertSourceScopes(
            homeSource = LibrarySharedArtworkKey.Album("album", home),
            homeDetail = LibrarySharedArtworkKey.Album("album", home),
            librarySource = LibrarySharedArtworkKey.Album("album", library),
            libraryDetail = LibrarySharedArtworkKey.Album("album", library)
        )
        assertSourceScopes(
            homeSource = LibrarySharedArtworkKey.Artist("artist", home),
            homeDetail = LibrarySharedArtworkKey.Artist("artist", home),
            librarySource = LibrarySharedArtworkKey.Artist("artist", library),
            libraryDetail = LibrarySharedArtworkKey.Artist("artist", library)
        )
        assertSourceScopes(
            homeSource = LibrarySharedArtworkKey.Playlist(42L, home),
            homeDetail = LibrarySharedArtworkKey.Playlist(42L, home),
            librarySource = LibrarySharedArtworkKey.Playlist(42L, library),
            libraryDetail = LibrarySharedArtworkKey.Playlist(42L, library)
        )
    }

    @Test
    fun detailEntryOriginSelectsTheMatchingSourceScope() {
        assertEquals(
            LibrarySharedArtworkSourceScope.HOME_PINNED,
            DetailEntryOrigin.HOME_PINNED.sharedArtworkSourceScope()
        )
        assertEquals(
            LibrarySharedArtworkSourceScope.LIBRARY_COLLECTION,
            DetailEntryOrigin.LIBRARY.sharedArtworkSourceScope()
        )
    }

    private fun assertSourceScopes(
        homeSource: LibrarySharedArtworkKey,
        homeDetail: LibrarySharedArtworkKey,
        librarySource: LibrarySharedArtworkKey,
        libraryDetail: LibrarySharedArtworkKey
    ) {
        assertNotEquals(homeSource, librarySource)
        assertEquals(homeSource, homeDetail)
        assertEquals(librarySource, libraryDetail)
    }
}
