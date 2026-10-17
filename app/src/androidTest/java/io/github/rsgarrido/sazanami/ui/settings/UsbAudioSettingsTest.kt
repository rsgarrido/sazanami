package io.github.rsgarrido.sazanami.ui.settings

import android.os.Build
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.lifecycle.Lifecycle
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.usb.*
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test

/** Fake immutable service evidence; permission and routing implementation are tested separately. */
class UsbAudioSettingsTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val identity = UsbAudioDeviceIdentity("test", 1, 2, 3)
    private val model: UsbAudioDescriptorModel get() {
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 10, 0), 0, 1, 1, 0x20,
            audioControl = UsbAudioControlDescriptor(0x0200, entities = listOf(
                UsbAudioEntity(43, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7),
                UsbAudioEntity(44, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(43), terminalType = 0x0101),
                UsbAudioEntity(45, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(44), clockSources = listOf(43), terminalType = 0x0301))))
        val idle = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 0), 0, 1, 2, 0x20)
        val target = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 9), 2, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(11, 5, 776, 1, synchronizationAddress = 0x8e,
                audio = UsbAudioEndpointDescriptor(0, 0, 0, 0)), UsbEndpointDescriptor(0x8e, 0x11, 4, 4)),
            audioStreaming = UsbAudioStreamingDescriptor(44, 0, 1, 1, 2, 3), audioFormat = UsbAudioFormatDescriptor(1, 2, 16))
        return UsbAudioDescriptorModel(200, null, listOf(UsbConfigurationDescriptor(18, 1, 182, 3, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 10, 2, 1, 0, 0x20)), listOf(ac, idle, target), emptyList(), emptyList())
    }
    private fun device(permission: Boolean) = UsbAudioDeviceSnapshot(identity, null, "Test DAC", 0,
        model.interfaces.map { iface -> UsbAndroidInterfaceSnapshot(iface.key.number, iface.key.alternate,
            iface.interfaceClass, iface.subclass, iface.protocol, iface.endpoints.map {
                UsbAndroidEndpointSnapshot(it.address, it.attributes, it.maxPacketSizeRaw, it.interval)
            }) }, permission)
    private fun selected(permission: Boolean = true) = UsbAudioDiagnosticState(serviceAvailable = true,
        status = if (permission) UsbAudioDiagnosticStatus.DESCRIPTORS_READ else UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
        devices = listOf(device(permission)), selectedIdentity = identity, generation = 7,
        descriptors = if (permission) model else null)
    private val usb = mutableStateOf(UsbAudioDiagnosticState(serviceAvailable = true, status = UsbAudioDiagnosticStatus.NO_DEVICE))
    private val output = mutableStateOf(IntegratedDirectPlaybackState(serviceAvailable = true))
    // Existing playback fixtures explicitly acknowledge the new default-off feature.
    private val experimentalEnabled = mutableStateOf(true)
    private var acknowledgments = 0
    private var disableRequests = 0
    private var requests = 0
    private var refreshes = 0
    private var outputRequests = mutableListOf<Boolean>()
    private var requestedToken: UsbAudioInspectionToken? = null
    private var selectedIdentity: UsbAudioDeviceIdentity? = null
    private fun text(id: Int) = compose.activity.getString(id)
    private fun progressIndicator() = compose.onNode(SemanticsMatcher.expectValue(
        SemanticsProperties.ProgressBarRangeInfo, ProgressBarRangeInfo.Indeterminate))
    private fun show() {
        // A screen-off/locked device can stop the host before its first Compose hierarchy exists.
        compose.activityRule.scenario.onActivity { activity ->
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O_MR1) {
                activity.setShowWhenLocked(true)
                activity.setTurnScreenOn(true)
            } else {
                @Suppress("DEPRECATION") // API 26 predates the Activity methods above.
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED or
                    WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON)
            }
        }
        compose.activityRule.scenario.moveToState(Lifecycle.State.RESUMED)
        compose.setContent {
            MaterialTheme {
                UsbAudioSettingsContent(usb.value, output.value, onBack = {}, onDiagnostics = {},
                    onSelectDevice = { selectedIdentity = it }, onPermission = { requests++ }, onRefresh = { refreshes++ },
                    onOutput = { direct, identity, generation ->
                        outputRequests.add(direct);requestedToken = identity?.let { UsbAudioInspectionToken(it, generation) }
                    }, experimentalEnabled = experimentalEnabled.value,
                    onAcknowledgeExperimental = { acknowledgments++; experimentalEnabled.value = true },
                    onDisableExperimental = { disableRequests++; experimentalEnabled.value = false })
            }
        }
    }

    @Test fun inspectionCompletesAndRepeatedRefreshIgnoresEarlierQueryEvidence() {
        usb.value = selected().copy(status = UsbAudioDiagnosticStatus.OPENING, descriptors = null)
        show()
        progressIndicator().assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_inspecting)).assertExists()
        compose.runOnIdle { usb.value = selected() }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().assertIsEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_refresh)).performScrollTo().performClick()
        compose.runOnIdle {
            assertEquals(1, refreshes)
            usb.value = selected().copy(generation = 8, status = UsbAudioDiagnosticStatus.OPENING, descriptors = null)
        }
        progressIndicator().assertExists()
        compose.runOnIdle {
            usb.value = selected().copy(generation = 8, clockDiagnostics = UsbAudioClockDiagnostics(
                7, UsbAudioClockResolution(), UsbClockDiagnosticPhase.QUERYING))
        }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_inspecting)).assertDoesNotExist()
    }

    @Test fun nativeConfigurationProgressEndsWhenPlaybackIsEstablishedWithoutReleasingUsb() {
        usb.value = selected().copy(integratedFlacUsbExperiment = UsbSilentTransportExperimentResult(7, 11,
            UsbSilentTransportResolution(), phase = UsbSilentPhase.CONFIGURING, mode = UsbPcmDiagnosticMode.FLAC_FULL))
        output.value = output.value.copy(enabled = true, normalReleased = true, cleanupSafe = false,
            phase = IntegratedDirectPhase.PREPARING)
        show()
        progressIndicator().assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_preparing)).assertExists()
        compose.runOnIdle {
            usb.value = usb.value.copy(integratedFlacUsbExperiment = usb.value.integratedFlacUsbExperiment?.copy(
                phase = UsbSilentPhase.STARTING_TRANSPORT))
            output.value = output.value.copy(phase = IntegratedDirectPhase.PLAYING,
                nativeTransportPhase = UsbFullTransportPhase.PLAYING, sampleRateHz = 44100)
        }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_inspecting)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_preparing)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().assertIsSelected()
        compose.onNodeWithText(text(R.string.usb_settings_playing)).assertExists()
        compose.onNodeWithText(compose.activity.getString(R.string.usb_settings_track_configured, 44100)).assertExists()
        compose.runOnIdle {
            assertTrue(usb.value.diagnosticOperationRunning)
            assertFalse(output.value.cleanupSafe)
            assertTrue(outputRequests.isEmpty())
            output.value = output.value.copy(phase = IntegratedDirectPhase.PAUSED, nativeTransportPhase = UsbFullTransportPhase.PAUSED)
        }
        progressIndicator().assertDoesNotExist()
        // Do not hide a genuinely separate current check merely because Direct is active.
        compose.runOnIdle { usb.value = usb.value.copy(clockDiagnostics = UsbAudioClockDiagnostics(
            7, UsbAudioClockResolution(), UsbClockDiagnosticPhase.QUERYING)) }
        progressIndicator().assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_inspecting)).assertExists()
        compose.runOnIdle { usb.value = usb.value.copy(clockDiagnostics = usb.value.clockDiagnostics?.copy(
            phase = UsbClockDiagnosticPhase.COMPLETE)) }
        progressIndicator().assertDoesNotExist()
    }

    @Test fun permissionDenialFailedInspectionAndRemovalClearProgress() {
        usb.value = selected(false).copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION)
        show()
        progressIndicator().assertExists()
        compose.runOnIdle { usb.value = selected(false).copy(status = UsbAudioDiagnosticStatus.PERMISSION_DENIED) }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_permission_denied)).assertExists()
        compose.runOnIdle { usb.value = selected().copy(status = UsbAudioDiagnosticStatus.OPENING, descriptors = null) }
        progressIndicator().assertExists()
        compose.runOnIdle { usb.value = selected().copy(status = UsbAudioDiagnosticStatus.ERROR, descriptors = null) }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_inspection_failed)).assertExists()
        compose.runOnIdle { usb.value = UsbAudioDiagnosticState(serviceAvailable = true, status = UsbAudioDiagnosticStatus.NO_DEVICE) }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_no_device)).assertExists()
    }

    @Test fun optingOutCancelsCheckingPresentationButStillShowsPendingAndroidRestoration() {
        usb.value = selected().copy(status = UsbAudioDiagnosticStatus.OPENING, descriptors = null)
        show()
        progressIndicator().assertExists()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).performClick()
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_inspecting)).assertDoesNotExist()
        compose.runOnIdle {
            assertEquals(1, disableRequests)
            output.value = output.value.copy(enabled = true, normalReleased = true,
                switching = DirectOutputSwitch.TO_ANDROID, cleanupSafe = false)
        }
        progressIndicator().assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_returning)).assertExists()
        compose.runOnIdle { output.value = IntegratedDirectPlaybackState(serviceAvailable = true) }
        progressIndicator().assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_android)).performScrollTo().assertIsSelected()
    }

    @Test fun defaultOffHidesDirectAndUsbOperationsWhileAndroidRemainsAvailable() {
        experimentalEnabled.value = false
        usb.value = selected(false)
        show()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).assertIsOff()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_grant)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_refresh)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_android)).performScrollTo().assertIsSelected().assertIsEnabled()
        compose.runOnIdle { assertEquals(0, requests); assertEquals(0, acknowledgments); assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun enablingRequiresAcknowledgmentAndDoesNotActivateOutput() {
        experimentalEnabled.value = false
        usb.value = selected()
        show()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).performClick()
        compose.onNodeWithText(text(R.string.usb_settings_experimental_warning)).assertIsDisplayed()
        compose.runOnIdle { assertFalse(experimentalEnabled.value); assertEquals(0, acknowledgments) }
        compose.onNodeWithText(text(R.string.usb_settings_experimental_enable)).performClick()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).assertIsOn()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().assertIsNotSelected()
        compose.runOnIdle { assertEquals(1, acknowledgments); assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun cancellingDisclosureLeavesExperimentalPlaybackDisabled() {
        experimentalEnabled.value = false
        show()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).performClick()
        compose.onNodeWithText(text(R.string.usb_settings_cancel)).performClick()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).assertIsOff()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).assertDoesNotExist()
        compose.runOnIdle { assertEquals(0, acknowledgments); assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun experimentalOptOutRemainsAvailableDuringActivePlaybackAndPermissionRequests() {
        usb.value = selected().copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION)
        output.value = output.value.copy(enabled = true, normalReleased = true, phase = IntegratedDirectPhase.PLAYING)
        show()
        compose.onNodeWithContentDescription(text(R.string.usb_settings_experimental)).assertIsOn().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, disableRequests); assertTrue(outputRequests.isEmpty()) }
        compose.onNodeWithText(text(R.string.usb_settings_direct)).assertDoesNotExist()
    }

    @Test fun withdrawingOptInDismissesAnUncommittedActivationDisclosure() {
        usb.value = selected()
        show()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.usb_settings_volume_warning)).assertIsDisplayed()
        compose.runOnIdle { experimentalEnabled.value = false }
        compose.onNodeWithText(text(R.string.usb_settings_volume_warning)).assertDoesNotExist()
        compose.runOnIdle { experimentalEnabled.value = true }
        compose.onNodeWithText(text(R.string.usb_settings_volume_warning)).assertDoesNotExist()
        compose.runOnIdle { assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun optInAndDiagnosticFeedbackHaveSpanishLocalization() {
        val english = android.content.res.Configuration(compose.activity.resources.configuration).apply {
            setLocale(java.util.Locale.forLanguageTag("en"))
        }
        val spanish = android.content.res.Configuration(english).apply { setLocale(java.util.Locale.forLanguageTag("es")) }
        val englishResources = compose.activity.createConfigurationContext(english).resources
        val spanishResources = compose.activity.createConfigurationContext(spanish).resources
        listOf(R.string.usb_settings_experimental, R.string.usb_settings_experimental_warning,
            R.string.usb_settings_experimental_enable, R.string.usb_settings_experimental_disabled,
            R.string.usb_settings_cancel, R.string.usb_diagnostics_capture_success,
            R.string.usb_diagnostics_capture_failed, R.string.usb_diagnostics_capture_historical,
            R.string.usb_diagnostics_capture_unavailable, R.string.usb_settings_explanation,
            R.string.usb_settings_session, R.string.usb_settings_preparing).forEach { id ->
            assertTrue(spanishResources.getString(id).isNotBlank())
            assertNotEquals(englishResources.getString(id), spanishResources.getString(id))
        }
        assertEquals("USB Direct is used for this playback session. New sessions start with Android audio.",
            englishResources.getString(R.string.usb_settings_session))
        assertEquals("USB directo se usa en esta sesión de reproducción. Las nuevas sesiones comienzan con el audio de Android.",
            spanishResources.getString(R.string.usb_settings_session))
    }

    @Test fun noDeviceShowsDisclosureFormatsAndAccessibleAndroidSelection() {
        show()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).assertIsNotSelected().assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_explanation)).assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_no_device)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_formats)).performScrollTo().assertIsDisplayed()
    }

    @Test fun permissionDenialAndPendingDoNotSelectUsbAndBlockDuplicatePermissionRequests() {
        usb.value = selected(false).copy(status = UsbAudioDiagnosticStatus.PERMISSION_DENIED);show()
        compose.onNodeWithText(text(R.string.usb_settings_permission_denied)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_grant)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(1, requests);usb.value = usb.value.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION) }
        compose.onNodeWithText(text(R.string.usb_settings_grant)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_requesting)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.runOnIdle { assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun explicitVolumeConfirmationRequestsInspectedDeviceWithoutPretendingActivationSucceeded() {
        usb.value = selected();show()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().performClick()
        compose.onNodeWithText(text(R.string.usb_settings_volume_warning)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_activate)).performClick()
        compose.runOnIdle {
            assertEquals(listOf(true), outputRequests);assertEquals(UsbAudioInspectionToken(identity, 7), requestedToken)
        }
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.runOnIdle { output.value = output.value.copy(enabled = true, switching = DirectOutputSwitch.TO_USB, cleanupSafe = false) }
        compose.onNodeWithText(text(R.string.usb_settings_activating)).assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsNotEnabled().assertIsNotSelected()
        compose.onNodeWithText(text(R.string.usb_settings_direct)).assertIsNotEnabled().assertIsNotSelected()
    }

    @Test fun changingSelectionDuringDisclosureRequiresNewConfirmationAndMultipleDevicesRemainSelectable() {
        usb.value = selected().copy(devices = listOf(device(true), device(true).copy(identity = identity.copy(deviceId = 2), product = "Other DAC")))
        show()
        compose.onNodeWithText("Other DAC").performScrollTo().performClick()
        compose.runOnIdle { assertEquals(identity.copy(deviceId = 2), selectedIdentity) }
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performScrollTo().performClick()
        compose.runOnIdle { usb.value = usb.value.copy(generation = 8) }
        compose.onNodeWithText(text(R.string.usb_settings_activate)).assertIsNotEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_cancel)).performClick()
        compose.runOnIdle { assertTrue(outputRequests.isEmpty()) }
    }

    @Test fun cleanupErrorAndReturnReflectOnlyConfirmedServiceState() {
        usb.value = selected()
        output.value = output.value.copy(enabled = true, normalReleased = true, cleanupSafe = false,
            phase = IntegratedDirectPhase.ERROR, error = "DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
        show()
        compose.onNodeWithText(text(R.string.usb_settings_cleanup_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_settings_android)).performScrollTo().performClick()
        compose.runOnIdle { assertEquals(listOf(false), outputRequests);output.value = output.value.copy(switching = DirectOutputSwitch.TO_ANDROID) }
        compose.onNodeWithText(text(R.string.usb_settings_returning)).assertExists()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsNotSelected()
        compose.runOnIdle { output.value = IntegratedDirectPlaybackState(serviceAvailable = true) }
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.onNodeWithText(text(R.string.usb_settings_android_active)).assertExists()
    }

    @Test fun activationFailureBeforePlaybackOffersLocalizedStatusAndDiagnostics() {
        usb.value = selected()
        output.value = output.value.copy(error = "DIRECT_USB_RESERVATION_HELD", activation = UsbActivationEvidence(1,
            stage = UsbActivationStage.PREFLIGHT, failureStage = UsbActivationStage.PREFLIGHT,
            failure = "DIRECT_USB_RESERVATION_HELD", output = UsbActivationOutput.ANDROID))
        show()
        compose.onNodeWithText(text(R.string.usb_settings_activation_failed)).performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_activation_copy)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_settings_troubleshooting)).performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.runOnIdle { assertTrue(outputRequests.isEmpty());assertNull(output.value.libraryId) }
    }
}
