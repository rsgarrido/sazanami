package io.github.rsgarrido.sazanami.ui.equalizer

import androidx.activity.ComponentActivity
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerPreferencesState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import io.github.rsgarrido.sazanami.player.equalizer.interchange.EqualizerProfileParser
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricEqualizerState
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilter
import io.github.rsgarrido.sazanami.player.equalizer.parametric.ParametricFilterFactory
import io.github.rsgarrido.sazanami.player.equalizer.parametric.gainDbOrNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test

class EqualizerScreenTest {
    @get:Rule
    val composeRule =
        createAndroidComposeRule<ComponentActivity>()

    @Test
    fun importExportActionsAreScopedTruthfullyByMode() {
        var imported = false
        var pasted = false
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(isLoaded = true),
                    actions = noOpActions().copy(
                        onImportFromFile = { imported = true },
                        onPasteEqText = { pasted = true }
                    )
                )
            }
        }

        composeRule.onNodeWithText("Import from file")
            .performClick()
        composeRule.onNodeWithText("Paste EQ text")
            .performClick()
        composeRule.onNodeWithText("Export current EQ")
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Current export is unavailable in Graphic mode.",
            substring = true
        ).assertExists()
        composeRule.runOnIdle {
            assertTrue(imported)
            assertTrue(pasted)
        }
    }

    @Test
    fun importPreviewShowsAllFiltersAndRequiresExplicitTenSelection() {
        val parsed = EqualizerProfileParser.parse(
            (1..12).joinToString("\n") { index ->
                "Filter $index: ON PK Fc ${100 + index} Hz " +
                    "Gain 1 dB Q 1"
            },
            sourceName = "Twelve Filters.txt"
        )
        val screenState = mutableStateOf(
            EqualizerScreenState(
                importPreview =
                    EqualizerImportPreviewState.fromText(parsed),
                isLoaded = true
            )
        )
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = screenState.value,
                    actions = noOpActions().copy(
                        onUpdateImportPreview = { transform ->
                            screenState.value =
                                screenState.value.copy(
                                    importPreview = transform(
                                        requireNotNull(
                                            screenState.value.importPreview
                                        )
                                    )
                                )
                        }
                    )
                )
            }
        }

        composeRule.onNodeWithTag("equalizer_import_preview")
            .assertExists()
        composeRule.onNodeWithText(
            "Twelve Filters.txt",
            substring = true
        )
            .assertExists()
        composeRule.onNodeWithText(
            "Selected: 0",
            substring = true
        )
            .assertExists()
        composeRule.onNodeWithText("Replace current Parametric EQ")
            .assertIsNotEnabled()
        composeRule.onNodeWithText("Select first 10")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithText(
            "Selected: 10",
            substring = true
        )
            .assertExists()
        composeRule.onNodeWithText("Replace current Parametric EQ")
            .assertIsEnabled()
    }

    @Test
    fun screenRendersAllBandsAndProductionSemantics() {
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(
                        editablePreferences =
                            EqualizerPreferencesState(
                                enabled = true
                            ),
                        isLoaded = true
                    ),
                    actions = noOpActions()
                )
            }
        }

        listOf(
            "31 Hz",
            "62 Hz",
            "125 Hz",
            "250 Hz",
            "500 Hz",
            "1 kHz",
            "2 kHz",
            "4 kHz",
            "8 kHz",
            "16 kHz"
        ).forEach { label ->
            composeRule.onNodeWithText(
                label,
                useUnmergedTree = true
            ).assertExists()
        }
        composeRule.onNode(
            hasContentDescription(
                "Equalizer response graph",
                substring = true
            )
        ).assertExists()
        composeRule.onNodeWithText(
            "Developer equalizer verification"
        ).assertDoesNotExist()
        composeRule.onNodeWithText("Bass test")
            .assertDoesNotExist()
        composeRule.onNodeWithText("Sample-peak limiter")
            .assertExists()
    }

    @Test
    fun presetSelectorInvokesBuiltInPresetAction() {
        var appliedIndex = -1
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(
                        isLoaded = true
                    ),
                    actions = noOpActions().copy(
                        onApplyBuiltInPreset = { index ->
                            appliedIndex = index
                        }
                    )
                )
            }
        }

        composeRule.onNodeWithText(
            "Choose or manage presets"
        ).performClick()
        composeRule.onNodeWithText("Bass Lift")
            .performClick()

        composeRule.runOnIdle {
            assertEquals(1, appliedIndex)
        }
    }

    @Test
    fun parametricModeRendersAccessibleFiltersMarkersAndFocusedEditor() {
        val peak = ParametricFilter.Peaking(
            id = "peak",
            enabled = true,
            frequencyHz = 1_000.0,
            gainDb = 4.0,
            q = 1.25
        )
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(
                        editablePreferences =
                            EqualizerPreferencesState(
                                enabled = true,
                                mode = EqualizerMode.PARAMETRIC,
                                parametricState =
                                    ParametricEqualizerState(
                                        filters = listOf(peak)
                                    )
                            ),
                        selectedParametricFilterId = peak.id,
                        isLoaded = true
                    ),
                    actions = noOpActions()
                )
            }
        }

        composeRule.onNodeWithText("Add Filter").assertExists()
        composeRule.onNode(
            hasContentDescription(
                "Filter 1, peaking, enabled",
                substring = true
            )
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(
                "Filter marker 1",
                substring = true
            )
        ).assertExists()
        composeRule.onNode(
            hasContentDescription("Edit filter 1")
        ).performScrollTo().performClick()
        composeRule.onNodeWithText("Frequency (Hz)")
            .assertExists()
        composeRule.onNodeWithText("Gain (dB)")
            .assertExists()
        composeRule.onNodeWithText("Q").assertExists()
        composeRule.onNodeWithText("Shelf slope S")
            .assertDoesNotExist()
    }

    @Test
    fun maximumTenParametricFiltersDisablesAddAction() {
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(
                        editablePreferences =
                            EqualizerPreferencesState(
                                mode = EqualizerMode.PARAMETRIC,
                                parametricState =
                                    ParametricEqualizerState(
                                        filters = List(10) { index ->
                                            ParametricFilterFactory.default(
                                                id = "filter-$index"
                                            )
                                        }
                                    )
                            ),
                        isLoaded = true
                    ),
                    actions = noOpActions()
                )
            }
        }

        composeRule.onNodeWithText("Add Filter")
            .assertIsNotEnabled()
        composeRule.onNodeWithText(
            "Maximum of ten filters reached."
        ).assertExists()
    }

    @Test
    fun parametricMarkerDragCapturesParentScrollWithoutInitialJump() {
        val initial = ParametricFilter.Peaking(
            id = "drag-peak",
            enabled = true,
            frequencyHz = 1_000.0,
            gainDb = 0.0,
            q = 1.0
        )
        val filter = mutableStateOf<ParametricFilter>(initial)
        var parentScroll: ScrollState? = null
        var previewCount = 0
        var commitCount = 0
        composeRule.setContent {
            MaterialTheme {
                val scrollState = rememberScrollState()
                parentScroll = scrollState
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Spacer(Modifier.height(360.dp))
                    EqualizerResponseGraph(
                        analysis = EqualizerAnalysisResult(),
                        filters = listOf(filter.value),
                        selectedFilterId = filter.value.id,
                        onPreviewFilter = { updated ->
                            previewCount += 1
                            filter.value = updated
                        },
                        onCommitFilter = {
                            commitCount += 1
                        }
                    )
                    Spacer(Modifier.height(900.dp))
                }
            }
        }

        val marker = composeRule.onNodeWithTag(
            parametricMarkerDragTargetTag(initial.id)
        )
        marker.performScrollTo()
        val density =
            composeRule.activity.resources.displayMetrics.density
        val bounds = marker.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width / density >= 47.5f)
        assertTrue(bounds.height / density >= 47.5f)

        marker.performTouchInput {
            down(center)
            moveBy(Offset(2f, 2f))
            up()
        }
        composeRule.runOnIdle {
            assertEquals(0, previewCount)
            assertEquals(initial, filter.value)
        }

        val scrollBeforeDrag = parentScroll!!.value
        marker.performTouchInput {
            down(Offset(3f, center.y))
            moveBy(Offset(72f, -54f))
            moveBy(Offset(18f, -18f))
            up()
        }
        composeRule.runOnIdle {
            assertTrue(previewCount >= 1)
            assertEquals(1, commitCount)
            assertTrue(filter.value.frequencyHz > initial.frequencyHz)
            assertTrue(
                filter.value.gainDbOrNull!! > initial.gainDb
            )
            assertEquals(scrollBeforeDrag, parentScroll!!.value)
        }
    }

    @Test
    fun graphicBandDragCapturesParentScrollFromWideTouchTarget() {
        val gain = mutableStateOf(0.0)
        var parentScroll: ScrollState? = null
        var previewCount = 0
        var commitCount = 0
        composeRule.setContent {
            MaterialTheme {
                val scrollState = rememberScrollState()
                parentScroll = scrollState
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Spacer(Modifier.height(360.dp))
                    EqualizerBandSlider(
                        frequencyHz = 1_000.0,
                        gainDb = gain.value,
                        unavailable = false,
                        onValueChange = { updated ->
                            previewCount += 1
                            gain.value = updated
                        },
                        onValueChangeFinished = {
                            commitCount += 1
                        },
                        onFineEditClick = {}
                    )
                    Spacer(Modifier.height(900.dp))
                }
            }
        }

        val target = composeRule.onNodeWithTag(
            equalizerBandDragTargetTag(1_000.0)
        )
        target.performScrollTo()
        val density =
            composeRule.activity.resources.displayMetrics.density
        val bounds = target.fetchSemanticsNode().boundsInRoot
        assertTrue(bounds.width / density >= 63.5f)
        assertTrue(bounds.height / density >= 48f)

        target.performTouchInput {
            down(center)
            moveBy(Offset(0f, 2f))
            up()
        }
        composeRule.runOnIdle {
            assertEquals(0, previewCount)
            assertEquals(0.0, gain.value, 0.0)
        }

        val scrollBeforeDrag = parentScroll!!.value
        target.performTouchInput {
            down(Offset(3f, center.y))
            moveBy(Offset(0f, -72f))
            moveBy(Offset(0f, -24f))
            up()
        }
        composeRule.runOnIdle {
            assertTrue(previewCount >= 1)
            assertEquals(1, commitCount)
            assertTrue(gain.value > 0.0)
            assertEquals(scrollBeforeDrag, parentScroll!!.value)
        }
    }

    @Test
    fun dragStartedOutsideEqualizerControlsStillScrollsPage() {
        var parentScroll: ScrollState? = null
        composeRule.setContent {
            MaterialTheme {
                val scrollState = rememberScrollState()
                parentScroll = scrollState
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(scrollState)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(240.dp)
                            .testTag("outside-equalizer-control")
                    )
                    Spacer(Modifier.height(1_200.dp))
                }
            }
        }

        composeRule.onNodeWithTag("outside-equalizer-control")
            .performTouchInput {
                down(center)
                moveBy(Offset(0f, -160f))
                up()
            }
        composeRule.runOnIdle {
            assertTrue(parentScroll!!.value > 0)
        }
    }

    @Test
    fun enabledLimiterShowsMetersDisablesAbAndResetsCounters() {
        var resetRequested = false
        composeRule.setContent {
            MaterialTheme {
                EqualizerScreen(
                    state = EqualizerScreenState(
                        editablePreferences =
                            EqualizerPreferencesState(
                                enabled = true,
                                limiterEnabled = true
                            ).withBandGainDb(0, 4.0),
                        runtimeState = EqualizerRuntimeState(
                            limiterEffectivelyActive = true,
                            limiterPrimed = true,
                            preLimiterPeakDbfs = 0.7,
                            postLimiterPeakDbfs = -1.0,
                            currentGainReductionDb = 2.4,
                            maximumRecentGainReductionDb = 3.1,
                            overRangeSampleCount = 8,
                            saturatedSampleCount = 2
                        ),
                        isLoaded = true
                    ),
                    actions = noOpActions().copy(
                        onResetLimiterMeters = {
                            resetRequested = true
                        }
                    )
                )
            }
        }

        composeRule.onNodeWithText(
            "Disable the limiter for exact A/B comparison."
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(
                "Pre-limiter peak",
                substring = true
            )
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(
                "Gain reduction",
                substring = true
            )
        ).assertExists()
        composeRule.onNode(
            hasContentDescription(
                "Reset limiter meters and counters"
            )
        )
            .performScrollTo()
            .performClick()
        composeRule.runOnIdle {
            assertTrue(resetRequested)
        }
    }

    private fun noOpActions() = EqualizerUiActions(
        onBack = {},
        onEnabledChanged = {},
        onModeChanged = {},
        onPreviewBandGain = { _, _ -> },
        onCommitBandGain = { _, _ -> },
        onCancelBandGainPreview = { _, _ -> },
        onPreviewPreamp = {},
        onCommitPreamp = {},
        onCancelPreampPreview = {},
        onAutomaticHeadroomChanged = {},
        onLimiterEnabledChanged = {},
        onPreviewLimiterCeiling = {},
        onCommitLimiterCeiling = {},
        onCancelLimiterCeilingPreview = {},
        onResetLimiterMeters = {},
        onApplyBuiltInPreset = {},
        onApplyUserPreset = {},
        onSaveUserPreset = {},
        onRenameUserPreset = { _, _ -> },
        onDeleteUserPreset = {},
        onSelectParametricFilter = {},
        onAddParametricFilter = {},
        onPreviewParametricFilter = {},
        onCommitParametricFilter = {},
        onCancelParametricFilterPreview = {},
        onMoveParametricFilter = { _, _ -> },
        onDeleteParametricFilter = {},
        onApplyParametricFlatPreset = {},
        onApplyParametricUserPreset = {},
        onSaveParametricUserPreset = {},
        onRenameParametricUserPreset = { _, _ -> },
        onDeleteParametricUserPreset = {},
        onImportFromFile = {},
        onPasteEqText = {},
        onExportCurrentEqText = {},
        onCopyCurrentEqText = {},
        onExportCurrentNative = {},
        onExportParametricPresetText = {},
        onExportParametricPresetNative = {},
        onDismissImportPreview = {},
        onUpdateImportPreview = {},
        onReplaceWithImportedProfile = {},
        onSaveImportedProfile = {},
        onResetToFlat = {},
        onComparisonBypassedChanged = {}
    )
}
