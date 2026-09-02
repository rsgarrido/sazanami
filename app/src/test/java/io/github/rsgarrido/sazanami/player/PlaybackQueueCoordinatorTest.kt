package io.github.rsgarrido.sazanami.player

import android.net.Uri
import io.github.rsgarrido.sazanami.data.PlaybackQueueEntryDraft
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.SongReference
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueWithEntries
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock

class PlaybackQueueCoordinatorTest {
    @Test
    fun activeEntrySelectionSeeksExactStableEntryWithoutTimelineReplacement() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("dup-1", 1L, 0, 0), spec("dup-2", 1L, 1, 1)), "dup-1"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("dup-1" to 1L, "dup-2" to 1L), "dup-1", 99L, true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.playEntry("A", "dup-2"))
        assertEquals("dup-2", runtime.seekedEntryId)
        assertEquals("dup-2", persistence.queue("A").queue.currentEntryId)
        assertEquals(listOf("dup-1", "dup-2"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(0, runtime.replaceCount)
        assertTrue(coordinator.playEntry("A", "dup-1"))
        assertEquals("dup-1", runtime.seekedEntryId)
        assertEquals(2, runtime.seekCount)
        assertFalse(coordinator.playEntry("B", "dup-2"))
        assertEquals(2, runtime.seekCount)
    }

    @Test
    fun jumpCheckpointAndQueueRoundTripRestoreCurrentWithoutConsumingEarlierEntries() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(
                        spec("A", 1L, 0, 0),
                        spec("B", 2L, 1, 1),
                        spec("C", 3L, 2, 2),
                        spec("D", 4L, 3, 3)
                    ),
                    current = "A",
                    position = 100L
                ))
                seed(queue("other", listOf(spec("other", 5L, 0, 0)), "other", 50L))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    listOf("A" to 1L, "B" to 2L, "C" to 3L, "D" to 4L),
                    "A",
                    100L,
                    false
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.playEntry("A", "C"))
            runtime.snapshot = runtime.snapshot?.copy(currentPositionMs = 4_321L)
            coordinator.persistActiveQueueSnapshot()
            assertTrue(coordinator.switchToQueue("other"))
            assertTrue(coordinator.switchToQueue("A"))

            assertEquals("C", runtime.snapshot?.currentEntryId)
            assertEquals(4_321L, runtime.snapshot?.currentPositionMs)
            assertEquals(
                listOf("A", "B", "C", "D"),
                persistence.queue("A").entries.map { it.entryId }
            )
        }

    @Test
    fun activeRemovalUndoRestoresStableEntryAtPositionWithoutReplacement() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("current", 1L, 0, 0), spec("removed", 2L, 1, 1), spec("last", 3L, 2, 2)), "current"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("current" to 1L, "removed" to 2L, "last" to 3L), "current", 123L, true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val removal = requireNotNull(coordinator.removeEntryForUndo("A", "removed"))
        assertTrue(coordinator.undoRemoveEntry(removal))

        assertEquals(listOf("current", "removed", "last"), runtime.snapshot?.entries?.map { it.entryId })
        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(123L, runtime.snapshot?.currentPositionMs)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf("current", "removed", "last"), persistence.queue("A").entries.map { it.entryId })
    }

    @Test
    fun inactiveRemovalUndoNeverTouchesLiveTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active"))
            seed(queue("B", listOf(spec("one", 2L, 0, 0), spec("two", 3L, 1, 1)), "one"))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("active" to 1L), "active", 88L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val removal = requireNotNull(coordinator.removeEntryForUndo("B", "two"))
        assertTrue(coordinator.undoRemoveEntry(removal))

        assertEquals(listOf("one", "two"), persistence.queue("B").entries.map { it.entryId })
        assertEquals(listOf("active"), runtime.snapshot?.entries?.map { it.entryId })
        assertEquals(0, runtime.insertCount)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun activeRuntimeRemovalRemainsSuccessfulWhenImmediatePersistenceCheckpointFails() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    "A",
                    listOf(spec("current", 1L, 0, 0), spec("removed", 2L, 1, 1)),
                    "current"
                ))
                failNextReplace = true
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    listOf("current" to 1L, "removed" to 2L),
                    "current",
                    321L,
                    true
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            val removal = coordinator.removeEntryForUndo("A", "removed")

            assertTrue(removal != null)
            assertEquals(listOf("current"), runtime.snapshot?.entries?.map { it.entryId })
            assertEquals("current", runtime.snapshot?.currentEntryId)
            assertEquals(321L, runtime.snapshot?.currentPositionMs)
            assertTrue(coordinator.undoRemoveEntry(requireNotNull(removal)))
            assertEquals(
                listOf("current", "removed"),
                runtime.snapshot?.entries?.map { it.entryId }
            )
            assertEquals(0, runtime.replaceCount)
        }
    @Test
    fun firstMeaningfulTimelineBootstrapsOneActiveQueueWithoutReplacingLiveSession() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(
            liveSnapshot(
                ids = listOf("one" to 1L, "two" to 2L),
                currentEntryId = "two",
                positionMs = 321L,
                shouldPlay = true,
                baseEntryIds = listOf("two", "one")
            )
        )
        val coordinator = coordinator(persistence, runtime)

        assertNull(coordinator.initialize())
        assertEquals(0, runtime.replaceCount)
        assertEquals("generated-queue", coordinator.persistActiveQueueSnapshot())
        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals("Queue 1", persistence.queues.getValue("generated-queue").queue.displayName)
        assertEquals(listOf("one", "two"), persistence.queue("generated-queue").entries.map { it.entryId })
        assertEquals(listOf(1, 0), persistence.queue("generated-queue").entries.map { it.baseOrder })
        assertEquals("two", persistence.queue("generated-queue").queue.currentEntryId)

        coordinator.persistActiveQueueSnapshot()
        assertEquals(1, persistence.createCount)
    }

    @Test
    fun initializationAdoptsAlreadyPlayingLiveTimelineInsteadOfRebuildingIt() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("live" to 9L), "live", 77L, shouldPlay = true)
        )

        coordinator(persistence, runtime).initialize()

        assertEquals(0, runtime.replaceCount)
        assertTrue(runtime.snapshot?.shouldPlay == true)
    }

    @Test
    fun liveTimelineThatAppearsDuringStartupResolutionWinsTheInitializationRace() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
        }
        val runtime = FakeRuntime(null)
        val live = liveSnapshot(listOf("live" to 9L), "live", 77L, shouldPlay = true)
        val access = FakeTrackAccess().apply {
            beforeResolve = { runtime.snapshot = live }
        }

        coordinator(persistence, runtime, access).initialize()

        assertEquals(0, runtime.replaceCount)
        assertEquals(live, runtime.snapshot)
    }

    @Test
    fun initializationRestoresPersistedActiveQueueIntoAnEmptyRuntimePaused() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(spec("a", 1L, 0, 0)),
                current = "a",
                position = 654L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE
            ))
        }
        val runtime = FakeRuntime(null)

        assertEquals("A", coordinator(persistence, runtime).initialize())

        val restored = requireNotNull(runtime.lastRestoration)
        assertEquals("a", restored.currentEntryId)
        assertEquals(654L, restored.currentPositionMs)
        assertFalse(restored.shouldPlay)
        assertTrue(restored.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE, restored.repeatMode)
        assertEquals(1, runtime.replaceCount)
    }

    @Test
    fun switchingAwayAndBackRestoresPositionExactShuffleOrderBaseOrderRepeatAndDuplicates() =
        runBlocking {
            val queueA = queue(
                id = "A",
                specs = listOf(
                    spec("a0", 1L, base = 0, playback = 1),
                    spec("a1", 1L, base = 1, playback = 2),
                    spec("a2", 2L, base = 2, playback = 0)
                ),
                current = "a0",
                position = 12_345L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ALL
            )
            val queueB = queue(
                id = "B",
                specs = listOf(spec("b0", 3L, 0, 0)),
                current = "b0",
                position = 55L,
                repeat = PersistedQueueRepeatMode.ONE
            )
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queueA)
                seed(queueB)
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf("a2" to 2L, "a0" to 1L, "a1" to 1L),
                    currentEntryId = "a0",
                    positionMs = 12_345L,
                    shouldPlay = true,
                    shuffle = true,
                    repeat = PersistedQueueRepeatMode.ALL
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            assertTrue(coordinator.switchToQueue("B"))
            assertEquals(listOf("b0"), runtime.lastRestoration?.entries?.map {
                it.persistedEntry.entryId
            })
            assertEquals(PersistedQueueRepeatMode.ONE, runtime.lastRestoration?.repeatMode)
            assertTrue(coordinator.switchToQueue("A"))

            val restoredA = requireNotNull(runtime.lastRestoration)
            assertEquals(listOf("a2", "a0", "a1"), restoredA.entries.map {
                it.persistedEntry.entryId
            })
            assertEquals(listOf(2, 0, 1), restoredA.entries.map {
                it.persistedEntry.baseOrder
            })
            assertEquals("a0", restoredA.currentEntryId)
            assertEquals(12_345L, restoredA.currentPositionMs)
            assertTrue(restoredA.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL, restoredA.repeatMode)
            assertEquals(listOf(1L, 1L), restoredA.entries.filter {
                it.song.id == 1L
            }.map { it.song.id })
            assertEquals("A", persistence.activeQueueId)
            assertEquals(2, runtime.replaceCount)
            assertEquals(2, runtime.prepareCount)
            assertEquals(2, runtime.playCount)
        }

    @Test
    fun switchPreservesPausedAndPlayingIntentWithoutDuplicatePrepareOrPlay() = runBlocking {
        val pausedPersistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b"))
        }
        val pausedRuntime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 1L, shouldPlay = false)
        )
        val pausedCoordinator = coordinator(pausedPersistence, pausedRuntime)
        pausedCoordinator.initialize()
        assertTrue(pausedCoordinator.switchToQueue("B"))
        assertFalse(requireNotNull(pausedRuntime.snapshot).shouldPlay)
        assertEquals(1, pausedRuntime.prepareCount)
        assertEquals(0, pausedRuntime.playCount)

        val playingPersistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b"))
        }
        val playingRuntime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 1L, shouldPlay = true)
        )
        val playingCoordinator = coordinator(playingPersistence, playingRuntime)
        playingCoordinator.initialize()
        assertTrue(playingCoordinator.switchToQueue("B"))
        assertTrue(requireNotNull(playingRuntime.snapshot).shouldPlay)
        assertEquals(1, playingRuntime.prepareCount)
        assertEquals(1, playingRuntime.playCount)
    }

    @Test
    fun runtimeMutationsPersistPlaybackAndBaseOrderWhileStateOnlyCheckpointsStayLightweight() =
        runBlocking {
            val persistence = FakePersistence(activeQueueId = "A").apply {
                seed(queue(
                    id = "A",
                    specs = listOf(
                        spec("a", 1L, base = 0, playback = 0),
                        spec("b", 2L, base = 1, playback = 1),
                        spec("c", 3L, base = 2, playback = 2)
                    ),
                    current = "a"
                ))
            }
            val runtime = FakeRuntime(
                liveSnapshot(
                    ids = listOf("c" to 3L, "a" to 1L, "b" to 2L),
                    currentEntryId = "c",
                    positionMs = 400L,
                    shouldPlay = true,
                    shuffle = true,
                    repeat = PersistedQueueRepeatMode.ONE,
                    baseEntryIds = listOf("a", "b", "c")
                )
            )
            val coordinator = coordinator(persistence, runtime)
            coordinator.initialize()

            coordinator.persistActiveQueueSnapshot()

            assertEquals(listOf("c", "a", "b"), persistence.queue("A").entries.map {
                it.entryId
            })
            assertEquals(listOf(2, 0, 1), persistence.queue("A").entries.map {
                it.baseOrder
            })
            assertEquals("c", persistence.queue("A").queue.currentEntryId)
            assertEquals(400L, persistence.queue("A").queue.currentPositionMs)
            assertTrue(persistence.queue("A").queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ONE, persistence.queue("A").queue.repeatMode)
            assertEquals(1, persistence.replaceEntriesCount)

            runtime.snapshot = runtime.snapshot?.copy(
                currentPositionMs = 800L,
                shuffleEnabled = false,
                repeatMode = PersistedQueueRepeatMode.ALL
            )
            coordinator.persistActiveQueueSnapshot()

            assertEquals(1, persistence.replaceEntriesCount)
            assertEquals(800L, persistence.queue("A").queue.currentPositionMs)
            assertFalse(persistence.queue("A").queue.shuffleEnabled)
            assertEquals(PersistedQueueRepeatMode.ALL, persistence.queue("A").queue.repeatMode)

            runtime.snapshot = runtime.snapshot?.copy(
                entries = listOf("c" to 3L, "b" to 2L, "a" to 1L).map { (entryId, trackId) ->
                    LivePlaybackQueueItem(entryId, evidence(entryId, trackId))
                },
                baseEntryIds = listOf("a", "b", "c")
            )
            coordinator.persistActiveQueueSnapshot()

            assertEquals(listOf("c", "b", "a"), persistence.queue("A").entries.map {
                it.entryId
            })
            assertEquals(listOf(2, 1, 0), persistence.queue("A").entries.map {
                it.baseOrder
            })
            assertEquals(2, persistence.replaceEntriesCount)
        }

    @Test
    fun newQueueFromCurrentCopiesStateWithoutChangingPlaybackOrActiveQueue() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                id = "A",
                specs = listOf(
                    spec("duplicate-1", 1L, base = 0, playback = 1),
                    spec("duplicate-2", 1L, base = 1, playback = 0)
                ),
                current = "duplicate-2",
                position = 987L,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                ids = listOf("duplicate-2" to 1L, "duplicate-1" to 1L),
                currentEntryId = "duplicate-2",
                positionMs = 987L,
                shouldPlay = true,
                shuffle = true,
                repeat = PersistedQueueRepeatMode.ONE,
                baseEntryIds = listOf("duplicate-1", "duplicate-2")
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val copied = requireNotNull(coordinator.createQueueFromCurrent())

        assertEquals("Queue 2", copied.queue.displayName)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf(1, 0), copied.entries.map { it.baseOrder })
        assertEquals(listOf(0, 1), copied.entries.map { it.playbackOrder })
        assertEquals(2, copied.entries.size)
        assertEquals(1, copied.entries.map { it.trackIdentityId }.distinct().size)
        assertTrue(copied.entries.none { copiedEntry ->
            persistence.queue("A").entries.any { sourceEntry ->
                sourceEntry.entryId == copiedEntry.entryId
            }
        })
        assertEquals(987L, copied.queue.currentPositionMs)
        assertTrue(copied.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.ONE, copied.queue.repeatMode)

        val secondCopy = requireNotNull(coordinator.createQueueFromCurrent())
        assertEquals("Queue 3", secondCopy.queue.displayName)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun generatedQueueNamesAdvancePastRenamedAndExistingGeneratedQueues() {
        assertEquals("Queue 2", nextDefaultQueueName(listOf("Morning")))
        assertEquals("Queue 4", nextDefaultQueueName(listOf("Queue 1", "Queue 3")))
    }

    @Test
    fun unresolvedEntriesAreSkippedAndMissingCurrentFallsForwardDeterministically() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue(
                id = "B",
                specs = listOf(
                    spec("before", 2L, 0, 0),
                    spec("missing-current", 99L, 1, 1),
                    spec("after", 3L, 2, 2),
                    spec("also-missing", 100L, 3, 3)
                ),
                current = "missing-current",
                position = 9_999L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 10L, shouldPlay = false)
        )
        val access = FakeTrackAccess().apply {
            unresolvedIdentityIds += setOf(99L, 100L)
        }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertTrue(coordinator.switchToQueue("B"))

        val restored = requireNotNull(runtime.lastRestoration)
        assertEquals(listOf("before", "after"), restored.entries.map {
            it.persistedEntry.entryId
        })
        assertEquals("after", restored.currentEntryId)
        assertEquals(0L, restored.currentPositionMs)
        assertEquals("B", persistence.activeQueueId)
    }

    @Test
    fun completelyUnresolvableTargetLeavesLiveTimelineAndActiveQueueUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("missing", 99L, 0, 0)), current = "missing"))
        }
        val original = liveSnapshot(listOf("a" to 1L), "a", 42L, shouldPlay = true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess().apply { unresolvedIdentityIds += 99L }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertFalse(coordinator.switchToQueue("B"))

        assertEquals(0, runtime.replaceCount)
        assertEquals(original, runtime.snapshot)
        assertEquals("A", persistence.activeQueueId)
    }

    @Test
    fun createInactiveQueuePreservesSelectionOrderAndLeavesActivePlaybackUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active", position = 900L))
        }
        val original = liveSnapshot(
            ids = listOf("active" to 1L),
            currentEntryId = "active",
            positionMs = 900L,
            shouldPlay = true,
            shuffle = true,
            repeat = PersistedQueueRepeatMode.ONE
        )
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val created = requireNotNull(
            coordinator.createInactiveQueue(listOf(song(3L), song(2L), song(3L)))
        )

        assertEquals("Queue 2", created.queue.displayName)
        assertEquals(listOf(3L, 2L, 3L), created.entries.map { it.trackIdentityId })
        assertEquals(listOf(0, 1, 2), created.entries.map { it.baseOrder })
        assertEquals(listOf(0, 1, 2), created.entries.map { it.playbackOrder })
        assertEquals(3, created.entries.map { it.entryId }.distinct().size)
        assertEquals(created.entries.first().entryId, created.queue.currentEntryId)
        assertEquals(0L, created.queue.currentPositionMs)
        assertFalse(created.queue.shuffleEnabled)
        assertEquals(PersistedQueueRepeatMode.OFF, created.queue.repeatMode)
        assertEquals("A", persistence.activeQueueId)
        assertEquals(listOf("active"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(900L, persistence.queue("A").queue.currentPositionMs)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun failedInactiveQueueResolutionCreatesNothingAndLeavesPlaybackUntouched() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("active", 1L, 0, 0)), "active"))
        }
        val original = liveSnapshot(listOf("active" to 1L), "active", 400L, true)
        val runtime = FakeRuntime(original)
        val access = FakeTrackAccess().apply { unresolvedIdentityIds += 2L }
        val coordinator = coordinator(persistence, runtime, access)
        coordinator.initialize()

        assertNull(coordinator.createInactiveQueue(listOf(song(2L))))

        assertEquals(listOf("A"), persistence.queues.keys.toList())
        assertEquals("A", persistence.activeQueueId)
        assertEquals(original, runtime.snapshot)
        assertEquals(0, runtime.replaceCount)
    }

    @Test
    fun playInNewQueuePreservesOldQueueActivatesNewQueueAndKeepsSongOrder() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a", position = 900L))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L), "a", 900L, shouldPlay = false)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        val created = requireNotNull(
            coordinator.createAndActivateQueue("Album", listOf(song(3L), song(2L), song(3L)))
        )

        assertEquals("generated-queue", persistence.activeQueueId)
        assertEquals("Album", created.queue.displayName)
        assertEquals(listOf(3L, 2L, 3L), runtime.lastRestoration?.entries?.map { it.song.id })
        assertEquals(3, created.entries.map { it.entryId }.distinct().size)
        assertEquals(900L, persistence.queue("A").queue.currentPositionMs)
        assertEquals(listOf("a"), persistence.queue("A").entries.map { it.entryId })
        assertEquals(1, runtime.playCount)
    }

    @Test
    fun singleSongNewQueueUsesPredictableGeneratedName() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(null)

        val created = requireNotNull(
            coordinator(persistence, runtime).createAndActivateQueue("", listOf(song(7L)))
        )

        assertEquals("Queue 1", created.queue.displayName)
        assertEquals(listOf(7L), runtime.lastRestoration?.entries?.map { it.song.id })
        assertTrue(runtime.lastRestoration?.shouldPlay == true)
    }

    @Test
    fun playlistNewQueuePreservesPreparedCustomOrder() = runBlocking {
        val persistence = FakePersistence()
        val runtime = FakeRuntime(null)

        val created = requireNotNull(
            coordinator(persistence, runtime).createAndActivateQueue(
                "Favorites",
                listOf(song(9L), song(4L), song(6L))
            )
        )

        assertEquals("Favorites", created.queue.displayName)
        assertEquals(listOf(9L, 4L, 6L), runtime.lastRestoration?.entries?.map { it.song.id })
    }

    @Test
    fun addingDuplicatesToInactiveQueueAppendsWithoutSwitchingOrTouchingTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue("B", listOf(spec("b", 2L, 0, 0)), current = "b", position = 77L))
        }
        val runtime = FakeRuntime(liveSnapshot(listOf("a" to 1L), "a", 42L, true))
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.appendToInactiveQueue("B", listOf(song(3L), song(3L))) != null)

        assertEquals("A", persistence.activeQueueId)
        assertEquals(0, runtime.replaceCount)
        assertEquals(listOf(2L, 3L, 3L), persistence.queue("B").entries.map { it.trackIdentityId })
        assertEquals(3, persistence.queue("B").entries.map { it.entryId }.distinct().size)
        assertEquals("b", persistence.queue("B").queue.currentEntryId)
        assertEquals(77L, persistence.queue("B").queue.currentPositionMs)
    }

    @Test
    fun activeRemovalAndValidUpcomingReorderUseEntryIdsWithoutReplacingTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(
                    spec("before", 1L, 0, 0),
                    spec("current", 2L, 1, 1),
                    spec("first-upcoming", 3L, 2, 2),
                    spec("dup", 2L, 3, 3)
                ),
                current = "current",
                position = 4_000L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf(
                    "before" to 1L,
                    "current" to 2L,
                    "first-upcoming" to 3L,
                    "dup" to 2L
                ),
                "current",
                4_000L,
                true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.removeEntry("A", "before"))
        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(4_000L, runtime.snapshot?.currentPositionMs)
        assertEquals(
            listOf("current", "first-upcoming", "dup"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
        assertTrue(coordinator.reorderEntry("A", "dup", 1))

        assertEquals("current", runtime.snapshot?.currentEntryId)
        assertEquals(4_000L, runtime.snapshot?.currentPositionMs)
        assertEquals(
            listOf("current", "dup", "first-upcoming"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
        assertEquals(0, runtime.replaceCount)
        assertEquals(
            listOf("current", "dup", "first-upcoming"),
            persistence.queue("A").entries.map { it.entryId }
        )
    }

    @Test
    fun activeReorderRejectsCurrentAndUpcomingMovesAcrossCurrentBoundary() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(
                    spec("current", 1L, 0, 0),
                    spec("upcoming-1", 2L, 1, 1),
                    spec("upcoming-2", 3L, 2, 2)
                ),
                current = "current"
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf("current" to 1L, "upcoming-1" to 2L, "upcoming-2" to 3L),
                "current",
                44L,
                true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertFalse(coordinator.reorderEntry("A", "upcoming-2", 0))
        assertFalse(coordinator.reorderEntry("A", "current", 2))
        assertEquals(0, runtime.moveCount)
        assertEquals(
            listOf("current", "upcoming-1", "upcoming-2"),
            runtime.snapshot?.entries?.map { it.entryId }
        )
    }

    @Test
    fun activeReorderIsDisabledWhileShuffleIsEnabled() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(spec("a", 1L, 0, 0), spec("b", 2L, 1, 1)),
                current = "a",
                shuffle = true
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(listOf("a" to 1L, "b" to 2L), "a", 12L, true, shuffle = true)
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertFalse(coordinator.reorderEntry("A", "b", 0))
        assertEquals(listOf("a", "b"), runtime.snapshot?.entries?.map { it.entryId })
    }

    @Test
    fun inactiveReorderPersistsWithoutChangingLiveTimeline() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue("A", listOf(spec("a", 1L, 0, 0)), current = "a"))
            seed(queue(
                "B",
                listOf(spec("b1", 2L, 0, 0), spec("b2", 3L, 1, 1)),
                current = "b1",
                position = 333L
            ))
        }
        val original = liveSnapshot(listOf("a" to 1L), "a", 44L, true)
        val runtime = FakeRuntime(original)
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.reorderEntry("B", "b2", 0))

        assertEquals(original, runtime.snapshot)
        assertEquals(listOf("b2", "b1"), persistence.queue("B").entries.map { it.entryId })
        assertEquals("b1", persistence.queue("B").queue.currentEntryId)
        assertEquals(333L, persistence.queue("B").queue.currentPositionMs)
    }

    @Test
    fun removingCurrentEntryChoosesNextThenPreviousAndResetsPosition() = runBlocking {
        val persistence = FakePersistence(activeQueueId = "A").apply {
            seed(queue(
                "A",
                listOf(spec("before", 1L, 0, 0), spec("current", 2L, 1, 1), spec("after", 3L, 2, 2)),
                current = "current",
                position = 2_500L
            ))
        }
        val runtime = FakeRuntime(
            liveSnapshot(
                listOf("before" to 1L, "current" to 2L, "after" to 3L),
                "current",
                2_500L,
                true
            )
        )
        val coordinator = coordinator(persistence, runtime)
        coordinator.initialize()

        assertTrue(coordinator.removeEntry("A", "current"))
        assertEquals("after", runtime.snapshot?.currentEntryId)
        assertEquals(0L, runtime.snapshot?.currentPositionMs)
        assertTrue(coordinator.removeEntry("A", "after"))
        assertEquals("before", runtime.snapshot?.currentEntryId)
        assertEquals(0L, runtime.snapshot?.currentPositionMs)
    }

    private fun coordinator(
        persistence: FakePersistence,
        runtime: FakeRuntime,
        access: FakeTrackAccess = FakeTrackAccess()
    ) = PlaybackQueueCoordinator(
        persistence = persistence,
        trackAccess = access,
        runtime = runtime,
        queueIdFactory = { "generated-queue" }
    )

    private class FakeRuntime(
        var snapshot: LivePlaybackQueueSnapshot?
    ) : PlaybackQueueRuntime {
        var replaceCount = 0
        var prepareCount = 0
        var playCount = 0
        var lastRestoration: PlaybackQueueRestoration? = null
        var seekedEntryId: String? = null
        var seekCount = 0
        var insertCount = 0
        var moveCount = 0

        override fun captureSnapshot(): LivePlaybackQueueSnapshot? = snapshot

        override fun replaceTimeline(restoration: PlaybackQueueRestoration) {
            replaceCount += 1
            prepareCount += 1
            if (restoration.shouldPlay) playCount += 1
            lastRestoration = restoration
            snapshot = liveSnapshot(
                ids = restoration.entries
                    .sortedBy { it.persistedEntry.playbackOrder }
                    .map { it.persistedEntry.entryId to it.song.id },
                currentEntryId = restoration.currentEntryId,
                positionMs = restoration.currentPositionMs,
                shouldPlay = restoration.shouldPlay,
                shuffle = restoration.shuffleEnabled,
                repeat = restoration.repeatMode
            )
        }

        override fun removeEntry(entryId: String): Boolean {
            val before = snapshot ?: return false
            val index = before.entries.indexOfFirst { it.entryId == entryId }
            if (index < 0) return false
            val remaining = before.entries.filterNot { it.entryId == entryId }
            val newCurrent = if (before.currentEntryId == entryId) {
                remaining.getOrNull(index)?.entryId ?: remaining.lastOrNull()?.entryId
            } else {
                before.currentEntryId
            }
            snapshot = before.copy(
                entries = remaining,
                baseEntryIds = before.baseEntryIds.filterNot { it == entryId },
                currentEntryId = newCurrent,
                currentPositionMs = before.currentPositionMs.takeIf {
                    newCurrent == before.currentEntryId
                } ?: 0L
            )
            return true
        }

        override fun moveEntry(entryId: String, toPlaybackOrder: Int): Boolean {
            val before = snapshot ?: return false
            val entries = before.entries.toMutableList()
            val from = entries.indexOfFirst { it.entryId == entryId }
            if (from < 0 || toPlaybackOrder !in entries.indices) return false
            entries.add(toPlaybackOrder, entries.removeAt(from))
            snapshot = before.copy(entries = entries)
            moveCount += 1
            return true
        }

        override fun seekToEntry(entryId: String): Boolean {
            val before = snapshot ?: return false
            if (before.entries.none { it.entryId == entryId }) return false
            if (before.currentEntryId != entryId) {
                seekedEntryId = entryId
                seekCount += 1
                snapshot = before.copy(currentEntryId = entryId, currentPositionMs = 0L)
            }
            return true
        }

        override fun insertEntry(
            item: ResolvedPlaybackQueueItem,
            playbackOrder: Int
        ): Boolean {
            val before = snapshot ?: return false
            val entries = before.entries.toMutableList()
            entries.add(playbackOrder.coerceIn(0, entries.size), LivePlaybackQueueItem(
                item.persistedEntry.entryId,
                evidence(item.persistedEntry.entryId, item.song.id)
            ))
            snapshot = before.copy(entries = entries)
            insertCount += 1
            return true
        }
    }

    private class FakeTrackAccess : PlaybackQueueTrackAccess {
        val unresolvedIdentityIds = mutableSetOf<Long>()
        var beforeResolve: (() -> Unit)? = null

        override suspend fun identify(
            items: List<LivePlaybackQueueItem>
        ): List<IdentifiedLivePlaybackQueueItem> = items.mapNotNull { item ->
            val id = item.evidence.reference.mediaStoreId
                ?: item.evidence.referenceKey.substringAfter("track-").toLong()
            if (id in unresolvedIdentityIds) null else IdentifiedLivePlaybackQueueItem(
                liveItem = item,
                trackIdentityId = id,
                localTrackBindingId = id * 10L
            )
        }

        override suspend fun resolve(
            entries: List<PlaybackQueueEntryEntity>
        ): List<ResolvedPlaybackQueueItem> {
            beforeResolve?.invoke()
            return entries.mapNotNull { entry ->
                if (entry.trackIdentityId in unresolvedIdentityIds) null else {
                    ResolvedPlaybackQueueItem(entry, song(entry.trackIdentityId))
                }
            }
        }
    }

    private class FakePersistence(
        var activeQueueId: String? = null
    ) : PlaybackQueuePersistence {
        val queues = linkedMapOf<String, PlaybackQueueWithEntries>()
        var createCount = 0
        var replaceEntriesCount = 0
        var failNextReplace = false

        fun seed(queue: PlaybackQueueWithEntries) {
            queues[queue.queue.queueId] = queue
        }

        fun queue(id: String): PlaybackQueueWithEntries = requireNotNull(queues[id])

        override suspend fun listQueues(): List<PlaybackQueueEntity> =
            queues.values.map(PlaybackQueueWithEntries::queue)

        override suspend fun getActiveQueueId(): String? = activeQueueId

        override suspend fun setActiveQueue(queueId: String?) {
            activeQueueId = queueId
        }

        override suspend fun loadQueue(queueId: String): PlaybackQueueWithEntries? =
            queues[queueId]?.ordered()

        override suspend fun createQueue(
            queueId: String,
            displayName: String,
            entries: List<PlaybackQueueEntryDraft>,
            currentEntryId: String?,
            currentPositionMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: PersistedQueueRepeatMode
        ): PlaybackQueueWithEntries {
            createCount += 1
            val queue = PlaybackQueueWithEntries(
                queue = entity(
                    id = queueId,
                    name = displayName,
                    current = currentEntryId,
                    position = currentPositionMs,
                    shuffle = shuffleEnabled,
                    repeat = repeatMode
                ),
                entries = entries.map { it.entity(queueId) }
            )
            queues[queueId] = queue
            return queue.ordered()
        }

        override suspend fun replaceEntries(
            queueId: String,
            entries: List<PlaybackQueueEntryDraft>,
            currentEntryId: String?,
            currentPositionMs: Long
        ): PlaybackQueueWithEntries {
            replaceEntriesCount += 1
            if (failNextReplace) {
                failNextReplace = false
                error("checkpoint failed")
            }
            val old = queue(queueId)
            val replacement = PlaybackQueueWithEntries(
                queue = old.queue.copy(
                    currentEntryId = currentEntryId,
                    currentPositionMs = currentPositionMs
                ),
                entries = entries.map { it.entity(queueId) }
            )
            queues[queueId] = replacement
            return replacement.ordered()
        }

        override suspend fun updateSavedPlaybackState(
            queueId: String,
            currentEntryId: String?,
            currentPositionMs: Long,
            shuffleEnabled: Boolean,
            repeatMode: PersistedQueueRepeatMode
        ) {
            val old = queue(queueId)
            queues[queueId] = old.copy(
                queue = old.queue.copy(
                    currentEntryId = currentEntryId,
                    currentPositionMs = currentPositionMs,
                    shuffleEnabled = shuffleEnabled,
                    repeatMode = repeatMode
                )
            )
        }

        override suspend fun duplicateQueue(
            sourceQueueId: String,
            displayName: String
        ): PlaybackQueueWithEntries {
            val source = queue(sourceQueueId)
            val queueId = "copy-${queues.size}"
            val copiedIds = source.entries.associate { entry ->
                entry.entryId to "$queueId-${entry.entryId}"
            }
            val copied = PlaybackQueueWithEntries(
                queue = source.queue.copy(
                    queueId = queueId,
                    displayName = displayName,
                    currentEntryId = source.queue.currentEntryId?.let(copiedIds::get)
                ),
                entries = source.entries.map { entry ->
                    entry.copy(
                        entryId = checkNotNull(copiedIds[entry.entryId]),
                        queueId = queueId
                    )
                }
            )
            queues[queueId] = copied
            return copied.ordered()
        }

        override suspend fun appendEntries(
            queueId: String,
            entries: List<PlaybackQueueEntryDraft>
        ): PlaybackQueueWithEntries {
            val old = queue(queueId)
            val start = old.entries.size
            val appended = old.copy(
                entries = old.entries + entries.mapIndexed { index, entry ->
                    entry.copy(baseOrder = start + index, playbackOrder = start + index)
                        .entity(queueId)
                }
            )
            queues[queueId] = appended
            return appended.ordered()
        }

        override suspend fun removeEntry(
            queueId: String,
            entryId: String
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val remaining = old.entries.filterNot { it.entryId == entryId }
                .mapIndexed { index, entry ->
                    entry.copy(baseOrder = index, playbackOrder = index)
                }
            return old.copy(entries = remaining).also { queues[queueId] = it }.ordered()
        }

        override suspend fun reorderEntry(
            queueId: String,
            entryId: String,
            toPlaybackOrder: Int,
            updateBaseOrder: Boolean
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val entries = old.entries.sortedBy { it.playbackOrder }.toMutableList()
            val from = entries.indexOfFirst { it.entryId == entryId }
            if (from < 0 || toPlaybackOrder !in entries.indices) return old
            entries.add(toPlaybackOrder, entries.removeAt(from))
            val updated = old.copy(entries = entries.mapIndexed { index, entry ->
                entry.copy(
                    playbackOrder = index,
                    baseOrder = if (updateBaseOrder) index else entry.baseOrder
                )
            })
            queues[queueId] = updated
            return updated.ordered()
        }

        override suspend fun restoreEntry(
            queueId: String,
            entry: PlaybackQueueEntryEntity,
            restoredCurrentEntryId: String?,
            restoredCurrentPositionMs: Long?
        ): PlaybackQueueWithEntries? {
            val old = queues[queueId] ?: return null
            val entries = old.entries.filterNot { it.entryId == entry.entryId }.toMutableList()
            entries.add(entry.playbackOrder.coerceIn(0, entries.size), entry)
            val updated = old.copy(
                queue = old.queue.copy(
                    currentEntryId = restoredCurrentEntryId ?: old.queue.currentEntryId,
                    currentPositionMs = restoredCurrentPositionMs ?: old.queue.currentPositionMs
                ),
                entries = entries.mapIndexed { index, candidate ->
                    candidate.copy(playbackOrder = index, baseOrder = index)
                }
            )
            queues[queueId] = updated
            return updated.ordered()
        }

        private fun PlaybackQueueWithEntries.ordered() = copy(
            entries = entries.sortedBy(PlaybackQueueEntryEntity::playbackOrder)
        )
    }

    private data class EntrySpec(
        val entryId: String,
        val trackId: Long,
        val base: Int,
        val playback: Int
    )

    private companion object {
        fun spec(entryId: String, trackId: Long, base: Int, playback: Int) =
            EntrySpec(entryId, trackId, base, playback)

        fun queue(
            id: String,
            specs: List<EntrySpec>,
            current: String?,
            position: Long = 0L,
            shuffle: Boolean = false,
            repeat: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF
        ) = PlaybackQueueWithEntries(
            queue = entity(id, id, current, position, shuffle, repeat),
            entries = specs.map { spec ->
                PlaybackQueueEntryEntity(
                    entryId = spec.entryId,
                    queueId = id,
                    trackIdentityId = spec.trackId,
                    localTrackBindingId = spec.trackId * 10L,
                    baseOrder = spec.base,
                    playbackOrder = spec.playback
                )
            }.sortedBy(PlaybackQueueEntryEntity::playbackOrder)
        )

        fun entity(
            id: String,
            name: String,
            current: String?,
            position: Long,
            shuffle: Boolean,
            repeat: PersistedQueueRepeatMode
        ) = PlaybackQueueEntity(
            queueId = id,
            displayName = name,
            createdAt = 1L,
            updatedAt = 1L,
            lastActiveAt = 1L,
            sourceType = null,
            sourceKey = null,
            currentEntryId = current,
            currentPositionMs = position,
            shuffleEnabled = shuffle,
            repeatMode = repeat
        )

        fun PlaybackQueueEntryDraft.entity(queueId: String) = PlaybackQueueEntryEntity(
            entryId = entryId,
            queueId = queueId,
            trackIdentityId = trackIdentityId,
            localTrackBindingId = localTrackBindingId,
            baseOrder = baseOrder,
            playbackOrder = playbackOrder
        )

        fun liveSnapshot(
            ids: List<Pair<String, Long>>,
            currentEntryId: String,
            positionMs: Long,
            shouldPlay: Boolean,
            shuffle: Boolean = false,
            repeat: PersistedQueueRepeatMode = PersistedQueueRepeatMode.OFF,
            baseEntryIds: List<String> = ids.map { pair -> pair.first }
        ) = LivePlaybackQueueSnapshot(
            entries = ids.map { (entryId, trackId) ->
                LivePlaybackQueueItem(entryId, evidence(entryId, trackId))
            },
            baseEntryIds = baseEntryIds,
            currentEntryId = currentEntryId,
            currentPositionMs = positionMs,
            shouldPlay = shouldPlay,
            shuffleEnabled = shuffle,
            repeatMode = repeat
        )

        fun evidence(entryId: String, trackId: Long) = ListeningMediaItemEvidence(
            itemInstanceId = entryId,
            referenceKey = "track-$trackId",
            reference = SongReference(
                mediaStoreId = null,
                volumeName = "external",
                contentUri = "content://track/$trackId",
                relativePath = "Music/",
                displayName = "$trackId.flac",
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 1L,
                duration = 180_000L,
                title = "Track $trackId",
                artist = "Artist",
                album = "Album",
                albumArtist = "Artist",
                legacyStableKey = "legacy-$trackId",
                portableKey = "portable-$trackId",
                portableKeyVersion = 1
            )
        )

        fun song(id: Long): Song {
            val uri = mock(Uri::class.java)
            org.mockito.Mockito.`when`(uri.toString()).thenReturn("content://media/$id")
            return Song(
                id = id,
                title = "Track $id",
                artist = "Artist",
                album = "Album",
                trackNumber = id.toInt(),
                duration = 180_000L,
                uri = uri,
                filePath = "/music/$id.flac",
                folderPath = "/music",
                albumArtUri = null,
                volumeName = "external",
                displayName = "$id.flac",
                relativePath = "Music/",
                fileSizeBytes = 1_000L,
                dateModifiedEpochSeconds = 1L
            )
        }
    }
}
