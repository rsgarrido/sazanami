package io.github.rsgarrido.sazanami.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CanonicalPlaybackContextTest {
    @Test
    fun albumLaunchPathsCaptureCanonicalBaseIndependentlyOfPlaybackOrder() {
        val canonicalKeys = listOf("track-a", "track-b", "track-c", "track-d")
        val launchPlaybackOrders = listOf(
            listOf("a", "b", "c", "d"), // Album Play.
            listOf("c", "d", "b", "a"), // Tap track C while song shuffle is already on.
            listOf("d", "b", "a", "c"), // Album Shuffle.
            listOf("b", "d", "a", "c")  // Manual playlist launched with shuffle on.
        )

        launchPlaybackOrders.forEach { playbackOrder ->
            val playbackEntries = playbackOrder.map { suffix ->
                "entry-$suffix" to "track-$suffix"
            }
            val baseEntryIds = captureCanonicalBaseEntryIds(
                canonicalReferenceKeys = canonicalKeys,
                playbackEntries = playbackEntries
            )

            assertEquals(listOf("entry-a", "entry-b", "entry-c", "entry-d"), baseEntryIds)
        }
    }

    @Test
    fun shuffleOffAfterEveryAlbumLaunchPathTargetsTheFreshCanonicalOrder() {
        val canonicalBase = listOf("entry-a", "entry-b", "entry-c", "entry-d")
        val launchPlaybackOrders = listOf(
            listOf("entry-a", "entry-b", "entry-c", "entry-d"),
            listOf("entry-c", "entry-d", "entry-b", "entry-a"),
            listOf("entry-d", "entry-b", "entry-a", "entry-c"),
            listOf("entry-b", "entry-d", "entry-a", "entry-c")
        )

        launchPlaybackOrders.forEach { playbackOrder ->
            val currentEntryId = playbackOrder.first()
            assertEquals(
                canonicalBase,
                buildSongShufflePlaybackOrder(
                    baseEntryIds = canonicalBase,
                    currentEntryId = currentEntryId,
                    queuedEntryIds = emptyList(),
                    shuffleEnabled = false
                )
            )
        }
    }

    @Test
    fun duplicateSongsCaptureDistinctCanonicalOccurrencesAndAppendQueuedEntries() {
        val baseEntryIds = captureCanonicalBaseEntryIds(
            canonicalReferenceKeys = listOf("track-a", "track-b", "track-a"),
            playbackEntries = listOf(
                "entry-a-2" to "track-a",
                "entry-queued" to "track-q",
                "entry-b" to "track-b",
                "entry-a-1" to "track-a"
            )
        )

        assertEquals(
            listOf("entry-a-2", "entry-b", "entry-a-1", "entry-queued"),
            baseEntryIds
        )
    }

    @Test
    fun canonicalCaptureRejectsMissingOccurrencesAndDuplicateEntryIds() {
        assertNull(captureCanonicalBaseEntryIds(
            canonicalReferenceKeys = listOf("track-a", "track-a"),
            playbackEntries = listOf("entry-a" to "track-a")
        ))
        assertNull(captureCanonicalBaseEntryIds(
            canonicalReferenceKeys = listOf("track-a"),
            playbackEntries = listOf(
                "same-entry" to "track-a",
                "same-entry" to "track-b"
            )
        ))
    }

    @Test
    fun newControllerTimelineCannotBeReconciledAgainstOldServiceEntryIds() {
        assertFalse(activeQueueTimelinesMatch(
            controllerEntryIds = listOf("new-a", "new-b", "new-c"),
            serviceEntryIds = listOf("old-c", "old-a", "old-b")
        ))
        assertFalse(activeQueueTimelinesMatch(
            controllerEntryIds = listOf("new-c", "new-a", "new-b"),
            serviceEntryIds = listOf("new-a", "new-b", "new-c")
        ))
        assertTrue(activeQueueTimelinesMatch(
            controllerEntryIds = listOf("new-c", "new-a", "new-b"),
            serviceEntryIds = listOf("new-c", "new-a", "new-b")
        ))
    }
}
