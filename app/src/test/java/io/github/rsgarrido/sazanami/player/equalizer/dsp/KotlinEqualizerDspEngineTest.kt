package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class KotlinEqualizerDspEngineTest {
    @Test
    fun disabledAndEffectivelyFlatBypassPreserveRawFloatBits() {
        val input = bypassSamples()
        listOf(
            flatConfiguration(enabled = false),
            EqualizerConfiguration(
                enabled = true,
                preampDb = EQUALIZER_DB_EPSILON,
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 1_000.0,
                        gainDb = -EQUALIZER_DB_EPSILON,
                        q = 1.0
                    )
                )
            )
        ).forEach { configuration ->
            val output = FloatArray(input.size)
            val engine = configuredEngine(
                configuration = configuration,
                channelCount = 1
            )

            engine.processInterleaved(
                input,
                0,
                output,
                0,
                input.size
            )

            assertRawBitsEqual(input, output)
        }
    }

    @Test
    fun bypassDoesNotMutateInputAndExactInPlaceDoesNothing() {
        val input = bypassSamples()
        val original = input.copyOf()
        val output = FloatArray(input.size)
        val engine = configuredEngine(flatConfiguration(), channelCount = 1)

        engine.processInterleaved(input, 0, output, 0, input.size)
        engine.processInterleaved(input, 0, input, 0, input.size)

        assertRawBitsEqual(original, input)
        assertRawBitsEqual(original, output)
    }

    @Test
    fun segmentedBypassEqualsOneShotBypassExactly() {
        val input = bypassSamples() + bypassSamples()
        val oneShot = FloatArray(input.size)
        val segmented = FloatArray(input.size)
        configuredEngine(flatConfiguration(), 1).processInterleaved(
            input,
            0,
            oneShot,
            0,
            input.size
        )
        val segmentedEngine = configuredEngine(flatConfiguration(), 1)

        segmentedEngine.processInterleaved(input, 0, segmented, 0, 5)
        segmentedEngine.processInterleaved(
            input,
            5,
            segmented,
            5,
            input.size - 5
        )

        assertRawBitsEqual(oneShot, segmented)
    }

    @Test
    fun preampGainIsAppliedExactlyOnceWithoutClipping() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 6.020_599_913_279_624,
            filters = emptyList()
        )
        val output = FloatArray(2)
        configuredEngine(configuration, 1).processInterleaved(
            input = floatArrayOf(0.25f, 1.25f),
            inputOffset = 0,
            output = output,
            outputOffset = 0,
            frameCount = 2
        )

        assertEquals(0.5f, output[0], SIGNAL_TOLERANCE)
        assertEquals(2.5f, output[1], SIGNAL_TOLERANCE)
        assertTrue(output[1] > 1.0f)
    }

    @Test
    fun peakingFilterBoostsSignalNearItsCenter() {
        val input = sineWave(
            frequencyHz = 1_000.0,
            sampleRateHz = 48_000,
            frameCount = 8_192
        )
        val output = FloatArray(input.size)
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 6.0,
                    q = 1.41
                )
            )
        )

        configuredEngine(configuration, 1).processInterleaved(
            input,
            0,
            output,
            0,
            input.size
        )

        assertTrue(rms(output) > rms(input) * 1.8)
    }

    @Test
    fun highQDecayFlushesOnlyAfterReachingInaudibleState() {
        val frameCount = 200_000
        val input = FloatArray(frameCount)
        input[0] = 1.0f
        val output = FloatArray(frameCount)
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 12.0,
                    q = 20.0
                )
            )
        )

        configuredEngine(configuration, 1)
            .processInterleaved(
                input,
                0,
                output,
                0,
                frameCount
            )

        val lastNonZeroIndex =
            output.indexOfLast { sample -> sample != 0.0f }
        assertTrue(lastNonZeroIndex > 0)
        assertTrue(lastNonZeroIndex < frameCount - 1_000)
        assertTrue(
            kotlin.math.abs(output[lastNonZeroIndex]) <=
                BIQUAD_STATE_FLUSH_THRESHOLD.toFloat() * 8.0f
        )
        output.takeLast(1_000).forEach { sample ->
            assertEquals(0.0f, sample, 0.0f)
        }
    }

    @Test
    fun oneShotAndSegmentedProcessingMatch() {
        val channelCount = 2
        val input = sineWave(
            frequencyHz = 777.0,
            sampleRateHz = 48_000,
            frameCount = 2_048,
            channelCount = channelCount
        )
        val oneShot = FloatArray(input.size)
        val segmented = FloatArray(input.size)
        val configuration = cascadeConfiguration()
        configuredEngine(configuration, channelCount).processInterleaved(
            input,
            0,
            oneShot,
            0,
            input.size / channelCount
        )
        val segmentedEngine = configuredEngine(configuration, channelCount)
        val firstFrames = 317

        segmentedEngine.processInterleaved(
            input,
            0,
            segmented,
            0,
            firstFrames
        )
        segmentedEngine.processInterleaved(
            input,
            firstFrames * channelCount,
            segmented,
            firstFrames * channelCount,
            input.size / channelCount - firstFrames
        )

        assertArrayEquals(oneShot, segmented, 0.0f)
    }

    @Test
    fun exactInPlaceMatchesOutOfPlaceAndLeavesSeparateInputUnchanged() {
        val input = sineWave(1_234.0, 48_000, 1_024, channelCount = 2)
        val original = input.copyOf()
        val outOfPlace = FloatArray(input.size)
        val inPlace = input.copyOf()
        val configuration = cascadeConfiguration()

        configuredEngine(configuration, 2).processInterleaved(
            input,
            0,
            outOfPlace,
            0,
            input.size / 2
        )
        configuredEngine(configuration, 2).processInterleaved(
            inPlace,
            0,
            inPlace,
            0,
            inPlace.size / 2
        )

        assertArrayEquals(original, input, 0.0f)
        assertFloatArraysClose(outOfPlace, inPlace)
    }

    @Test
    fun monoStereoAndSixChannelStatesAreIndependent() {
        listOf(1, 2, 6).forEach { channelCount ->
            val frameCount = 128
            val impulseChannel = channelCount - 1
            val input = FloatArray(frameCount * channelCount)
            input[impulseChannel] = 1.0f
            val output = FloatArray(input.size)
            configuredEngine(cascadeConfiguration(), channelCount)
                .processInterleaved(
                    input,
                    0,
                    output,
                    0,
                    frameCount
                )

            repeat(frameCount) { frameIndex ->
                repeat(channelCount) { channelIndex ->
                    if (channelIndex != impulseChannel) {
                        assertEquals(
                            "leak at frame $frameIndex channel $channelIndex",
                            0.0f,
                            output[frameIndex * channelCount + channelIndex],
                            0.0f
                        )
                    }
                }
            }
            assertTrue(output.any { sample -> sample != 0.0f })
        }
    }

    @Test
    fun resetClearsHistoryAndRetainsCoefficients() {
        val configuration = cascadeConfiguration()
        val engine = configuredEngine(configuration, 1)
        val seed = FloatArray(64).also { samples -> samples[0] = 1.0f }
        engine.processInterleaved(seed, 0, FloatArray(seed.size), 0, seed.size)
        engine.reset()
        val probe = FloatArray(64).also { samples -> samples[0] = 0.5f }
        val afterReset = FloatArray(probe.size)
        val fresh = FloatArray(probe.size)

        engine.processInterleaved(
            probe,
            0,
            afterReset,
            0,
            probe.size
        )
        configuredEngine(configuration, 1).processInterleaved(
            probe,
            0,
            fresh,
            0,
            probe.size
        )

        assertArrayEquals(fresh, afterReset, 0.0f)
    }

    @Test
    fun reconfigurationDoesNotLeakOldHistory() {
        val engine = configuredEngine(cascadeConfiguration(), 1)
        val impulse = FloatArray(64).also { samples -> samples[0] = 1.0f }
        engine.processInterleaved(
            impulse,
            0,
            FloatArray(impulse.size),
            0,
            impulse.size
        )
        val newConfiguration = EqualizerConfiguration(
            enabled = true,
            preampDb = 3.0,
            filters = listOf(
                EqualizerFilterSpec.HighShelf(
                    frequencyHz = 5_000.0,
                    gainDb = -2.0
                )
            )
        )
        engine.configure(newConfiguration, 48_000, 1)
        val afterReconfiguration = FloatArray(impulse.size)
        val fresh = FloatArray(impulse.size)

        engine.processInterleaved(
            impulse,
            0,
            afterReconfiguration,
            0,
            impulse.size
        )
        configuredEngine(newConfiguration, 1).processInterleaved(
            impulse,
            0,
            fresh,
            0,
            impulse.size
        )

        assertArrayEquals(fresh, afterReconfiguration, 0.0f)
    }

    @Test
    fun validProcessingIsFiniteAcrossSampleRateMatrix() {
        TEST_SAMPLE_RATES.forEach { sampleRateHz ->
            val input = sineWave(997.0, sampleRateHz, 2_048)
            val output = FloatArray(input.size)
            configuredEngine(
                configuration = cascadeConfiguration(),
                channelCount = 1,
                sampleRateHz = sampleRateHz
            ).processInterleaved(
                input,
                0,
                output,
                0,
                input.size
            )

            assertTrue(
                "non-finite output at $sampleRateHz Hz",
                output.all(Float::isFinite)
            )
        }
    }

    @Test
    fun invalidConfigurationAndBufferRangesAreRejected() {
        val engine = KotlinEqualizerDspEngine()
        assertThrows(IllegalStateException::class.java) {
            engine.processInterleaved(
                FloatArray(1),
                0,
                FloatArray(1),
                0,
                1
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.configure(flatConfiguration(), 0, 1)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.configure(flatConfiguration(), 48_000, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            engine.configure(
                flatConfiguration(),
                48_000,
                1,
                automaticHeadroomDb = -1.0
            )
        }

        engine.configure(flatConfiguration(), 48_000, 2)
        val input = FloatArray(8)
        val output = FloatArray(8)
        listOf(
            { engine.processInterleaved(input, -1, output, 0, 1) },
            { engine.processInterleaved(input, 0, output, -1, 1) },
            { engine.processInterleaved(input, 0, output, 0, -1) },
            { engine.processInterleaved(input, 7, output, 0, 1) },
            { engine.processInterleaved(input, 0, output, 7, 1) },
            {
                engine.processInterleaved(
                    input,
                    0,
                    output,
                    0,
                    Int.MAX_VALUE
                )
            }
        ).forEach { invalidCall ->
            assertThrows(IllegalArgumentException::class.java) {
                invalidCall()
            }
        }
    }

    @Test
    fun partiallyOverlappingRangesAreRejected() {
        val samples = FloatArray(16)
        val engine = configuredEngine(flatConfiguration(), 1)

        assertThrows(IllegalArgumentException::class.java) {
            engine.processInterleaved(
                input = samples,
                inputOffset = 0,
                output = samples,
                outputOffset = 1,
                frameCount = 8
            )
        }
    }

    private fun configuredEngine(
        configuration: EqualizerConfiguration,
        channelCount: Int,
        sampleRateHz: Int = 48_000
    ): KotlinEqualizerDspEngine {
        return KotlinEqualizerDspEngine().also { engine ->
            engine.configure(
                configuration = configuration,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount
            )
        }
    }

    private fun cascadeConfiguration(): EqualizerConfiguration {
        return EqualizerConfiguration(
            enabled = true,
            preampDb = 1.5,
            filters = listOf(
                EqualizerFilterSpec.LowShelf(
                    frequencyHz = 120.0,
                    gainDb = 2.0,
                    slope = 0.8
                ),
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 4.0,
                    q = 1.41
                ),
                EqualizerFilterSpec.HighShelf(
                    frequencyHz = 4_000.0,
                    gainDb = -2.0,
                    slope = 1.0
                )
            )
        )
    }

    private fun bypassSamples(): FloatArray {
        return floatArrayOf(
            0.75f,
            -0.75f,
            0.0f,
            -0.0f,
            Float.fromBits(1),
            Float.fromBits(0x80000001.toInt()),
            1.25f,
            -2.5f
        )
    }

    private fun assertRawBitsEqual(
        expected: FloatArray,
        actual: FloatArray
    ) {
        assertEquals(expected.size, actual.size)
        expected.indices.forEach { index ->
            assertEquals(
                "raw bits at sample $index",
                expected[index].toRawBits(),
                actual[index].toRawBits()
            )
        }
    }
}
