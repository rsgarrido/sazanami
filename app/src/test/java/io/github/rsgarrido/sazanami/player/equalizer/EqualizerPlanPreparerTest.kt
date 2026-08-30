package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomCalculator
import io.github.rsgarrido.sazanami.player.equalizer.dsp.BiquadDesigner
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.dsp.KotlinEqualizerDspEngine
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EqualizerPlanPreparerTest {
    @Test
    fun defaultSnapshotPreparesAsBypassForActualFormat() {
        val format = format(sampleRateHz = 44_100, channelCount = 2)

        val plan = EqualizerPlanPreparer.prepare(
            snapshot = EqualizerRuntimeSnapshot.DEFAULT,
            processorFormat = format
        )

        assertTrue(plan.bypassed)
        assertEquals(0L, plan.sourceSnapshotVersion)
        assertEquals(format, plan.processorFormat)
        assertEquals(44_100, plan.cascade.sampleRateHz)
        assertEquals(2, plan.cascade.channelCount)
        assertEquals(0, plan.validFilterCount)
        assertTrue(plan.ignoredFilters.isEmpty())
    }

    @Test
    fun validFiltersPreserveOrderAndMatchPhaseADesigner() {
        val first = EqualizerFilterSpec.HighShelf(
            frequencyHz = 6_000.0,
            gainDb = 3.0,
            slope = 0.8
        )
        val second = EqualizerFilterSpec.Peaking(
            frequencyHz = 1_000.0,
            gainDb = -4.0,
            q = 1.41
        )
        val plan = EqualizerPlanPreparer.prepare(
            snapshot(
                version = 7L,
                filters = listOf(first, second)
            ),
            format()
        )
        val expected = listOf(first, second)
            .flatMap { filter ->
                val coefficient = BiquadDesigner.design(filter, 48_000)
                listOf(
                    coefficient.b0,
                    coefficient.b1,
                    coefficient.b2,
                    coefficient.a1,
                    coefficient.a2
                )
            }
            .toDoubleArray()

        assertEquals(7L, plan.sourceSnapshotVersion)
        assertEquals(2, plan.validFilterCount)
        assertArrayEquals(
            expected,
            plan.cascade.coefficientsCopy(),
            1e-12
        )
    }

    @Test
    fun automaticHeadroomMatchesPhaseACalculator() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = 2.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = 6.0,
                    q = 1.41
                )
            )
        )
        val snapshot = EqualizerRuntimeSnapshot(
            version = 3L,
            configuration = configuration,
            automaticHeadroomEnabled = true
        )

        val plan = EqualizerPlanPreparer.prepare(snapshot, format())
        val expected = AutomaticHeadroomCalculator.calculate(
            configuration,
            sampleRateHz = 48_000
        )

        assertEquals(
            expected,
            plan.automaticHeadroomResult
        )
    }

    @Test
    fun sixteenKilohertzBandIsIgnoredAt32KhzWithoutClamping() {
        val plan = EqualizerPlanPreparer.prepare(
            snapshot(
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 16_000.0,
                        gainDb = 6.0,
                        q = 1.41
                    )
                )
            ),
            format(sampleRateHz = 32_000)
        )

        assertTrue(plan.bypassed)
        assertEquals(0, plan.validFilterCount)
        assertEquals(1, plan.ignoredFilters.size)
        assertEquals(
            16_000.0,
            plan.ignoredFilters.single().frequencyHz,
            0.0
        )
        assertEquals(
            IgnoredEqualizerFilterReason.AT_OR_ABOVE_NYQUIST,
            plan.ignoredFilters.single().reason
        )
    }

    @Test
    fun sixteenKilohertzBandIsValidAt44100Hz() {
        val plan = EqualizerPlanPreparer.prepare(
            snapshot(
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 16_000.0,
                        gainDb = 6.0,
                        q = 1.41
                    )
                )
            ),
            format(sampleRateHz = 44_100)
        )

        assertFalse(plan.bypassed)
        assertEquals(1, plan.validFilterCount)
        assertTrue(plan.ignoredFilters.isEmpty())
    }

    @Test
    fun invalidBandDoesNotDisableValidBand() {
        val valid = EqualizerFilterSpec.Peaking(
            frequencyHz = 1_000.0,
            gainDb = 4.0,
            q = 1.0
        )
        val invalid = EqualizerFilterSpec.LowShelf(
            frequencyHz = 200.0,
            gainDb = 4.0,
            slope = 2.0
        )

        val plan = EqualizerPlanPreparer.prepare(
            snapshot(filters = listOf(invalid, valid)),
            format()
        )

        assertFalse(plan.bypassed)
        assertEquals(1, plan.validFilterCount)
        assertEquals(1, plan.ignoredFilters.size)
        assertEquals(
            IgnoredEqualizerFilterReason.INVALID_PARAMETERS,
            plan.ignoredFilters.single().reason
        )
        assertTrue(plan.cascade.coefficientsCopy().all(Double::isFinite))
    }

    @Test
    fun preparedEngineMatchesPhaseAConfigurationPathAndReservesCapacity() {
        val configuration = EqualizerConfiguration(
            enabled = true,
            preampDb = -2.0,
            filters = listOf(
                EqualizerFilterSpec.LowShelf(150.0, 4.0, 0.8),
                EqualizerFilterSpec.Peaking(1_000.0, -3.0, 1.41)
            )
        )
        val plan = EqualizerPlanPreparer.prepare(
            EqualizerRuntimeSnapshot(
                version = 9L,
                configuration = configuration,
                automaticHeadroomEnabled = false
            ),
            format(channelCount = 2)
        )
        val input = FloatArray(2_048) { index ->
            ((index % 31) / 31.0f) - 0.5f
        }
        val expected = FloatArray(input.size)
        val actual = FloatArray(input.size)
        KotlinEqualizerDspEngine().also { engine ->
            engine.configure(configuration, 48_000, 2)
            engine.processInterleaved(
                input,
                0,
                expected,
                0,
                input.size / 2
            )
        }
        val path = plan.createProcessingPath()

        path.process(
            input = input,
            output = actual,
            frameCount = input.size / 2
        )

        assertArrayEquals(expected, actual, 0.0f)
        val capacity = requireNotNull(path.capacitySnapshot())
        assertTrue(capacity.sectionCapacity >= 10)
        assertTrue(capacity.channelCapacity >= 8)
    }

    private fun snapshot(
        version: Long = 1L,
        filters: List<EqualizerFilterSpec>
    ): EqualizerRuntimeSnapshot {
        return EqualizerRuntimeSnapshot(
            version = version,
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = filters
            ),
            automaticHeadroomEnabled = false
        )
    }

    private fun format(
        sampleRateHz: Int = 48_000,
        channelCount: Int = 2
    ): EqualizerProcessorFormat {
        return EqualizerProcessorFormat(
            sampleRateHz = sampleRateHz,
            channelCount = channelCount,
            pcmEncoding = C.ENCODING_PCM_16BIT
        )
    }
}
