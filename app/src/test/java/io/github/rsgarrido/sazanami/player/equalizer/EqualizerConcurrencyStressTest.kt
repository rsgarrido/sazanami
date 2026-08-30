package io.github.rsgarrido.sazanami.player.equalizer

import androidx.media3.common.C
import io.github.rsgarrido.sazanami.player.equalizer.performance.EqualizerPerformanceFixtures
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class EqualizerConcurrencyStressTest {
    @Before
    fun resetBefore() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun resetAfter() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun racingPreparationIsBoundedAndNewestConfigurationWins() {
        val format = EqualizerProcessorFormat(
            sampleRateHz = 192_000,
            channelCount = 6,
            pcmEncoding = C.ENCODING_PCM_16BIT
        )
        val configurations = EqualizerPerformanceFixtures
            .namedConfigurations.values.toList()
        val failure = AtomicReference<Throwable?>(null)
        val executor = Executors.newFixedThreadPool(2)
        try {
            val requester = executor.submit {
                runCatching {
                    repeat(1_000) { index ->
                        EqualizerRuntimeBridge.requestConfiguration(
                            configuration =
                                configurations[
                                    index % configurations.size
                                ],
                            automaticHeadroomEnabled =
                                index % 2 == 0,
                            mode = if (index % 2 == 0) {
                                EqualizerMode.GRAPHIC
                            } else {
                                EqualizerMode.PARAMETRIC
                            }
                        )
                    }
                }.exceptionOrNull()?.let(failure::set)
            }
            val preparer = executor.submit {
                runCatching {
                    repeat(100) {
                        EqualizerRuntimeBridge
                            .prepareForProcessorFormat(format)
                    }
                }.exceptionOrNull()?.let(failure::set)
            }

            requester.get(15, TimeUnit.SECONDS)
            preparer.get(15, TimeUnit.SECONDS)
            val finalRequest =
                EqualizerRuntimeBridge.requestConfiguration(
                    configuration =
                        configurations.last(),
                    automaticHeadroomEnabled = true,
                    mode = EqualizerMode.PARAMETRIC
                )
            val finalPath =
                EqualizerRuntimeBridge
                    .prepareForProcessorFormat(format)
            EqualizerRuntimeBridge.publishStateForTest()

            assertNull(failure.get())
            assertEquals(
                finalRequest.version,
                finalPath.plan.sourceSnapshotVersion
            )
            assertEquals(
                format,
                finalPath.plan.processorFormat
            )
            assertEquals(
                finalRequest.version,
                EqualizerRuntimeBridge.state.value
                    .preparedPlanVersion
            )
        } finally {
            executor.shutdownNow()
            assertTrue(
                executor.awaitTermination(3, TimeUnit.SECONDS)
            )
        }
    }
}
