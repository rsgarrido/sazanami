package io.github.rsgarrido.sazanami.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ArtistIdentityTest {
    @Test
    fun caseAndWhitespaceEquivalentNamesHaveOneStableIdentity() {
        val expected = artistIdentity("The Warning")

        assertEquals(expected, artistIdentity("  THE   WARNING  "))
        assertEquals(expected.key, artistIdentity("The Warning").key)
    }

    @Test
    fun genuinelyDifferentNamesRemainDistinctWithoutFuzzyMatching() {
        assertNotEquals(artistIdentity("Warning"), artistIdentity("The Warning"))
        assertNotEquals(artistIdentity("Artist"), artistIdentity("Artists"))
    }

    @Test
    fun blankArtistUsesReservedNonEditableUnknownIdentity() {
        assertEquals(UNKNOWN_ARTIST_IDENTITY, artistIdentity(" \t "))
        assertTrue(artistIdentity("").isUnknown)
        assertFalse(artistIdentity("").supportsCustomPicture)
    }
}
