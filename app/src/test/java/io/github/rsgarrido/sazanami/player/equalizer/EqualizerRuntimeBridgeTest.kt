package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterMeterSnapshot
import java.lang.reflect.Modifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EqualizerRuntimeBridgeTest {
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        EqualizerRuntimeBridge.release()
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    }

    @After
    fun tearDown() {
        EqualizerRuntimeBridge.release()
        scope.cancel()
    }

    @Test
    fun defaultSnapshotIsDisabledAndFlat() {
        val snapshot = EqualizerRuntimeBridge.requestedSnapshot()

        assertFalse(snapshot.configuration.enabled)
        assertTrue(snapshot.configuration.isEffectivelyFlat)
        assertEquals(0L, snapshot.version)
    }

    @Test
    fun requestedVersionsIncreaseAndLatestSnapshotWins() {
        val first = EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(gainDb = 2.0),
            automaticHeadroomEnabled = false
        )
        val second = EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(gainDb = 6.0),
            automaticHeadroomEnabled = true
        )

        assertTrue(second.version > first.version)
        assertSame(second, EqualizerRuntimeBridge.requestedSnapshot())
        assertTrue(
            EqualizerRuntimeBridge
                .requestedSnapshot()
                .automaticHeadroomEnabled
        )
    }

    @Test
    fun activeRequestRequiresDecodedPcmBeforeItsPlanIsPrepared() {
        val snapshot = EqualizerRuntimeBridge.requestConfiguration(
            configuration = activeConfiguration(gainDb = 6.0),
            automaticHeadroomEnabled = true
        )
        val state = EqualizerRuntimeBridge.state.value

        assertTrue(state.requestedEnabled)
        assertTrue(state.effectivelyActive)
        assertFalse(state.bypassed)
        assertTrue(state.requiresDecodedPcm)
        assertEquals(snapshot.version, state.configurationVersion)
        assertEquals(null, state.appliedPlanVersion)
    }

    @Test
    fun limiterRequestRequiresDecodedPcmBeforePreparationAndPublishesFormat() {
        val format = format(96_000)
        EqualizerRuntimeBridge.publishProcessorFormat(format)
        EqualizerRuntimeBridge.start(scope)
        val snapshot = EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = 0.0,
                filters = emptyList()
            ),
            automaticHeadroomEnabled = true,
            limiterConfiguration = LimiterConfiguration(
                enabled = true,
                ceilingDbfs = -2.0
            )
        )

        assertTrue(EqualizerRuntimeBridge.state.value.requiresDecodedPcm)
        waitUntil {
            EqualizerRuntimeBridge
                .latestCompatibleLimiterConfiguration(format)
                ?.configurationVersion == snapshot.version
        }
        EqualizerRuntimeBridge.publishStateForTest()
        val state = EqualizerRuntimeBridge.state.value

        assertTrue(state.limiterRequestedEnabled)
        assertEquals(-2.0, state.limiterCeilingDbfs, 0.0)
        assertEquals(480, state.limiterLookaheadFrames)
        assertEquals(5.0, state.limiterLookaheadMilliseconds, 0.0)
        assertEquals(100.0, state.limiterReleaseMilliseconds, 0.0)
    }

    @Test
    fun enabledFlatRequestRemainsBypassedAndAllowsUserOffloadPolicy() {
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = emptyList()
            ),
            automaticHeadroomEnabled = true
        )
        val state = EqualizerRuntimeBridge.state.value

        assertTrue(state.requestedEnabled)
        assertFalse(state.effectivelyActive)
        assertTrue(state.bypassed)
        assertFalse(state.requiresDecodedPcm)
    }

    @Test
    fun comparisonBypassKeepsDecodedPcmRequiredUntilSessionEnds() {
        val active = activeConfiguration(gainDb = 6.0)
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = active,
            automaticHeadroomEnabled = true
        )
        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = true,
            bypassed = true
        )
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = active.preampDb,
                filters = active.filters
            ),
            automaticHeadroomEnabled = true
        )

        val bypass = EqualizerRuntimeBridge.state.value
        assertTrue(bypass.comparisonSessionActive)
        assertTrue(bypass.comparisonBypassed)
        assertTrue(bypass.requiresDecodedPcm)

        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = true,
            bypassed = false
        )
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = active,
            automaticHeadroomEnabled = true
        )
        assertTrue(
            EqualizerRuntimeBridge.state.value
                .requiresDecodedPcm
        )

        EqualizerRuntimeBridge.setComparisonState(
            sessionActive = false,
            bypassed = false
        )
        EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = false,
                preampDb = active.preampDb,
                filters = active.filters
            ),
            automaticHeadroomEnabled = true
        )
        val ended = EqualizerRuntimeBridge.state.value
        assertFalse(ended.comparisonSessionActive)
        assertFalse(ended.comparisonBypassed)
        assertFalse(ended.requiresDecodedPcm)
    }

    @Test
    fun requestWithOnlyNyquistInvalidFilterAllowsOffloadAfterPreparation() {
        val format = format(32_000)
        EqualizerRuntimeBridge.publishProcessorFormat(format)
        EqualizerRuntimeBridge.start(scope)
        val snapshot = EqualizerRuntimeBridge.requestConfiguration(
            configuration = EqualizerConfiguration(
                enabled = true,
                preampDb = 0.0,
                filters = listOf(
                    EqualizerFilterSpec.Peaking(
                        frequencyHz = 16_000.0,
                        gainDb = 6.0,
                        q = 1.41
                    )
                )
            ),
            automaticHeadroomEnabled = true
        )

        waitUntil {
            EqualizerRuntimeBridge
                .latestCompatiblePath(format)
                ?.plan
                ?.sourceSnapshotVersion == snapshot.version &&
                !EqualizerRuntimeBridge.state.value.requiresDecodedPcm
        }
        val state = EqualizerRuntimeBridge.state.value

        assertTrue(state.requestedEnabled)
        assertFalse(state.effectivelyActive)
        assertTrue(state.bypassed)
        assertFalse(state.requiresDecodedPcm)
        assertEquals(0, state.validFilterCount)
        assertEquals(1, state.ignoredFilterCount)
    }

    @Test
    fun latestCompatiblePlanWinsAcrossVersionAndFormatChanges() {
        val format48 = format(48_000)
        EqualizerRuntimeBridge.publishProcessorFormat(format48)
        EqualizerRuntimeBridge.start(scope)
        val first = EqualizerRuntimeBridge.requestConfiguration(
            activeConfiguration(gainDb = 2.0),
            automaticHeadroomEnabled = false
        )
        waitUntil {
            EqualizerRuntimeBridge
                .latestCompatiblePath(format48)
                ?.plan
                ?.sourceSnapshotVersion == first.version
        }
        val second = EqualizerRuntimeBridge.requestConfiguration(
            activeConfiguration(gainDb = 7.0),
            automaticHeadroomEnabled = true
        )
        waitUntil {
            EqualizerRuntimeBridge
                .latestCompatiblePath(format48)
                ?.plan
                ?.sourceSnapshotVersion == second.version
        }

        val format44 = format(44_100)
        EqualizerRuntimeBridge.publishProcessorFormat(format44)
        waitUntil {
            EqualizerRuntimeBridge
                .latestCompatiblePath(format44)
                ?.plan
                ?.sourceSnapshotVersion == second.version
        }

        assertEquals(
            44_100,
            EqualizerRuntimeBridge
                .latestCompatiblePath(format44)
                ?.plan
                ?.processorFormat
                ?.sampleRateHz
        )
        EqualizerRuntimeBridge.publishStateForTest()
        assertEquals(
            second.version,
            EqualizerRuntimeBridge.state.value.preparedPlanVersion
        )
        assertTrue(
            requireNotNull(
                EqualizerRuntimeBridge
                    .state
                    .value
                    .planPreparationLatencyMillis
            ) >= 0L
        )
        assertEquals(
            null,
            EqualizerRuntimeBridge.latestCompatiblePath(format48)
        )
    }

    @Test
    fun startIsIdempotentAndReleaseStopsCoordinator() {
        val countBefore = EqualizerRuntimeBridge.coordinatorStartCount()

        EqualizerRuntimeBridge.start(scope)
        EqualizerRuntimeBridge.start(scope)

        assertEquals(
            countBefore + 1,
            EqualizerRuntimeBridge.coordinatorStartCount()
        )
        assertTrue(EqualizerRuntimeBridge.isCoordinatorRunning())

        EqualizerRuntimeBridge.release()

        assertFalse(EqualizerRuntimeBridge.isCoordinatorRunning())
        assertEquals(
            EqualizerRuntimeState(),
            EqualizerRuntimeBridge.state.value
        )
    }

    @Test
    fun identicalStatePublicationIsStructurallyDeduplicated() {
        EqualizerRuntimeBridge.publishStateForTest()
        val first = EqualizerRuntimeBridge.state.value

        EqualizerRuntimeBridge.publishStateForTest()

        assertSame(first, EqualizerRuntimeBridge.state.value)
    }

    @Test
    fun inactiveLimiterDisplaysTheSameHeldPeakBeforeAndAfterLimiter() {
        EqualizerRuntimeBridge.publishLimiterMeterSnapshot(
            LimiterMeterSnapshot(
                preLimiterPeakDbfs = -9.0,
                postLimiterPeakDbfs = -2.0
            )
        )

        EqualizerRuntimeBridge.publishStateForTest()

        val state = EqualizerRuntimeBridge.state.value
        assertFalse(state.limiterEffectivelyActive)
        assertEquals(
            state.preLimiterPeakDbfs,
            state.postLimiterPeakDbfs,
            0.0
        )
    }

    @Test
    fun runtimeStateContainsNoAndroidOrMedia3Objects() {
        EqualizerRuntimeState::class.java.declaredFields
            .filterNot { field -> Modifier.isStatic(field.modifiers) }
            .forEach { field ->
                assertFalse(field.type.name.startsWith("android."))
                assertFalse(field.type.name.startsWith("androidx.media3."))
            }
    }

    private fun activeConfiguration(
        gainDb: Double
    ): EqualizerConfiguration {
        return EqualizerConfiguration(
            enabled = true,
            preampDb = 0.0,
            filters = listOf(
                EqualizerFilterSpec.Peaking(
                    frequencyHz = 1_000.0,
                    gainDb = gainDb,
                    q = 1.41
                )
            )
        )
    }

    private fun format(sampleRateHz: Int): EqualizerProcessorFormat {
        return EqualizerProcessorFormat(
            sampleRateHz = sampleRateHz,
            channelCount = 2,
            pcmEncoding = C.ENCODING_PCM_16BIT
        )
    }

    private fun waitUntil(condition: () -> Boolean) {
        val deadline = System.nanoTime() + 3_000_000_000L
        while (!condition() && System.nanoTime() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("Condition was not met before timeout", condition())
    }
}
