package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
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
    private var requests = 0
    private var outputRequests = mutableListOf<Boolean>()
    private var requestedToken: UsbAudioInspectionToken? = null
    private var selectedIdentity: UsbAudioDeviceIdentity? = null
    private fun text(id: Int) = compose.activity.getString(id)
    private fun show() {
        compose.setContent {
            MaterialTheme {
                UsbAudioSettingsContent(usb.value, output.value, onBack = {}, onDiagnostics = {},
                    onSelectDevice = { selectedIdentity = it }, onPermission = { requests++ }, onRefresh = {},
                    onOutput = { direct, identity, generation ->
                        outputRequests.add(direct);requestedToken = identity?.let { UsbAudioInspectionToken(it, generation) }
                    })
            }
        }
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
        compose.onNodeWithText(text(R.string.usb_settings_direct)).performClick()
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
        compose.onNodeWithText("Other DAC (2:3) #2").performScrollTo().performClick()
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

    @Test fun activationFailureBeforePlaybackHasLocalizedDetailsAndCopyAction() {
        usb.value = selected()
        output.value = output.value.copy(error = "DIRECT_USB_RESERVATION_HELD", activation = UsbActivationEvidence(1,
            stage = UsbActivationStage.PREFLIGHT, failureStage = UsbActivationStage.PREFLIGHT,
            failure = "DIRECT_USB_RESERVATION_HELD", output = UsbActivationOutput.ANDROID))
        show()
        compose.onNodeWithText(compose.activity.getString(R.string.usb_activation_result_title, 1L))
            .performScrollTo().assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_activation_copy)).performScrollTo().assertIsDisplayed().assertIsEnabled()
        compose.onNodeWithText(text(R.string.usb_settings_android)).assertIsSelected()
        compose.runOnIdle { assertTrue(outputRequests.isEmpty());assertNull(output.value.libraryId) }
    }
}
