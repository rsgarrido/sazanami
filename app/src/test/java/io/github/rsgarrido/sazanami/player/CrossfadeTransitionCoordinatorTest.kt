package io.github.rsgarrido.sazanami.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CrossfadeTransitionCoordinatorTest {
    @Test
    fun eligibilityRequiresLongKnownTrackMatchingReadyStandbyAndExactBaselines() {
        val eligible = eligibilityInput()

        assertTrue(CrossfadeEligibility.isEligible(eligible))
        assertFalse(CrossfadeEligibility.isEligible(eligible.copy(durationMillis = -1L)))
        assertFalse(CrossfadeEligibility.isEligible(eligible.copy(durationMillis = 5_000L)))
        assertFalse(CrossfadeEligibility.isEligible(eligible.copy(standbyPrepared = false)))
        assertFalse(CrossfadeEligibility.isEligible(eligible.copy(targetMatches = false)))
        assertFalse(
            CrossfadeEligibility.isEligible(eligible.copy(incomingBaselineExact = false))
        )
        assertFalse(CrossfadeEligibility.isEligible(eligible.copy(repeatOne = true)))
        assertFalse(
            CrossfadeEligibility.isEligible(eligible.copy(cancelledByInteraction = true))
        )
        assertFalse(
            CrossfadeEligibility.isEligible(
                eligible.copy(preserveNaturalAlbumTransition = true)
            )
        )
    }

    @Test
    fun smoothstepIsComplementaryAtEndpointsMidpointAndIntermediateProgress() {
        assertClose(0f, CrossfadeTransitionCoordinator.smoothstep(0f))
        assertClose(0.5f, CrossfadeTransitionCoordinator.smoothstep(0.5f))
        assertClose(1f, CrossfadeTransitionCoordinator.smoothstep(1f))
        assertClose(0.15625f, CrossfadeTransitionCoordinator.smoothstep(0.25f))
        assertClose(
            1f,
            CrossfadeTransitionCoordinator.smoothstep(0.25f) +
                (1f - CrossfadeTransitionCoordinator.smoothstep(0.25f))
        )
    }

    @Test
    fun positionDrivenCrossfadeStartsAtFiveSecondsAndHandsOffOnceAtMidpoint() {
        val harness = Harness(
            durationMillis = 10_000L,
            positionMillis = 4_999L,
            outgoingBaseline = 0.6f,
            incomingBaseline = 0.4f
        )

        harness.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.SCHEDULED, harness.coordinator.state)
        assertEquals(0, harness.output.startCount)

        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 5_000L)
        harness.advanceBy(500L)
        assertEquals(1, harness.output.startCount)
        assertClose(0.6f, harness.output.outgoingPhysicalVolume)
        assertClose(0f, harness.output.incomingPhysicalVolume)

        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 7_500L)
        harness.advanceBy(50L)
        assertEquals(1, harness.output.midpointCount)
        assertClose(0.3f, harness.output.outgoingPhysicalVolume)
        assertClose(0.2f, harness.output.incomingPhysicalVolume)
        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            harness.coordinator.state
        )

        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 8_000L)
        harness.advanceBy(50L)
        assertEquals(1, harness.output.midpointCount)

        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 10_000L)
        harness.advanceBy(50L)
        assertEquals(1, harness.output.completeCount)
        assertClose(0f, harness.output.outgoingPhysicalVolume)
        assertClose(0.4f, harness.output.incomingPhysicalVolume)
        assertEquals(CrossfadeTransitionState.IDLE, harness.coordinator.state)
    }

    @Test
    fun bufferingCancelsWithoutFadingOutgoingIntoSilence() {
        val harness = Harness(durationMillis = 10_000L, positionMillis = 5_000L)
        harness.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.CROSSFADING, harness.coordinator.state)
        harness.output.snapshot = harness.output.snapshot.copy(
            positionMillis = 5_500L,
            incomingProgressing = false
        )

        harness.advanceBy(50L)

        assertEquals(1, harness.output.cancelCount)
        assertFalse(harness.output.cancelledAfterMidpoint)
        assertTrue(harness.output.outgoingPhysicalVolume > 0f)
        assertEquals(CrossfadeTransitionState.CANCELLED, harness.coordinator.state)
    }

    @Test
    fun preparedIncomingGetsBoundedTimeToStartBeforeCrossfadeIsCancelled() {
        val harness = Harness(
            durationMillis = 10_000L,
            positionMillis = 5_000L,
            incomingProgressing = false
        )

        harness.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.CROSSFADING, harness.coordinator.state)
        assertEquals(0, harness.output.cancelCount)
        assertTrue(harness.output.envelopes.isEmpty())

        harness.advanceBy(100L)
        assertEquals(CrossfadeTransitionState.CROSSFADING, harness.coordinator.state)
        assertEquals(0, harness.output.cancelCount)

        harness.output.snapshot = harness.output.snapshot.copy(
            positionMillis = 5_100L,
            incomingProgressing = true
        )
        harness.advanceBy(50L)

        assertEquals(CrossfadeTransitionState.CROSSFADING, harness.coordinator.state)
        assertEquals(0, harness.output.cancelCount)
        assertTrue(harness.output.envelopes.isNotEmpty())
    }

    @Test
    fun incomingThatNeverStartsIsCancelledAfterStartupGracePeriod() {
        val harness = Harness(
            durationMillis = 10_000L,
            positionMillis = 5_000L,
            incomingProgressing = false
        )

        harness.coordinator.reevaluate()
        repeat(
            (
                CrossfadeTransitionCoordinator.INCOMING_START_GRACE_MILLIS /
                    CrossfadeTransitionCoordinator.FRAME_INTERVAL_MILLIS
                ).toInt()
        ) {
            harness.output.snapshot = harness.output.snapshot.copy(
                positionMillis = harness.output.snapshot.positionMillis +
                    CrossfadeTransitionCoordinator.FRAME_INTERVAL_MILLIS
            )
            harness.advanceBy(CrossfadeTransitionCoordinator.FRAME_INTERVAL_MILLIS)
        }

        assertEquals(1, harness.output.cancelCount)
        assertEquals(CrossfadeTransitionState.CANCELLED, harness.coordinator.state)
    }

    @Test
    fun explicitCancellationPreventsStaleScheduledFrames() {
        val harness = Harness(durationMillis = 20_000L, positionMillis = 1_000L)
        harness.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.SCHEDULED, harness.coordinator.state)

        harness.coordinator.cancel(permanent = true)
        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 15_000L)
        harness.advanceBy(10_000L)

        assertEquals(0, harness.output.startCount)
        assertTrue(harness.output.envelopes.isEmpty())
        assertEquals(CrossfadeTransitionState.CANCELLED, harness.coordinator.state)
    }

    @Test
    fun cancellationAfterMidpointSelectsIncomingAndCancelsStaleFrames() {
        val harness = Harness(durationMillis = 10_000L, positionMillis = 5_000L)
        harness.coordinator.reevaluate()
        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 7_500L)
        harness.advanceBy(50L)
        assertEquals(1, harness.output.midpointCount)

        harness.coordinator.cancel(permanent = true)
        val envelopeCount = harness.output.envelopes.size
        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 10_000L)
        harness.advanceBy(1_000L)

        assertEquals(1, harness.output.cancelCount)
        assertTrue(harness.output.cancelledAfterMidpoint)
        assertEquals(envelopeCount, harness.output.envelopes.size)
        assertEquals(0, harness.output.completeCount)
    }

    @Test
    fun cancellationBeforeMidpointSelectsOutgoingAndCancelsStaleFrames() {
        val harness = Harness(durationMillis = 10_000L, positionMillis = 5_000L)
        harness.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.CROSSFADING, harness.coordinator.state)

        harness.coordinator.cancel(permanent = true)
        val envelopeCount = harness.output.envelopes.size
        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 10_000L)
        harness.advanceBy(1_000L)

        assertEquals(1, harness.output.cancelCount)
        assertFalse(harness.output.cancelledAfterMidpoint)
        assertEquals(envelopeCount, harness.output.envelopes.size)
        assertEquals(0, harness.output.completeCount)
    }

    @Test
    fun oneAndTwelveSecondDurationsScheduleFromTheirOwnWindows() {
        val oneSecond = Harness(
            durationMillis = 10_000L,
            positionMillis = 8_999L,
            crossfadeDurationMillis = 1_000L
        )
        oneSecond.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.SCHEDULED, oneSecond.coordinator.state)
        oneSecond.output.snapshot = oneSecond.output.snapshot.copy(positionMillis = 9_000L)
        oneSecond.advanceBy(1L)
        assertEquals(CrossfadeTransitionState.CROSSFADING, oneSecond.coordinator.state)

        val twelveSeconds = Harness(
            durationMillis = 20_000L,
            positionMillis = 8_000L,
            crossfadeDurationMillis = 12_000L
        )
        twelveSeconds.coordinator.reevaluate()
        assertEquals(CrossfadeTransitionState.CROSSFADING, twelveSeconds.coordinator.state)
    }

    @Test
    fun activeTransitionRetainsCapturedDurationAfterPreferenceChange() {
        val harness = Harness(
            durationMillis = 20_000L,
            positionMillis = 15_000L,
            crossfadeDurationMillis = 5_000L
        )
        harness.coordinator.reevaluate()
        harness.coordinator.updateDuration(1_000L)
        assertEquals(5_000L, harness.coordinator.currentDurationMillis)

        harness.output.snapshot = harness.output.snapshot.copy(positionMillis = 17_500L)
        harness.advanceBy(50L)

        assertEquals(1, harness.output.midpointCount)
        assertClose(0.5f, harness.output.incomingPhysicalVolume)

        harness.coordinator.cancel(permanent = false)
        assertEquals(1_000L, harness.coordinator.currentDurationMillis)
    }

    private fun eligibilityInput() = CrossfadeEligibilityInput(
        durationMillis = 10_000L,
        crossfadeDurationMillis = 5_000L,
        standbyPrepared = true,
        targetMatches = true,
        incomingBaselineExact = true,
        outgoingBaselineExact = true,
        repeatOne = false,
        shuffleEnabled = false,
        pipelinesValid = true,
        cancelledByInteraction = false,
        preserveNaturalAlbumTransition = false
    )

    private class Harness(
        durationMillis: Long,
        positionMillis: Long,
        outgoingBaseline: Float = 1f,
        incomingBaseline: Float = 1f,
        crossfadeDurationMillis: Long = 5_000L,
        incomingProgressing: Boolean = true
    ) {
        private val clock = FakeClock()
        private val scheduler = FakeScheduler(clock)
        val output = RecordingOutput(
            snapshot = CrossfadePlaybackSnapshot(
                eligible = true,
                durationMillis = durationMillis,
                positionMillis = positionMillis,
                outgoingProgressing = true,
                incomingProgressing = incomingProgressing
            ),
            outgoingBaseline = outgoingBaseline,
            incomingBaseline = incomingBaseline
        )
        val coordinator = CrossfadeTransitionCoordinator(
            output = output,
            clock = clock,
            scheduler = scheduler,
            durationMillis = crossfadeDurationMillis
        )

        fun advanceBy(millis: Long) {
            scheduler.advanceBy(millis)
        }
    }

    private class RecordingOutput(
        var snapshot: CrossfadePlaybackSnapshot,
        private val outgoingBaseline: Float,
        private val incomingBaseline: Float
    ) : CrossfadeTransitionOutput {
        var startCount = 0
        var midpointCount = 0
        var completeCount = 0
        var cancelCount = 0
        var cancelledAfterMidpoint = false
        var outgoingPhysicalVolume = outgoingBaseline
        var incomingPhysicalVolume = 0f
        val envelopes = mutableListOf<Pair<Float, Float>>()

        override fun snapshot(): CrossfadePlaybackSnapshot = snapshot

        override fun onCrossfadeStart(): Boolean {
            startCount += 1
            return true
        }

        override fun onCrossfadeEnvelope(
            outgoingEnvelope: Float,
            incomingEnvelope: Float,
            progress: Float
        ) {
            envelopes += outgoingEnvelope to incomingEnvelope
            outgoingPhysicalVolume = outgoingBaseline * outgoingEnvelope
            incomingPhysicalVolume = incomingBaseline * incomingEnvelope
        }

        override fun onLogicalMidpoint(): Boolean {
            midpointCount += 1
            return true
        }

        override fun onCrossfadeComplete(): Boolean {
            completeCount += 1
            return true
        }

        override fun onCrossfadeCancelled(logicallyHandedOff: Boolean) {
            cancelCount += 1
            cancelledAfterMidpoint = logicallyHandedOff
        }
    }

    private class FakeClock : CrossfadeClock {
        var nowMillis = 0L
        override fun elapsedRealtimeMillis(): Long = nowMillis
    }

    private class FakeScheduler(
        private val clock: FakeClock
    ) : CrossfadeScheduler {
        private data class Task(
            val dueMillis: Long,
            val order: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        )

        private val tasks = mutableListOf<Task>()
        private var nextOrder = 0L

        override fun schedule(
            delayMillis: Long,
            action: () -> Unit
        ): CrossfadeCancellation {
            val task = Task(clock.nowMillis + delayMillis, nextOrder++, action)
            tasks += task
            return CrossfadeCancellation { task.cancelled = true }
        }

        fun advanceBy(millis: Long) {
            val target = clock.nowMillis + millis
            while (true) {
                val next = tasks
                    .filterNot { it.cancelled }
                    .filter { it.dueMillis <= target }
                    .minWithOrNull(compareBy<Task> { it.dueMillis }.thenBy { it.order })
                    ?: break
                tasks.remove(next)
                clock.nowMillis = next.dueMillis
                next.action()
            }
            clock.nowMillis = target
        }
    }

    private companion object {
        fun assertClose(expected: Float, actual: Float) {
            assertEquals(expected.toDouble(), actual.toDouble(), 0.0001)
        }
    }
}
