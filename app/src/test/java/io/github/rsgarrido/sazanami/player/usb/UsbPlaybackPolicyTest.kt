package io.github.rsgarrido.sazanami.player.usb

import io.github.rsgarrido.sazanami.player.*
import androidx.media3.common.Player
import org.junit.Assert.*
import org.junit.Test
import java.nio.file.Files
import java.nio.file.Paths

class UsbPlaybackPolicyTest {
    private val identity = UsbAudioDeviceIdentity("test-bus", 1, 2, 3)
    private val model = UsbAudioDescriptorParser.parse(byteArrayOf())
    private val device = UsbAudioDeviceSnapshot(identity, null, "DAC", 0,
        listOf(UsbAndroidInterfaceSnapshot(1, 0, 1, 1, 32, emptyList())), false)
    private val selected = UsbAudioDiagnosticState(serviceAvailable = true,
        status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED, devices = listOf(device), selectedIdentity = identity, generation = 7)

    @Test fun releaseRequiresExplicitAuthorizationAndDoesNotAuthorizeExperiments() {
        for (debug in listOf(false, true)) {
            assertFalse(UsbPlaybackPolicy.allows(UsbPlaybackOperation.INTEGRATED_PLAYBACK, debug))
            assertTrue(UsbPlaybackPolicy.allows(UsbPlaybackOperation.INTEGRATED_PLAYBACK, debug, authorized = true))
            assertEquals(debug, UsbPlaybackPolicy.allows(UsbPlaybackOperation.DEVELOPER_EXPERIMENT, debug, authorized = true))
        }
        for (internal in listOf(false, true)) {
            assertTrue(allowsDirectCommand(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, internal))
            assertFalse(allowsDirectCommand(Player.COMMAND_SET_VOLUME, internal))
            assertEquals(internal,allowsDirectCommand(Player.COMMAND_SET_REPEAT_MODE, internal))
        }
    }

    @Test fun absentOrMultipleUnselectedDevicesNeverAuthorizePlayback() {
        assertEquals("DIRECT_SERVICE_UNAVAILABLE", UsbAudioDiagnosticState().activationFailure(null, 0))
        val empty = selected.copy(devices = emptyList(), selectedIdentity = null)
        assertEquals("DIRECT_USB_DEVICE_UNAVAILABLE", empty.activationFailure(null, 7))
        val multiple = selected.copy(devices = listOf(device, device.copy(identity = identity.copy(deviceId = 2))), selectedIdentity = null)
        assertNull(multiple.selectedDevice)
        assertEquals("DIRECT_USB_SELECTION_REQUIRED", multiple.activationFailure(null, 7))
        assertEquals(device, selected.selectedDevice)
    }

    @Test fun permissionAndDescriptorEvidenceArePrerequisitesNotActivation() {
        assertEquals("DIRECT_USB_PERMISSION_FAILED", selected.activationFailure(identity, 7))
        assertEquals("DIRECT_USB_PERMISSION_FAILED", selected.copy(status = UsbAudioDiagnosticStatus.PERMISSION_DENIED).activationFailure(identity, 7))
        val granted = selected.copy(devices = listOf(device.copy(hasPermission = true)))
        assertEquals("DIRECT_USB_INSPECTION_REQUIRED", granted.activationFailure(identity, 7))
        assertEquals("DIRECT_USB_PERMISSION_PENDING", granted.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION).activationFailure(identity, 7))
        assertEquals("DIRECT_USB_INCOMPATIBLE", granted.copy(status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ, descriptors = model).activationFailure(identity, 7))
        assertEquals("DIRECT_USB_SELECTION_CHANGED", granted.activationFailure(identity, 6))
        assertEquals("DIRECT_USB_SELECTION_CHANGED", granted.activationFailure(identity.copy(deviceId = 2), 7))
        assertFalse(IntegratedDirectPlaybackState().confirmedDirect)
        assertFalse(IntegratedDirectPlaybackState(enabled = true, switching = DirectOutputSwitch.TO_USB).confirmedDirect)
        assertFalse(IntegratedDirectPlaybackState(enabled = true, normalReleased = false).confirmedDirect)
        assertTrue(IntegratedDirectPlaybackState(enabled = true, normalReleased = true).confirmedDirect)
    }

    @Test fun leavingOrDetachingCannotTurnAnOldPermissionRequestIntoAuthority() {
        val gate = UsbAudioGenerationGate()
        val request = UsbAudioPermissionRequest("nonce", gate.select(identity)!!)
        gate.select(null)
        assertFalse(request.matches("nonce", identity, gate))
        gate.select(identity)
        assertFalse(request.matches("nonce", identity, gate))
        assertFalse(request.matches("another-nonce", identity, gate))
        // A permission callback owns no mode-selection operation; a fresh state defaults to Android.
        assertFalse(IntegratedDirectPlaybackState().enabled)
    }

    @Test fun candidateSupportsPacked24WithoutRequiringAPcm16Alternate() {
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 10, 0), 0, 1, 1, 0x20,
            audioControl = UsbAudioControlDescriptor(0x0200, entities = listOf(
                UsbAudioEntity(43, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7),
                UsbAudioEntity(44, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(43), terminalType = 0x0101),
                UsbAudioEntity(45, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(44), clockSources = listOf(43), terminalType = 0x0301))))
        val idle = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 0), 0, 1, 2, 0x20)
        val target = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 9), 2, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(11, 5, 776, 1, synchronizationAddress = 0x8e,
                audio = UsbAudioEndpointDescriptor(0, 0, 0, 0)), UsbEndpointDescriptor(0x8e, 0x11, 4, 4)),
            audioStreaming = UsbAudioStreamingDescriptor(44, 0, 1, 1, 2, 3), audioFormat = UsbAudioFormatDescriptor(1, 3, 24))
        val descriptors = UsbAudioDescriptorModel(200, null, listOf(UsbConfigurationDescriptor(18, 1, 182, 3, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 10, 2, 1, 0, 0x20)), listOf(ac, idle, target), emptyList(), emptyList())
        val facts = descriptors.interfaces.map { iface -> UsbAndroidInterfaceSnapshot(iface.key.number, iface.key.alternate,
            iface.interfaceClass, iface.subclass, iface.protocol, iface.endpoints.map {
                UsbAndroidEndpointSnapshot(it.address, it.attributes, it.maxPacketSizeRaw, it.interval)
            }) }
        val candidate = selected.copy(status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ, descriptors = descriptors,
            devices = listOf(device.copy(interfaces = facts, hasPermission = true)))
        assertNull(candidate.activationFailure(identity, 7))
        assertEquals(48000L, candidate.integratedCandidate()!!.plan!!.configurationPlan.sampleRateHz)
        assertEquals(24, candidate.integratedCandidate()!!.plan!!.configurationPlan.validBits)
        assertNull(UsbSilentTransportPlan.resolve(descriptors, facts).plan)
        // Descriptor candidacy still does not assert clock-range or runtime configuration success.
        assertFalse(IntegratedDirectPlaybackState().enabled)
    }

    @Test fun releaseBoundariesRemainScopedAndNormalReleaseFollowsAuthorization() {
        fun source(name: String) = listOf(Paths.get("src/main", name), Paths.get("app/src/main", name))
            .first { Files.isRegularFile(it) }.toFile().readText()
        val controller = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt")
        assertTrue(controller.contains("UsbPlaybackOperation.DEVELOPER_EXPERIMENT, BuildConfig.DEBUG"))
        assertTrue(controller.contains("if (client == null && !developerOperationsAllowed()) return@onMain"))
        assertTrue(controller.contains("integratedSelection ?: throw UsbIntegratedStartException"))
        assertTrue(controller.contains("mode != UsbPcmDiagnosticMode.FLAC_FULL || !silentRun"))
        val permission = controller.substringAfter("fun permissionResult(").substringBefore("private fun inspect(")
        assertTrue(permission.contains("request.matches(requestId, identity, gate)"))
        assertTrue(permission.contains("manager?.hasPermission(device) == true"))
        assertFalse(permission.contains("authorizeIntegratedSelection"))
        val player = source("java/io/github/rsgarrido/sazanami/player/ExperimentalUsbPlaybackPlayer.kt")
        assertTrue(player.indexOf("selection.authorize(owner,identity,generation)") < player.indexOf("releaseNormal()"))
        assertTrue(player.indexOf("direct.stopAndJoin()") < player.indexOf("recreateNormal()"))
        assertFalse(player.contains("BuildConfig.DEBUG"))
        val bridge = source("java/io/github/rsgarrido/sazanami/player/IntegratedDirectPlaybackBridge.kt")
        assertFalse(bridge.contains("BuildConfig.DEBUG"))
        assertTrue(bridge.contains("if(owner===player)"))
        val service = source("java/io/github/rsgarrido/sazanami/player/PlaybackService.kt")
        assertTrue(service.contains("selection=io.github.rsgarrido.sazanami.player.usb.UsbOutputSelectionCallbacks { usbAudioController }"))
        assertFalse(service.contains("usbAudioController?.authorizeIntegratedSelection"))
        assertFalse(service.contains("usbAudioController?.integratedSelectionFailure"))
        assertFalse(source("../../build.gradle.kts").contains("DEBUG=true"))
    }
}
