package io.github.rsgarrido.sazanami.player.usb

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic configuration, restoration and errno evidence only. No JNI loading or USB hardware.
 * Source checks constrain the narrow Android/native boundary; real ABI/DAC behavior needs manual verification.
 */
class UsbAudioConfigurationExperimentTest {
    @Test fun pcm24At96SharesAlternateRequiresFreshRangeAndBothReadbacksAndRestores192k() {
        val selected=UsbAudioConfigurationPlan.resolve(model,android,96000,24)
        val target=selected.plan!!
        assertEquals(UsbAudioConfigurationPlan.resolve(model,android,48000,24).plan!!.streaming,target.streaming)
        assertEquals(3,target.subslotBytes);assertEquals(24,target.validBits)
        val fake=Fake(target).apply { rate=192000 }
        val result=run(fake,selected)
        assertTrue(result.selectedFormatMatches(96000,2,24))
        assertFalse(result.selectedFormatMatches(96000,2,16))
        assertEquals(96000L,result.targetClockSet!!.request.rateHz)
        assertEquals(listOf(0,119,1,0),result.targetClockSet!!.request.payload)
        assertEquals(96000L,result.targetClockReadback!!.currentFrequencyHz)
        assertEquals(96000L,result.configuredClockReadback!!.currentFrequencyHz)
        assertEquals(listOf(96000L,192000L),fake.clockWrites)
        assertEquals(192000L,result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,result.restorationStatus)
        assertTrue(fake.events.indexOf("setClock:192000")<fake.events.indexOf("releaseAS:8"))
        assertTrue(fake.events.indexOf("releaseAS:8")<fake.events.indexOf("releaseAC:7"))
        for (bad in listOf(Fake(target).apply { rate=44100;applyTargetRate=false },
            Fake(target).apply { rateAfterTargetAlt=48000 })) {
            val mismatch=run(bad,selected)
            assertFalse(mismatch.selectedFormatMatches(96000,2,24))
            assertEquals(UsbTargetConfigurationStatus.FAILED,mismatch.targetStatus)
            assertEquals(UsbConfigurationRestorationStatus.RESTORED,mismatch.restorationStatus)
        }
        val excluded=Fake(target).apply { ranges=ranges.filter { it.minimumHz!=96000L } }
        val blocked=run(excluded,selected)
        assertEquals(UsbTargetConfigurationStatus.BLOCKED,blocked.targetStatus)
        assertTrue(excluded.clockWrites.isEmpty());assertFalse(excluded.events.any { it.startsWith("claimAS:") })
        for (format in listOf(UsbAudioFormatDescriptor(1,4,24),UsbAudioFormatDescriptor(1,3,16))) {
            val wrong=model.copy(interfaces=model.interfaces.map { if(it.audioFormat?.validBits==24) it.copy(audioFormat=format) else it })
            assertNull(UsbAudioConfigurationPlan.resolve(wrong,androidFacts(wrong),96000,24).plan)
        }
        assertNull(UsbAudioConfigurationPlan.resolve(model,android,96000,16).plan)
    }
    @Test fun pcm24SelectionIsDescriptorResolvedAndFourByteSubslotsAreRejected() {
        val selected=UsbAudioConfigurationPlan.resolve(model,android,48000,24)
        assertEquals(6,selected.plan!!.streaming.alternate);assertEquals(3,selected.plan!!.subslotBytes)
        assertEquals(24,selected.plan!!.validBits);assertEquals(6,selected.plan!!.pcmFormat.bytesPerFrame)
        val result=run(Fake(selected.plan!!).apply { rate=192000 },selected)
        assertTrue(result.selectedFormatMatches(48000,2,24))
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,result.restorationStatus)
        assertEquals(192000L,result.originalClockReadback!!.currentFrequencyHz)
        assertFalse(result.selectedFormatMatches(48000,2,16))
        val wrong=model.copy(interfaces=model.interfaces.map { if(it.audioFormat?.validBits==24)
            it.copy(audioFormat=UsbAudioFormatDescriptor(1,4,24)) else it })
        assertNull(UsbAudioConfigurationPlan.resolve(wrong,androidFacts(wrong),48000,24).plan)
        assertNull(UsbAudioConfigurationPlan.resolve(model,android,44100,24).plan)
    }
    @Test fun selected48000UsesSamePcm16AlternateAndRestoresArbitraryCapturedRate() {
        val selected=UsbAudioConfigurationPlan.resolve(model,android,48000)
        assertEquals(plan.streaming,selected.plan!!.streaming)
        val fake=Fake(selected.plan!!).apply { rate=192000 }
        val result=run(fake,selected)
        assertTrue(result.selectedRateMatches(48000))
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,result.restorationStatus)
        assertEquals(192000L,result.originalClockRateHz)
        assertEquals(192000L,result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(listOf(48000L,192000L),fake.clockWrites)
        assertTrue(fake.events.indexOf("setClock:192000")<fake.events.indexOf("releaseAS:8"))
        assertTrue(fake.events.indexOf("releaseAS:8")<fake.events.indexOf("releaseAC:7"))
        val ignored=Fake(selected.plan!!).apply { rate=44100;applyTargetRate=false }
        val mismatch=run(ignored,selected)
        assertFalse(mismatch.selectedRateMatches(48000))
        assertEquals(UsbTargetConfigurationStatus.FAILED,mismatch.targetStatus)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED,mismatch.restorationStatus)
    }
    private val model = descriptorModel()
    private val android = androidFacts(model)
    private val resolution = UsbAudioConfigurationPlan.resolve(model, android)
    private val plan = resolution.plan!!

    @Test fun selectsOnlyStereoTypeOnePcmTwoByteSixteenBitAlternate() {
        assertEquals(7, plan.clock.audioControl.number)
        assertEquals(8, plan.streaming.number)
        assertEquals(5, plan.streaming.alternate)
        assertEquals(29, plan.clock.clockEntityId)
        assertEquals(44_100L, plan.sampleRateHz)
        assertEquals(2, plan.channels)
        assertEquals(2, plan.subslotBytes)
        assertEquals(16, plan.validBits)
        assertEquals(0, plan.idle.alternate)
    }

    @Test fun rejectsNonPcmAndMixedFormatBitmap() {
        listOf(2L, 3L, 4L).forEach { bitmap ->
            assertNull(resolve(changeTarget { it.copy(audioStreaming = it.audioStreaming!!.copy(formats = bitmap)) }).plan)
        }
    }

    @Test fun rejectsWrongChannelsSubslotBitsOrFormatType() {
        val variants = listOf(changeTarget { it.copy(audioStreaming = it.audioStreaming!!.copy(channelCount = 1)) },
            changeTarget { it.copy(audioFormat = UsbAudioFormatDescriptor(1, 3, 16)) },
            changeTarget { it.copy(audioFormat = UsbAudioFormatDescriptor(1, 2, 24)) },
            changeTarget { it.copy(audioFormat = UsbAudioFormatDescriptor(2, 2, 16)) })
        variants.forEach { assertNull(resolve(it).plan) }
    }

    @Test fun ambiguousMatchingAlternatesBlockAllOwnershipAndWrites() {
        val target = model.interfaces.single { it.key == plan.streaming }
        val ambiguous = model.copy(interfaces = model.interfaces + target.copy(key = target.key.copy(alternate = 9)))
        val failed = resolve(ambiguous)
        assertNull(failed.plan)
        val fake = Fake()
        assertEquals(UsbTargetConfigurationStatus.BLOCKED, run(fake, failed).targetStatus)
        assertEquals(listOf("close"), fake.events)
    }

    @Test fun ambiguousClockSourceOrFunctionAssociationBlocksResolution() {
        assertNull(resolve(model.copy(associations = emptyList())).plan)
        val ac = model.interfaces.single { it.isAudioControl }
        val entities = ac.audioControl!!.entities.map { if (it.kind == UsbAudioEntityKind.OUTPUT_TERMINAL)
            it.copy(clockSources = listOf(30)) else it } + UsbAudioEntity(30, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7)
        assertNull(resolve(model.copy(interfaces = model.interfaces.map { if (it == ac)
            it.copy(audioControl = ac.audioControl.copy(entities = entities)) else it })).plan)
    }

    @Test fun parserWarningsAndReadonlyClockBlockForceOwnership() {
        assertNull(resolve(model.copy(warnings = listOf("truncated descriptor"))).plan)
        val readonly = model.copy(interfaces = model.interfaces.map { iface -> if (iface.isAudioControl)
            iface.copy(audioControl = iface.audioControl!!.copy(entities = iface.audioControl.entities.map {
                if (it.kind == UsbAudioEntityKind.CLOCK_SOURCE) it.copy(controls = 5) else it })) else iface })
        assertNull(resolve(readonly).plan)
    }

    @Test fun missingIdleDescriptorOrAndroidHandlesBlocksTarget() {
        assertNull(resolve(model.copy(interfaces = model.interfaces.filterNot { it.key == plan.idle })).plan)
        assertNull(UsbAudioConfigurationPlan.resolve(model, android.filterNot { it.number == 7 }).plan)
        assertNull(UsbAudioConfigurationPlan.resolve(model, android.filterNot { it.number == 8 && it.alternate == 0 }).plan)
        assertNull(UsbAudioConfigurationPlan.resolve(model, android.filterNot { it.number == 8 && it.alternate == 5 }).plan)
    }

    @Test fun anotherAsInterfaceInSameFunctionBlocksSharedClockMutation() {
        val expanded = model.copy(associations = model.associations.map { it.copy(interfaceCount = 3) },
            interfaces = model.interfaces + UsbInterfaceDescriptor(UsbInterfaceKey(18, 9, 0), 0, 1, 2, 0x20))
        val result = resolve(expanded)
        assertNull(result.plan)
        assertTrue(result.issues.any { it.contains("unclaimed streams could share") })
    }

    @Test fun nonIsochronousOrInputOnlyEndpointCannotResolvePlaybackTarget() {
        assertNull(resolve(changeTarget { it.copy(endpoints = listOf(UsbEndpointDescriptor(5, 2, 240, 1))) }).plan)
        assertNull(resolve(changeTarget { it.copy(endpoints = listOf(UsbEndpointDescriptor(0x85, 5, 240, 1))) }).plan)
    }

    @Test fun nativeBaselineFailureStopsBeforeForceOwnership() {
        val fake = Fake().apply { baselineStatus = UsbFsProbeStatus.NATIVE_UNAVAILABLE }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.BASELINE, result.failedStep)
        assertEquals(listOf("snapshot", "close"), fake.events)
    }

    @Test fun claimsAcBeforeFreshPreflightThenAs() {
        val fake = Fake()
        val result = run(fake)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        val acIndex = fake.events.indexOf("claimAC:7:true")
        val asIndex = fake.events.indexOf("claimAS:8:true")
        assertTrue(acIndex < fake.events.indexOf("read:FREQUENCY_RANGE"))
        assertTrue(fake.events.indexOf("read:CLOCK_VALIDITY") < asIndex)
        assertTrue(asIndex < fake.events.indexOf("setClock:44100"))
    }

    @Test fun acClaimFalsePreventsPreflightAsAndWrites() {
        val fake = Fake().apply { acClaimResult = false }
        val result = run(fake)
        assertEquals(false, result.acClaim.returned)
        assertFalse(result.asClaim.attempted)
        assertTrue(fake.readRequests.isEmpty())
        assertTrue(fake.clockWrites.isEmpty())
        assertTrue(fake.alternates.isEmpty())
        assertFalse(result.acRelease.attempted)
    }

    @Test fun acClaimExceptionIsRecordedAndNeverClaimsAs() {
        val result = run(Fake().apply { acClaimThrows = true })
        assertEquals("IllegalStateException", result.acClaim.exceptionType)
        assertFalse(result.asClaim.attempted)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
    }

    @Test fun freshRangeWithout44100PreventsAsAndMutation() {
        val fake = Fake().apply { ranges = listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0)) }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.PREFLIGHT, result.failedStep)
        assertFalse(result.asClaim.attempted)
        assertTrue(fake.clockWrites.isEmpty())
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun failedOrMalformedRangePreventsAsAndMutation() {
        listOf(Fake().apply { rangeError = 32 to "EPIPE" }, Fake().apply { malformedRange = true }).forEach { fake ->
            val result = run(fake)
            assertEquals(UsbConfigurationStep.PREFLIGHT, result.failedStep)
            assertTrue(fake.clockWrites.isEmpty())
            assertFalse(result.asClaim.attempted)
        }
    }

    @Test fun originalCurFailurePreventsMutationAndLeavesOriginalUnknown() {
        val fake = Fake().apply { preflightFrequencyError = true }
        val result = run(fake)
        assertNull(result.originalClockRateHz)
        assertFalse(result.asClaim.attempted)
        assertTrue(fake.clockWrites.isEmpty())
    }

    @Test fun originalRateMustBePositiveAndRepresentedByFreshRange() {
        listOf(0L, 123_456L).forEach { original ->
            val fake = Fake().apply { rate = original }
            val result = run(fake)
            assertEquals(UsbConfigurationStep.PREFLIGHT, result.failedStep)
            assertTrue(fake.clockWrites.isEmpty())
        }
    }

    @Test fun originalRateIsCapturedFromCurrentAttachmentNotFrameworkOrHistory() {
        val fake = Fake().apply { rate = 96_000L }
        val result = run(fake)
        assertEquals(96_000L, result.originalClockRateHz)
        assertEquals(listOf(44_100L, 96_000L), fake.clockWrites)
        assertEquals(96_000L, fake.rate)
    }

    @Test fun asClaimFalseOrExceptionPreventsClockAndAlternateMutation() {
        listOf(Fake().apply { asClaimResult = false }, Fake().apply { asClaimThrows = true }).forEach { fake ->
            val result = run(fake)
            assertEquals(UsbConfigurationStep.AS_CLAIM, result.failedStep)
            assertTrue(fake.clockWrites.isEmpty())
            assertTrue(fake.alternates.isEmpty())
            assertFalse(result.asRelease.attempted)
            assertEquals(true, result.acRelease.returned)
        }
    }

    @Test fun onlyResolvedAudioInterfacesAreClaimedNeverHid() {
        val fake = Fake()
        run(fake)
        assertEquals(listOf("claimAC:7:true", "claimAS:8:true"), fake.events.filter { it.startsWith("claim") })
        assertTrue(fake.snapshots.all { it.driverBindings.single { binding -> binding.iface.number == 4 }.driver == "test_hid_driver" })
    }

    @Test fun getInterfaceUsesStandardInterfaceReadEncoding() {
        val request = UsbConfigurationControlRequest.getInterface(plan)
        assertEquals(0x81, request.requestType)
        assertEquals(0x0A, request.request)
        assertEquals(0, request.value)
        assertEquals(8, request.index)
        assertEquals(1, request.length)
        assertTrue(request.payload.isEmpty())
    }

    @Test fun initialNonIdleQueryIsAdvisoryAndExplicitIdleReplacesIt() {
        val fake = Fake().apply { alt = 6 }
        val result = run(fake)
        assertEquals(6, result.originalAlternate!!.alternate)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(listOf(0, 5, 0), fake.alternates)
        assertEquals(0, fake.alt)
    }

    @Test fun failedOrShortInitialQueryDoesNotReplaceExplicitIdleEvidence() {
        listOf(Fake().apply { getAltErrorAt = 1 }, Fake().apply { getAltShortAt = 1 }).forEach { fake ->
            assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, run(fake).targetStatus)
        }
    }

    @Test fun targetSetCurEncodingAnd44100LittleEndianPayloadAreExact() {
        val result = run(Fake())
        val request = result.targetClockSet!!.request
        assertEquals(0x21, request.requestType)
        assertEquals(1, request.request)
        assertEquals(0x0100, request.value)
        assertEquals(0x1D07, request.index)
        assertEquals(4, request.length)
        assertEquals(listOf(0x44, 0xAC, 0, 0), request.payload)
        assertEquals(44_100L, request.rateHz)
    }

    @Test fun restorationPayloadIsDerivedFromOriginalUnsignedRate() {
        val result = run(Fake().apply { rate = 96_000L })
        assertEquals(96_000L, result.originalClockRestore!!.request.rateHz)
        assertEquals(listOf(0x00, 0x77, 0x01, 0x00), result.originalClockRestore.request.payload)
    }

    @Test fun failedSetCurPreventsTargetAlternateAndStillRestoresPossibleMutation() {
        val fake = Fake().apply { targetWriteError = 32 to "EPIPE" }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.STALL_LIKE, result.targetClockSet!!.status)
        assertTrue(result.clockWriteAttempted)
        assertFalse(result.alternateSelectionAttempted)
        assertEquals(listOf(44_100L, 48_000L), fake.clockWrites)
        assertEquals(48_000L, fake.rate)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun shortSetCurIsNotSuccessfulAndPayloadAndLengthRemainEvidence() {
        val result = run(Fake().apply { targetWriteLength = 3 })
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, result.targetClockSet!!.status)
        assertEquals(3, result.targetClockSet.native!!.returnValue)
        assertEquals(4, result.targetClockSet.native.requestedLength)
        assertEquals(listOf(0x44, 0xAC, 0, 0), result.targetClockSet.request.payload)
        assertFalse(result.alternateSelectionAttempted)
    }

    @Test fun nativeWriteErrnoCategoriesRemainDistinct() {
        listOf(Triple(32, "EPIPE", UsbClockQueryStatus.STALL_LIKE), Triple(16, "EBUSY", UsbClockQueryStatus.INTERFACE_BUSY),
            Triple(22, "EINVAL", UsbClockQueryStatus.INVALID_REQUEST), Triple(5, "EIO", UsbClockQueryStatus.TRANSPORT_ERROR),
            Triple(1, "EPERM", UsbClockQueryStatus.PERMISSION_LOST), Triple(13, "EACCES", UsbClockQueryStatus.PERMISSION_LOST)).forEach { (number, name, status) ->
            val result = run(Fake().apply { targetWriteError = number to name })
            assertEquals(number, result.targetClockSet!!.native!!.errno)
            assertEquals(name, result.targetClockSet.native.errnoName)
            assertEquals(status, result.targetClockSet.status)
        }
    }

    @Test fun curReadbackMustEqual44100BeforeAlternateSelection() {
        val fake = Fake().apply { applyTargetRate = false }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.CLOCK_READBACK, result.failedStep)
        assertEquals(48_000L, result.targetClockReadback!!.currentFrequencyHz)
        assertEquals(listOf(0, 0), fake.alternates)
    }

    @Test fun validityBecomesTrueBeforeSelectingTargetAlternate() {
        val fake = Fake().apply { targetInvalidReads = 2 }
        val result = run(fake)
        assertEquals(listOf(false, false, true), result.targetValidity.map { it.result.clockValid })
        assertEquals(listOf(0L, 50L, 100L), result.targetValidity.map { it.elapsedMs })
        assertTrue(fake.events.indexOf("setAlt:5") > fake.events.indexOf("pause:50"))
    }

    @Test fun invalidClockPollingEndsWithinOneSecondAndNeverSelectsAlternate() {
        val fake = Fake().apply { targetInvalidReads = 100 }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.CLOCK_VALIDITY, result.failedStep)
        assertEquals(21, result.targetValidity.size)
        assertEquals(1_000L, result.targetValidity.last().elapsedMs)
        assertFalse(result.alternateSelectionAttempted)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun failedValidityReadStopsPollingWithoutTreatingItAsUnsupported() {
        val fake = Fake().apply { targetValidityError = true }
        val result = run(fake)
        assertEquals(1, result.targetValidity.size)
        assertEquals(UsbClockQueryStatus.STALL_LIKE, result.targetValidity.single().result.status)
        assertEquals(listOf(0, 0), fake.alternates)
    }

    @Test fun alternateSelectionUsesResolvedAsAndAlternateFiveNotHardcodedOne() {
        val result = run(Fake())
        assertEquals(8, result.targetAlternateSet!!.request.plan.streaming.number)
        assertEquals(5, result.targetAlternateSet.request.alternate)
        assertEquals(5, result.targetAlternateReadback!!.alternate)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
    }

    @Test fun failedTargetAlternateStillAttemptsIdleAndOriginalClockRestoration() {
        val fake = Fake().apply { targetAltError = true }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.ALTERNATE_SET, result.failedStep)
        assertEquals(listOf(0, 5, 0), fake.alternates)
        assertEquals(0, result.idleRestorationReadback!!.alternate)
        assertEquals(48_000L, fake.rate)
    }

    @Test fun successfulSetInterfaceWithoutMatchingGetInterfaceIsNotVerified() {
        val fake = Fake().apply { applyTargetAlt = false }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.SUCCESS, result.targetAlternateSet!!.status)
        assertEquals(0, result.targetAlternateReadback!!.alternate)
        assertEquals(UsbConfigurationStep.ALTERNATE_READBACK, result.failedStep)
        assertEquals(UsbTargetConfigurationStatus.FAILED, result.targetStatus)
    }

    @Test fun configuredClockAndValidityAreReadWhileTargetAlternateIsActive() {
        val result = run(Fake())
        assertEquals(44_100L, result.configuredClockReadback!!.currentFrequencyHz)
        assertEquals(true, result.configuredClockValidity!!.clockValid)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
    }

    @Test fun clockChangingAtActiveAlternateFailsConfigurationButRestores() {
        val fake = Fake().apply { rateAfterTargetAlt = 96_000L }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.CONFIGURED_CLOCK, result.failedStep)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
        assertEquals(48_000L, fake.rate)
    }

    @Test fun invalidClockAtActiveAlternateFailsConfigurationButRestores() {
        val result = run(Fake().apply { configuredValid = false })
        assertEquals(UsbConfigurationStep.CONFIGURED_VALIDITY, result.failedStep)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun noEndpointOperationsExistInConfigurationTransportContract() {
        val methods = UsbAudioConfigurationTransport::class.java.methods.map { it.name }
        listOf("bulkTransfer", "submitUrb", "readFeedback", "writePcm", "writeEndpoint", "readEndpoint").forEach {
            assertFalse(methods.contains(it))
        }
    }

    @Test fun successfulConfigurationDoesNotClaimPlaybackOrBitPerfectActive() {
        val result = run(Fake())
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
        assertFalse(result.javaClass.declaredFields.any { it.name.contains("bitPerfectActive", ignoreCase = true) })
        assertTrue(report(result).contains("Bit-perfect active: NO"))
    }

    @Test fun cleanupSetsAndVerifiesIdleBeforeRestoringOriginalRate() {
        val fake = Fake()
        val result = run(fake)
        assertEquals(0, result.idleAlternateRestore!!.request.alternate)
        assertEquals(0, result.idleRestorationReadback!!.alternate)
        assertTrue(fake.events.lastIndexOf("setAlt:0") < fake.events.indexOf("setClock:48000"))
        assertTrue(fake.events.lastIndexOf("setAlt:0") > fake.events.indexOf("setAlt:5"))
        val restoreIndex = fake.events.indexOf("setClock:48000")
        assertEquals("getAlt:0", fake.events[restoreIndex - 1])
    }

    @Test fun idleRestoreFailurePreventsClockRestoreWhenIdleCannotBeVerified() {
        val fake = Fake().apply { idleAltError = true; applyIdleAlt = false }
        val result = run(fake)
        assertEquals(5, result.idleRestorationReadback!!.alternate)
        assertNull(result.originalClockRestore)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun negativeIdleSetterDoesNotPermitClockRestoreEvenIfQueryReportsIdle() {
        val fake = Fake().apply { idleAltError = true }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.STALL_LIKE, result.idleAlternateRestore!!.status)
        assertEquals(0, result.idleRestorationReadback!!.alternate)
        assertNull(result.originalClockRestore)
        assertEquals(UsbConfigurationRestorationStatus.FAILED, result.restorationStatus)
    }

    @Test fun originalRateAlready44100SkipsRedundantRestoreWriteButVerifiesIt() {
        val fake = Fake().apply { rate = 44_100L }
        val result = run(fake)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertNull(result.originalClockRestore)
        assertEquals(44_100L, result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(true, result.originalClockValidity.last().result.clockValid)
        assertTrue(result.restorationNotes.any { it.contains("redundant restore SET_CUR skipped") })
    }

    @Test fun restoreSetCurReadbackAndValidityAreSeparateRequiredEvidence() {
        val result = run(Fake())
        assertEquals(48_000L, result.originalClockRestore!!.request.rateHz)
        assertEquals(48_000L, result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(true, result.originalClockValidity.last().result.clockValid)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun wrongRestoredRateRemainsVisibleIndependentlyOfForwardSuccess() {
        val result = run(Fake().apply { applyRestoreRate = false })
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(44_100L, result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(UsbConfigurationRestorationStatus.PARTIAL, result.restorationStatus)
    }

    @Test fun failedRestoreWriteDoesNotOverwriteVerifiedTarget() {
        val result = run(Fake().apply { restoreWriteError = true })
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(UsbClockQueryStatus.STALL_LIKE, result.originalClockRestore!!.status)
        assertEquals(UsbConfigurationRestorationStatus.PARTIAL, result.restorationStatus)
        assertTrue(report(result).contains("Target configuration: TARGET_CONFIGURATION_VERIFIED"))
        assertTrue(report(result).contains("Configuration restoration: PARTIAL"))
    }

    @Test fun invalidRestoredClockValidityIsBoundedAndSeparateFromTargetSuccess() {
        val result = run(Fake().apply { restoredValid = false })
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(21, result.originalClockValidity.size)
        assertEquals(UsbConfigurationRestorationStatus.PARTIAL, result.restorationStatus)
    }

    @Test fun reverseReleaseOrderOccursAfterConfigurationRestoration() {
        val fake = Fake()
        run(fake)
        assertTrue(fake.events.indexOf("setClock:48000") < fake.events.indexOf("releaseAS:8"))
        assertTrue(fake.events.indexOf("releaseAS:8") < fake.events.indexOf("releaseAC:7"))
        assertTrue(fake.events.indexOf("releaseAC:7") < fake.events.indexOf("close"))
    }

    @Test fun releaseFailureAndExceptionStillAttemptOtherReleaseAndClose() {
        listOf(Fake().apply { asReleaseResult = false }, Fake().apply { asReleaseThrows = true },
            Fake().apply { acReleaseThrows = true }).forEach { fake ->
            val result = run(fake)
            assertTrue(result.asRelease.attempted)
            assertTrue(result.acRelease.attempted)
            assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
        }
    }

    @Test fun failedReadAfterBothClaimsStillReleasesBoth() {
        val result = run(Fake().apply { getAltErrorAt = 1 })
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
    }

    @Test fun nativeWriteExceptionMayHaveMutatedClockSoRestorationStillRuns() {
        val fake = Fake().apply { throwTargetWrite = true }
        val result = run(fake)
        assertEquals("IllegalStateException", result.targetClockSet!!.exceptionType)
        assertEquals(48_000L, fake.rate)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun cancellationAfterClockMutationRestoresOriginalThenReleasesAndCloses() {
        val fake = Fake().apply { cancelTargetWrite = true }
        try { run(fake); throw AssertionError("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(48_000L, fake.rate)
        assertTrue(fake.events.indexOf("setClock:48000") < fake.events.indexOf("releaseAS:8"))
        assertEquals("close", fake.events.last())
    }

    @Test fun cancellationAtActiveAlternateRestoresIdleAndOriginalRate() {
        val fake = Fake().apply { cancelConfiguredRead = true }
        try { run(fake); throw AssertionError("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(listOf(0, 5, 0), fake.alternates)
        assertEquals(0, fake.alt)
        assertEquals(48_000L, fake.rate)
        assertTrue(fake.events.contains("releaseAS:8"))
        assertTrue(fake.events.contains("releaseAC:7"))
    }

    @Test fun actualCoroutineCancellationDoesNotSkipRestoration() {
        val fake = Fake()
        try {
            runBlocking {
                val job = currentCoroutineContext()[Job]!!
                fake.afterTargetWrite = { job.cancel() }
                UsbAudioConfigurationExperiment.run(resolution, 3, fake)
            }
            throw AssertionError("Cancellation must propagate")
        } catch (_: CancellationException) { }
        assertEquals(48_000L, fake.rate)
        assertTrue(fake.events.contains("releaseAS:8"))
        assertEquals("close", fake.events.last())
    }

    @Test fun nativeEnodevStopsAllSpeculativeForwardAndRestorationWrites() {
        val fake = Fake().apply { targetWriteError = 19 to "ENODEV" }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.DEVICE_DISCONNECTED, result.accessFailure)
        assertEquals(UsbTargetConfigurationStatus.NON_AUTHORITATIVE, result.targetStatus)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertEquals(listOf(0), fake.alternates)
        assertEquals(UsbConfigurationRestorationStatus.INDETERMINATE, result.restorationStatus)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun permissionLossAfterMutationBlocksFurtherWritesAndInvalidatesAuthority() {
        val fake = Fake().apply { targetWriteError = 13 to "EACCES" }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.PERMISSION_LOST, result.accessFailure)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertEquals(UsbKernelBindingRecovery.INDETERMINATE, result.kernelRecovery)
    }

    @Test fun physicalDetachAfterMutationStopsFurtherWritesButReleasesOriginalHandles() {
        val fake = Fake()
        var detached = false
        fake.afterTargetWrite = { detached = true }
        val result = run(fake) { if (detached) UsbClockQueryStatus.DEVICE_DISCONNECTED else null }
        assertEquals(UsbClockQueryStatus.DEVICE_DISCONNECTED, result.accessFailure)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
        assertEquals(UsbConfigurationRestorationStatus.INDETERMINATE,result.restorationStatus)
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED,result.closeStatus)
        assertEquals("close", fake.events.last())
    }

    @Test fun nativeBadFdStopsSpeculativeRestorationWrites() {
        val fake = Fake().apply { targetWriteError = 9 to "EBADF" }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.TRANSPORT_ERROR, result.accessFailure)
        assertEquals(UsbTargetConfigurationStatus.NON_AUTHORITATIVE, result.targetStatus)
        assertEquals(listOf(44_100L), fake.clockWrites)
    }

    @Test fun invalidFdAfterMutationSkipsReleaseIoAndStillAttemptsClose() {
        val fake = Fake()
        fake.afterTargetWrite = { fake.fdValid = false }
        val result = run(fake)
        assertFalse(result.asRelease.attempted)
        assertFalse(result.acRelease.attempted)
        assertTrue(result.asRelease.note!!.contains("Invalid fd"))
        assertEquals(UsbAudioCleanupStatus.SUCCEEDED, result.closeStatus)
        assertEquals(listOf(44_100L), fake.clockWrites)
    }

    @Test fun staleGenerationAfterMutationCannotConfigureOrPublishNewAttachment() {
        val fake = Fake()
        var stale = false
        fake.afterTargetWrite = { stale = true }
        val result = run(fake) { if (stale) UsbClockQueryStatus.STALE_GENERATION else null }
        assertEquals(UsbClockQueryStatus.STALE_GENERATION, result.accessFailure)
        assertEquals(listOf(44_100L), fake.clockWrites)
        val report = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 4, configurationExperiment = result))
        assertTrue(report.contains("Stale generation; previous Session 6 transaction evidence omitted"))
        assertFalse(report.contains("payload: 44 AC 00 00"))
    }

    @Test fun invalidFdSkipsUnsafeConfigurationAndStillClosesConnection() {
        val fake = Fake().apply { fdValid = false }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.TRANSPORT_ERROR, result.accessFailure)
        assertEquals(listOf("close"), fake.events)
    }

    @Test fun enumerationRecoveryNeverVerifiesFunctionalAudio() {
        val result = run(Fake())
        assertEquals(UsbKernelBindingRecovery.RESTORED, result.kernelRecovery)
        assertEquals(UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT, result.frameworkRecovery)
        assertEquals(UsbRecoveryStatus.ENUMERATION_RECOVERED, result.enumerationRecovery!!.recoveryStatus)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun frameworkFailureRemainsSeparateFromConfigurationSuccess() {
        val result = run(Fake().apply { finalFramework = UsbAndroidAudioDiagnostics(querySucceeded = false) })
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        assertEquals(UsbFrameworkEnumerationRecovery.INDETERMINATE, result.frameworkRecovery)
        assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalAudioRecovery)
    }

    @Test fun reportContainsConfigurationRestorationRawEvidenceAndReconnectCaveat() {
        val report = report(run(Fake()))
        listOf("USB Configuration Experiment — Session 6", "sample rate=44100 Hz", "subslot=2 bytes; valid bits=16",
            "Original clock captured: 48000 Hz", "payload: 44 AC 00 00", "wIndex=0x1D07",
            "USBDEVFS_SETINTERFACE: interface=8; alternate=5", "decoded alternate=5", "decoded alternate=0",
            "Data endpoint used: NO", "Feedback endpoint used: NO", "Audio transmitted: NO",
            "Functional USB audio recovery: NOT_VERIFIED", "physical DAC disconnect/reconnect may be required",
            "tested FIIO KA15/Samsung environment", "Bit-perfect active: NO").forEach { assertTrue("Missing: $it", report.contains(it)) }
        assertFalse(report.contains("CLEAN_" + "RECOVERY"))
        assertFalse(report.contains("factory reset", ignoreCase = true))
    }

    @Test fun reportDoesNotExposePrivateBusKeySerialOrExceptionMessage() {
        val result = run(Fake().apply { throwTargetWrite = true })
        val report = report(result)
        assertFalse(report.contains("private-device-path"))
        assertFalse(report.contains("private exception detail"))
        assertTrue(report.contains("exception type=IllegalStateException"))
    }

    @Test fun busyStateSerializesConfigurationWithExistingDiagnosticActions() {
        val state = UsbAudioDiagnosticState(configurationExperiment = UsbAudioConfigurationResult(3, resolution, UsbConfigurationPhase.QUERYING))
        assertTrue(state.diagnosticOperationRunning)
        assertFalse(state.copy(configurationExperiment = run(Fake())).diagnosticOperationRunning)
    }

    @Test fun nativeBoundaryUsesOnlyApprovedSpecificOperationsAndBorrowedFd() {
        val native = source("cpp/usb_fs_diagnostics.cpp")
        val clockWrite = native.substringAfter("UsbFsDiagnostics_nativeSetClockFrequency(").substringBefore("// No detach/reset operation:")
        assertTrue(clockWrite.contains("control.bRequestType = 0x21"))
        assertTrue(clockWrite.contains("control.bRequest = 0x01"))
        assertTrue(clockWrite.contains("control.wValue = 0x0100"))
        assertTrue(clockWrite.contains("control.wLength = 4"))
        assertTrue(clockWrite.contains("!sazanami::directpcm::supportsRate(static_cast<uint32_t>(requestedRateHz)) && requestedRateHz != originalRateHz"))
        assertTrue(clockWrite.contains("requestedRateHz < 1 || requestedRateHz > 0xffffffffLL"))
        val returned = clockWrite.substringAfter("const int returned = ::ioctl(borrowedFd, USBDEVFS_CONTROL, &control);")
        assertTrue(returned.trimStart().startsWith("const int savedError = returned < 0 ? errno : 0;"))
        val alt = native.substringAfter("UsbFsDiagnostics_nativeSetAudioStreamingAlternate(")
        assertTrue(alt.contains("requestedAlternate != idleAlternate && requestedAlternate != targetAlternate"))
        assertTrue(alt.contains("selection.interface = static_cast<unsigned int>(asNumber)"))
        assertTrue(alt.substringAfter("::ioctl(borrowedFd, USBDEVFS_SETINTERFACE, &selection);").trimStart()
            .startsWith("const int savedError = returned < 0 ? errno : 0;"))
        assertFalse(native.contains("::close("))
        assertFalse(native.contains("::dup("))
        listOf("USBDEVFS_RESET", "USBDEVFS_CONNECT", "USBDEVFS_DISCONNECT", "USBDEVFS_SUBMITURB", "USBDEVFS_REAPURB", "USBDEVFS_DISCARDURB").forEach {
            assertFalse(native.contains(it))
        }
    }

    @Test fun androidAdapterUsesResolvedAsForceTrueAndAndroidReleaseWithoutEndpointApis() {
        val adapter = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioConfigurationConnection.kt")
        assertTrue(adapter.contains("connection.claimInterface(resolvedAudioStreamingInterface, true)"))
        assertTrue(adapter.contains("connection.releaseInterface(streamingHandle)"))
        assertTrue(adapter.contains("request === plan.asClaim"))
        listOf("bulkTransfer(", "UsbRequest", "submitUrb(", "AudioTrack(", "Media3", "Feature Unit", "sysfs").forEach {
            assertFalse(adapter.contains(it))
        }
    }

    @Test fun uiIsExplicitUserActionWithNoAutomaticPlaybackOrMixerIntegration() {
        val ui = source("java/io/github/rsgarrido/sazanami/ui/settings/UsbAudioDiagnosticsSection.kt")
        assertTrue(ui.contains("UsbAudioDiagnosticBridge.configurePcm16(selected.identity)"))
        assertTrue(ui.contains("usb_diagnostics_configure_note"))
        assertTrue(ui.contains("usb_diagnostics_configuration_reconnect"))
        val transaction = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioConfigurationExperiment.kt")
        listOf("AudioMixerAttributes", "MIXER_BEHAVIOR_BIT_PERFECT", "setPreferredMixerAttributes", "AudioSink",
            "AudioTrack(", "readFeedback", "writePcm", "UsbRequest", "sysfs").forEach { assertFalse(transaction.contains(it)) }
        assertTrue(transaction.contains("withContext(NonCancellable)"))
    }

    private fun run(fake: Fake, selected: UsbAudioConfigurationResolution = resolution, access: () -> UsbClockQueryStatus? = { null }) =
        runBlocking { UsbAudioConfigurationExperiment.run(selected, 3, fake, access) }

    @Test fun liveTelemetryKeepsOnlyVerifiedClockFactsForItsOwnAttachment() {
        val verified=run(Fake())
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,verified.targetStatus)
        val key=UsbTelemetrySession(3,8);val store=UsbTransportTelemetryStore();store.begin(key)
        store.configured(UsbTelemetrySession(4,8),verified);assertNull(store.state.value.clockFacts)
        store.configured(key,verified.copy(targetStatus=UsbTargetConfigurationStatus.FAILED));assertNull(store.state.value.clockFacts)
        store.configured(key,verified.copy(configuredClockReadback=verified.configuredClockReadback!!.copy(currentFrequencyHz=96000)))
        assertNull(store.state.value.clockFacts)
        store.configured(key,verified)
        assertEquals(plan.clock.clockEntityId,store.state.value.clockFacts!!.entity)
        assertEquals(plan.sampleRateHz,store.state.value.clockFacts!!.configuredReadbackHz)
        assertTrue(store.state.value.clockFacts!!.configuredValid)
        store.finish(key);assertEquals(plan.sampleRateHz,store.state.value.clockFacts!!.targetReadbackHz)
        store.begin(UsbTelemetrySession(4,9));assertNull(store.state.value.clockFacts)
        store.configured(UsbTelemetrySession(4,9),verified);assertNull(store.state.value.clockFacts)
    }

    @Test fun rawZeroOneAndFourAreUnsignedAndDescriptorDeclared() {
        val declared = zeroThroughFourPlan()
        listOf(0, 1, 4).forEach { raw ->
            val evidence = decodeAlternate(declared, raw)
            assertEquals(raw, evidence.alternate)
            assertEquals(UsbAlternateDescriptorValidation.VALID_DECLARED_ALTERNATE, evidence.descriptorValidation)
            assertTrue(evidence.authoritative)
        }
    }

    @Test fun rawSixIsNotDeclaredWhenDescriptorsExposeZeroThroughFour() {
        val evidence = decodeAlternate(zeroThroughFourPlan(), 6)
        assertEquals(6, evidence.alternate)
        assertEquals(listOf(6), evidence.native!!.rawBytes)
        assertEquals(UsbClockQueryStatus.SUCCESS, evidence.status)
        assertEquals(UsbAlternateDescriptorValidation.INVALID_UNDECLARED_ALTERNATE, evidence.descriptorValidation)
        assertFalse(evidence.authoritative)
    }

    @Test fun signedJniBytePreservesUnsigned255WithoutMetadataContamination() {
        val evidence = decodeAlternate(plan, 255)
        assertEquals(255, evidence.alternate)
        assertFalse(evidence.authoritative)
        assertEquals(UsbFsControlEvidence.GET_INTERFACE, evidence.native!!.operation)
        assertEquals(UsbFsEvidence.NATIVE_API_VERSION, evidence.native.apiVersion)
        assertEquals(1, evidence.native.actualLength)
    }

    @Test fun packetVersionOperationLengthAndRawStorageAreValidatedSeparately() {
        val packet = UsbFsNativeControlResult(1, 1, 0, "", byteArrayOf(6), operation = UsbFsControlEvidence.GET_INTERFACE)
        listOf(packet.copy(apiVersion = UsbFsEvidence.NATIVE_API_VERSION - 1),
            packet.copy(operation = UsbFsControlEvidence.CLOCK_SET), packet.copy(actualLength = 0),
            packet.copy(requestedLength = 4), packet.copy(rawBytes = byteArrayOf(1, 6)), packet.copy(errno = 6)).forEach {
            try {
                UsbFsControlEvidence.decode(it, 1, UsbFsControlEvidence.GET_INTERFACE)
                throw AssertionError("Malformed packet must not supply alternate evidence")
            } catch (_: IllegalArgumentException) { }
        }
    }

    @Test fun getInterfaceNegativeShortAndOversizedReturnsCannotSupplyAlternate() {
        listOf(UsbFsNativeControlResult(1, -1, 32, "EPIPE", byteArrayOf(), operation = UsbFsControlEvidence.GET_INTERFACE),
            UsbFsNativeControlResult(1, 0, 0, "", byteArrayOf(), operation = UsbFsControlEvidence.GET_INTERFACE),
            UsbFsNativeControlResult(1, 2, 0, "", byteArrayOf(), operation = UsbFsControlEvidence.GET_INTERFACE)).forEach { packet ->
            val evidence = UsbConfigurationControlEvidence.decode(UsbConfigurationControlRequest.getInterface(plan),
                UsbFsControlEvidence.decode(packet, 1, UsbFsControlEvidence.GET_INTERFACE))
            assertNull(evidence.alternate)
            assertFalse(evidence.authoritative)
            assertTrue(evidence.descriptorValidation in setOf(UsbAlternateDescriptorValidation.TRANSFER_FAILED, UsbAlternateDescriptorValidation.MALFORMED))
        }
    }

    @Test fun nativeGetInterfaceStorageIsInitializedAndCopiedOnlyForExactReturn() {
        val native = source("cpp/usb_fs_diagnostics.cpp")
        val get = native.substringAfter("UsbFsDiagnostics_nativeGetAudioStreamingInterface(").substringBefore("// Only Sampling Frequency")
        listOf("std::array<jbyte, 1> bytes{}", "usbdevfs_ctrltransfer control{}", "control.data = bytes.data()",
            "control.bRequestType = 0x81", "control.bRequest = 0x0A", "control.wValue = 0",
            "control.wIndex = static_cast<__u16>(asNumber)", "control.wLength = 1",
            "controlPacket(env, 1, returned, savedError, bytes.data(), 2)").forEach { assertTrue(get.contains(it)) }
        val packet = native.substringAfter("jobject controlPacket(").substringBefore("} // namespace")
        assertTrue(packet.contains("operation == 2 ? (returned == 1 ? 1 : 0)"))
        assertTrue(packet.contains("SetByteArrayRegion(raw, 0, actual, bytes)"))
        assertTrue(packet.contains("(IIILjava/lang/String;[BIII)V"))
        assertTrue(packet.contains("length, returned, savedError, errorName, raw"))
        assertTrue(packet.contains("kApiVersion, operation, actual"))
    }

    @Test fun ambiguousZeroEndpointIdleDescriptorsBlockConfiguration() {
        val idle = model.interfaces.single { it.key == plan.idle }
        assertNull(resolve(model.copy(interfaces = model.interfaces + idle.copy(key = idle.key.copy(alternate = 9)))).plan)
        assertNull(resolve(model.copy(interfaces = model.interfaces + idle)).plan)
    }

    @Test fun structurallyInconsistentIdleDescriptorBlocksConfiguration() {
        assertNull(resolve(model.copy(interfaces = model.interfaces.map {
            if (it.key == plan.idle) it.copy(endpointCount = 1) else it
        })).plan)
    }

    @Test fun idleAlternateComesFromUniqueDescriptorEvenWhenItIsNotZero() {
        val changed = model.copy(interfaces = model.interfaces.map {
            if (it.key == plan.idle) it.copy(key = it.key.copy(alternate = 3)) else it
        })
        val resolved = resolve(changed).plan!!
        assertEquals(3, resolved.idle.alternate)
        assertEquals(3, resolved.asClaim.audioStreaming.alternate)
        assertEquals(3, UsbStreamingAlternateRequest.idle(resolved).alternate)
    }

    @Test fun explicitIdleSetterFollowsAsClaimAndPrecedesClockMutation() {
        val fake = Fake()
        val result = run(fake)
        assertTrue(fake.events.indexOf("claimAS:8:true") < fake.events.indexOf("setAlt:0"))
        assertTrue(fake.events.indexOf("setAlt:0") < fake.events.indexOf("setClock:44100"))
        assertTrue(result.idleMutation.established)
        assertEquals(UsbAlternateCorroboration.CORROBORATED, result.idleMutation.corroboration)
        assertEquals(3L, result.idleAlternateSet!!.generation)
        assertTrue(result.idleAlternateSet.order!! < result.idleAlternateReadback!!.order!!)
        assertNotNull(result.idleAlternateSet.elapsedMs)
    }

    @Test fun failedInitialIdleSetterPreventsClockWriteAndTriggersIdleCleanup() {
        val fake = Fake().apply { initialIdleError = true }
        val result = run(fake)
        assertEquals(UsbConfigurationStep.IDLE, result.failedStep)
        assertTrue(fake.clockWrites.isEmpty())
        assertEquals(listOf(0, 0), fake.alternates)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun undeclaredInitialAndIdleQueriesDoNotBecomeStateOrBlockSuccessfulSetter() {
        val fake = Fake().apply { reportedAlternates[1] = 9; reportedAlternates[2] = 9 }
        val result = run(fake)
        assertFalse(result.originalAlternate!!.authoritative)
        assertFalse(result.idleAlternateReadback!!.authoritative)
        assertEquals(UsbAlternateCorroboration.INCONSISTENT_UNDECLARED, result.idleMutation.corroboration)
        assertTrue(result.idleMutation.established)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
    }

    @Test fun failedOrMalformedIdleAdvisoryQueryDoesNotBlockSuccessfulSetter() {
        listOf(Fake().apply { getAltErrorAt = 2 }, Fake().apply { getAltShortAt = 2 }).forEach { fake ->
            val result = run(fake)
            assertEquals(UsbAlternateCorroboration.UNAVAILABLE, result.idleMutation.corroboration)
            assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
        }
    }

    @Test fun differentDeclaredIdleQueryBlocksClockMutation() {
        val fake = Fake().apply { reportedAlternates[2] = 6 }
        val result = run(fake)
        assertEquals(UsbAlternateCorroboration.CONTRADICTORY_DECLARED, result.idleMutation.corroboration)
        assertEquals(UsbConfigurationStep.IDLE, result.failedStep)
        assertTrue(fake.clockWrites.isEmpty())
        assertFalse(result.alternateSelectionAttempted)
        assertEquals(true, result.asRelease.returned)
    }

    @Test fun undeclaredTargetQueryIsInconsistentButCoreConfigurationCanBeVerified() {
        val result = run(Fake().apply { reportedAlternates[3] = 9 })
        assertEquals(UsbClockQueryStatus.SUCCESS, result.targetAlternateSet!!.status)
        assertEquals(UsbAlternateCorroboration.INCONSISTENT_UNDECLARED, result.targetMutation.corroboration)
        assertFalse(result.targetAlternateReadback!!.authoritative)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
    }

    @Test fun unavailableTargetQueryAllowsCoreConfigurationVerification() {
        val result = run(Fake().apply { getAltErrorAt = 3 })
        assertEquals(UsbAlternateCorroboration.UNAVAILABLE, result.targetMutation.corroboration)
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.targetStatus)
    }

    @Test fun differentDeclaredTargetQueryBlocksVerifiedStatus() {
        val result = run(Fake().apply { reportedAlternates[3] = 6 })
        assertEquals(UsbAlternateCorroboration.CONTRADICTORY_DECLARED, result.targetMutation.corroboration)
        assertEquals(UsbConfigurationStep.ALTERNATE_READBACK, result.failedStep)
        assertEquals(UsbTargetConfigurationStatus.FAILED, result.targetStatus)
    }

    @Test fun undeclaredRestorationQueryPermitsDynamicOriginalClockRestore() {
        val fake = Fake().apply { rate = 192_000L; reportedAlternates[4] = 9 }
        val result = run(fake)
        assertEquals(UsbAlternateCorroboration.INCONSISTENT_UNDECLARED, result.restorationMutation.corroboration)
        assertEquals(listOf(44_100L, 192_000L), fake.clockWrites)
        assertEquals(192_000L, result.originalClockReadback!!.currentFrequencyHz)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun failedRestorationAdvisoryQueryPermitsClockRestoreAfterSuccessfulSetter() {
        val result = run(Fake().apply { getAltErrorAt = 4 })
        assertEquals(UsbAlternateCorroboration.UNAVAILABLE, result.restorationMutation.corroboration)
        assertEquals(48_000L, result.originalClockRestore!!.request.rateHz)
        assertEquals(UsbConfigurationRestorationStatus.RESTORED, result.restorationStatus)
    }

    @Test fun contradictoryDeclaredRestorationQueryPreventsClockWrite() {
        val fake = Fake().apply { reportedAlternates[4] = 6 }
        val result = run(fake)
        assertEquals(UsbClockQueryStatus.SUCCESS, result.idleAlternateRestore!!.status)
        assertEquals(UsbAlternateCorroboration.CONTRADICTORY_DECLARED, result.restorationMutation.corroboration)
        assertNull(result.originalClockRestore)
        assertEquals(listOf(44_100L), fake.clockWrites)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun advisoryQueryStillStopsWritesOnDetachPermissionLossOrBadFd() {
        listOf(19 to "ENODEV", 13 to "EACCES", 9 to "EBADF").forEach { error ->
            val fake = Fake().apply { getAltErrorAt = 2; getAltFailure = error }
            val result = run(fake)
            assertNotNull(result.accessFailure)
            assertTrue(fake.clockWrites.isEmpty())
            assertEquals(listOf(0), fake.alternates)
            assertEquals(UsbTargetConfigurationStatus.NON_AUTHORITATIVE, result.targetStatus)
        }
    }

    @Test fun staleGenerationAfterIdleSelectionBlocksClockAndCleanupWrites() {
        val fake = Fake()
        var stale = false
        fake.afterInitialIdle = { stale = true }
        val result = run(fake) { if (stale) UsbClockQueryStatus.STALE_GENERATION else null }
        assertEquals(UsbClockQueryStatus.STALE_GENERATION, result.accessFailure)
        assertTrue(fake.clockWrites.isEmpty())
        assertEquals(listOf(0), fake.alternates)
        assertEquals(true, result.asRelease.returned)
        assertEquals(true, result.acRelease.returned)
    }

    @Test fun cancellationAfterUncertainInitialIdleSelectionRestoresAndReleases() {
        val fake = Fake().apply { cancelInitialIdle = true }
        try { run(fake); throw AssertionError("Cancellation must propagate") }
        catch (_: CancellationException) { }
        assertEquals(listOf(0, 0), fake.alternates)
        assertTrue(fake.clockWrites.isEmpty())
        assertEquals("close", fake.events.last())
    }

    @Test fun reportSeparatesHostMutationQueryAndDescriptorValidation() {
        val result = run(Fake().apply { reportedAlternates[1] = 9; reportedAlternates[2] = 9; reportedAlternates[3] = 9; reportedAlternates[4] = 9 })
        val text = report(result)
        listOf("Initial GET_INTERFACE advisory probe", "Host-issued SETINTERFACE (primary)",
            "Device-returned GET_INTERFACE (advisory)", "Descriptor validity: INVALID_UNDECLARED_ALTERNATE",
            "authoritative current-alt query evidence: NO", "corroboration=INCONSISTENT_UNDECLARED",
            "packet version=", "native operation=", "generation=3; order=", "Bit-perfect active: NO").forEach { assertTrue(text.contains(it)) }
        assertFalse(text.contains("KA15 has broken GET_INTERFACE"))
    }

    @Test fun historicalBlockedRawSixRunCanStillBeRepresentedWithoutInventingAlternate() {
        val declared = zeroThroughFourPlan()
        val result = UsbAudioConfigurationResult(3, UsbAudioConfigurationResolution(declared),
            targetStatus = UsbTargetConfigurationStatus.BLOCKED, failedStep = UsbConfigurationStep.IDLE,
            originalClockRateHz = 192_000L, originalAlternate = decodeAlternate(declared, 6),
            acClaim = UsbConfigurationAndroidOwnership(true, true), asClaim = UsbConfigurationAndroidOwnership(true, true),
            asRelease = UsbConfigurationAndroidOwnership(true, true), acRelease = UsbConfigurationAndroidOwnership(true, true),
            enumerationRecovery = UsbAudioForcedExperimentResult(3, declared.probe, recoveryStatus = UsbRecoveryStatus.ENUMERATION_RECOVERED))
        val text = report(result)
        assertTrue(text.contains("decoded alternate=6"))
        assertTrue(text.contains("Descriptor validity: INVALID_UNDECLARED_ALTERNATE"))
        assertTrue(text.contains("Target configuration: BLOCKED"))
        assertTrue(text.contains("Kernel/framework summary: ENUMERATION_RECOVERED"))
        assertFalse(result.clockWriteAttempted)
        assertFalse(result.idleSelectionAttempted)
        assertFalse(result.alternateSelectionAttempted)
        assertFalse(declared.declaredAlternates.contains(6))
    }

    private fun decodeAlternate(selected: UsbAudioConfigurationPlan, raw: Int): UsbConfigurationControlEvidence {
        val packet = UsbFsNativeControlResult(1, 1, 0, "", byteArrayOf(raw.toByte()), operation = UsbFsControlEvidence.GET_INTERFACE)
        return UsbConfigurationControlEvidence.decode(UsbConfigurationControlRequest.getInterface(selected),
            UsbFsControlEvidence.decode(packet, 1, UsbFsControlEvidence.GET_INTERFACE))
    }
    private fun zeroThroughFourPlan(): UsbAudioConfigurationPlan {
        val fourth = model.interfaces.single { it.key.alternate == 7 }.copy(key = plan.streaming.copy(alternate = 4))
        return resolve(model.copy(interfaces = model.interfaces.map { if (it.isAudioStreaming && it.key.alternate > 0)
            it.copy(key = it.key.copy(alternate = it.key.alternate - 4)) else it } + fourth)).plan!!
    }
    private fun report(result: UsbAudioConfigurationResult) = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 3, configurationExperiment = result))
    private fun resolve(changed: UsbAudioDescriptorModel) = UsbAudioConfigurationPlan.resolve(changed, androidFacts(changed))
    private fun changeTarget(change: (UsbInterfaceDescriptor) -> UsbInterfaceDescriptor) = model.copy(interfaces = model.interfaces.map {
        if (it.key == plan.streaming) change(it) else it
    })
    private fun androidFacts(model: UsbAudioDescriptorModel) = model.interfaces.map {
        UsbAndroidInterfaceSnapshot(it.key.number, it.key.alternate, it.interfaceClass, it.subclass, it.protocol, emptyList())
    }
    private fun u32(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
    private fun source(relative: String): String = listOf(Paths.get("src/main", relative), Paths.get("app/src/main", relative))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    private fun audio() = UsbAndroidAudioDiagnostics(listOf(UsbAndroidAudioOutput("Synthetic USB output", 22, 90,
        "card=3;device=0", listOf(48_000), listOf(2), listOf(2), listOf(3), listOf(3))))

    private inner class Fake(private val selectedPlan: UsbAudioConfigurationPlan = plan) : UsbAudioConfigurationTransport {
        var acClaimResult = true; var asClaimResult = true
        var acClaimThrows = false; var asClaimThrows = false
        var asReleaseResult = true; var asReleaseThrows = false; var acReleaseThrows = false
        var acOwned = false; var asOwned = false
        var fdValid = true; var closed = false
        var rate = 48_000L; var alt = 0
        var baselineStatus = UsbFsProbeStatus.RECORDED
        var ranges = listOf(44_100L, 48_000L, 96_000L, 192_000L).map { UsbSamplingFrequencyRange(it, it, 0) }
        var rangeError: Pair<Int, String>? = null; var malformedRange = false; var preflightFrequencyError = false
        var getAltErrorAt: Int? = null; var getAltShortAt: Int? = null
        var getAltFailure = 32 to "EPIPE"
        val reportedAlternates = mutableMapOf<Int, Int>()
        var targetWriteError: Pair<Int, String>? = null; var targetWriteLength = 4
        var restoreWriteError = false; var applyTargetRate = true; var applyRestoreRate = true
        var throwTargetWrite = false; var cancelTargetWrite = false; var afterTargetWrite: (() -> Unit)? = null
        var targetInvalidReads = 0; var targetValidityError = false
        var configuredValid = true; var restoredValid = true; var rateAfterTargetAlt: Long? = null
        var targetAltError = false; var idleAltError = false; var applyTargetAlt = true; var applyIdleAlt = true
        var initialIdleError = false; var applyInitialIdle = true; var cancelInitialIdle = false
        var afterInitialIdle: (() -> Unit)? = null
        var cancelConfiguredRead = false
        var finalFramework = audio()
        var now = 0L
        private var restoring = false; private var targetValidityCount = 0; private var getAltCount = 0
        private var idleSetCount = 0
        private var evidence: UsbFsControlEvidence? = null
        val events = mutableListOf<String>()
        val readRequests = mutableListOf<UsbClockReadRequest>()
        val clockWrites = mutableListOf<Long>()
        val alternates = mutableListOf<Int>()
        val snapshots = mutableListOf<UsbFsOwnershipDiagnostics>()
        override fun interfaces() = android.mapIndexed { position, iface -> UsbAudioClaimInterface(position,
            iface.number, iface.alternate, iface.interfaceClass, iface.subclass, iface.protocol) }
        override fun connectionValid() = fdValid && !closed
        override fun snapshot(): UsbFsOwnershipDiagnostics {
            events += "snapshot"
            val bindings = selectedPlan.probe.interfaces.map { iface ->
                val driver = when {
                    iface.number == 4 -> "test_hid_driver"
                    iface.number == 7 && acOwned -> "usbfs"
                    iface.number == 8 && asOwned -> "usbfs"
                    else -> "test_driver_${iface.number}"
                }
                UsbFsDriverBinding(iface, UsbFsIoctlResult(UsbFsIoctlOperation.GETDRIVER, 0, 0, null), driver)
            }
            return UsbFsOwnershipDiagnostics(3, selectedPlan.probe, if (snapshots.isEmpty()) baselineStatus else UsbFsProbeStatus.RECORDED,
                bindings, UsbFsSpeedEvidence(UsbFsIoctlResult(UsbFsIoctlOperation.GET_SPEED, 3, 0, null), UsbFsSpeed.HIGH),
                androidAudio = audio()).also { snapshots += it }
        }
        override fun frameworkSnapshot() = finalFramework
        override fun claim(request: UsbAudioForcedClaimRequest): Boolean {
            events += "claimAC:${request.audioControl.number}:${request.force}"
            if (acClaimThrows) throw IllegalStateException("private exception detail")
            acOwned = acClaimResult
            return acClaimResult
        }
        override fun claimStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean {
            check(acOwned)
            events += "claimAS:${request.audioStreaming.number}:${request.force}"
            if (asClaimThrows) throw IllegalStateException("private exception detail")
            asOwned = asClaimResult
            return asClaimResult
        }
        override fun releaseStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean {
            events += "releaseAS:${request.audioStreaming.number}"
            asOwned = false
            if (asReleaseThrows) throw IllegalStateException("private exception detail")
            return asReleaseResult
        }
        override fun release(request: UsbAudioForcedClaimRequest): Boolean {
            events += "releaseAC:${request.audioControl.number}"
            acOwned = false
            if (acReleaseThrows) throw IllegalStateException("private exception detail")
            return true
        }
        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
            check(acOwned)
            events += "read:${request.query}"
            readRequests += request
            if (cancelConfiguredRead && alt == 5 && request.query == UsbClockQuery.CURRENT_FREQUENCY && !restoring)
                throw CancellationException("cancel active alternate")
            val error = when {
                request.query == UsbClockQuery.FREQUENCY_RANGE -> rangeError
                request.query == UsbClockQuery.CURRENT_FREQUENCY && clockWrites.isEmpty() && preflightFrequencyError -> 5 to "EIO"
                request.query == UsbClockQuery.CLOCK_VALIDITY && clockWrites.isNotEmpty() && !restoring && targetValidityError -> 32 to "EPIPE"
                else -> null
            }
            if (error != null) { evidence = UsbFsControlEvidence(request.length, -1, error.first, error.second, emptyList()); return -1 }
            val rangeBody = byteArrayOf(ranges.size.toByte(), 0) + ranges.flatMap { (u32(it.minimumHz) + u32(it.maximumHz) + u32(it.resolutionHz)).toList() }.toByteArray()
            val valid = when {
                request.query != UsbClockQuery.CLOCK_VALIDITY -> true
                clockWrites.isEmpty() -> true
                restoring -> restoredValid
                alt == 5 -> configuredValid
                else -> { targetValidityCount++; targetValidityCount > targetInvalidReads }
            }
            val bytes = when {
                request.rangeHeaderOnly -> byteArrayOf(if (malformedRange) 0 else ranges.size.toByte(), 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> rangeBody
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(rate)
                else -> byteArrayOf(if (valid) 1 else 0)
            }
            bytes.copyInto(buffer, endIndex = minOf(bytes.size, buffer.size))
            evidence = UsbFsControlEvidence(request.length, bytes.size, 0, null, bytes.map { it.toInt() and 0xff })
            return bytes.size
        }
        override fun lastControlEvidence() = evidence
        override fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
            check(acOwned && asOwned)
            if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE) {
                getAltCount++
                events += "getAlt:$alt"
                val native = when (getAltCount) {
                    getAltErrorAt -> UsbFsControlEvidence(1, -1, getAltFailure.first, getAltFailure.second, emptyList())
                    getAltShortAt -> UsbFsControlEvidence(1, 0, 0, null, emptyList())
                    else -> UsbFsControlEvidence(1, 1, 0, null, listOf(reportedAlternates[getAltCount] ?: alt))
                }
                return UsbConfigurationControlEvidence.decode(request, native.copy(operation = UsbFsControlEvidence.GET_INTERFACE))
            }
            val requested = request.rateHz!!
            events += "setClock:$requested"
            clockWrites += requested
            val target = request.operation == UsbConfigurationControlOperation.TARGET_CLOCK_SET
            if (!target) restoring = true
            if ((target && applyTargetRate) || (!target && applyRestoreRate)) rate = requested
            if (target && throwTargetWrite) throw IllegalStateException("private exception detail")
            if (target && cancelTargetWrite) throw CancellationException("cancel after possible mutation")
            if (target) afterTargetWrite?.invoke()
            val error = if (target) targetWriteError else if (restoreWriteError) 32 to "EPIPE" else null
            val count = if (target) targetWriteLength else 4
            val native = if (error != null) UsbFsControlEvidence(4, -1, error.first, error.second, emptyList())
                else UsbFsControlEvidence(4, count, 0, null, request.payload.take(count))
            return UsbConfigurationControlEvidence.decode(request, native.copy(operation = UsbFsControlEvidence.CLOCK_SET))
        }
        override fun setAlternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence {
            check(acOwned && asOwned)
            events += "setAlt:${request.alternate}"
            alternates += request.alternate
            val idle = request.alternate == 0
            if (idle) idleSetCount++
            val initialIdle = idle && idleSetCount == 1
            if (idle && !initialIdle) restoring = true
            if ((initialIdle && applyInitialIdle) || (idle && !initialIdle && applyIdleAlt) || (!idle && applyTargetAlt)) alt = request.alternate
            if (initialIdle && cancelInitialIdle) throw CancellationException("cancel after possible idle selection")
            if (initialIdle) afterInitialIdle?.invoke()
            if (!idle) rateAfterTargetAlt?.let { rate = it }
            val error = if (initialIdle) initialIdleError else if (idle) idleAltError else targetAltError
            return UsbStreamingAlternateEvidence(request,
                UsbFsIoctlResult(UsbFsIoctlOperation.SETINTERFACE, if (error) -1 else 0, if (error) 32 else 0, if (error) "EPIPE" else null),
                if (error) UsbClockQueryStatus.STALL_LIKE else UsbClockQueryStatus.SUCCESS)
        }
        override fun close() { events += "close"; closed = true; acOwned = false; asOwned = false }
        override fun monotonicMs() = now
        override suspend fun pause(milliseconds: Long) { events += "pause:$milliseconds"; now += milliseconds }
    }

    private fun descriptorModel(): UsbAudioDescriptorModel {
        val entities = listOf(UsbAudioEntity(29, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7),
            UsbAudioEntity(33, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(29), terminalType = 0x0101),
            UsbAudioEntity(41, UsbAudioEntityKind.FEATURE_UNIT, signalSources = listOf(33)),
            UsbAudioEntity(47, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(41), clockSources = listOf(29), terminalType = 0x0301))
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 7, 0), 0, 1, 1, 0x20,
            audioControl = UsbAudioControlDescriptor(0x0200, entities = entities))
        val idle = UsbInterfaceDescriptor(UsbInterfaceKey(18, 8, 0), 0, 1, 2, 0x20)
        val formats = listOf(5 to UsbAudioFormatDescriptor(1, 2, 16), 6 to UsbAudioFormatDescriptor(1, 3, 24),
            7 to UsbAudioFormatDescriptor(1, 4, 32))
        val streams = formats.map { (alternate, format) -> UsbInterfaceDescriptor(UsbInterfaceKey(18, 8, alternate), 2, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(5, 5, 240, 1), UsbEndpointDescriptor(0x85, 0x11, 4, 1)),
            audioStreaming = UsbAudioStreamingDescriptor(33, 0, 1, 1, 2, 3), audioFormat = format) }
        return UsbAudioDescriptorModel(300, null, listOf(UsbConfigurationDescriptor(18, 1, 282, 3, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 7, 2, 1, 0, 0x20)),
            listOf(UsbInterfaceDescriptor(UsbInterfaceKey(18, 4, 0), 0, 3, 0, 0), ac, idle) + streams, emptyList(), emptyList())
    }
}
