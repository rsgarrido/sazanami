package com.example.cdplaya.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SmoothPlaybackTransitionCoordinatorTest {
    @Test
    fun pauseFadesFromFullGainToZeroBeforeForwardingPause() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)

        harness.coordinator.requestPause()
        assertFalse(harness.coordinator.logicalPlayWhenReady)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())

        harness.advanceBy(100)
        assertClose(0.5f, harness.coordinator.envelope)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())

        harness.advanceBy(100)
        assertClose(0f, harness.coordinator.envelope)
        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
        assertEquals(
            SmoothPlaybackTransitionState.PAUSED_SILENT,
            harness.coordinator.state
        )
    }

    @Test
    fun playStartsSilentWaitsForAudibleThenFadesToFullGain() {
        val harness = Harness(initialPlayWhenReady = false, initialAudible = false)

        harness.coordinator.requestPlay()
        assertTrue(harness.coordinator.logicalPlayWhenReady)
        assertEquals(listOf(true), harness.output.playWhenReadyCalls)
        assertClose(0f, harness.output.volume)
        assertEquals(
            SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE,
            harness.coordinator.state
        )

        harness.advanceBy(500)
        assertClose(0f, harness.output.volume)

        harness.coordinator.onAudibilityChanged(true)
        harness.advanceBy(200)

        assertClose(1f, harness.output.volume)
        assertEquals(
            SmoothPlaybackTransitionState.FULLY_AUDIBLE,
            harness.coordinator.state
        )
    }

    @Test
    fun pauseThenPlayReversesFromCurrentEnvelope() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(100)
        val midpoint = harness.coordinator.envelope

        harness.coordinator.requestPlay()

        assertClose(midpoint, harness.coordinator.envelope)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())
        harness.advanceBy(100)
        assertClose(1f, harness.coordinator.envelope)
    }

    @Test
    fun playThenPauseReversesFromCurrentEnvelope() {
        val harness = Harness(initialPlayWhenReady = false, initialAudible = false)
        harness.coordinator.requestPlay()
        harness.coordinator.onAudibilityChanged(true)
        harness.advanceBy(100)
        val midpoint = harness.coordinator.envelope

        harness.coordinator.requestPause()

        assertClose(midpoint, harness.coordinator.envelope)
        harness.advanceBy(100)
        assertClose(0f, harness.coordinator.envelope)
        assertEquals(listOf(true, false), harness.output.playWhenReadyCalls)
    }

    @Test
    fun repeatedRapidTogglesNeverJumpToAnEndpoint() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(60)
        val afterPause = harness.coordinator.envelope
        harness.coordinator.requestPlay()
        harness.advanceBy(30)
        val afterPlay = harness.coordinator.envelope
        harness.coordinator.requestPause()

        assertTrue(afterPause in 0f..1f)
        assertTrue(afterPlay > afterPause)
        assertClose(afterPlay, harness.coordinator.envelope)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())
    }

    @Test
    fun pauseWhileBufferingForwardsImmediatelyWithoutFadeDelay() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = false)

        harness.coordinator.requestPause()

        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
        assertClose(0f, harness.coordinator.envelope)
        assertEquals(
            SmoothPlaybackTransitionState.PAUSED_SILENT,
            harness.coordinator.state
        )
    }

    @Test
    fun bufferingDuringFadeInReturnsToSilentWaitingState() {
        val harness = Harness(initialPlayWhenReady = false, initialAudible = false)
        harness.coordinator.requestPlay()
        harness.coordinator.onAudibilityChanged(true)
        harness.advanceBy(80)
        assertTrue(harness.coordinator.envelope > 0f)

        harness.coordinator.onAudibilityChanged(false)

        assertClose(0f, harness.coordinator.envelope)
        assertEquals(
            SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE,
            harness.coordinator.state
        )
        harness.advanceBy(500)
        assertClose(0f, harness.coordinator.envelope)
    }

    @Test
    fun disabledPreferenceUsesImmediateExistingBehavior() {
        val harness = Harness(
            initialPlayWhenReady = true,
            initialAudible = true,
            enabled = false,
            baselineVolume = 0.65f
        )

        harness.coordinator.requestPause()

        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
        assertClose(0.65f, harness.output.volume)

        harness.coordinator.requestPlay()

        assertEquals(listOf(false, true), harness.output.playWhenReadyCalls)
        assertClose(0.65f, harness.output.volume)
    }

    @Test
    fun disablingDuringFadeResolvesLogicalPauseAndRestoresBaseline() {
        val harness = Harness(
            initialPlayWhenReady = true,
            initialAudible = true,
            baselineVolume = 0.7f
        )
        harness.coordinator.requestPause()
        harness.advanceBy(80)

        harness.coordinator.setEnabled(false)

        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
        assertClose(1f, harness.coordinator.envelope)
        assertClose(0.7f, harness.output.volume)
        harness.advanceBy(500)
        assertClose(0.7f, harness.output.volume)
    }

    @Test
    fun disablingDuringFadeInFinishesImmediatelyAtBaseline() {
        val harness = Harness(
            initialPlayWhenReady = false,
            initialAudible = false,
            baselineVolume = 0.55f
        )
        harness.coordinator.requestPlay()
        harness.coordinator.onAudibilityChanged(true)
        harness.advanceBy(80)

        harness.coordinator.setEnabled(false)

        assertEquals(listOf(true), harness.output.playWhenReadyCalls)
        assertTrue(harness.coordinator.logicalPlayWhenReady)
        assertClose(1f, harness.coordinator.envelope)
        assertClose(0.55f, harness.output.volume)
    }

    @Test
    fun effectiveVolumeAlwaysComposesBaselineAndEnvelope() {
        val harness = Harness(
            initialPlayWhenReady = true,
            initialAudible = true,
            baselineVolume = 0.6f
        )
        harness.coordinator.requestPause()
        harness.advanceBy(100)

        assertClose(0.5f, harness.coordinator.envelope)
        assertClose(0.3f, harness.output.volume)
    }

    @Test
    fun baselineChangeDuringFadeUsesCurrentEnvelopeImmediately() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(100)

        harness.coordinator.setBaselineVolume(0.4f)

        assertClose(0.4f, harness.coordinator.baselineVolume)
        assertClose(0.2f, harness.output.volume)
        harness.advanceBy(100)
        assertClose(0f, harness.output.volume)
    }

    @Test
    fun safetyBypassDuringFadeOutPausesImmediately() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(60)

        harness.coordinator.bypassForSafety()

        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
        assertClose(0f, harness.output.volume)
        harness.advanceBy(500)
        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
    }

    @Test
    fun safetyBypassDuringFadeInCancelsWithoutPausingPlayIntent() {
        val harness = Harness(initialPlayWhenReady = false, initialAudible = false)
        harness.coordinator.requestPlay()
        harness.coordinator.onAudibilityChanged(true)
        harness.advanceBy(60)

        harness.coordinator.bypassForSafety()

        assertEquals(listOf(true), harness.output.playWhenReadyCalls)
        assertTrue(harness.coordinator.logicalPlayWhenReady)
        assertClose(0f, harness.output.volume)
        assertEquals(
            SmoothPlaybackTransitionState.WAITING_FOR_AUDIBLE,
            harness.coordinator.state
        )
    }

    @Test
    fun systemPauseCancelsFadeAndUpdatesLogicalState() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(50)

        harness.coordinator.onSystemPlayWhenReadyChanged(false)

        assertFalse(harness.coordinator.logicalPlayWhenReady)
        assertClose(0f, harness.output.volume)
        harness.advanceBy(500)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())
    }

    @Test
    fun releaseCancelsPendingFramesWithoutForwardingAnotherCommand() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)
        harness.coordinator.requestPause()
        harness.advanceBy(50)
        val volumeAtRelease = harness.output.volume

        harness.coordinator.release()
        harness.advanceBy(500)

        assertClose(volumeAtRelease, harness.output.volume)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())
    }

    @Test
    fun roleRebindCancelsOldFadeAndUsesIncomingBaselineAndPlayIntent() {
        val harness = Harness(
            initialPlayWhenReady = true,
            initialAudible = true,
            baselineVolume = 0.8f
        )
        harness.coordinator.requestPause()
        harness.advanceBy(60)

        harness.coordinator.rebindPhysicalPlayer(
            physicalPlayWhenReady = false,
            isAudible = false,
            baselineVolume = 0.35f,
            logicalPlayWhenReady = true
        )
        val volumeAfterRebind = harness.output.volume
        harness.advanceBy(500)

        assertClose(0.35f, volumeAfterRebind)
        assertClose(volumeAfterRebind, harness.output.volume)
        assertTrue(harness.output.playWhenReadyCalls.isEmpty())

        harness.coordinator.activateReboundPhysicalPlayer()
        assertEquals(listOf(true), harness.output.playWhenReadyCalls)
        harness.coordinator.onAudibilityChanged(true)
        harness.coordinator.requestPause()
        harness.advanceBy(100)

        assertClose(0.5f, harness.coordinator.envelope)
        assertClose(0.175f, harness.output.volume)
    }

    @Test
    fun completedPauseIsForwardedExactlyOnce() {
        val harness = Harness(initialPlayWhenReady = true, initialAudible = true)

        harness.coordinator.requestPause()
        harness.advanceBy(1_000)
        harness.coordinator.requestPause()
        harness.advanceBy(1_000)

        assertEquals(listOf(false), harness.output.playWhenReadyCalls)
    }

    private class Harness(
        initialPlayWhenReady: Boolean,
        initialAudible: Boolean,
        baselineVolume: Float = 1f,
        enabled: Boolean = true
    ) {
        val clock = FakeClock()
        val scheduler = FakeScheduler(clock)
        val output = RecordingOutput(
            playWhenReady = initialPlayWhenReady,
            volume = baselineVolume
        )
        val coordinator = SmoothPlaybackTransitionCoordinator(
            output = output,
            clock = clock,
            scheduler = scheduler,
            initialPhysicalPlayWhenReady = initialPlayWhenReady,
            initialAudible = initialAudible,
            initialBaselineVolume = baselineVolume,
            initiallyEnabled = enabled,
            durationMillis = 200L,
            frameIntervalMillis = 10L
        )

        fun advanceBy(millis: Long) {
            scheduler.advanceBy(millis)
        }
    }

    private class RecordingOutput(
        var playWhenReady: Boolean,
        var volume: Float
    ) : SmoothPlaybackTransitionOutput {
        val playWhenReadyCalls = mutableListOf<Boolean>()

        override fun setPhysicalPlayWhenReady(playWhenReady: Boolean) {
            this.playWhenReady = playWhenReady
            playWhenReadyCalls += playWhenReady
        }

        override fun setEffectiveVolume(volume: Float) {
            this.volume = volume
        }
    }

    private class FakeClock : PlaybackTransitionClock {
        var nowMillis: Long = 0L

        override fun elapsedRealtimeMillis(): Long = nowMillis
    }

    private class FakeScheduler(
        private val clock: FakeClock
    ) : PlaybackTransitionScheduler {
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
        ): TransitionCancellation {
            val task = Task(
                dueMillis = clock.nowMillis + delayMillis,
                order = nextOrder++,
                action = action
            )
            tasks += task
            return TransitionCancellation { task.cancelled = true }
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
