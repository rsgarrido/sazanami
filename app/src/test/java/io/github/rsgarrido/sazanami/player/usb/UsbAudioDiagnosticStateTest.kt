package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioDiagnosticStateTest {
    private val identity = UsbAudioDeviceIdentity("/dev/bus/usb/private-test-key", 42, 0x1234, 0x5678)

    @Test fun oldResultCannotAuthorizeAReconnectedDeviceWithIdenticalAndroidIds() {
        val gate = UsbAudioGenerationGate()
        val old = UsbAudioPermissionRequest("old-request", gate.select(identity)!!)
        gate.select(null)
        val replacement = gate.select(identity)!!
        assertFalse(old.matches("old-request", identity, gate))
        assertTrue(gate.accepts(replacement))
        assertTrue(replacement.generation > old.token.generation)
    }

    @Test fun requestNonceAndEntireDeviceIdentityMustBothMatch() {
        val gate = UsbAudioGenerationGate()
        val request = UsbAudioPermissionRequest("request", gate.select(identity)!!)
        assertTrue(request.matches("request", identity, gate))
        assertFalse(request.matches("another-request", identity, gate))
        assertFalse(request.matches("request", identity.copy(deviceId = 43), gate))
        assertFalse(request.matches("request", identity.copy(vendorId = 0x9999), gate))
        assertFalse(request.matches("request", identity.copy(deviceName = "another-bus-key"), gate))
        assertFalse(request.matches("request", null, gate))
    }

    @Test fun selectionChangeInvalidatesPermissionAndDescriptorTokens() {
        val gate = UsbAudioGenerationGate()
        val first = gate.select(identity)!!
        val next = gate.select(identity.copy(productId = 0xabcd))!!
        assertFalse(gate.accepts(first))
        assertTrue(gate.accepts(next))
        gate.select(null)
        assertFalse(gate.accepts(next))
    }

    @Test fun repeatedInspectionOfTheSameAttachmentRejectsTheEarlierRead() {
        val gate = UsbAudioGenerationGate()
        val firstRead = gate.select(identity)!!
        val refreshRead = gate.select(identity)!!
        assertFalse(gate.accepts(firstRead))
        assertTrue(gate.accepts(refreshRead))
    }

    @Test fun copiedReportContainsUnknownAndMalformedRawEvidenceButNoBusPathOrStringPayload() {
        val bytes = byteArrayOf(4, 0x7f, 0xab.toByte(), 0xcd.toByte(),
            6, 3, 'S'.code.toByte(), 0, 'N'.code.toByte(), 0, 0, 4, 0x55)
        val model = UsbAudioDescriptorParser.parse(bytes)
        val report = UsbAudioDiagnosticReport.format(state(model))
        assertTrue(report.contains("04 7F AB CD"))
        assertTrue(report.contains("00 04 55"))
        assertTrue(report.contains("string descriptor omitted for privacy"))
        assertFalse(report.contains("06 03 53 00 4E 00"))
        assertFalse(report.contains(identity.deviceName))
        assertTrue(report.contains("Rate range not yet queried"))
        assertTrue(report.contains("No UAC2 GET_CUR/GET_RANGE or SET_CUR"))
    }

    @Test fun conciseReportDoesNotRepeatRawBytesAndCopiedReportHasEveryRecord() {
        val model = UsbAudioDescriptorParser.parse(byteArrayOf(4, 0x7f, 1, 2, 4, 0x7e, 3, 4))
        val concise = UsbAudioDiagnosticReport.format(state(model), includeRaw = false)
        val full = UsbAudioDiagnosticReport.format(state(model))
        assertFalse(concise.contains("04 7F 01 02"))
        assertTrue(full.contains("04 7F 01 02"))
        assertTrue(full.contains("04 7E 03 04"))
        assertEquals(2, model.rawDescriptors.size)
    }

    @Test fun clearingSelectionCannotRetainAConnectedDeviceInTheReport() {
        val report = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(
            serviceAvailable = true, status = UsbAudioDiagnosticStatus.NO_DEVICE, generation = 9))
        assertTrue(report.contains("No USB device detected"))
        assertFalse(report.contains("VID: 0x1234"))
        assertFalse(report.contains("Status: BitPerfectActive"))
    }

    @Test fun manufacturerAndProductCannotInsertExtraReportLines() {
        val report = UsbAudioDiagnosticReport.format(state(null).copy(devices = listOf(device().copy(
            product = "Device\nINJECTED", manufacturer = "Maker\r\nMORE"))))
        assertTrue(report.contains("Product: Device INJECTED"))
        assertFalse(report.contains("\nINJECTED"))
        assertFalse(report.contains("\nMORE"))
    }

    private fun device() = UsbAudioDeviceSnapshot(identity, "Test manufacturer", "Synthetic USB device", 0,
        emptyList(), true)
    private fun state(model: UsbAudioDescriptorModel?) = UsbAudioDiagnosticState(
        serviceAvailable = true, status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ,
        devices = listOf(device()), selectedIdentity = identity, generation = 3, descriptors = model)
}
