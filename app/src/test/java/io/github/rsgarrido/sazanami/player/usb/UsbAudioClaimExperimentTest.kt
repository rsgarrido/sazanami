package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Fakes exercise ownership, ordering and existing control decoding; no hardware or Android APIs. */
class UsbAudioClaimExperimentTest {
    private val target = UsbAudioClockTarget(UsbInterfaceKey(18, 7, 0), 29, 5,
        listOf(UsbInterfaceKey(18, 8, 1)))
    private val resolution = UsbAudioClockResolution(target, 1)
    private val ac = UsbAudioClaimInterface(1, 7, 0, 1, 1, 0x20)

    @Test fun onlyResolvedAcIsEligibleEvenWhenOtherAcHidAndStreamingAppearFirst() {
        val interfaces = listOf(ac.copy(position = 0, number = 3), ac.copy(position = 2, number = 8, subclass = 2),
            ac.copy(position = 3, number = 0, interfaceClass = 3), ac)
        val request = UsbAudioControlClaimRequest.resolve(target, interfaces)!!
        assertEquals(ac, request.audioControl)
        assertFalse(request.force)
        assertNull(UsbAudioControlClaimRequest.resolve(target, interfaces.filterNot { it == ac }))
    }

    @Test fun streamingHidNonUac2NonzeroAlternateDuplicateAndConflictingTargetCannotBeClaimed() {
        listOf(ac.copy(subclass = 2), ac.copy(interfaceClass = 3), ac.copy(protocol = 0), ac.copy(alternate = 1)).forEach {
            assertNull(UsbAudioControlClaimRequest.resolve(target, listOf(it)))
        }
        assertNull(UsbAudioControlClaimRequest.resolve(target, listOf(ac, ac.copy(position = 5))))
        assertNull(UsbAudioControlClaimRequest.resolve(target.copy(audioControl = target.audioControl.copy(alternate = 1)), listOf(ac)))
        assertNull(UsbAudioControlClaimRequest.resolve(target.copy(audioControl = target.audioControl.copy(number = 8)), listOf(ac.copy(number = 8))))
    }

    @Test fun claimSuccessRepeatsOnlyTheSameClockReadsWithForceFalseAndThenReleasesBeforeClose() {
        val fake = Fake()
        val result = run(fake)
        assertEquals(UsbAudioClaimStatus.CLAIM_SUCCEEDED, result.claimStatus)
        assertEquals(true, result.claimReturned)
        assertFalse(result.force)
        assertFalse(fake.claims.single().force)
        assertEquals(ac, fake.claims.single().audioControl)
        assertEquals(UsbClockDiagnosticPhase.COMPLETE, result.claimed!!.phase)
        assertEquals(UsbClockClaimMode.UNCLAIMED, result.unclaimed!!.claimMode)
        assertEquals(UsbClockClaimMode.AUDIOCONTROL_NON_FORCED, result.claimed!!.claimMode)
        assertEquals(listOf("read:A:CURRENT_FREQUENCY", "read:A:FREQUENCY_RANGE", "read:A:CLOCK_VALIDITY", "claim:7:false",
            "read:B:CURRENT_FREQUENCY", "read:B:FREQUENCY_RANGE", "read:B:FREQUENCY_RANGE", "read:B:CLOCK_VALIDITY",
            "release:7", "close"), fake.events)
        fake.requests.forEach { assertEquals(0xA1, it.requestType); assertEquals(0x1D07, it.index) }
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.releaseStatus)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
    }

    @Test fun claimFailureStopsClaimedReadsWithoutRetryOrReleaseAndDoesNotInferCause() {
        val fake = Fake().apply { claimResult = false }
        val result = run(fake)
        assertEquals(UsbAudioClaimStatus.CLAIM_FAILED, result.claimStatus)
        assertEquals(false, result.claimReturned)
        assertNull(result.claimed)
        assertEquals(1, fake.claims.size)
        assertFalse(fake.events.any { it.startsWith("read:B") || it.startsWith("release:") })
        assertEquals(UsbAudioCleanupStatus.NOT_ATTEMPTED, result.releaseStatus)
        assertEquals("close", fake.events.last())
        assertTrue(result.detail!!.contains("cause unknown"))
        assertFalse(result.detail!!.contains("kernel"))
    }

    @Test fun rejectedClaimedReadsStillReleaseAndClose() {
        val fake = Fake().apply { claimedReadsSucceed = false }
        val result = run(fake)
        assertTrue(result.claimed!!.results.all { it.status == UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR })
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun malformedClaimedReadsStillReleaseAndKeepRawEvidence() {
        val fake = Fake().apply { claimedReply = { _, buffer -> buffer[0] = 0x7f; 1 } }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.claimed!!.results.last().status)
        assertEquals(listOf(0x7f), result.claimed!!.results.last().attempts.single().rawBytes)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun transportExceptionDuringClaimedReadsStillReleasesAndOmitsExceptionText() {
        val fake = Fake().apply { claimedReply = { _, _ -> throw IllegalStateException("private serial/path") } }
        val result = run(fake)
        assertTrue(result.claimed!!.results.all { it.status == UsbClockQueryStatus.TRANSPORT_ERROR })
        assertFalse(result.claimed!!.results.any { it.detail.orEmpty().contains("private") })
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun exceptionAfterClaimOutsideDecoderReleasesBeforeClosing() {
        val fake = Fake()
        val result = UsbAudioClaimExperiment.run(resolution, 3, fake) {
            if (fake.owned) throw IllegalStateException("private") else null
        }
        assertEquals(UsbAudioClaimStatus.EXCEPTION, result.claimStatus)
        assertEquals(true, result.claimReturned)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.releaseStatus)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun cancellationDuringClaimedReadPropagatesOnlyAfterReleaseAndClose() {
        val fake = Fake().apply { claimedReply = { _, _ -> throw CancellationException("cancel") } }
        var cancelled = false
        try { run(fake) } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
        assertFalse(fake.owned)
    }

    @Test fun cancellationImmediatelyAfterClaimStillReleasesWithoutIssuingStageB() {
        val fake = Fake()
        var cancelled = false
        try {
            UsbAudioClaimExperiment.run(resolution, 3, fake) { if (fake.owned) throw CancellationException("cancel") else null }
        } catch (_: CancellationException) { cancelled = true }
        assertTrue(cancelled)
        assertFalse(fake.events.any { it.startsWith("read:B") })
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun detachAfterClaimStopsReadsAndToleratesFailedRelease() {
        val fake = Fake().apply { releaseResult = false }
        val result = UsbAudioClaimExperiment.run(resolution, 3, fake) {
            if (fake.owned) UsbClockQueryStatus.DEVICE_DISCONNECTED else null
        }
        assertEquals(UsbAudioClaimStatus.DEVICE_DISCONNECTED, result.claimStatus)
        assertEquals(true, result.claimReturned)
        assertNull(result.claimed)
        assertEquals(UsbAudioCleanupStatus.FAILED, result.releaseStatus)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
        assertTrue(UsbAudioClaimExperiment.diagnostics(result).results.all { it.status == UsbClockQueryStatus.DEVICE_DISCONNECTED })
        assertEquals(UsbAudioClaimComparison.ACCESS_CHANGED, result.comparison())
    }

    @Test fun detachDuringClaimedReadRetainsRawAttemptButCannotSupplyPreferredValues() {
        var detached = false
        val fake = Fake().apply { claimedReply = { _, buffer ->
            frequency(48_000).copyInto(buffer)
            detached = true
            4
        } }
        val result = UsbAudioClaimExperiment.run(resolution, 3, fake) {
            if (detached) UsbClockQueryStatus.DEVICE_DISCONNECTED else null
        }
        assertEquals(1, fake.events.count { it.startsWith("read:B") })
        assertEquals(listOf(0x80, 0xBB, 0, 0), result.claimed!!.results.first().attempts.single().rawBytes)
        assertNull(UsbAudioClaimExperiment.diagnostics(result).results.first().currentFrequencyHz)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun newGenerationAfterClaimRejectsTheOldOperationIncludingReusedAndroidIdentity() {
        val identity = UsbAudioDeviceIdentity("private-bus-key", 42, 0x1234, 0x5678)
        val gate = UsbAudioGenerationGate()
        val token = gate.select(identity)!!
        val fake = Fake().apply { afterClaim = { gate.select(null); gate.select(identity) } }
        val result = UsbAudioClaimExperiment.run(resolution, token.generation, fake) {
            gate.clockAccessFailure(token, devicePresent = true, permissionGranted = true)
        }
        assertEquals(UsbAudioClaimStatus.STALE_GENERATION, result.claimStatus)
        assertFalse(gate.accepts(token))
        assertNull(result.claimed)
        assertNull(UsbAudioClaimExperiment.diagnostics(result).results.first().currentFrequencyHz)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun missingOrNonAcAndroidInterfaceFailsSafely() {
        listOf(emptyList(), listOf(ac.copy(subclass = 2)), listOf(ac.copy(interfaceClass = 3))).forEach { available ->
            val fake = Fake().apply { availableInterfaces = available }
            val result = run(fake)
            assertEquals(UsbAudioClaimStatus.INTERFACE_NOT_FOUND, result.claimStatus)
            assertTrue(fake.claims.isEmpty())
            assertNull(result.claimed)
            assertEquals("close", fake.events.last())
        }
    }

    @Test fun ambiguousTopologyNeverReadsOrClaims() {
        val fake = Fake()
        val result = UsbAudioClaimExperiment.run(UsbAudioClockResolution(issues = listOf("Ambiguous")), 3, fake)
        assertEquals(UsbAudioClaimStatus.TARGET_AMBIGUOUS, result.claimStatus)
        assertEquals(listOf("close"), fake.events)
    }

    @Test fun permissionLossBeforeAnyReadsOrImmediatelyBeforeClaimDoesNotClaim() {
        val beforeReads = Fake()
        val result = UsbAudioClaimExperiment.run(resolution, 3, beforeReads) { UsbClockQueryStatus.PERMISSION_LOST }
        assertEquals(UsbAudioClaimStatus.PERMISSION_LOST, result.claimStatus)
        assertEquals(listOf("close"), beforeReads.events)
        var lost = false
        val beforeClaim = Fake().apply { onInterfaces = { lost = true } }
        val second = UsbAudioClaimExperiment.run(resolution, 3, beforeClaim) { if (lost) UsbClockQueryStatus.PERMISSION_LOST else null }
        assertEquals(UsbAudioClaimStatus.PERMISSION_LOST, second.claimStatus)
        assertTrue(beforeClaim.claims.isEmpty())
        assertNull(second.claimed)
        assertEquals("close", beforeClaim.events.last())
    }

    @Test fun permissionLossBeforePublishOmitsStageValuesButKeepsCleanupEvidence() {
        val identity = UsbAudioDeviceIdentity("private-bus-key", 42, 0x1234, 0x5678)
        val gate = UsbAudioGenerationGate()
        val token = gate.select(identity)!!
        val result = UsbAudioClaimExperiment.run(resolution, token.generation, Fake())
        val failure = gate.clockAccessFailure(token, devicePresent = true, permissionGranted = false)!!
        val invalid = UsbAudioClaimExperiment.invalidate(result, failure, "Permission lost before publication")
        assertTrue(invalid.results.all { it.status == UsbClockQueryStatus.PERMISSION_LOST })
        assertNull(invalid.results.first().currentFrequencyHz)
        assertNull(invalid.claimExperiment!!.unclaimed)
        assertNull(invalid.claimExperiment!!.claimed)
        assertEquals(UsbClockClaimMode.AUDIOCONTROL_NON_FORCED, invalid.claimMode)
        assertEquals(true, invalid.claimExperiment!!.claimReturned)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, invalid.claimExperiment!!.releaseStatus)
    }

    @Test fun accessRevalidationTransportErrorAlsoPreventsOldValuesBecomingAuthoritative() {
        val fake = Fake().apply { unclaimedReadsSucceed = true }
        val result = UsbAudioClaimExperiment.run(resolution, 3, fake) {
            if (fake.owned) UsbClockQueryStatus.TRANSPORT_ERROR else null
        }
        assertEquals(UsbAudioClaimStatus.TRANSPORT_ERROR, result.claimStatus)
        assertNull(result.preferredStage(UsbClockQuery.CURRENT_FREQUENCY))
        assertTrue(UsbAudioClaimExperiment.diagnostics(result).results.all { it.status == UsbClockQueryStatus.TRANSPORT_ERROR })
        assertEquals(UsbAudioClaimComparison.ACCESS_CHANGED, result.comparison())
    }

    @Test fun comparisonDistinguishesNegativeBaselineClaimSuccessAndSuccessfulReads() {
        val result = run(Fake())
        assertEquals(UsbAudioClaimComparison.CLAIM_IMPROVED_READS, result.comparison())
        assertEquals(UsbClockDiagnosticPhase.COMPLETE, UsbAudioClaimExperiment.diagnostics(result).phase)
    }

    @Test fun comparisonDistinguishesFailedOwnershipFromRejectedControlReads() {
        assertEquals(UsbAudioClaimComparison.CLAIM_UNAVAILABLE, run(Fake().apply { claimResult = false }).comparison())
        assertEquals(UsbAudioClaimComparison.CLAIM_DID_NOT_RESOLVE, run(Fake().apply { claimedReadsSucceed = false }).comparison())
    }

    @Test fun successfulBaselineIsPreferredWhenClaimedReadsFailAndDoesNotProveClaimNecessity() {
        val result = run(Fake().apply { unclaimedReadsSucceed = true; claimedReadsSucceed = false })
        assertEquals(UsbAudioClaimComparison.BASELINE_SUCCEEDED, result.comparison())
        assertTrue(UsbAudioClaimExperiment.diagnostics(result).results.all { it.status == UsbClockQueryStatus.SUCCESS })
        assertTrue(result.preferredStage(UsbClockQuery.FREQUENCY_RANGE) === result.unclaimed)
    }

    @Test fun mismatchedStageGenerationCannotProvideComparisonOrPreferredValues() {
        val result = run(Fake())
        val mismatched = result.copy(claimed = result.claimed!!.copy(generation = 99))
        assertEquals(UsbAudioClaimComparison.INDETERMINATE, mismatched.comparison())
        assertNull(mismatched.preferredStage(UsbClockQuery.FREQUENCY_RANGE))
        assertTrue(UsbAudioClaimExperiment.diagnostics(mismatched).results.none { it.status == UsbClockQueryStatus.SUCCESS })
    }

    @Test fun preferredEvidenceCanUseSuccessfulQueriesFromDifferentStagesWithoutDiscardingFailures() {
        val fake = Fake().apply {
            unclaimedReadsSucceed = true
            claimedReply = { request, buffer ->
                if (request.query == UsbClockQuery.CURRENT_FREQUENCY) {
                    frequency(96_000).copyInto(buffer)
                    4
                } else -1
            }
        }
        val result = run(fake)
        val diagnostics = UsbAudioClaimExperiment.diagnostics(result)
        assertEquals(96_000L, diagnostics.results.first().currentFrequencyHz)
        assertTrue(result.preferredStage(UsbClockQuery.CURRENT_FREQUENCY) === result.claimed)
        assertTrue(result.preferredStage(UsbClockQuery.FREQUENCY_RANGE) === result.unclaimed)
        assertTrue(result.preferredStage(UsbClockQuery.CLOCK_VALIDITY) === result.unclaimed)
        assertTrue(result.claimed!!.results.drop(1).all { it.status == UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR })
        assertTrue(diagnostics.results.all { it.status == UsbClockQueryStatus.SUCCESS })
    }

    @Test fun claimedRangeFeedsTheExistingMilestoneAssessmentWithoutAnotherDecoder() {
        val result = run(Fake())
        val range = UsbAudioClaimExperiment.diagnostics(result).results.single { it.query == UsbClockQuery.FREQUENCY_RANGE }
        assertEquals(listOf(UsbSamplingFrequencyRange(48_000, 192_000, 48_000)), range.ranges)
        assertEquals(listOf(UsbClockRateSupport.NOT_REPRESENTED, UsbClockRateSupport.REPRESENTED,
            UsbClockRateSupport.REPRESENTED, UsbClockRateSupport.REPRESENTED),
            UsbAudioClockControl.milestoneRatesHz.map { UsbAudioClockControl.assessRate(it, range) })
    }

    @Test fun releaseExceptionDoesNotPreventCloseAndIsReportedHonestly() {
        val fake = Fake().apply { releaseException = true }
        val result = run(fake)
        assertEquals(UsbAudioCleanupStatus.EXCEPTION, result.releaseStatus)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    private fun run(fake: Fake) = UsbAudioClaimExperiment.run(resolution, 3, fake)
    private fun frequency(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }

    private inner class Fake : UsbAudioClaimTransport {
        var availableInterfaces = listOf(ac)
        var claimResult = true
        var releaseResult = true
        var releaseException = false
        var unclaimedReadsSucceed = false
        var claimedReadsSucceed = true
        var owned = false
        var afterClaim: (() -> Unit)? = null
        var onInterfaces: (() -> Unit)? = null
        var claimedReply: ((UsbClockReadRequest, ByteArray) -> Int)? = null
        val events = mutableListOf<String>()
        val claims = mutableListOf<UsbAudioControlClaimRequest>()
        val requests = mutableListOf<UsbClockReadRequest>()

        override fun interfaces(): List<UsbAudioClaimInterface> { onInterfaces?.invoke(); return availableInterfaces }
        override fun claim(request: UsbAudioControlClaimRequest): Boolean {
            events += "claim:${request.audioControl.number}:${request.force}"
            claims += request
            owned = claimResult
            afterClaim?.invoke()
            return claimResult
        }
        override fun release(request: UsbAudioControlClaimRequest): Boolean {
            events += "release:${request.audioControl.number}"
            if (releaseException) throw IllegalStateException("detached/private")
            owned = false
            return releaseResult
        }
        override fun close() { events += "close"; owned = false }
        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
            events += "read:${if (owned) "B" else "A"}:${request.query}"
            requests += request
            if (owned) claimedReply?.let { return it(request, buffer) }
            if (!(if (owned) claimedReadsSucceed else unclaimedReadsSucceed)) return -1
            val reply = when {
                request.rangeHeaderOnly -> byteArrayOf(1, 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> byteArrayOf(1, 0) + frequency(48_000) + frequency(192_000) + frequency(48_000)
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> frequency(48_000)
                else -> byteArrayOf(1)
            }
            reply.copyInto(buffer)
            return reply.size
        }
    }
}
