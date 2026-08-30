package io.github.rsgarrido.sazanami.player

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackStateCheckpointPolicyTest {
    @Test
    fun checkpointsOnlyWhilePlayingAfterInterval() {
        val policy = PlaybackStateCheckpointPolicy(intervalMillis = 10_000L)
        policy.recordCheckpoint(1_000L)

        assertFalse(policy.shouldCheckpoint(isPlaying = false, nowMillis = 20_000L))
        assertFalse(policy.shouldCheckpoint(isPlaying = true, nowMillis = 10_999L))
        assertTrue(policy.shouldCheckpoint(isPlaying = true, nowMillis = 11_000L))
    }

    @Test
    fun recordingCheckpointRestartsThrottleWindow() {
        val policy = PlaybackStateCheckpointPolicy(intervalMillis = 10_000L)
        policy.recordCheckpoint(1_000L)
        assertTrue(policy.shouldCheckpoint(true, 11_000L))
        policy.recordCheckpoint(11_000L)
        assertFalse(policy.shouldCheckpoint(true, 20_999L))
    }
}
