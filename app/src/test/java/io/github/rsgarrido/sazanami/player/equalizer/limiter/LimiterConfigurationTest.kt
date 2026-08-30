package io.github.rsgarrido.sazanami.player.equalizer.limiter

import kotlin.math.exp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test

class LimiterConfigurationTest {
    @Test
    fun defaultsAndNormalizationAreStable() {
        val defaults = LimiterConfiguration()

        assertFalse(defaults.enabled)
        assertEquals(-1.0, defaults.ceilingDbfs, 0.0)
        assertEquals(
            -1.0,
            LimiterConfiguration(ceilingDbfs = -0.96).ceilingDbfs,
            0.0
        )
    }

    @Test
    fun supportedCeilingEndpointsAreAccepted() {
        assertEquals(
            -3.0,
            LimiterConfiguration(ceilingDbfs = -3.0).ceilingDbfs,
            0.0
        )
        assertEquals(
            0.0,
            LimiterConfiguration(ceilingDbfs = 0.0).ceilingDbfs,
            0.0
        )
    }

    @Test
    fun invalidCeilingsAreRejected() {
        listOf(
            Double.NaN,
            Double.POSITIVE_INFINITY,
            -3.1,
            0.1
        ).forEach { ceiling ->
            assertThrows(IllegalArgumentException::class.java) {
                LimiterConfiguration(ceilingDbfs = ceiling)
            }
        }
    }

    @Test
    fun lookaheadAndReleaseAreSampleRateAware() {
        mapOf(
            32_000 to 160,
            44_100 to 221,
            48_000 to 240,
            96_000 to 480,
            192_000 to 960
        ).forEach { (sampleRate, expectedFrames) ->
            assertEquals(
                expectedFrames,
                LimiterMath.lookaheadFrames(sampleRate)
            )
            assertEquals(
                exp(-1.0 / (sampleRate * 0.1)),
                LimiterMath.releaseCoefficient(sampleRate),
                1.0e-15
            )
        }
    }

    @Test
    fun preparedConfigurationValidatesFormatAndVersion() {
        assertThrows(IllegalArgumentException::class.java) {
            LimiterPreparedConfiguration.prepare(
                LimiterConfiguration(enabled = true),
                sampleRateHz = 0,
                channelCount = 2,
                configurationVersion = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LimiterPreparedConfiguration.prepare(
                LimiterConfiguration(enabled = true),
                sampleRateHz = 48_000,
                channelCount = 0,
                configurationVersion = 1L
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            LimiterPreparedConfiguration.prepare(
                LimiterConfiguration(enabled = true),
                sampleRateHz = 48_000,
                channelCount = 2,
                configurationVersion = -1L
            )
        }
    }
}
