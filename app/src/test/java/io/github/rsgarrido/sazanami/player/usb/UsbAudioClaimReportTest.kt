package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UsbAudioClaimReportTest {
    private val identity = UsbAudioDeviceIdentity("/dev/bus/usb/private-test-key", 42, 0x1234, 0x5678)
    private val target = UsbAudioClockTarget(UsbInterfaceKey(18, 7, 0), 29, 5,
        listOf(UsbInterfaceKey(18, 8, 1)))
    private val resolution = UsbAudioClockResolution(target, 1)

    @Test fun reportDistinguishesBothStagesNonForcedClaimReleaseAndPreferredRangeEvidence() {
        val report = report(success())
        listOf("AudioControl Claim Experiment", "Resolved AC interface: configuration@18 interface 7 alternate 0",
            "Resolved Clock Source: 29", "force: false", "claimInterface result: success (true)",
            "release attempted: yes", "release result: SUCCEEDED", "connection close result: SUCCEEDED",
            "Stage A — Unclaimed:", "Stage B — While AudioControl claimed:",
            "GET_CUR sampling frequency: REJECTED_OR_TRANSPORT_ERROR", "GET_CUR sampling frequency: SUCCESS",
            "GET_RANGE sampling frequency: SUCCESS", "GET_CUR clock validity: SUCCESS",
            "Claim mode: AUDIOCONTROL_NON_FORCED", "Raw: 80 BB 00 00", "wIndex=0x1D07",
            "min=48000 max=192000 res=48000", "44100 Hz: not represented by returned range",
            "48000 Hz: supported by returned range", "96000 Hz: supported by returned range",
            "192000 Hz: supported by returned range", "Comparison: CLAIM_IMPROVED_READS",
            "No forced claim was attempted.").forEach { assertTrue("Missing: $it", report.contains(it)) }
        assertFalse(report.contains(identity.deviceName))
        assertFalse(report.contains("Claim needed: no, these reads completed without claiming any interface."))
    }

    @Test fun failedClaimIsNotAControlRequestRejectionOrProvenKernelConflict() {
        val failed = success().copy(claimStatus = UsbAudioClaimStatus.CLAIM_FAILED, claimReturned = false,
            claimed = null, releaseStatus = UsbAudioCleanupStatus.NOT_ATTEMPTED)
        val report = report(failed)
        assertTrue(report.contains("Claim status: CLAIM_FAILED"))
        assertTrue(report.contains("claimInterface result: failure (false), cause unknown"))
        assertTrue(report.contains("release attempted: no"))
        assertTrue(report.contains("GET_CUR sampling frequency: REJECTED_OR_TRANSPORT_ERROR"))
        assertTrue(report.contains("Comparison: CLAIM_UNAVAILABLE"))
        assertTrue(report.contains("No claimed query set issued/retained"))
        assertTrue(report.contains("No forced claim was attempted."))
        assertFalse(report.contains("kernel driver conflict"))
        assertFalse(report.contains("GET_CUR sampling frequency: SUCCESS"))
    }

    @Test fun releaseFailureDoesNotBecomeSuccessInReport() {
        val report = report(success().copy(releaseStatus = UsbAudioCleanupStatus.FAILED))
        assertTrue(report.contains("release attempted: yes"))
        assertTrue(report.contains("release result: FAILED"))
        assertTrue(report.contains("connection close result: SUCCEEDED"))
    }

    @Test fun staleExperimentCannotExposeStagePayloadsOrComparisonAsCurrent() {
        val diagnostics = UsbAudioClaimExperiment.diagnostics(success())
        val report = UsbAudioDiagnosticReport.format(state(diagnostics).copy(generation = 10))
        assertTrue(report.contains("Stale generation; prior experiment evidence omitted"))
        assertFalse(report.contains("Raw: 80 BB 00 00"))
        assertFalse(report.contains("Comparison: CLAIM_IMPROVED_READS"))
        assertFalse(report.contains("min=48000"))
    }

    @Test fun permissionFailureBeforePublicationKeepsCleanupFactsAndOmitsOldDecodedValues() {
        val diagnostics = UsbAudioClaimExperiment.invalidate(success(), UsbClockQueryStatus.PERMISSION_LOST, "Permission lost")
        val report = UsbAudioDiagnosticReport.format(state(diagnostics))
        assertTrue(report.contains("Claim status: PERMISSION_LOST"))
        assertTrue(report.contains("claimInterface result: success (true)"))
        assertTrue(report.contains("release result: SUCCEEDED"))
        assertTrue(report.contains("Comparison: ACCESS_CHANGED"))
        assertFalse(report.contains("Current clock sampling frequency: 48000 Hz"))
        assertFalse(report.contains("Raw: 80 BB 00 00"))
        assertFalse(report.contains("min=48000"))
    }

    @Test fun queryingAndNotRequestedExperimentsAreExplicitWithoutInventedPlaybackState() {
        val querying = UsbAudioClockDiagnostics(9, resolution, UsbClockDiagnosticPhase.QUERYING,
            claimExperiment = UsbAudioClaimExperimentResult(9, resolution))
        val report = UsbAudioDiagnosticReport.format(state(querying))
        assertTrue(report.contains("Experiment: QUERYING"))
        assertTrue(report.contains("Claim status: NOT_ATTEMPTED"))
        assertTrue(report.contains("release attempted: no"))
        val idle = UsbAudioDiagnosticReport.format(state(querying.copy(claimExperiment = null)))
        assertTrue(idle.contains("Not requested. Use Test AudioControl claim + clock reads."))
        assertFalse(report.contains("BitPerfectActive"))
        assertFalse(report.contains("BitPerfectEnabled"))
        assertFalse(report.contains("USB_STREAM_ACTIVE"))
        // Session 25 reports telemetry availability; the old pre-integration capability
        // disclaimer is obsolete. A clock-claim query alone still proves no playback state.
        for(text in listOf(report,idle)) {
            assertTrue(text.contains("SAZANAMI USB AUDIO DIAGNOSTIC — SESSION 25"))
            assertTrue(text.contains("Live native USB transport diagnostics — Session 25\n"))
            val telemetry=text.substringAfter("Live native USB transport diagnostics — Session 25\n").substringBefore("\nDevice\n")
            assertTrue(telemetry.contains("Capture status: IDLE"))
            assertTrue(telemetry.contains("attachment generation=unavailable; native stream/session=unavailable"))
            assertTrue(telemetry.contains("Snapshot unavailable:"))
            assertFalse(telemetry.contains("LIVE observation"))
            assertFalse(telemetry.contains("playback="))
            assertFalse(telemetry.contains("USB OUT: submitted="))
            assertFalse(text.contains(identity.deviceName))
        }
    }

    private fun success(): UsbAudioClaimExperimentResult {
        val baseline = UsbAudioClockControl.query(resolution, 9, UsbClockReadTransport { _, _ -> -1 })
        val claimed = UsbAudioClockControl.query(resolution, 9, UsbClockReadTransport { request, buffer ->
            val bytes = when {
                request.rangeHeaderOnly -> byteArrayOf(1, 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> byteArrayOf(1, 0) + u32(48_000) + u32(192_000) + u32(48_000)
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(48_000)
                else -> byteArrayOf(1)
            }
            bytes.copyInto(buffer)
            bytes.size
        }).copy(claimMode = UsbClockClaimMode.AUDIOCONTROL_NON_FORCED)
        val request = UsbAudioControlClaimRequest.resolve(target, listOf(UsbAudioClaimInterface(0, 7, 0, 1, 1, 0x20)))!!
        return UsbAudioClaimExperimentResult(9, resolution, UsbAudioClaimStatus.CLAIM_SUCCEEDED, request,
            baseline, claimed, UsbAudioCleanupStatus.SUCCEEDED, UsbAudioCleanupStatus.SUCCEEDED, claimReturned = true)
    }

    private fun report(result: UsbAudioClaimExperimentResult) = UsbAudioDiagnosticReport.format(state(UsbAudioClaimExperiment.diagnostics(result)))
    private fun state(diagnostics: UsbAudioClockDiagnostics) = UsbAudioDiagnosticState(
        serviceAvailable = true, status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ,
        devices = listOf(UsbAudioDeviceSnapshot(identity, "Test manufacturer", "Synthetic USB device", 0, emptyList(), true)),
        selectedIdentity = identity, generation = 9, clockDiagnostics = diagnostics)
    private fun u32(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
}
