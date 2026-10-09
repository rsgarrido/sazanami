package io.github.rsgarrido.sazanami.player.usb

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/** Pure synthetic topology, usbfs packets and worker lifecycle. No USB hardware or native loading.
 * Source-boundary assertions below guard prohibited native/Android operations; they do not
 * substitute for the user's manual ABI, driver or real-DAC verification.
 */
class UsbAudioForcedExperimentTest {
    private val model = descriptorModel()
    private val android = model.interfaces.map {
        UsbAndroidInterfaceSnapshot(it.key.number, it.key.alternate, it.interfaceClass, it.subclass, it.protocol, emptyList())
    }
    private val plan = UsbFsProbePlan.create(model, android)
    private val facts = android.mapIndexed { position, iface -> UsbAudioClaimInterface(position,
        iface.number, iface.alternate, iface.interfaceClass, iface.subclass, iface.protocol) }

    @Test fun forcedTargetIsResolvedAcAndNonForcedRequestStaysFalse() {
        val request = UsbAudioForcedClaimRequest.resolve(plan, facts)!!
        assertEquals(7, request.audioControl.number)
        assertTrue(request.audioControl.isUac2AudioControl)
        assertTrue(request.force)
        assertFalse(UsbAudioControlClaimRequest.resolve(plan.resolution.target!!, facts)!!.force)
    }

    @Test fun hidCannotSubstituteForAndroidAc() {
        val changed = facts.map { if (it.number == 7) it.copy(interfaceClass = 3, subclass = 0, protocol = 0) else it }
        assertNull(UsbAudioForcedClaimRequest.resolve(plan, changed))
    }

    @Test fun audioStreamingCannotSubstituteForAndroidAc() {
        assertNull(UsbAudioForcedClaimRequest.resolve(plan, facts.map { if (it.number == 7) it.copy(subclass = 2) else it }))
    }

    @Test fun duplicateOrWrongAlternateAcCannotForceClaim() {
        assertNull(UsbAudioForcedClaimRequest.resolve(plan, facts + facts.single { it.number == 7 }.copy(position = 99)))
        assertNull(UsbAudioForcedClaimRequest.resolve(plan, facts.map { if (it.number == 7) it.copy(alternate = 1) else it }))
    }

    @Test fun ambiguousTopologyBlocksClaimAndClosesConnection() {
        val ambiguous = UsbFsProbePlan.create(model.copy(associations = emptyList()), android)
        val fake = Fake(ambiguous)
        val result = run(fake, ambiguous)
        assertNull(result.claimReturned)
        assertTrue(fake.requests.isEmpty())
        assertEquals(listOf("close"), fake.events)
    }

    @Test fun malformedDescriptorsCannotProduceForceTarget() {
        val invalid = UsbFsProbePlan.create(model.copy(warnings = listOf("malformed descriptor")), android)
        assertNull(invalid.audioControl)
        assertNull(UsbAudioForcedClaimRequest.resolve(invalid, facts))
    }

    @Test fun preexistingPermissionFailureBlocksBaselineAndClaim() {
        val fake = Fake()
        val result = run(fake) { UsbClockQueryStatus.PERMISSION_LOST }
        assertEquals(UsbClockQueryStatus.PERMISSION_LOST, result.accessFailure)
        assertEquals(listOf("close"), fake.events)
    }

    @Test fun missingNativeBaselineOrSpeedBlocksDisruptiveClaim() {
        listOf(UsbFsProbeStatus.NATIVE_UNAVAILABLE, UsbFsProbeStatus.MALFORMED_NATIVE_RESULT).forEach { status ->
            val fake = Fake().apply { baselineStatus = status }
            assertNull(run(fake).claimReturned)
            assertTrue(fake.events.none { it.startsWith("claim") })
        }
        val fake = Fake().apply { speedFails = true }
        assertNull(run(fake).claimReturned)
    }

    @Test fun claimFalsePreventsAllNativeReadsAndRelease() {
        val fake = Fake().apply { claimResult = false }
        val result = run(fake)
        assertEquals(false, result.claimReturned)
        assertTrue(fake.requests.isEmpty())
        assertEquals(UsbAudioCleanupStatus.NOT_ATTEMPTED, result.releaseStatus)
        assertEquals(listOf("snapshot:before", "claim:7:true", "close"), fake.events)
        assertEquals(fake.frameworkAfter, result.afterAndroidAudio)
    }

    @Test fun claimTrueReadsRangeFirstThenCurAndValidityAndOnlyClaimsAcOnce() {
        val fake = Fake()
        val result = run(fake)
        assertEquals(listOf("claim:7:true"), fake.events.filter { it.startsWith("claim:") })
        assertEquals(listOf(UsbClockQuery.FREQUENCY_RANGE, UsbClockQuery.FREQUENCY_RANGE,
            UsbClockQuery.CURRENT_FREQUENCY, UsbClockQuery.CLOCK_VALIDITY), fake.requests.map { it.query })
        assertTrue(fake.events.indexOf("snapshot:owned") < fake.events.indexOf("read:FREQUENCY_RANGE"))
        assertEquals(UsbClockClaimMode.AUDIOCONTROL_FORCED_NATIVE, result.clocks!!.claimMode)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
    }

    @Test fun requestFieldsUseResolvedClockAndAcWithoutKa15Constants() {
        val fake = Fake()
        run(fake)
        fake.requests.forEach {
            assertEquals(0xA1, it.requestType)
            assertEquals((29 shl 8) or 7, it.index)
            assertEquals(0, it.value and 0xff)
            assertEquals(if (it.query == UsbClockQuery.FREQUENCY_RANGE) 2 else 1, it.request)
            assertEquals(if (it.query == UsbClockQuery.CLOCK_VALIDITY) 0x0200 else 0x0100, it.value)
        }
        assertEquals(listOf(2, 14, 4, 1), fake.requests.map { it.length })
    }

    @Test fun nativeRangeFeedsExistingDecoderAndPreservesBothRawAttempts() {
        val result = run(Fake())
        val range = range(result)
        assertEquals(UsbClockQueryStatus.SUCCESS, range.status)
        assertEquals(listOf(UsbSamplingFrequencyRange(44_100, 44_100, 0)), range.ranges)
        assertEquals(1, range.declaredSubrangeCount)
        assertEquals(listOf(2, 14), range.attempts.map { it.nativeResult!!.returnValue })
        assertEquals(2, range.attempts.first().rawBytes.size)
        assertEquals(14, range.attempts.last().rawBytes.size)
    }

    @Test fun nativeCurDecodesUnsignedFrequencyAndBooleanValidity() {
        val fake = Fake().apply { frequency = 0xffffffffL; valid = false }
        val results = run(fake).clocks!!.results
        assertEquals(0xffffffffL, results.single { it.query == UsbClockQuery.CURRENT_FREQUENCY }.currentFrequencyHz)
        assertEquals(false, results.single { it.query == UsbClockQuery.CLOCK_VALIDITY }.clockValid)
    }

    @Test fun singleton44100IsSupportedOnlyByRange() {
        assertEquals(UsbClockRateSupport.REPRESENTED, UsbAudioClockControl.assessRate(44_100, range(run(Fake()))))
    }

    @Test fun integerAndStepped44100IntervalsAreSupported() {
        listOf(UsbSamplingFrequencyRange(40_000, 50_000, 1), UsbSamplingFrequencyRange(22_050, 88_200, 22_050)).forEach { tuple ->
            val fake = Fake().apply { ranges = listOf(tuple) }
            assertEquals(UsbClockRateSupport.REPRESENTED, UsbAudioClockControl.assessRate(44_100, range(run(fake))))
        }
    }

    @Test fun absent44100IsNotRepresentedAndOtherMilestoneRatesAreAssessed() {
        val fake = Fake().apply { ranges = listOf(UsbSamplingFrequencyRange(48_000, 192_000, 48_000)) }
        val range = range(run(fake))
        assertEquals(listOf(UsbClockRateSupport.NOT_REPRESENTED, UsbClockRateSupport.REPRESENTED,
            UsbClockRateSupport.REPRESENTED, UsbClockRateSupport.REPRESENTED),
            UsbAudioClockControl.milestoneRatesHz.map { UsbAudioClockControl.assessRate(it, range) })
    }

    @Test fun failedAndMalformedNativeRangesKeep44100Indeterminate() {
        val failed = Fake().apply { nativeError = 32 to "EPIPE" }
        assertEquals(UsbClockRateSupport.INDETERMINATE, UsbAudioClockControl.assessRate(44_100, range(run(failed))))
        val malformed = Fake().apply { ranges = listOf(UsbSamplingFrequencyRange(44_100, 48_000, 0)) }
        val result = range(run(malformed))
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.status)
        assertEquals(UsbClockRateSupport.INDETERMINATE, UsbAudioClockControl.assessRate(44_100, result))
    }

    @Test fun boundedRangeCountBlocksBodyWithoutOverflow() {
        val fake = Fake().apply { headerOverride = byteArrayOf(0xff.toByte(), 0xff.toByte()) }
        val result = range(run(fake))
        assertEquals(65535, result.declaredSubrangeCount)
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.status)
        assertEquals(1, fake.requests.count { it.query == UsbClockQuery.FREQUENCY_RANGE })
    }

    @Test fun shortRangeAndChangedCountArePreservedWithoutSupportClaims() {
        val short = Fake().apply { bodyOverride = byteArrayOf(1, 0, 0) }
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, range(run(short)).status)
        val changed = Fake().apply { bodyOverride = byteArrayOf(2, 0) + u32(44_100) + u32(44_100) + u32(0) }
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, range(run(changed)).status)
    }

    @Test fun nativeErrorsRemainDistinctAndReleaseEvenWhenEveryReadFails() {
        listOf(Triple(32, "EPIPE", UsbClockQueryStatus.STALL_LIKE),
            Triple(16, "EBUSY", UsbClockQueryStatus.INTERFACE_BUSY),
            Triple(22, "EINVAL", UsbClockQueryStatus.INVALID_REQUEST),
            Triple(5, "EIO", UsbClockQueryStatus.TRANSPORT_ERROR)).forEach { (number, name, status) ->
            val fake = Fake().apply { nativeError = number to name }
            val result = run(fake)
            val clocks = result.clocks!!
            assertTrue(clocks.results.all { it.status == status })
            clocks.results.forEach {
                assertEquals(number, it.attempts.single().nativeResult!!.errno)
                assertEquals(name, it.attempts.single().nativeResult!!.errnoName)
                assertTrue(it.attempts.single().rawBytes.isEmpty())
            }
            assertEquals(true, result.releaseReturned)
            assertTrue(fake.events.indexOf("release:7") < fake.events.indexOf("snapshot:after"))
        }
    }

    @Test fun nativeEnodevStopsFurtherControlsAndInvalidatesAllDeviceValues() {
        val fake = Fake().apply { nativeError = 19 to "ENODEV" }
        val result = run(fake)
        assertEquals(1, fake.requests.size)
        assertEquals(UsbRecoveryStatus.DEVICE_DISCONNECTED, result.recoveryStatus)
        assertNull(result.clocks)
        assertNull(result.before)
        assertEquals(true, result.releaseReturned)
        assertEquals("close", fake.events.last())
    }

    @Test fun nativePermissionErrorStopsFurtherControlsAndReleases() {
        val fake = Fake().apply { nativeError = 13 to "EACCES" }
        val result = run(fake)
        assertEquals(1, fake.requests.size)
        assertEquals(UsbClockQueryStatus.PERMISSION_LOST, result.accessFailure)
        assertEquals(true, result.releaseReturned)
    }

    @Test fun ownedSnapshotExceptionStillReleasesAndThenObservesRecovery() {
        val fake = Fake().apply { ownedSnapshotThrows = true }
        val result = run(fake)
        assertEquals(true, result.releaseReturned)
        assertTrue(fake.requests.isEmpty())
        assertTrue(result.detail!!.contains("IllegalStateException"))
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
    }

    @Test fun controlExceptionStillReleasesAndDoesNotBecomeUnsupported() {
        val fake = Fake().apply { readException = IllegalStateException("private path") }
        val result = run(fake)
        assertTrue(result.clocks!!.results.all { it.status == UsbClockQueryStatus.TRANSPORT_ERROR })
        assertEquals(true, result.releaseReturned)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
    }

    @Test fun readCancellationReleasesBeforeCloseAndPropagatesCancellation() {
        val fake = Fake().apply { readException = CancellationException("cancel worker") }
        try { run(fake); throw AssertionError("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(listOf("release:7", "close"), fake.events.takeLast(2))
    }

    @Test fun cancellationDuringRecoveryDoesNotDuplicateReleaseAndStillCloses() {
        val fake = Fake().apply { restored = false; pauseCancellation = true }
        try { run(fake); throw AssertionError("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(1, fake.events.count { it == "release:7" })
        assertEquals("close", fake.events.last())
    }

    @Test fun generationInvalidationWhileOwnedStopsReadsAndStillReleases() {
        val fake = Fake()
        val result = run(fake) { if (fake.owned) UsbClockQueryStatus.STALE_GENERATION else null }
        assertEquals(UsbRecoveryStatus.STALE_GENERATION, result.recoveryStatus)
        assertTrue(fake.requests.isEmpty())
        assertEquals(true, result.releaseReturned)
        assertTrue(result.recoveryObservations.isEmpty())
    }

    @Test fun physicalDetachWhileOwnedStopsReadsAndStillAttemptsRelease() {
        val fake = Fake().apply { releaseResult = false }
        val result = run(fake) { if (fake.owned) UsbClockQueryStatus.DEVICE_DISCONNECTED else null }
        assertEquals(UsbRecoveryStatus.DEVICE_DISCONNECTED, result.recoveryStatus)
        assertEquals(false, result.releaseReturned)
        assertEquals(UsbAudioCleanupStatus.FAILED, result.releaseStatus)
        assertTrue(fake.requests.isEmpty())
    }

    @Test fun falseAndExceptionalReleaseResultsArePreservedAndNeverRepaired() {
        val failed = Fake().apply { releaseResult = false }
        val result = run(failed)
        assertEquals(UsbRecoveryStatus.RELEASE_FAILED, result.recoveryStatus)
        assertEquals(false, result.releaseReturned)
        assertEquals(1, result.recoveryObservations.size)
        assertEquals(0L, failed.now)
        val exception = Fake().apply { releaseThrows = true }
        val thrown = run(exception)
        assertEquals(UsbRecoveryStatus.RELEASE_FAILED, thrown.recoveryStatus)
        assertEquals("IllegalStateException", thrown.releaseException)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, thrown.closeStatus)
    }

    @Test fun claimExceptionDoesNotReleaseOrReadAndOmitsPrivateMessage() {
        val fake = Fake().apply { claimThrows = true }
        val result = run(fake)
        assertEquals("SecurityException", result.claimException)
        assertEquals(UsbAudioCleanupStatus.NOT_ATTEMPTED, result.releaseStatus)
        assertTrue(fake.requests.isEmpty())
        assertFalse(report(result).contains("private path"))
    }

    @Test fun snapshotsRemainSeparateAndOwnedAcEnodataIsEvidence() {
        val result = run(Fake())
        assertEquals("baseline_driver_7", result.before!!.driverBindings.single { it.iface.number == 7 }.driver)
        val owned = result.whileOwned!!.driverBindings.single { it.iface.number == 7 }
        assertNull(owned.driver)
        assertEquals(61, owned.result.errno)
        assertEquals("ENODATA", owned.result.errnoName)
        val after = requireNotNull(result.after)
        assertEquals("baseline_driver_7", after.driverBindings.single { it.iface.number == 7 }.driver)
        assertNull(result.before.claim)
        assertNull(result.whileOwned.claim)
        assertNull(after.claim)
    }

    @Test fun recoveryPollingTerminatesAtTwoSecondsWithoutReset() {
        val fake = Fake().apply { restored = false; frameworkAfter = UsbAndroidAudioDiagnostics() }
        val result = run(fake)
        assertEquals(UsbRecoveryStatus.NOT_RECOVERED, result.recoveryStatus)
        assertTrue(result.recoveryWindowExpired)
        assertEquals(2_000L, fake.now)
        assertEquals(9, result.recoveryObservations.size)
        assertEquals(8, fake.events.count { it.startsWith("pause:") })
        assertEquals(listOf("claim:7:true"), fake.events.filter { it.startsWith("claim:") })
    }

    @Test fun eventualDriverRestorationIsObservedAndStopsPollingEarly() {
        val fake = Fake().apply { restoredAtObservation = 3 }
        val result = run(fake)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
        assertEquals(500L, fake.now)
        assertEquals(3, result.recoveryObservations.size)
        assertFalse(result.recoveryWindowExpired)
    }

    @Test fun audioStreamingOrHidDriverLossPreventsKernelRestoration() {
        listOf(4, 8).forEach { interfaceNumber ->
            val fake = Fake().apply { missingAfterInterface = interfaceNumber }
            val result = run(fake)
            assertFalse(UsbAudioForcedExperiment.kernelRestored(result))
            assertEquals(UsbRecoveryStatus.FRAMEWORK_PRESENT_KERNEL_UNCONFIRMED, result.recoveryStatus)
        }
    }

    @Test fun kernelRestoredFrameworkAbsentOrAmbiguousRemainsPending() {
        listOf(UsbAndroidAudioDiagnostics(), UsbAndroidAudioDiagnostics(listOf(audio(), audio().copy(id = 91))),
            UsbAndroidAudioDiagnostics(listOf(audio().copy(safeAddress = null)))).forEach { framework ->
            val result = run(Fake().apply { frameworkAfter = framework })
            assertTrue(UsbAudioForcedExperiment.kernelRestored(result))
            assertEquals(UsbRecoveryStatus.KERNEL_RECOVERED_FRAMEWORK_PENDING, result.recoveryStatus)
        }
    }

    @Test fun frameworkIdAndRateChangesStaySecondaryToNative44100Evidence() {
        val fake = Fake().apply { frameworkAfter = UsbAndroidAudioDiagnostics(listOf(audio().copy(id = 92, sampleRates = listOf(48_000)))) }
        val result = run(fake)
        assertTrue(UsbAudioForcedExperiment.frameworkCandidateReturned(result))
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
        assertEquals(UsbClockRateSupport.REPRESENTED, UsbAudioClockControl.assessRate(44_100, range(result)))
        assertTrue(report(result).contains("44.1 kHz / 44100 Hz — CD milestone: SUPPORTED"))
        assertTrue(report(result).contains("90 -> 92"))
    }

    @Test fun staleGenerationCannotPublishRatesOrSuccessfulRecovery() {
        val result = run(Fake())
        val report = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 4, forcedExperiment = result))
        assertTrue(report.contains("Stale generation; Session 5 device evidence omitted"))
        assertFalse(report.contains("CD milestone: SUPPORTED"))
        val invalid = result.invalidate(UsbClockQueryStatus.STALE_GENERATION, "stale")
        assertNull(invalid.clocks)
        assertEquals(true, invalid.releaseReturned)
        assertEquals(UsbRecoveryStatus.STALE_GENERATION, invalid.recoveryStatus)
    }

    @Test fun forcedBusyStateBlocksOtherDiagnosticActionsWithoutPlaybackMode() {
        val state = UsbAudioDiagnosticState(forcedExperiment = UsbAudioForcedExperimentResult(3, plan, UsbForcedPhase.QUERYING))
        assertTrue(state.diagnosticOperationRunning)
        assertFalse(state.copy(forcedExperiment = run(Fake())).diagnosticOperationRunning)
        assertFalse(state.javaClass.declaredFields.any { it.name.contains("bitPerfect", ignoreCase = true) })
    }

    @Test fun reportPreservesNativeErrorsRawFieldsAndScopeExclusions() {
        val result = run(Fake().apply { nativeError = 32 to "EPIPE" })
        val report = report(result)
        listOf("Forced AudioControl Ownership Experiment", "force: true", "USBDEVFS_CONTROL: ioctl return=-1 errno=32",
            "errno name=EPIPE", "requested length=2", "wIndex=0x1D07", "Raw: none",
            "AudioStreaming force claim attempted: NO", "SET_CUR issued: NO", "SET_INTERFACE issued: NO",
            "Audio data transmitted: NO", "No Bit-perfect active state", "SECONDARY framework evidence").forEach {
            assertTrue("Missing report evidence: $it", report.contains(it))
        }
        assertTrue(report.contains("CD milestone: INDETERMINATE"))
    }

    @Test fun nativePacketValidationRetainsNumericErrorAndRejectsInconsistentLengths() {
        val request = UsbClockReadRequest.rangeHeader(plan.resolution.target!!)
        val error = UsbFsControlEvidence.decode(UsbFsNativeControlResult(2, -1, 16, "EBUSY", byteArrayOf()), request)
        assertEquals(-1, error.returnValue)
        assertEquals(16, error.errno)
        assertEquals(UsbClockQueryStatus.INTERFACE_BUSY, error.failureStatus())
        listOf(UsbFsNativeControlResult(4, 2, 0, "", byteArrayOf(1, 0)),
            UsbFsNativeControlResult(2, -1, 0, "", byteArrayOf()),
            UsbFsNativeControlResult(2, 2, 0, "", byteArrayOf(1))).forEach { packet ->
            try { UsbFsControlEvidence.decode(packet, request); throw AssertionError("Invalid packet accepted") }
            catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun baselineNativeDisconnectPreventsClaimAndLateRecoveryCannotBecomeEnumerationRecovered() {
        val fake = Fake().apply { baselineStatus = UsbFsProbeStatus.DEVICE_DISCONNECTED }
        val result = run(fake)
        assertEquals(UsbRecoveryStatus.DEVICE_DISCONNECTED, result.recoveryStatus)
        assertNull(result.claimReturned)
        assertNull(result.before)
        assertEquals(listOf("snapshot:before", "close"), fake.events)
    }

    @Test fun closeExceptionPreventsEnumerationClassificationAndKeepsReleaseFact() {
        val result = run(Fake().apply { closeThrows = true })
        assertEquals(true, result.releaseReturned)
        assertEquals(UsbAudioCleanupStatus.EXCEPTION, result.closeStatus)
        assertEquals(UsbRecoveryStatus.INDETERMINATE, result.recoveryStatus)
        assertTrue(result.afterAndroidAudio != null)
    }

    @Test fun nativeSecurityExceptionStopsReadsAfterFirstAttemptAndStillReleases() {
        val fake = Fake().apply { readException = SecurityException("private denied") }
        val result = run(fake)
        assertEquals(1, fake.requests.size)
        assertEquals(UsbClockQueryStatus.PERMISSION_LOST, result.accessFailure)
        assertEquals(true, result.releaseReturned)
    }

    @Test fun snapshotOnlyPacketDecoderRejectsAnAccidentalClaimProbe() {
        val bindings = snapshot(plan, false, true)
        assertNull(bindings.claim)
        val count = plan.interfaces.size
        val values = listOf(UsbFsEvidence.NATIVE_API_VERSION, count) + plan.interfaces.flatMap { listOf(it.number, 0, 0) } +
            listOf(3, 0, 1, -1, 16, 0, 0, 0)
        val packet = UsbFsNativeResult(values.toIntArray(), Array(count) { "driver" },
            (List(count) { "" } + listOf("", "EBUSY", "")).toTypedArray(), "HIGH")
        assertEquals(UsbFsProbeStatus.MALFORMED_NATIVE_RESULT,
            UsbFsEvidence.decode(packet, plan, 3, claimProbeExpected = false).status)
    }

    @Test fun nativeSourceCapturesErrnoImmediatelyAndNeverOwnsBorrowedFd() {
        val native = source("cpp/usb_fs_diagnostics.cpp")
        val read = native.substringAfter("const int returned = ::ioctl(borrowedFd, USBDEVFS_CONTROL, &control);")
        assertTrue(read.trimStart().startsWith("const int savedError = returned < 0 ? errno : 0;"))
        assertFalse(native.contains("::close("))
        assertFalse(native.contains("::dup("))
        listOf("USBDEVFS_DISCONNECT", "USBDEVFS_CONNECT", "USBDEVFS_RESET", "USBDEVFS_SUBMITURB").forEach {
            assertFalse(native.contains(it))
        }
        assertTrue(native.contains("requestType != 0xA1"))
        assertTrue(native.contains("length <= 770"))
    }

    @Test fun androidSourceForcesOnlyValidatedAcAndUsesNativeReadsAndAndroidRelease() {
        val adapter = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioForcedConnection.kt")
        assertTrue(adapter.contains("connection.claimInterface(resolvedAudioControlInterface, true)"))
        assertTrue(adapter.contains("request.audioControl.isUac2AudioControl"))
        assertTrue(adapter.contains("connection.releaseInterface("))
        assertTrue(adapter.contains("probeClaim = false"))
        assertTrue(adapter.contains("UsbFsDiagnostics.readClock(connection.fileDescriptor, request)"))
        assertFalse(adapter.contains("controlTransfer("))
        assertFalse(adapter.contains("setInterface("))
        assertFalse(adapter.contains("bulkTransfer("))
        assertTrue(source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioClaimConnection.kt").contains("connection.claimInterface(iface, false)"))
    }

    @Test fun restoredBindingsAndSuccessfulReleaseMeanEnumerationOnly() {
        val result = run(Fake())
        assertEquals(true, result.releaseReturned)
        assertEquals(UsbKernelBindingRecovery.RESTORED, result.kernelRecovery)
        assertEquals(UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT, result.frameworkRecovery)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun kernelBindingsHaveTheirOwnDimensionWhenFrameworkCandidateIsMissing() {
        val result = run(Fake().apply { frameworkAfter = UsbAndroidAudioDiagnostics() })
        assertEquals(UsbKernelBindingRecovery.RESTORED, result.kernelRecovery)
        assertEquals(UsbFrameworkEnumerationRecovery.CANDIDATE_MISSING, result.frameworkRecovery)
        assertEquals(UsbRecoveryStatus.KERNEL_RECOVERED_FRAMEWORK_PENDING, result.recoveryStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun frameworkContinuityHasItsOwnDimensionWhenKernelBindingsAreMissing() {
        val result = run(Fake().apply { restored = false })
        assertEquals(UsbKernelBindingRecovery.NOT_RESTORED, result.kernelRecovery)
        assertEquals(UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT, result.frameworkRecovery)
        assertEquals(UsbRecoveryStatus.FRAMEWORK_PRESENT_KERNEL_UNCONFIRMED, result.recoveryStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun sameAudioDeviceIdCannotVerifyAudioRouting() {
        val result = run(Fake())
        assertEquals(result.before!!.androidAudio!!.outputs.single().id, result.afterAndroidAudio!!.outputs.single().id)
        assertEquals(UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT, result.frameworkRecovery)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun sameAlsaAddressCannotVerifyAudioRouting() {
        val result = run(Fake())
        assertEquals(result.before!!.androidAudio!!.outputs.single().safeAddress, result.afterAndroidAudio!!.outputs.single().safeAddress)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.recoveryStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun sameCapabilityArraysCannotVerifyAudioRouting() {
        val result = run(Fake())
        val before = result.before!!.androidAudio!!.outputs.single()
        val after = result.afterAndroidAudio!!.outputs.single()
        assertEquals(before.sampleRates, after.sampleRates)
        assertEquals(before.encodings, after.encodings)
        assertEquals(before.channelCounts, after.channelCounts)
        assertEquals(before.channelMasks, after.channelMasks)
        assertEquals(before.channelIndexMasks, after.channelIndexMasks)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun hidContinuityDoesNotVerifyUsbAudioEvenWithCompleteEnumeration() {
        val result = run(Fake())
        val before = result.before!!.driverBindings.single { it.iface.roles.contains(UsbFsInterfaceRole.HID) }
        val after = result.after!!.driverBindings.single { it.iface.number == before.iface.number }
        assertEquals(before.driver, after.driver)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
        assertTrue(report(result).contains("HID availability is independent evidence"))
    }

    @Test fun functionalDimensionCannotBeConstructedAsSuccessfulForAnyAutomatedOutcome() {
        assertEquals(listOf(UsbFunctionalAudioRecovery.NOT_VERIFIED), UsbFunctionalAudioRecovery.entries)
        listOf(Fake(), Fake().apply { claimResult = false }, Fake().apply { releaseResult = false },
            Fake().apply { restored = false }, Fake().apply { closeThrows = true }).forEach { fake ->
            assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, run(fake).functionalAudioRecovery)
        }
    }

    @Test fun failedFrameworkQueryIsIndeterminateRatherThanCandidateMissing() {
        val result = run(Fake().apply { frameworkAfter = UsbAndroidAudioDiagnostics(
            detail = "query failed", querySucceeded = false) })
        assertEquals(UsbFrameworkEnumerationRecovery.INDETERMINATE, result.frameworkRecovery)
        assertEquals(UsbKernelBindingRecovery.RESTORED, result.kernelRecovery)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun missingAddressAndMultipleFrameworkCandidatesAreExplicitlyAmbiguous() {
        listOf(UsbAndroidAudioDiagnostics(listOf(audio().copy(safeAddress = null))),
            UsbAndroidAudioDiagnostics(listOf(audio(), audio().copy(id = 99)))).forEach { framework ->
            val result = run(Fake().apply { frameworkAfter = framework })
            assertEquals(UsbFrameworkEnumerationRecovery.AMBIGUOUS, result.frameworkRecovery)
            assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
        }
    }

    @Test fun invalidatedEvidenceCannotRetainRestoredDimensionsButRemainsFunctionallyUnverified() {
        val result = run(Fake()).invalidate(UsbClockQueryStatus.STALE_GENERATION, "stale")
        assertEquals(UsbKernelBindingRecovery.INDETERMINATE, result.kernelRecovery)
        assertEquals(UsbFrameworkEnumerationRecovery.INDETERMINATE, result.frameworkRecovery)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun reportSeparatesEnumerationFromFunctionalRecoveryAndWarnsAfterClaim() {
        val report = report(run(Fake()))
        listOf("Kernel/framework recovery: ENUMERATION_RECOVERED", "Kernel bindings: RESTORED",
            "Android USB audio enumeration: CANDIDATE_PRESENT", "Functional audio recovery: NOT_VERIFIED",
            "do not prove that audio routing or DAC playback is functional", "Post-experiment warning:",
            "physically disconnect and reconnect the DAC before continuing", "same ID, ALSA address or capability arrays").forEach {
            assertTrue("Missing corrected interpretation: $it", report.contains(it))
        }
        assertFalse(report.contains("CLEAN_" + "RECOVERY"))
        assertFalse(report.contains("Clean recovery", ignoreCase = true))
    }

    @Test fun reportIdentifiesReconnectCaveatAsManualEvidenceForTestedEnvironmentOnly() {
        val report = report(run(Fake()))
        assertTrue(report.contains("known test evidence; not automatically detected by the app"))
        assertTrue(report.contains("tested FIIO KA15/Samsung environment"))
        assertTrue(report.contains("phone speaker despite restored kernel/framework enumeration"))
        assertTrue(report.contains("Physical disconnect/reconnect restored DAC playback"))
        assertTrue(report.contains("not a universal rule for USB DACs"))
    }

    @Test fun reportDeviceSideNoteDoesNotClaimResetOrIntentionalSettingsModification() {
        val report = report(run(Fake()))
        assertTrue(report.contains("Device-side state appeared to reinitialize during manual testing"))
        assertTrue(report.contains("cause was not established"))
        listOf("factory reset", "firmware reset", "settings were intentionally modified").forEach {
            assertFalse(report.contains(it, ignoreCase = true))
        }
    }

    @Test fun allTenDiscreteHardwareRatesKeepInitialFourSupportedDespiteFunctionalUncertainty() {
        val rates = listOf(44_100L, 48_000L, 88_200L, 96_000L, 176_400L, 192_000L, 352_800L,
            384_000L, 705_600L, 768_000L)
        val fake = Fake().apply { ranges = rates.map { UsbSamplingFrequencyRange(it, it, 0) } }
        val result = run(fake)
        val range = range(result)
        assertEquals(10, range.declaredSubrangeCount)
        assertEquals(rates, range.ranges.map { it.minimumHz })
        assertEquals(listOf(2, 122, 4, 1), fake.requests.map { it.length })
        assertTrue(result.clocks!!.results.all { it.status == UsbClockQueryStatus.SUCCESS })
        UsbAudioClockControl.milestoneRatesHz.forEach {
            assertEquals(UsbClockRateSupport.REPRESENTED, UsbAudioClockControl.assessRate(it, range))
        }
        val report = report(result)
        listOf("44100 Hz — CD milestone: SUPPORTED", "48000 Hz: SUPPORTED", "96000 Hz: SUPPORTED",
            "192000 Hz: SUPPORTED", "GET_RANGE sampling frequency: SUCCESS",
            "GET_CUR sampling frequency: SUCCESS", "GET_CUR clock validity: SUCCESS").forEach {
            assertTrue("Native findings must remain intact: $it", report.contains(it))
        }
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun recoveryInterpretationPreservesRawClockAndUsbfsDriverEvidence() {
        val original = run(Fake())
        fun bindingNames(snapshot: UsbFsOwnershipDiagnostics, owned: Boolean) = snapshot.copy(driverBindings =
            snapshot.driverBindings.map { binding -> binding.copy(
                result = UsbFsIoctlResult(UsbFsIoctlOperation.GETDRIVER, 0, 0, null),
                driver = when (binding.iface.number) { 4 -> "usbhid"; 7 -> if (owned) "usbfs" else "snd-usb-audio"; else -> "snd-usb-audio" }) })
        val result = original.copy(before = bindingNames(original.before!!, false),
            whileOwned = bindingNames(original.whileOwned!!, true),
            recoveryObservations = original.recoveryObservations.map { it.copy(snapshot = bindingNames(it.snapshot, false)) })
        assertSame(original.clocks, result.clocks)
        assertSame(original.clocks!!.results.first().attempts.first().nativeResult,
            result.clocks!!.results.first().attempts.first().nativeResult)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, UsbAudioForcedExperiment.classify(result))
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
        val report = report(result)
        listOf("driver=usbfs", "driver=snd-usb-audio", "driver=usbhid", "claimInterface result: true",
            "Android releaseInterface result: true", "USBDEVFS_CONTROL: ioctl return=").forEach { assertTrue(report.contains(it)) }
    }

    @Test fun uiHasIndependentResultsAndProminentReconnectWarningWithoutPlaybackTest() {
        val ui = source("java/io/github/rsgarrido/sazanami/ui/settings/UsbAudioDiagnosticsSection.kt")
        listOf("result.kernelRecovery", "result.frameworkRecovery", "usb_diagnostics_functional_not_verified",
            "usb_diagnostics_post_force_warning", "usb_diagnostics_manual_ka15_note", "if (result.claimReturned == true)").forEach {
            assertTrue(ui.contains(it))
        }
        listOf("AudioTrack(", "setPreferredDevice(", "emitTone(", "player.play(", "USBDEVFS_RESET").forEach {
            assertFalse(ui.contains(it))
        }
    }

    @Test fun interpretationModelHasNoResetRouteRepairOrPlaybackImplementation() {
        val model = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioForcedExperiment.kt")
        listOf("USBDEVFS_RESET", "USBDEVFS_CONNECT", "USBDEVFS_DISCONNECT", "AudioTrack(",
            "setPreferredDevice(", "submitUrb(", "bitPerfectActive").forEach { assertFalse(model.contains(it)) }
        assertFalse(UsbRecoveryStatus.entries.any { it.name.contains("CLEAN") || it.name.contains("PLAYBACK") })
    }

    private fun source(relative: String): String {
        val paths = listOf(Paths.get("src/main", relative), Paths.get("app/src/main", relative))
        return paths.first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    }

    private fun run(fake: Fake, selectedPlan: UsbFsProbePlan = plan, access: () -> UsbClockQueryStatus? = { null }) =
        runBlocking { UsbAudioForcedExperiment.run(selectedPlan, 3, fake, access) }
    private fun range(result: UsbAudioForcedExperimentResult) = result.clocks!!.results.single { it.query == UsbClockQuery.FREQUENCY_RANGE }
    private fun report(result: UsbAudioForcedExperimentResult) = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(
        generation = 3, forcedExperiment = result))
    private fun u32(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
    private fun audio() = UsbAndroidAudioOutput("Synthetic USB output", 22, 90, "card=3;device=0",
        listOf(48_000), listOf(2), listOf(2), listOf(3), listOf(3))

    private fun snapshot(selectedPlan: UsbFsProbePlan, owned: Boolean, restored: Boolean,
        missing: Int? = null, speedFails: Boolean = false): UsbFsOwnershipDiagnostics {
        val bindings = selectedPlan.interfaces.map {
            val noDriver = (it.number == 7 && (owned || !restored)) || it.number == missing
            UsbFsDriverBinding(it, UsbFsIoctlResult(UsbFsIoctlOperation.GETDRIVER, if (noDriver) -1 else 0,
                if (noDriver) 61 else 0, if (noDriver) "ENODATA" else null), if (noDriver) null else "baseline_driver_${it.number}")
        }
        return UsbFsOwnershipDiagnostics(3, selectedPlan, UsbFsProbeStatus.RECORDED, bindings,
            UsbFsSpeedEvidence(UsbFsIoctlResult(UsbFsIoctlOperation.GET_SPEED, if (speedFails) -1 else 3,
                if (speedFails) 25 else 0, if (speedFails) "ENOTTY" else null), if (speedFails) UsbFsSpeed.UNKNOWN else UsbFsSpeed.HIGH),
            androidAudio = UsbAndroidAudioDiagnostics(listOf(audio())))
    }

    private inner class Fake(private val selectedPlan: UsbFsProbePlan = plan) : UsbAudioForcedTransport {
        var claimResult = true
        var releaseResult = true
        var claimThrows = false
        var releaseThrows = false
        var closeThrows = false
        var ownedSnapshotThrows = false
        var baselineStatus = UsbFsProbeStatus.RECORDED
        var speedFails = false
        var owned = false
        var restored = true
        var restoredAtObservation = 1
        var missingAfterInterface: Int? = null
        var frameworkAfter = UsbAndroidAudioDiagnostics(listOf(audio()))
        var frequency = 48_000L
        var valid = true
        var ranges = listOf(UsbSamplingFrequencyRange(44_100, 44_100, 0))
        var nativeError: Pair<Int, String>? = null
        var headerOverride: ByteArray? = null
        var bodyOverride: ByteArray? = null
        var readException: Exception? = null
        var pauseCancellation = false
        var now = 0L
        private var beforeSeen = false
        private var afterCount = 0
        private var evidence: UsbFsControlEvidence? = null
        val events = mutableListOf<String>()
        val requests = mutableListOf<UsbClockReadRequest>()
        override fun interfaces() = facts
        override fun frameworkSnapshot() = frameworkAfter
        override fun snapshot(): UsbFsOwnershipDiagnostics {
            if (!beforeSeen) {
                beforeSeen = true
                events += "snapshot:before"
                return this@UsbAudioForcedExperimentTest.snapshot(selectedPlan, false, true, speedFails = speedFails).copy(status = baselineStatus)
            }
            if (owned) {
                events += "snapshot:owned"
                if (ownedSnapshotThrows) throw IllegalStateException("private path")
                return this@UsbAudioForcedExperimentTest.snapshot(selectedPlan, true, false)
            }
            events += "snapshot:after"
            afterCount++
            return this@UsbAudioForcedExperimentTest.snapshot(selectedPlan, false, restored && afterCount >= restoredAtObservation, missingAfterInterface)
                .copy(androidAudio = frameworkAfter)
        }
        override fun claim(request: UsbAudioForcedClaimRequest): Boolean {
            events += "claim:${request.audioControl.number}:${request.force}"
            if (claimThrows) throw SecurityException("private path")
            owned = claimResult
            return claimResult
        }
        override fun release(request: UsbAudioForcedClaimRequest): Boolean {
            events += "release:${request.audioControl.number}"
            owned = false
            if (releaseThrows) throw IllegalStateException("private path")
            return releaseResult
        }
        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
            check(owned)
            events += "read:${request.query}"
            requests += request
            readException?.let { throw it }
            nativeError?.let { (number, name) ->
                evidence = UsbFsControlEvidence(request.length, -1, number, name, emptyList())
                return -1
            }
            val body = byteArrayOf(ranges.size.toByte(), 0) + ranges.flatMap {
                (u32(it.minimumHz) + u32(it.maximumHz) + u32(it.resolutionHz)).toList()
            }.toByteArray()
            val bytes = when {
                request.rangeHeaderOnly -> headerOverride ?: byteArrayOf(ranges.size.toByte(), 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> bodyOverride ?: body
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(frequency)
                else -> byteArrayOf(if (valid) 1 else 0)
            }
            bytes.copyInto(buffer, endIndex = minOf(bytes.size, buffer.size))
            evidence = UsbFsControlEvidence(request.length, bytes.size, 0, null, bytes.map { it.toInt() and 0xff })
            return bytes.size
        }
        override fun lastControlEvidence() = evidence
        override fun close() {
            events += "close"
            owned = false
            if (closeThrows) throw IllegalStateException("private close")
        }
        override fun monotonicMs() = now
        override suspend fun pause(milliseconds: Long) {
            events += "pause:$milliseconds"
            if (pauseCancellation) throw CancellationException("cancel recovery")
            now += milliseconds
        }
    }

    private fun descriptorModel(): UsbAudioDescriptorModel {
        val entities = listOf(UsbAudioEntity(29, UsbAudioEntityKind.CLOCK_SOURCE, controls = 5),
            UsbAudioEntity(33, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(29), terminalType = 0x0101),
            UsbAudioEntity(41, UsbAudioEntityKind.FEATURE_UNIT, signalSources = listOf(33)),
            UsbAudioEntity(47, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(41), clockSources = listOf(29), terminalType = 0x0301))
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 7, 0), 0, 1, 1, 0x20,
            audioControl = UsbAudioControlDescriptor(0x0200, entities = entities))
        val streams = (1..3).map { UsbInterfaceDescriptor(UsbInterfaceKey(18, 8, it), 1, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(5, 5, 400, 1)),
            audioStreaming = UsbAudioStreamingDescriptor(33, 0, 1, 1, 2, 3), audioFormat = UsbAudioFormatDescriptor(1, 4, 24)) }
        return UsbAudioDescriptorModel(300, null, listOf(UsbConfigurationDescriptor(18, 1, 282, 3, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 7, 2, 1, 0, 0x20)),
            listOf(UsbInterfaceDescriptor(UsbInterfaceKey(18, 4, 0), 0, 3, 0, 0), ac) + streams, emptyList(), emptyList())
    }
}
