package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryReconciliationUiMappingTest {
    @Test fun candidateCategoriesUseUserFacingCopyAndNeverEnumNamesOrPercentages() {
        val copies = ReconciliationCandidateCategory.entries.map(::candidateEvidenceCopy)
        assertEquals("Title, artist, and album match", copies[0])
        assertTrue(candidateEvidenceCopy(ReconciliationCandidateCategory.CANONICAL_METADATA)
            .contains("typography normalization"))
        assertEquals(
            "Similar title",
            candidateEvidenceCopy(ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT)
        )
        assertTrue(candidateEvidenceCopy(ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE)
            .contains("metadata is missing"))
        assertTrue(candidateEvidenceCopy(ReconciliationCandidateCategory.VERSION_SENSITIVE)
            .contains("different song version"))
        assertTrue(candidateEvidenceCopy(ReconciliationCandidateCategory.AMBIGUOUS)
            .contains("Multiple library versions"))
        copies.forEach { copy ->
            assertFalse(copy.contains('_'))
            assertFalse(copy.contains('%'))
        }
    }

    @Test fun versionAndAmbiguityWarningsExistIndependentlyOfColor() {
        assertEquals(
            "This may be a different version of the song.",
            candidateWarningCopy(ReconciliationCandidateCategory.VERSION_SENSITIVE)
        )
        assertTrue(candidateWarningCopy(ReconciliationCandidateCategory.AMBIGUOUS)!!.contains("Multiple"))
        assertNull(candidateWarningCopy(ReconciliationCandidateCategory.STRONG_METADATA))
    }

    @Test fun missingAlbumNeverLeavesDanglingSeparator() {
        assertEquals("The Gathering", formatArtistAlbum("The Gathering", ""))
        assertEquals("The Warning · Keep Me Fed", formatArtistAlbum("The Warning", "Keep Me Fed"))
        assertFalse(formatArtistAlbum("The Gathering", "").endsWith("·"))
    }

    @Test fun duplicateVersionDetailIncludesDurationAndFormat() {
        val target = LocalReconciliationTarget(
            1, 2, "ref", "Six Feet Deep", "The Warning",
            "Live From Auditorio Nacional, CDMX", null, 181_000,
            "song.flac", "flac", "Music/The Warning/Live"
        )
        assertEquals("3:01 · FLAC", formatTargetDetails(target))
    }

    @Test fun unicodeAndLongMetadataRemainUnmodified() {
        assertEquals("夢中猫", formatArtistAlbum("夢中猫", ""))
        val longAlbum = "A very long fictional album title intended to wrap on a narrow phone"
        assertTrue(formatArtistAlbum("Artist", longAlbum).endsWith(longAlbum))
    }
}
