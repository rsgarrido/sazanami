package io.github.rsgarrido.sazanami.ui.equalizer

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerFilterSpec
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.withGainDb
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class EqualizerUiControllerTest {
    @Test
    fun importPreviewIsSilentAndReplacePublishesOneFinalConfiguration() =
        runBlocking {
            EqualizerRuntimeBridge.release()
            val context =
                ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined
            )
            val repository = AppPreferencesRepository.create(
                context = context,
                scope = scope,
                dataStoreFileName =
                    "import_controller_${System.nanoTime()}.preferences_pb",
                legacyStores = emptyList()
            )
            val controller = EqualizerUiController(
                preferencesRepository = repository,
                runtimeState =
                    MutableStateFlow(EqualizerRuntimeState()),
                scope = scope
            )
            try {
                withTimeout(5_000) {
                    controller.state.first { it.isLoaded }
                }
                repository.replaceEqualizerPreferences(
                    EqualizerPreferencesState(
                        enabled = true,
                        mode = EqualizerMode.GRAPHIC,
                        limiterEnabled = true,
                        limiterCeilingDbfs = -2.0,
                        bandGainsDb = List(10) { index ->
                            index - 4.0
                        }
                    )
                )
                withTimeout(5_000) {
                    controller.state.first {
                        it.durablePreferences.enabled &&
                            it.durablePreferences.limiterEnabled
                    }
                }
                val versionBefore =
                    EqualizerRuntimeBridge.requestedSnapshot().version
                val durableBefore =
                    repository.state.value.equalizerPreferences

                controller.openImportPreview(
                    "Preamp: -6 dB\n" +
                        "Filter 1: ON PK Fc 1000 Hz " +
                        "Gain 3 dB Q 1",
                    "Headphones ParametricEQ.txt"
                )
                withTimeout(5_000) {
                    controller.state.first {
                        it.importPreview != null
                    }
                }

                assertEquals(
                    versionBefore,
                    EqualizerRuntimeBridge
                        .requestedSnapshot().version
                )
                assertEquals(
                    durableBefore,
                    repository.state.value.equalizerPreferences
                )
                controller.dismissImportPreview()
                assertEquals(
                    durableBefore,
                    repository.state.value.equalizerPreferences
                )

                controller.openImportPreview(
                    "Preamp: -6 dB\n" +
                        "Filter 1: ON PK Fc 1000 Hz " +
                        "Gain 3 dB Q 1",
                    "Headphones.txt"
                )
                withTimeout(5_000) {
                    controller.state.first {
                        it.importPreview != null
                    }
                }
                val beforeApply =
                    EqualizerRuntimeBridge.requestedSnapshot().version
                controller.replaceWithImportedProfile()
                withTimeout(5_000) {
                    controller.state.first {
                        it.importPreview == null &&
                            !it.importInProgress
                    }
                }
                val requested =
                    EqualizerRuntimeBridge.requestedSnapshot()

                assertEquals(beforeApply + 1, requested.version)
                assertEquals(EqualizerMode.PARAMETRIC, requested.mode)
                assertEquals(1, requested.configuration.filters.size)
                assertEquals(-6.0, requested.configuration.preampDb, 0.0)
                assertTrue(requested.configuration.enabled)
                assertTrue(requested.limiterConfiguration.enabled)

                val durable = withTimeout(5_000) {
                    repository.state.first {
                        it.equalizerPreferences.mode ==
                            EqualizerMode.PARAMETRIC
                    }
                }.equalizerPreferences
                assertEquals(1, durable.parametricState.filters.size)
                assertEquals(-6.0, durable.parametricState.preampDb, 0.0)
                assertEquals(
                    durableBefore.bandGainsDb,
                    durable.bandGainsDb
                )
            } finally {
                controller.release()
                scope.cancel()
                EqualizerRuntimeBridge.release()
            }
        }

    @Test
    fun parametricPreviewCancelCommitAndModeSwitchConverge() =
        runBlocking {
            EqualizerRuntimeBridge.release()
            val context =
                ApplicationProvider.getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined
            )
            val repository = AppPreferencesRepository.create(
                context = context,
                scope = scope,
                dataStoreFileName =
                    "param_controller_${System.nanoTime()}.preferences_pb",
                legacyStores = emptyList()
            )
            val controller = EqualizerUiController(
                preferencesRepository = repository,
                runtimeState =
                    MutableStateFlow(EqualizerRuntimeState()),
                scope = scope
            )
            try {
                withTimeout(5_000) {
                    controller.state.first { it.isLoaded }
                }
                controller.setMode(EqualizerMode.PARAMETRIC)
                controller.addParametricFilter()
                val original = controller.state.value
                    .editablePreferences.parametricState.filters.single()
                val boosted = original.withGainDb(4.0)

                controller.previewParametricFilter(boosted)
                assertEquals(
                    4.0,
                    (EqualizerRuntimeBridge.requestedSnapshot()
                        .configuration.filters.single() as
                        EqualizerFilterSpec.Peaking).gainDb,
                    0.0
                )
                assertEquals(
                    0.0,
                    repository.state.value.equalizerPreferences
                        .parametricState.filters.single()
                        .let {
                            (it as ParametricFilter.Peaking)
                                .gainDb
                        },
                    0.0
                )

                controller.cancelParametricFilterPreview(original)
                assertEquals(
                    0.0,
                    (EqualizerRuntimeBridge.requestedSnapshot()
                        .configuration.filters.single() as
                        EqualizerFilterSpec.Peaking).gainDb,
                    0.0
                )
                controller.commitParametricFilter(boosted)
                controller.setEnabled(true)
                withTimeout(5_000) {
                    repository.state.first { state ->
                        state.equalizerPreferences.mode ==
                            EqualizerMode.PARAMETRIC &&
                            (
                                state.equalizerPreferences.parametricState
                                    .filters.single() as
                                    ParametricFilter.Peaking
                                ).gainDb == 4.0
                    }
                }
                controller.setComparisonBypassed(true)
                assertTrue(controller.state.value.comparisonBypassed)

                controller.setMode(EqualizerMode.GRAPHIC)

                assertFalse(controller.state.value.comparisonBypassed)
                assertEquals(
                    EqualizerMode.GRAPHIC,
                    EqualizerRuntimeBridge.requestedSnapshot().mode
                )
                assertTrue(
                    repository.state.value.equalizerPreferences
                        .parametricState.filters.single() == boosted
                )
            } finally {
                controller.release()
                scope.cancel()
                EqualizerRuntimeBridge.release()
            }
        }

    @Test
    fun previewIsTransientCommitPersistsAndComparisonReturnsToA() =
        runBlocking {
            EqualizerRuntimeBridge.release()
            val context = ApplicationProvider
                .getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined
            )
            val repository =
                AppPreferencesRepository.create(
                    context = context,
                    scope = scope,
                    dataStoreFileName =
                        "controller_${System.nanoTime()}.preferences_pb",
                    legacyStores = emptyList()
                )
            val runtime =
                MutableStateFlow(EqualizerRuntimeState())
            val controller = EqualizerUiController(
                preferencesRepository = repository,
                runtimeState = runtime,
                scope = scope
            )
            try {
                withTimeout(5_000) {
                    controller.state.first { it.isLoaded }
                }

                controller.previewBandGain(0, 4.0)
                assertEquals(
                    4.0,
                    (EqualizerRuntimeBridge
                        .requestedSnapshot()
                        .configuration.filters[0] as
                        EqualizerFilterSpec.Peaking).gainDb,
                    0.0
                )
                assertEquals(
                    0.0,
                    repository.state.value
                        .equalizerPreferences.bandGainsDb[0],
                    0.0
                )

                controller.commitBandGain(0, 4.0)
                withTimeout(5_000) {
                    repository.state.first { state ->
                        state.equalizerPreferences
                            .bandGainsDb[0] == 4.0
                    }
                }
                controller.setEnabled(true)
                withTimeout(5_000) {
                    repository.state.first { state ->
                        state.equalizerPreferences.enabled
                    }
                }
                withTimeout(5_000) {
                    controller.state.first {
                            state ->
                        state.editablePreferences.enabled &&
                            state.comparisonAvailable
                    }
                }

                controller.setComparisonBypassed(true)
                assertFalse(
                    EqualizerRuntimeBridge
                        .requestedSnapshot()
                        .configuration.enabled
                )
                assertTrue(
                    EqualizerRuntimeBridge.state.value
                        .requiresDecodedPcm
                )
                assertTrue(
                    repository.state.value
                        .equalizerPreferences.enabled
                )

                controller.closeScreen()
                assertTrue(
                    EqualizerRuntimeBridge
                        .requestedSnapshot()
                        .configuration.enabled
                )
                assertFalse(
                    EqualizerRuntimeBridge.state.value
                        .comparisonSessionActive
                )
            } finally {
                controller.release()
                scope.cancel()
                EqualizerRuntimeBridge.release()
            }
        }

    @Test
    fun cancellingFinePreviewRestoresRuntimeWithoutPersistence() =
        runBlocking {
            EqualizerRuntimeBridge.release()
            val context = ApplicationProvider
                .getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined
            )
            val repository =
                AppPreferencesRepository.create(
                    context = context,
                    scope = scope,
                    dataStoreFileName =
                        "cancel_${System.nanoTime()}.preferences_pb",
                    legacyStores = emptyList()
                )
            val controller = EqualizerUiController(
                preferencesRepository = repository,
                runtimeState = MutableStateFlow(
                    EqualizerRuntimeState()
                ),
                scope = scope
            )
            try {
                withTimeout(5_000) {
                    controller.state.first { it.isLoaded }
                }
                controller.previewPreamp(-5.0)
                assertEquals(
                    -5.0,
                    EqualizerRuntimeBridge
                        .requestedSnapshot()
                        .configuration.preampDb,
                    0.0
                )

                controller.cancelPreampPreview(0.0)
                controller.closeScreen()

                assertEquals(
                    0.0,
                    repository.state.value
                        .equalizerPreferences.preampDb,
                    0.0
                )
                assertEquals(
                    0.0,
                    EqualizerRuntimeBridge
                        .requestedSnapshot()
                        .configuration.preampDb,
                    0.0
                )
            } finally {
                controller.release()
                scope.cancel()
                EqualizerRuntimeBridge.release()
            }
        }

    @Test
    fun limiterPreviewCommitAndComparisonAvailabilityStayTruthful() {
        runBlocking {
            EqualizerRuntimeBridge.release()
            val context = ApplicationProvider
                .getApplicationContext<Context>()
            val scope = CoroutineScope(
                SupervisorJob() + Dispatchers.Unconfined
            )
            val repository =
                AppPreferencesRepository.create(
                    context = context,
                    scope = scope,
                    dataStoreFileName =
                        "limiter_${System.nanoTime()}.preferences_pb",
                    legacyStores = emptyList()
                )
            val controller = EqualizerUiController(
                preferencesRepository = repository,
                runtimeState =
                    MutableStateFlow(EqualizerRuntimeState()),
                scope = scope
            )
            try {
                withTimeout(5_000) {
                    controller.state.first { it.isLoaded }
                }
                controller.previewLimiterCeiling(-2.4)
                assertEquals(
                    -2.4,
                    EqualizerRuntimeBridge.requestedSnapshot()
                        .limiterConfiguration.ceilingDbfs,
                    0.0
                )
                assertEquals(
                    -1.0,
                    repository.state.value.equalizerPreferences
                        .limiterCeilingDbfs,
                    0.0
                )

                controller.commitLimiterCeiling(-2.4)
                controller.setLimiterEnabled(true)
                val enabled = withTimeout(5_000) {
                    controller.state.first { state ->
                        state.editablePreferences.limiterEnabled &&
                            state.editablePreferences
                                .limiterCeilingDbfs == -2.4
                    }
                }
                assertFalse(enabled.comparisonAvailable)
                assertTrue(
                    EqualizerRuntimeBridge.requestedSnapshot()
                        .limiterConfiguration.enabled
                )

                controller.setLimiterEnabled(false)
                withTimeout(5_000) {
                    repository.state.first { state ->
                        !state.equalizerPreferences.limiterEnabled
                    }
                }
            } finally {
                controller.release()
                scope.cancel()
                EqualizerRuntimeBridge.release()
            }
        }
    }
}
