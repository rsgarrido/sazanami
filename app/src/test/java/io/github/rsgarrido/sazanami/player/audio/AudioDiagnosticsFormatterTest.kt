package io.github.rsgarrido.sazanami.player.audio

import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPlanApplicationMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioDiagnosticsFormatterTest {
    @Test
    fun sourceFormattingOmitsUnknownValuesAndLabelsKnownValuesAsSource() {
        assertEquals("Unknown", formatAudioSource(null))
        assertEquals(
            "FLAC · 96 kHz · 2 channels · 2304 kbps source",
            formatAudioSource(
                AudioSourceFormat(
                    sampleMimeType = "audio/flac",
                    sampleRateHz = 96_000,
                    channelCount = 2,
                    bitrateBitsPerSecond = 2_304_000
                )
            )
        )
    }

    @Test
    fun requestedActiveAndSleepingWordingAreDistinct() {
        val requested = AudioOffloadRuntimeState.create(
            AudioOffloadPreference.AUTOMATIC,
            isOffloadedPlayback = false,
            isSleepingForOffload = false
        )
        val active = AudioOffloadRuntimeState.create(
            AudioOffloadPreference.AUTOMATIC,
            isOffloadedPlayback = true,
            isSleepingForOffload = false
        )
        val sleeping = AudioOffloadRuntimeState.create(
            AudioOffloadPreference.AUTOMATIC,
            isOffloadedPlayback = true,
            isSleepingForOffload = true
        )

        assertEquals(
            "Automatic requested — not active for the current playback",
            formatAudioOffloadStatus(requested)
        )
        assertEquals("Active", formatAudioOffloadStatus(active))
        assertEquals("Active — processor sleeping", formatAudioOffloadStatus(sleeping))
    }

    @Test
    fun formattingMakesNoBitPerfectOrHardwareOutputClaim() {
        val text = formatAudioSource(
            AudioSourceFormat(sampleMimeType = "audio/flac", sampleRateHz = 192_000)
        )

        assertFalse(text.contains("bit-perfect", ignoreCase = true))
        assertFalse(text.contains("hardware output", ignoreCase = true))
        assertTrue(text.contains("FLAC"))
    }

    @Test
    fun equalizerFormattingDistinguishesBypassActiveAndFormat() {
        val bypassed = EqualizerRuntimeState()
        val active = EqualizerRuntimeState(
            processorConfigured = true,
            requestedEnabled = true,
            effectivelyActive = true,
            bypassed = false,
            sampleRateHz = 48_000,
            channelCount = 2,
            requiresDecodedPcm = true
        )

        assertEquals("Bypassed", formatEqualizerStatus(bypassed))
        assertEquals("Active", formatEqualizerStatus(active))
        assertEquals(
            "PCM16, 48 kHz, 2 channels",
            formatEqualizerProcessorFormat(active)
        )
        assertEquals(
            "Unconfigured",
            formatEqualizerProcessorFormat(bypassed)
        )
    }

    @Test
    fun compatibilityReportsEqualizerConstraintOnlyWhenPresent() {
        val bypassed = AudioOutputUiState()
        val active = AudioOutputUiState(
            offloadState = AudioOffloadRuntimeState(
                knownCompatibilityConstraints =
                    AudioOffloadRuntimeState.DEFAULT_COMPATIBILITY_CONSTRAINTS +
                        AudioCompatibilityConstraint
                            .EQUALIZER_REQUIRES_DECODED_PCM
            )
        )

        assertFalse(
            formatAudioCompatibility(bypassed)
                .contains("Equalizer requires decoded PCM")
        )
        assertTrue(
            formatAudioCompatibility(active)
                .contains("Equalizer requires decoded PCM")
        )
    }

    @Test
    fun equalizerTimingDistinguishesCrossfadeFromMedia3Flush() {
        val crossfade = EqualizerRuntimeState(
            lastPlanApplicationMode =
                EqualizerPlanApplicationMode.CROSSFADE,
            lastTransitionFrameCount = 882,
            lastTransitionDurationMillis = 20.0,
            lastTransitionSampleRateHz = 44_100,
            planPreparationLatencyMillis = 7L,
            planApplicationLatencyMillis = 18L
        )
        val flushed = crossfade.copy(
            lastPlanApplicationMode =
                EqualizerPlanApplicationMode.DIRECT_AFTER_FLUSH,
            lastTransitionFrameCount = 0,
            lastTransitionDurationMillis = 0.0
        )

        assertEquals(
            "20.00 ms crossfade " +
                "(882 frames at 44.1 kHz; last completed adoption)",
            formatEqualizerPlanApplication(crossfade)
        )
        assertEquals(
            "Direct after Media3 flush",
            formatEqualizerPlanApplication(flushed)
        )
        assertEquals(
            "7 ms preparation · 18 ms to DSP application",
            formatEqualizerPlanLatency(crossfade)
        )
    }
}
