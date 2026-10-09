package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioClockReportTest {
    private val identity = UsbAudioDeviceIdentity("/dev/bus/usb/private-test-key", 42, 0x1234, 0x5678)
    private val target = UsbAudioClockTarget(UsbInterfaceKey(18, 7, 0), 29, 5,
        listOf(UsbInterfaceKey(18, 8, 1)))
    private val resolution = UsbAudioClockResolution(target, 1)

    @Test fun reportIncludesClockRawEvidenceTuplesAndAllMilestoneAssessmentsWithoutBusPath() {
        val report = UsbAudioDiagnosticReport.format(state(diagnostics()))
        listOf("UAC2 Clock Queries", "Query status: COMPLETE", "AudioControl interface: 7 alternate 0",
            "Clock Source entity: 29", "Claim needed: no", "Current clock sampling frequency: 48000 Hz",
            "Clock valid: true", "min=48000 max=96000 res=48000 (stepped interval)",
            "44100 Hz: not represented by returned range", "48000 Hz: supported by returned range",
            "96000 Hz: supported by returned range", "192000 Hz: not represented by returned range",
            "GET_CUR sampling frequency: SUCCESS", "GET_RANGE sampling frequency: SUCCESS",
            "GET_CUR clock validity: SUCCESS", "bmRequestType=0xA1", "wValue=0x0200",
            "wIndex=0x1D07", "Raw: 80 BB 00 00", "Raw: 01 00").forEach {
            assertTrue("Missing report evidence: $it", report.contains(it))
        }
        assertFalse(report.contains(identity.deviceName))
        assertFalse(report.contains("BitPerfectActive"))
        assertTrue(report.contains("Serial: not queried or included"))
        assertTrue(report.contains("not track rate, PCM format support, or Bit-perfect playback"))
    }

    @Test fun malformedRangeIsVisibleButCannotProducePositiveRateAssessment() {
        val good = diagnostics()
        val bad = good.copy(phase = UsbClockDiagnosticPhase.PARTIAL, results = good.results.map {
            if (it.query == UsbClockQuery.FREQUENCY_RANGE) it.copy(status = UsbClockQueryStatus.MALFORMED_RESPONSE,
                detail = "Synthetic malformed reply") else it
        })
        val report = UsbAudioDiagnosticReport.format(state(bad))
        assertTrue(report.contains("GET_RANGE sampling frequency: MALFORMED_RESPONSE"))
        assertTrue(report.contains("min=48000 max=96000 res=48000"))
        assertTrue(report.contains("Synthetic malformed reply"))
        UsbAudioClockControl.milestoneRatesHz.forEach {
            assertTrue(report.contains("$it Hz: indeterminate"))
        }
        assertFalse(report.contains("Hz: supported by returned range"))
    }

    @Test fun reportOmitsRawAndDecodedClockEvidenceFromOlderGeneration() {
        val report = UsbAudioDiagnosticReport.format(state(diagnostics()).copy(generation = 4))
        assertTrue(report.contains("Stale generation; prior clock values omitted"))
        assertFalse(report.contains("Current clock sampling frequency: 48000 Hz"))
        assertFalse(report.contains("Raw: 80 BB 00 00"))
        assertFalse(report.contains("min=48000"))
    }

    @Test fun ambiguousTargetAndNegativeTransferDoNotClaimAnInterfaceWasRequired() {
        val unavailable = UsbAudioClockControl.unavailable(3, UsbAudioClockResolution(issues = listOf("Ownership ambiguous")),
            UsbClockQueryStatus.AMBIGUOUS_TARGET, "No query issued")
        val report = UsbAudioDiagnosticReport.format(state(unavailable))
        assertTrue(report.contains("AudioControl / Clock Source: unresolved"))
        assertTrue(report.contains("Ownership ambiguous"))
        assertTrue(report.contains("AMBIGUOUS_TARGET"))
        val failed = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { _, _ -> -1 })
        val failureReport = UsbAudioDiagnosticReport.format(state(failed))
        assertTrue(failureReport.contains("REJECTED_OR_TRANSPORT_ERROR"))
        assertTrue(failureReport.contains("STALL/rejection and transport failure cannot be distinguished"))
        assertTrue(failureReport.contains("Claim needed: unknown"))
        assertTrue(failureReport.contains("returned=-1"))
    }

    @Test fun queryingPhaseAndFalseValidityAreExplicit() {
        val inProgress = UsbAudioClockDiagnostics(3, resolution, UsbClockDiagnosticPhase.QUERYING)
        assertTrue(UsbAudioDiagnosticReport.format(state(inProgress)).contains("Query status: QUERYING"))
        val falseClock = diagnostics().copy(results = diagnostics().results.map {
            if (it.query == UsbClockQuery.CLOCK_VALIDITY) it.copy(clockValid = false) else it
        })
        assertTrue(UsbAudioDiagnosticReport.format(state(falseClock)).contains("Clock valid: false"))
    }

    private fun diagnostics() = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
        val reply = when {
            request.rangeHeaderOnly -> byteArrayOf(1, 0)
            request.query == UsbClockQuery.FREQUENCY_RANGE -> byteArrayOf(1, 0) + u32(48_000) + u32(96_000) + u32(48_000)
            request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(48_000)
            else -> byteArrayOf(1)
        }
        reply.copyInto(buffer)
        reply.size
    })

    private fun state(diagnostics: UsbAudioClockDiagnostics) = UsbAudioDiagnosticState(
        serviceAvailable = true, status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ,
        devices = listOf(UsbAudioDeviceSnapshot(identity, "Test manufacturer", "Synthetic USB device", 0, emptyList(), true)),
        selectedIdentity = identity, generation = 3, clockDiagnostics = diagnostics)
    private fun u32(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
}
