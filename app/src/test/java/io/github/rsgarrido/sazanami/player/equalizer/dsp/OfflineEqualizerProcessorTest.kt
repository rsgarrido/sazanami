package io.github.rsgarrido.sazanami.player.equalizer.dsp

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertThrows
import org.junit.Test

class OfflineEqualizerProcessorTest {
    @Test
    fun returnsNewOutputWithoutMutatingInput() {
        val input = sineWave(1_000.0, 48_000, 1_024)
        val original = input.copyOf()

        val output = OfflineEqualizerProcessor().process(
            input = input,
            sampleRateHz = 48_000,
            channelCount = 1,
            configuration = configuration()
        )

        assertNotSame(input, output)
        assertArrayEquals(original, input, 0.0f)
    }

    @Test
    fun outputMatchesDirectEngineUse() {
        val input = sineWave(
            frequencyHz = 1_234.0,
            sampleRateHz = 48_000,
            frameCount = 2_048,
            channelCount = 2
        )
        val configuration = configuration()
        val expected = FloatArray(input.size)
        KotlinEqualizerDspEngine().also { engine ->
            engine.configure(
                configuration = configuration,
                sampleRateHz = 48_000,
                channelCount = 2,
                automaticHeadroomDb = 1.25
            )
            engine.processInterleaved(
                input,
                0,
                expected,
                0,
                input.size / 2
            )
        }

        val actual = OfflineEqualizerProcessor().process(
            input = input,
            sampleRateHz = 48_000,
            channelCount = 2,
            configuration = configuration,
            automaticHeadroomDb = 1.25
        )

        assertArrayEquals(expected, actual, 0.0f)
    }

    @Test
    fun supportsMonoStereoAndMultichannelInput() {
        listOf(1, 2, 6).forEach { channelCount ->
            val input = sineWave(
                frequencyHz = 500.0,
                sampleRateHz = 48_000,
                frameCount = 128,
                channelCount = channelCount
            )

            val output = OfflineEqualizerProcessor().process(
                input,
                48_000,
                channelCount,
                configuration()
            )

            assertEquals(input.size, output.size)
        }
    }

    @Test
    fun rejectsNonFrameAlignedInputAndInvalidChannelCount() {
        assertThrows(IllegalArgumentException::class.java) {
            OfflineEqualizerProcessor().process(
                input = FloatArray(5),
                sampleRateHz = 48_000,
                channelCount = 2,
                configuration = flatConfiguration()
            )
        }
        assertThrows(IllegalArgumentException::class.java) {
            OfflineEqualizerProcessor().process(
                input = FloatArray(0),
                sampleRateHz = 48_000,
                channelCount = 0,
                configuration = flatConfiguration()
            )
        }
    }

    @Test
    fun emptyInputIsHandledSafely() {
        val input = FloatArray(0)

        val output = OfflineEqualizerProcessor().process(
            input,
            sampleRateHz = 48_000,
            channelCount = 2,
            configuration = configuration()
        )

        assertNotSame(input, output)
        assertEquals(0, output.size)
    }

    private fun configuration(): EqualizerConfiguration {
        return EqualizerConfiguration(
            enabled = true,
            preampDb = 2.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 4.0,
                    q = 1.41
                )
            )
        )
    }
}
