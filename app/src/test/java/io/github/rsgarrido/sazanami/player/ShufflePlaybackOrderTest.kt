package io.github.rsgarrido.sazanami.player

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class ShufflePlaybackOrderTest {
    @Test
    fun customCommandUsesSession2ControllerSetterWhenControllerIsPresent() = runBlocking {
        val controllerRequests = mutableListOf<Boolean>()
        var serviceOnlyCalled = false

        val usedController = routeExternalSongShuffleRequest(
            enabled = true,
            controllerSetter = { enabled ->
                controllerRequests += enabled
                true
            },
            serviceOnlySetter = { serviceOnlyCalled = true }
        )

        assertTrue(usedController)
        assertEquals(listOf(true), controllerRequests)
        assertFalse(serviceOnlyCalled)
    }

    @Test
    fun customCommandUsesStableServicePathWhenControllerIsAbsent() = runBlocking {
        val serviceOnlyRequests = mutableListOf<Boolean>()

        val usedController = routeExternalSongShuffleRequest(
            enabled = false,
            controllerSetter = { false },
            serviceOnlySetter = { enabled -> serviceOnlyRequests += enabled }
        )

        assertFalse(usedController)
        assertEquals(listOf(false), serviceOnlyRequests)
    }

    @Test
    fun offOnOffOnRestoresCanonicalOrderAndCreatesAFreshShuffle() {
        val base = listOf("a", "b", "c", "d")
        var shuffleCount = 0
        val shuffle: (List<String>) -> List<String> = { remaining ->
            shuffleCount += 1
            if (shuffleCount == 1) remaining.reversed() else remaining.drop(1) + remaining.take(1)
        }

        val firstShuffle = requireNotNull(buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "c",
            queuedEntryIds = emptyList(),
            shuffleEnabled = true,
            shuffleRemaining = shuffle
        ))
        val restored = requireNotNull(buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "c",
            queuedEntryIds = emptyList(),
            shuffleEnabled = false,
            shuffleRemaining = shuffle
        ))
        val secondShuffle = requireNotNull(buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "c",
            queuedEntryIds = emptyList(),
            shuffleEnabled = true,
            shuffleRemaining = shuffle
        ))

        assertEquals(listOf("c", "d", "b", "a"), firstShuffle)
        assertEquals(base, restored)
        assertEquals(listOf("c", "b", "d", "a"), secondShuffle)
        assertNotEquals(firstShuffle, secondShuffle)
        assertEquals(2, shuffleCount)
    }

    @Test
    fun duplicateCurrentOccurrenceIsPreservedByStableEntryIdentity() {
        val base = listOf("a-1", "b", "a-2", "c")
        val shuffled = listOf("b", "a-1", "c", "a-2")
        val target = requireNotNull(buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "a-2",
            queuedEntryIds = emptyList(),
            shuffleEnabled = false
        ))
        val moves = requireNotNull(planCurrentPreservingTimelineMoves(
            currentOrder = shuffled,
            targetOrder = target,
            currentEntryId = "a-2"
        ))

        assertEquals(base, applyMovesWithoutMovingCurrent(shuffled, moves, "a-2"))
        assertEquals(2, target.indexOf("a-2"))
        assertEquals(base.toSet(), target.toSet())
    }

    @Test
    fun currentItemMovesBetweenFirstMiddleAndLastWithoutBeingMovedDirectly() {
        val cases = listOf(
            Triple(listOf("c", "a", "b", "d"), listOf("a", "b", "c", "d"), "c"),
            Triple(listOf("a", "b", "c", "d"), listOf("c", "d", "a", "b"), "c"),
            Triple(listOf("d", "a", "b", "c"), listOf("a", "b", "c", "d"), "d")
        )

        cases.forEach { (current, target, currentEntryId) ->
            val moves = requireNotNull(planCurrentPreservingTimelineMoves(
                currentOrder = current,
                targetOrder = target,
                currentEntryId = currentEntryId
            ))
            assertEquals(
                target,
                applyMovesWithoutMovingCurrent(current, moves, currentEntryId)
            )
        }
    }

    @Test
    fun explicitQueuedOccurrencesStayImmediatelyAfterCurrent() {
        val base = listOf("a", "b", "c", "d", "queued-1", "queued-2")

        val unshuffled = buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "b",
            queuedEntryIds = listOf("queued-1", "queued-2"),
            shuffleEnabled = false
        )
        val shuffled = buildSongShufflePlaybackOrder(
            baseEntryIds = base,
            currentEntryId = "b",
            queuedEntryIds = listOf("queued-1", "queued-2"),
            shuffleEnabled = true,
            shuffleRemaining = { entries -> entries.reversed() }
        )

        assertEquals(listOf("a", "b", "queued-1", "queued-2", "c", "d"), unshuffled)
        assertEquals(listOf("b", "queued-1", "queued-2", "d", "c", "a"), shuffled)
    }

    @Test
    fun serviceOnlyPlayNextMatchingPreservesDuplicateOccurrencesByEntryId() {
        val timeline = listOf(
            "played-a" to "song-a",
            "current-b" to "song-b",
            "queued-a-1" to "song-a",
            "ordinary-c" to "song-c",
            "queued-a-2" to "song-a",
            "ordinary-d" to "song-d"
        )

        val queuedEntryIds = matchQueuedEntryIdsForShuffle(
            timelineEntryReferences = timeline,
            currentEntryId = "current-b",
            queuedReferenceKeys = listOf("song-a", "song-a")
        )
        val target = buildSongShufflePlaybackOrder(
            baseEntryIds = timeline.map { (entryId, _) -> entryId },
            currentEntryId = "current-b",
            queuedEntryIds = queuedEntryIds,
            shuffleEnabled = true,
            shuffleRemaining = { entries -> entries.reversed() }
        )

        assertEquals(listOf("queued-a-1", "queued-a-2"), queuedEntryIds)
        assertEquals(
            listOf(
                "current-b",
                "queued-a-1",
                "queued-a-2",
                "ordinary-d",
                "ordinary-c",
                "played-a"
            ),
            target
        )
    }

    @Test
    fun invalidOrMembershipChangingOrdersAreRejected() {
        assertNull(buildSongShufflePlaybackOrder(
            baseEntryIds = listOf("a", "a"),
            currentEntryId = "a",
            queuedEntryIds = emptyList(),
            shuffleEnabled = false
        ))
        assertNull(buildSongShufflePlaybackOrder(
            baseEntryIds = listOf("a", "b"),
            currentEntryId = "a",
            queuedEntryIds = emptyList(),
            shuffleEnabled = true,
            shuffleRemaining = { listOf("new-entry") }
        ))
        assertNull(planCurrentPreservingTimelineMoves(
            currentOrder = listOf("a", "b"),
            targetOrder = listOf("a", "c"),
            currentEntryId = "a"
        ))
        assertNull(planCurrentPreservingTimelineReplacements(
            currentOrder = listOf("a", "b"),
            targetOrder = listOf("a", "c"),
            currentEntryId = "a"
        ))
    }

    @Test
    fun exactTimelineReplacementHandlesNontrivialPermutationsAtEveryCurrentBoundary() {
        val cases = listOf(
            Triple(
                listOf("c", "f", "a", "e", "b", "d"),
                listOf("a", "b", "c", "d", "e", "f"),
                "c"
            ),
            Triple(
                listOf("f", "c", "a", "e", "b", "d"),
                listOf("a", "b", "c", "d", "e", "f"),
                "c"
            ),
            Triple(
                listOf("f", "d", "b", "e", "a", "c"),
                listOf("a", "b", "c", "d", "e", "f"),
                "c"
            )
        )

        cases.forEach { (initial, target, currentEntryId) ->
            val result = applyReplacementsKeepingCurrentOccurrence(initial, target, currentEntryId)

            assertEquals(target, result.finalOrder)
            assertSame(result.originalCurrentOccurrence, result.finalCurrentOccurrence)
        }
    }

    @Test
    fun suffixThenPrefixReplacementDoesNotUseStaleIndicesAcrossCurrentBoundary() {
        val initial = listOf("c", "f", "a", "e", "b", "d")
        val target = listOf("a", "b", "c", "d", "e", "f")
        val replacements = requireNotNull(planCurrentPreservingTimelineReplacements(
            currentOrder = initial,
            targetOrder = target,
            currentEntryId = "c"
        ))

        assertEquals(
            listOf(
                PlaybackTimelineReplacement(1, 6, listOf("d", "e", "f")),
                PlaybackTimelineReplacement(0, 0, listOf("a", "b"))
            ),
            replacements
        )
        val result = applyReplacementsKeepingCurrentOccurrence(initial, target, "c")
        assertEquals(target, result.finalOrder)
        assertSame(result.originalCurrentOccurrence, result.finalCurrentOccurrence)
    }

    @Test
    fun duplicateSongsRemainDistinctOccurrencesDuringExactTimelineReplacement() {
        val initial = listOf("song-a-2", "song-c", "song-b", "song-a-1")
        val target = listOf("song-a-1", "song-b", "song-a-2", "song-c")

        val result = applyReplacementsKeepingCurrentOccurrence(initial, target, "song-a-2")

        assertEquals(target, result.finalOrder)
        assertSame(result.originalCurrentOccurrence, result.finalCurrentOccurrence)
        assertEquals(1, result.finalOrder.count { entryId -> entryId == "song-a-1" })
        assertEquals(1, result.finalOrder.count { entryId -> entryId == "song-a-2" })
    }

    private fun applyMovesWithoutMovingCurrent(
        initial: List<String>,
        moves: List<PlaybackTimelineMove>,
        currentEntryId: String
    ): List<String> {
        val working = initial.toMutableList()
        moves.forEach { move ->
            assertTrue(working[move.fromIndex] != currentEntryId)
            working.add(move.toIndex, working.removeAt(move.fromIndex))
            assertTrue(currentEntryId in working)
        }
        return working
    }

    private data class TimelineOccurrence(val entryId: String)

    private data class TimelineReplacementResult(
        val finalOrder: List<String>,
        val originalCurrentOccurrence: TimelineOccurrence,
        val finalCurrentOccurrence: TimelineOccurrence
    )

    private fun applyReplacementsKeepingCurrentOccurrence(
        initial: List<String>,
        target: List<String>,
        currentEntryId: String
    ): TimelineReplacementResult {
        val occurrencesByEntryId = initial.associateWith(::TimelineOccurrence)
        val working = initial.map { entryId -> requireNotNull(occurrencesByEntryId[entryId]) }
            .toMutableList()
        val originalCurrent = requireNotNull(occurrencesByEntryId[currentEntryId])
        val replacements = requireNotNull(planCurrentPreservingTimelineReplacements(
            currentOrder = initial,
            targetOrder = target,
            currentEntryId = currentEntryId
        ))

        replacements.forEach { replacement ->
            val currentIndex = working.indexOf(originalCurrent)
            assertTrue(currentIndex !in replacement.fromIndex until replacement.toIndex)
            repeat(replacement.toIndex - replacement.fromIndex) {
                working.removeAt(replacement.fromIndex)
            }
            working.addAll(
                replacement.fromIndex,
                replacement.replacementEntryIds.map { entryId ->
                    requireNotNull(occurrencesByEntryId[entryId])
                }
            )
            assertSame(originalCurrent, working.first { occurrence ->
                occurrence.entryId == currentEntryId
            })
        }

        return TimelineReplacementResult(
            finalOrder = working.map(TimelineOccurrence::entryId),
            originalCurrentOccurrence = originalCurrent,
            finalCurrentOccurrence = working.first { occurrence ->
                occurrence.entryId == currentEntryId
            }
        )
    }
}
