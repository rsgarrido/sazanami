package io.github.rsgarrido.sazanami.player.usb

import java.nio.file.Files
import java.nio.file.Paths
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

/** Synthetic evidence and source-boundary tests, never claims about real kernel/DAC completions. */
class UsbSilentTransportTest {
    private val model = descriptors()
    private val resolution = resolve(model)
    private val plan = resolution.plan!!
    private val configurationPlan = plan.configurationPlan
    private fun resolve(value: UsbAudioDescriptorModel) = UsbSilentTransportPlan.resolve(value, android(value))
    private fun android(value: UsbAudioDescriptorModel) = value.interfaces.map { iface -> UsbAndroidInterfaceSnapshot(
        iface.key.number, iface.key.alternate, iface.interfaceClass, iface.subclass, iface.protocol,
        iface.endpoints.map { UsbAndroidEndpointSnapshot(it.address, it.attributes, it.maxPacketSizeRaw, it.interval) }) }
    private fun changeData(transform: (UsbEndpointDescriptor) -> UsbEndpointDescriptor) = model.copy(interfaces = model.interfaces.map {
        if (it.key == configurationPlan.streaming) it.copy(endpoints = it.endpoints.map { ep -> if (ep.address == 11) transform(ep) else ep }) else it })
    private fun changeFeedback(transform: (UsbEndpointDescriptor) -> UsbEndpointDescriptor) = model.copy(interfaces = model.interfaces.map {
        if (it.key == configurationPlan.streaming) it.copy(endpoints = it.endpoints.map { ep -> if (ep.address == 0x8e) transform(ep) else ep }) else it })
    private fun withoutFeedback() = model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming)
        it.copy(endpointCount = 1, endpoints = it.endpoints.filterNot { ep -> ep.isIn }) else it })
    private fun bytes(raw: Long) = (0..3).map { ((raw shr (it * 8)) and 0xff).toInt() }
    private fun decode(raw: Long) = UsbExplicitFeedback.decode(bytes(raw))
    private fun source(relative: String) = listOf(Paths.get("src/main", relative), Paths.get("app/src/main", relative))
        .first { Files.isRegularFile(it) }.toFile().readText(Charsets.UTF_8)
    private val nominalFeedback = 361267L

    @Test fun acceptsOnlyStereoPcm16And44100() {
        assertEquals(44100L, configurationPlan.sampleRateHz); assertEquals(2, configurationPlan.channels)
        assertEquals(2, configurationPlan.subslotBytes); assertEquals(16, configurationPlan.validBits)
        listOf(UsbAudioFormatDescriptor(1, 3, 24), UsbAudioFormatDescriptor(1, 4, 32)).forEach { format ->
            assertNull(resolve(model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming) it.copy(audioFormat = format) else it })).plan)
        }
    }
    @Test fun resolvesSyntheticEndpointsRatherThanKa15Numbers() { assertEquals(11, plan.data.address); assertEquals(0x8e, plan.feedback.address); assertEquals(9, configurationPlan.streaming.alternate) }
    @Test fun rejectsNonIsoData() { assertNull(resolve(changeData { it.copy(attributes = 6) }).plan) }
    @Test fun rejectsDataIn() { assertNull(resolve(changeData { it.copy(address = 0x8b) }).plan) }
    @Test fun rejectsNonDataUsage() { assertNull(resolve(changeData { it.copy(attributes = 0x25) }).plan) }
    @Test fun rejectsAdaptiveOrSynchronousData() { listOf(9, 13).forEach { attr -> assertNull(resolve(changeData { it.copy(attributes = attr) }).plan) } }
    @Test fun rejectsNonIsoFeedback() { assertNull(resolve(changeFeedback { it.copy(attributes = 0x12) }).plan) }
    @Test fun rejectsFeedbackOut() { assertNull(resolve(changeFeedback { it.copy(address = 14) }).plan) }
    @Test fun rejectsFeedbackWithoutFeedbackUsage() { assertNull(resolve(changeFeedback { it.copy(attributes = 1) }).plan) }
    @Test fun missingFeedbackBlocks() { assertNull(resolve(withoutFeedback()).plan) }
    @Test fun ambiguousFeedbackBlocks() {
        val value = model.copy(interfaces = model.interfaces.map { if (it.key == configurationPlan.streaming)
            it.copy(endpointCount = 3, endpoints = it.endpoints + plan.feedback.copy(address = 0x8f)) else it })
        assertNull(resolve(value).plan)
    }
    @Test fun synchronizationAddressMustAgree() { assertNull(resolve(changeData { it.copy(synchronizationAddress = 0x8f) }).plan) }
    @Test fun maxPacketsOnlyCannotSilentlyPadPcm() { assertNull(resolve(changeData { it.copy(audio = UsbAudioEndpointDescriptor(0x80, 0, 0, 0)) }).plan) }
    @Test fun missingClassSpecificDataDescriptorBlocks() { assertNull(resolve(changeData { it.copy(audio = null) }).plan) }
    @Test fun androidEndpointMismatchBlocks() { assertNull(UsbSilentTransportPlan.resolve(model, android(model).map { it.copy(endpoints = emptyList()) }).plan) }
    @Test fun actualHighSpeedIsRequired() { listOf(null, UsbFsSpeed.FULL, UsbFsSpeed.SUPER, UsbFsSpeed.UNKNOWN).forEach { assertNull(plan.timing(it)) } }
    @Test fun highSpeedDataIntervalIs125Microseconds() { assertEquals(125L, plan.timing(UsbFsSpeed.HIGH)!!.dataIntervalUs) }
    @Test fun feedbackIntervalUsesExponentAndNotFrameRateScaling() { assertEquals(1000L, plan.timing(UsbFsSpeed.HIGH)!!.feedbackIntervalUs); assertEquals(500L, UsbIsoTiming.serviceIntervalUs(UsbFsSpeed.HIGH, 3)) }
    @Test fun invalidIntervalBoundsAreRejected() { listOf(0, 17).forEach { assertNull(UsbIsoTiming.serviceIntervalUs(UsbFsSpeed.HIGH, it)) } }
    @Test fun boundedIntervalsRejectLargeQueueSpans() { assertNull(resolve(changeData { it.copy(interval = 4) }).plan); assertNull(resolve(changeFeedback { it.copy(interval = 8) }).plan) }
    @Test fun highSpeedCapacityIncludesEncodedTransactions() { assertEquals(776, UsbIsoTiming.highSpeedCapacity(776)); assertEquals(2048, UsbIsoTiming.highSpeedCapacity(1024 or (1 shl 11))); assertEquals(3072, UsbIsoTiming.highSpeedCapacity(1024 or (2 shl 11))) }
    @Test fun reservedCapacityAndOversizedTransactionAreRejected() { listOf(0, 1025, 4 or (3 shl 11), 4 or 0x8000).forEach { assertNull(UsbIsoTiming.highSpeedCapacity(it)) } }
    @Test fun highBandwidthTransactionsRequireIntervalOne() { assertNull(resolve(changeData { it.copy(maxPacketSizeRaw = 1024 or 0x800, interval = 2) }).plan) }
    @Test fun endpointTooSmallForPlausibleRateBlocksAtBaseline() { val selected = resolve(changeData { it.copy(maxPacketSizeRaw = 20) }); assertNotNull(selected.plan); assertNull(selected.plan!!.timing(UsbFsSpeed.HIGH)) }
    @Test fun feedbackRepresentationRequiresFourByteEndpoint() { listOf(3, 5, 4 or 0x800).forEach { raw -> assertNull(resolve(changeFeedback { it.copy(maxPacketSizeRaw = raw) }).plan) } }
    @Test fun knownFeedbackDecodesLeFixedPointPerMicroframe() { val sample = decode(nominalFeedback); assertEquals(UsbFeedbackValidity.VALID, sample.validity); assertEquals(44100.0, sample.rateHz!!, 0.2); assertEquals(5.5125, sample.framesPerServiceInterval(125)!!, 0.0001) }
    @Test fun feedbackPollingIntervalDoesNotMultiplySampleRate() { assertEquals(44100.0, decode(nominalFeedback).rateHz!!, 0.2); assertEquals(44.1, decode(nominalFeedback).framesPerServiceInterval(1000)!!, 0.001) }
    @Test fun wrongFeedbackLengthIsRejected() { listOf(emptyList(), listOf(1, 2, 3), listOf(1, 2, 3, 4, 5)).forEach { assertEquals(UsbFeedbackValidity.WRONG_LENGTH, UsbExplicitFeedback.decode(it).validity) } }
    @Test fun zeroFeedbackIsRejected() { assertEquals(UsbFeedbackValidity.ZERO, decode(0).validity) }
    @Test fun reservedMostSignificantBitsAreRejected() { assertEquals(UsbFeedbackValidity.RESERVED_BITS, decode(0xf0000001L).validity) }
    @Test fun implausibleFeedbackIsNotClamped() { listOf(1L, 48000L * 65536 / 8000, 0x0fffffffL).forEach { assertEquals(UsbFeedbackValidity.IMPLAUSIBLE, decode(it).validity) } }
    @Test fun plausibilityWindowAcceptsInnerAndRejectsOuterValues() { assertEquals(5L, UsbExplicitFeedback.PLAUSIBILITY_PERCENT); assertEquals(UsbFeedbackValidity.VALID, decode(nominalFeedback * 96 / 100).validity); assertEquals(UsbFeedbackValidity.IMPLAUSIBLE, decode(nominalFeedback * 94 / 100).validity) }
    @Test fun negativeFeedbackPacketStatusIsNotData() { assertEquals(UsbFeedbackValidity.COMPLETION_ERROR, UsbExplicitFeedback.decode(bytes(nominalFeedback), -18).validity) }
    @Test fun unsignedFeedbackArithmeticAvoidsOverflow() { assertEquals(0xffffffffL, decode(0xffffffffL).raw); assertEquals(UsbFeedbackValidity.RESERVED_BITS, decode(0xffffffffL).validity) }
    @Test fun exactNominalAccumulatorProduces44100FramesIn8000Microframes() { val scheduler = UsbIsoFrameAccumulator(); repeat(8000) { scheduler.next(1) }; assertEquals(44100L, scheduler.frames); assertEquals(0L, scheduler.remainder) }
    @Test fun schedulingLargerIntervalsPreservesExactRate() { val scheduler = UsbIsoFrameAccumulator(); repeat(2000) { scheduler.next(4) }; assertEquals(44100L, scheduler.frames) }
    @Test fun nominalToFeedbackSwitchRetainsFractionalRemainder() {
        val scheduler = UsbIsoFrameAccumulator(); repeat(7) { scheduler.next(1) }; val remainder = scheduler.remainder
        val expected = (remainder + nominalFeedback * 8000) / UsbIsoFrameAccumulator.DENOMINATOR
        assertEquals(expected.toInt(), scheduler.next(1, nominalFeedback))
    }
    @Test fun latestFeedbackGovernsPacketizationWithoutSourceChanges() { val slow = UsbIsoFrameAccumulator(); val fast = UsbIsoFrameAccumulator(); repeat(8000) { slow.next(1, nominalFeedback * 96 / 100); fast.next(1, nominalFeedback * 104 / 100) }; assertTrue(slow.frames < fast.frames) }
    @Test fun frameCountersAreMonotonic() { val scheduler = UsbIsoFrameAccumulator(); repeat(1000) { val before = scheduler.frames; val count = scheduler.next(1); assertEquals(before + count, scheduler.frames); assertTrue(count in 5..6) } }
    @Test fun bytesPerFrameIsExactlyFour() { assertEquals(4, UsbIsoTiming.BYTES_PER_FRAME) }
    @Test fun poolDepthIsModestAndBounded() { val timing = plan.timing(UsbFsSpeed.HIGH)!!; assertTrue(UsbIsoTiming.OUT_POOL * UsbIsoTiming.OUT_PACKETS * timing.dataIntervalUs <= 32000); assertEquals(2, UsbIsoTiming.FEEDBACK_POOL) }
    @Test fun bootstrapAndHoldoverAreFinite() { assertEquals(100L, UsbIsoTiming.BOOTSTRAP_MS); assertEquals(20L, plan.timing(UsbFsSpeed.HIGH)!!.feedbackHoldoverMs); assertTrue(UsbIsoTiming.DURATION_MS > UsbIsoTiming.BOOTSTRAP_MS) }
    @Test fun unsupportedAccumulatorIntervalIsRejected() { try { UsbIsoFrameAccumulator().next(32768); fail() } catch (_: IllegalArgumentException) {} }
    @Test fun verifiedResultRequiresCleanAccounting() { assertTrue(native().verified); assertFalse(native("framesCompleted" to 999).verified); assertFalse(native("framesSubmitted" to 999).verified) }
    @Test fun sourceScheduledMismatchPreventsSuccess() { assertFalse(native("sourceFramesGenerated" to 999).verified) }
    @Test fun byteFrameAndPacketUrbConsistencyAreRequired() { assertFalse(native("outBytesSubmitted" to 3999, "kernelOutActualBytes" to 3999).verified); assertFalse(native("outUrbsSubmitted" to 24, "outUrbsCompleted" to 24).verified) }
    @Test fun missingNativeSnapshotDoesNotPretendNormalDurationStop() { val result = UsbIsoTransportResult(17, 23, exceptionType = "Unavailable"); assertEquals("UNAVAILABLE", result.stopReason); assertFalse(result.verified) }
    @Test fun packetAndUrbMismatchPreventsSuccess() { assertFalse(native("outPacketsCompleted" to 199).verified); assertFalse(native("outUrbsCompleted" to 24).verified) }
    @Test fun validFeedbackAndLockedSchedulingAreRequired() { assertFalse(native("feedbackPacketsValid" to 0).verified); assertFalse(native("feedbackScheduledPackets" to 0).verified) }
    @Test fun compromisedIntegrityNeverReportsVerified() { UsbIsoNativeStats.reasons.indices.forEach { assertFalse(native("integrityMask" to (1L shl it)).verified) } }
    @Test fun workerMustStopAndOutstandingMustReachZero() { assertFalse(native("workerStopped" to 0).shutdownAccounted); assertFalse(native("outstandingUrbsFinal" to 1).verified) }
    @Test fun stopMustBeNormalDurationCompletion() { (1 until UsbIsoNativeStats.stops.size).forEach { assertFalse(native("stopReason" to it.toLong()).verified) } }
    @Test fun normalStopCannotVerifyAnEarlyAbortedDuration() { assertFalse(native("durationUs" to 900000).verified) }
    @Test fun nonzeroPcmPreventsVerification() { assertFalse(native("pcmByteOr" to 1).verified) }
    @Test fun cancellationAndUnsubmittedFramesRemainExplicitFailures() { assertFalse(native("framesCancelled" to 1).verified); assertFalse(native("framesNotSubmitted" to 1).verified) }
    @Test fun detachAndPermissionFailureInvalidateNativeAuthority() { assertFalse(native().copy(authorityFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED).verified); assertFalse(native().copy(authorityFailure = UsbClockQueryStatus.PERMISSION_LOST).valid) }
    @Test fun snapshotRejectsOldAttachmentAndStreamGeneration() { listOf(snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[1] = 16 }), snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[2] = 22 })).forEach { rejects(it) } }
    @Test fun snapshotRejectsWrongVersionAndSchemaSize() { rejects(snapshot().copy(numbers = snapshot().numbers.copyOf().also { it[0] = 0 })); rejects(snapshot().copy(numbers = longArrayOf(1, 17, 23))) }
    @Test fun unknownStateAndStopCodesAreRejected() { rejects(snapshot("state" to 99)); rejects(snapshot("stopReason" to 99)) }
    @Test fun feedbackEvidencePreservesRawBytesAndCompletionStatus() {
        val result = UsbIsoNativeStats.decode(snapshot().copy(feedback = longArrayOf(nominalFeedback, 4, 0, 1000, 0, 1000)), 17, 23)
        assertEquals(bytes(nominalFeedback), result.feedback.single().bytes); assertEquals(1000L, result.feedback.single().conservativeAgeUs)
    }
    @Test fun packetEvidencePreservesNegativeStatusAndRequestedActualLengths() {
        val result = UsbIsoNativeStats.decode(snapshot().copy(packets = longArrayOf(11, -18, -18, 24, 0, 23, 1)), 17, 23)
        assertEquals(-18, result.packets.single().packetStatus); assertEquals(24L, result.packets.single().requestedLength); assertEquals(0L, result.packets.single().actualLength)
    }
    @Test fun latePacketFromOldStreamIsRejected() { rejects(snapshot().copy(packets = longArrayOf(11, 0, 0, 24, 24, 22, 1))) }
    @Test fun malformedFeedbackEvidenceAndExcessiveExamplesAreRejected() { rejects(snapshot().copy(feedback = longArrayOf(1))); rejects(snapshot().copy(feedback = LongArray(13 * 6))) }
    @Test fun malformedUnsignedFeedbackLengthIsPreservedWithoutClamping() {
        val result = UsbIsoNativeStats.decode(snapshot("integrityMask" to 32).copy(feedback = longArrayOf(1, 0xffffffffL, 0, 1000, 1, 1000)), 17, 23)
        assertEquals(0xffffffffL, result.feedback.single().length); assertNull(result.feedback.single().rateHz)
    }
    @Test fun busyGateIncludesTheWholeSilentTransaction() { assertTrue(UsbAudioDiagnosticState(silentExperiment = UsbSilentTransportExperimentResult(17, 23, resolution, UsbSilentPhase.CONFIGURING)).diagnosticOperationRunning) }
    @Test fun silentVerificationDoesNotAssertFunctionalRecovery() { assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, UsbSilentTransportExperimentResult(17, 23, resolution).functionalRecovery) }

    @Test fun nativeSourceGeneratesAndChecksZeroOnly() {
        val core = source("cpp/usb_iso_core.h"); assertTrue(core.contains("bytes[i] = 0")); assertTrue(core.contains("byteOr |= bytes[i]"))
        val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("urb->buffer_length = static_cast<int>(length)")); assertTrue(native.contains("source.inspect("))
    }
    @Test fun nativeCapacityOverflowFailsWithoutTruncating() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("!packetFits(frames, payload)")); assertTrue(native.contains("fail(CapacityExceeded, Stop::Capacity)")) }
    @Test fun nativeHasNoPerPacketAllocations() { val submit = source("cpp/usb_iso_transport.cpp").substringAfter("bool submit(Slot& slot)").substringBefore("void packetExample"); listOf("resize(", "malloc(", "calloc(", "new ").forEach { assertFalse(submit.contains(it)) } }
    @Test fun nativeQueueUnderrunIsFailureNotDroppedWork() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("outstanding(false) == 0")); assertTrue(native.contains("fail(QueueUnderrun, Stop::Underrun)")) }
    @Test fun nativePreservesFractionAndNeverHardcodesPacketPattern() { val core = source("cpp/usb_iso_core.h"); assertTrue(core.contains("remainder %= denominator")); assertTrue(core.contains("static_cast<uint64_t>(feedback) * 8000")); assertFalse(core.contains("resample")) }
    @Test fun feedbackStalenessIncludesDelayedReaping() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("lastValid = slot.submittedAt")); assertTrue(native.contains("if (stale)")); assertTrue(native.contains("feedbackExpired(")) }
    @Test fun knownIdentityIsRequiredForDiscardAndCompletion() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("!slot.identity.outstanding || slot.identity.discardRequested")); assertTrue(native.contains("slot.urb.get() == completed")); assertTrue(native.contains("fail(UnknownCompletion")); assertTrue(native.contains("fail(DuplicateCompletion")) }
    @Test fun nativeInspectsEveryPacketAndShortActualLength() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("for (unsigned i = 0; i < slot.packets; ++i)")); assertTrue(native.contains("packet.actual_length != packet.length")); assertTrue(native.contains("packet.status != 0")); assertTrue(native.contains("slot.urb->error_count != 0")) }
    @Test fun nativeUsesRealUapiVariableSizedUrbs() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("allocationBytes(sizeof(usbdevfs_urb), sizeof(usbdevfs_iso_packet_desc), count)")); assertTrue(source("cpp/usb_iso_core.h").contains("extra <= SIZE_MAX - base")); assertTrue(native.contains("USBDEVFS_SUBMITURB")); assertTrue(native.contains("USBDEVFS_REAPURBNDELAY")); assertTrue(native.contains("USBDEVFS_DISCARDURB")) }
    @Test fun nativeRetainsUnreapedPoolsUntilJavaConnectionClose() { val native = source("cpp/usb_iso_transport.cpp"); assertTrue(native.contains("if (!connectionClosed && session->outstanding(false) + session->outstanding(true) != 0) return JNI_FALSE")); assertFalse(native.contains("::close(")); assertFalse(native.contains(".detach(")) }
    @Test fun kotlinOnlyMonitorsAndAlwaysAwaitsNativeWorker() { val kotlin = source("java/io/github/rsgarrido/sazanami/player/usb/UsbNativeSilentTransport.kt"); assertTrue(kotlin.contains("withContext(NonCancellable)")); assertTrue(kotlin.contains("worker.await()")); assertFalse(kotlin.contains("framesPerInterval")); assertFalse(kotlin.contains("bulkTransfer")) }
    @Test fun forbiddenAudioAndResetPathsAreAbsentFromNewTransport() {
        val native = source("cpp/usb_iso_transport.cpp") + source("cpp/usb_iso_core.h")
        listOf("USBDEVFS_RESET", "USBDEVFS_SETCONFIGURATION", "USBDEVFS_CONNECT", "USBDEVFS_DISCONNECT", "sin(", "libusb", "AudioTrack", "AudioSink").forEach { assertFalse(native.contains(it)) }
    }
    @Test fun actionIsDebugOnlyAndNeverAutomaticPlayback() { val controller = source("java/io/github/rsgarrido/sazanami/player/usb/UsbAudioController.kt"); assertTrue(controller.contains("if (BuildConfig.DEBUG) runConfigurationDiagnostic(identity, silentRun = true)")); assertFalse(controller.contains("setPreferredMixerAttributes")); assertFalse(controller.contains("AudioTrack(")) }

    @Test fun configurationIsReusedBeforeEndpointWorkAndIdleAfterward() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events)
        val result = experiment(fake, backend)
        assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, result.status)
        assertTrue(fake.events.indexOf("native") > fake.events.indexOf("setAlt:9"))
        assertTrue(fake.events.lastIndexOf("setAlt:0") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("restoreClock") > fake.events.indexOf("nativeStopped"))
        assertTrue(fake.events.indexOf("releaseAS") < fake.events.indexOf("releaseAC")); assertTrue(fake.events.indexOf("close") > fake.events.indexOf("nativeStopped"))
    }
    @Test fun endpointLifecyclePhasesPrecedeSharedRestoration() {
        val fake = ConfigurationFake(); val phases = mutableListOf<UsbSilentPhase>()
        runBlocking { UsbSilentTransportExperiment.run(resolution, 17, 23, fake, { _, _ -> BackendFake(fake.events) },
            onPhase = { phases += it; if (it == UsbSilentPhase.RESTORING_USB) assertFalse(fake.events.contains("restoreClock")) }) }
        assertEquals(listOf(UsbSilentPhase.STARTING_TRANSPORT, UsbSilentPhase.TRANSPORT_STOPPED, UsbSilentPhase.RESTORING_USB), phases)
    }
    @Test fun undeclaredGetInterfaceDoesNotBlockSilentTransport() { val fake = ConfigurationFake().apply { reportedAlternate = 6 }; assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, experiment(fake, BackendFake(fake.events)).status) }
    @Test fun declaredContradictionPreventsAnyEndpointWork() { val fake = ConfigurationFake().apply { reportedAlternate = 9 }; val result = experiment(fake, BackendFake(fake.events)); assertFalse(fake.events.contains("native")); assertFalse(result.transport?.verified == true) }
    @Test fun failedConfigurationPreventsAnyEndpointWork() { val fake = ConfigurationFake().apply { claimAs = false }; experiment(fake, BackendFake(fake.events)); assertFalse(fake.events.contains("native")) }
    @Test fun nonHighSpeedBlocksBeforeClaims() { val fake = ConfigurationFake().apply { speed = UsbFsSpeed.FULL }; val result = experiment(fake, BackendFake(fake.events)); assertEquals(UsbSilentStatus.BLOCKED, result.status); assertFalse(fake.events.contains("claimAC")) }
    @Test fun unresolvedFeedbackBlocksBeforeClaims() { val fake = ConfigurationFake(); experiment(fake, BackendFake(fake.events), resolve(withoutFeedback())); assertFalse(fake.events.contains("claimAC")); assertFalse(fake.events.contains("native")) }
    @Test fun unsafeEndpointShutdownSkipsAltClockAndExplicitReleasesUntilClose() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { safe = false; result = native("outstandingUrbsFinal" to 1, "integrityMask" to (1L shl 16)) }
        val result = experiment(fake, backend)
        assertEquals(UsbSilentStatus.FAILED, result.status); assertEquals(1, fake.events.count { it == "setAlt:0" })
        assertFalse(fake.events.contains("restoreClock")); assertFalse(fake.events.contains("releaseAS")); assertFalse(fake.events.contains("releaseAC"))
        assertTrue(fake.events.indexOf("close") < fake.events.indexOf("poolReleasedAfterClose")); assertTrue(result.connectionCloseRequired)
        assertEquals(1L, result.connectionCloseDisposedUrbs); assertEquals(0L, result.outstandingAfterConnectionClose)
    }
    @Test fun nativeDetachInvalidatesConfigurationAndSkipsWrites() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to (1L shl 13)).copy(authorityFailure = UsbClockQueryStatus.DEVICE_DISCONNECTED) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, result.status); assertFalse(fake.events.contains("restoreClock"))
    }
    @Test fun nativeBadFdAuthorityPreventsAnyRestorationWrites() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to 2).copy(authorityFailure = UsbClockQueryStatus.TRANSPORT_ERROR) }
        assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, experiment(fake, backend).status); assertFalse(fake.events.contains("restoreClock")); assertEquals(1, fake.events.count { it == "setAlt:0" })
    }
    @Test fun oldStreamResultCannotPublishCountersIntoCurrentStream() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native().copy(streamGeneration = 22) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.NON_AUTHORITATIVE, result.status); assertTrue(result.transport!!.counters.isEmpty()); assertFalse(fake.events.contains("restoreClock"))
    }
    @Test fun transportFailurePreservesPartialCountersAndRestoresConfiguration() {
        val fake = ConfigurationFake(); val backend = BackendFake(fake.events).apply { result = native("integrityMask" to 8, "framesCompleted" to 900) }
        val result = experiment(fake, backend); assertEquals(UsbSilentStatus.FAILED, result.status); assertEquals(900L, result.transport!!.count("framesCompleted")); assertTrue(fake.events.contains("restoreClock"))
        assertEquals(UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED, result.configuration!!.targetStatus)
    }
    @Test fun restorationFailureDoesNotInventTransportFailureOrAndroidRecovery() {
        val fake = ConfigurationFake().apply { restoreFails = true }; val result = experiment(fake, BackendFake(fake.events))
        assertEquals(UsbSilentStatus.SILENT_TRANSPORT_VERIFIED, result.status); assertEquals(UsbConfigurationRestorationStatus.PARTIAL, result.configuration!!.restorationStatus); assertEquals(UsbFunctionalAudioRecovery.NOT_VERIFIED, result.functionalRecovery)
    }
    @Test fun originalClockIsRestoredDynamically() { val fake = ConfigurationFake().apply { rate = 96000 }; val result = experiment(fake, BackendFake(fake.events)); assertEquals(96000L, result.configuration!!.originalClockReadback!!.currentFrequencyHz) }
    @Test fun reportIncludesPartialFailureShutdownAndSeparateRecovery() {
        val fake = ConfigurationFake(); val result = experiment(fake, BackendFake(fake.events).apply { this.result = native("integrityMask" to 8, "framesCompleted" to 900) })
        val text = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 17, silentExperiment = result))
        listOf("Silent Direct USB Transport — Session 7", "DIGITAL ZERO ONLY", "framesCompleted: 900", "COMPROMISED", "worker stopped=true", "Functional normal USB playback: NOT_VERIFIED", "NOT IMPLEMENTED / NOT ASSERTED").forEach { assertTrue(text.contains(it)) }
    }
    @Test fun staleAttachmentReportSuppressesTransportEvidence() { val fake = ConfigurationFake(); val result = experiment(fake, BackendFake(fake.events)); val text = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(generation = 18, silentExperiment = result)); assertTrue(text.contains("previous Session 7 evidence omitted")); assertFalse(text.contains("framesCompleted: 1000")) }

    private fun experiment(connection: ConfigurationFake, backend: BackendFake, selected: UsbSilentTransportResolution = resolution) = runBlocking {
        UsbSilentTransportExperiment.run(selected, 17, 23, connection, { _, _ -> backend })
    }
    private fun snapshot(vararg changed: Pair<String, Long>): UsbIsoNativeSnapshot {
        val values = mutableMapOf("version" to 1L, "generation" to 17L, "streamGeneration" to 23L, "started" to 1L, "state" to 7L,
            "workerStopped" to 1L, "durationUs" to 1008000L, "sourceFramesGenerated" to 1000L, "framesScheduled" to 1000L, "framesSubmitted" to 1000L, "framesCompleted" to 1000L,
            "outUrbsSubmitted" to 25L, "outUrbsCompleted" to 25L, "outPacketsSubmitted" to 200L, "outPacketsCompleted" to 200L,
            "feedbackUrbsSubmitted" to 100L, "feedbackUrbsCompleted" to 100L, "feedbackPacketsValid" to 98L, "feedbackScheduledPackets" to 190L, "outBytesSubmitted" to 4000L, "kernelOutActualBytes" to 4000L)
        values.putAll(changed)
        return UsbIsoNativeSnapshot(UsbIsoNativeStats.fields.map { values[it] ?: 0L }.toLongArray(), longArrayOf(), longArrayOf())
    }
    private fun native(vararg changed: Pair<String, Long>) = UsbIsoNativeStats.decode(snapshot(*changed), 17, 23)
    private fun rejects(packet: UsbIsoNativeSnapshot) { try { UsbIsoNativeStats.decode(packet, 17, 23); fail("Malformed evidence accepted") } catch (_: IllegalArgumentException) {} }
    private inner class BackendFake(private val events: MutableList<String>) : UsbSilentTransportBackend {
        var result = native(); var safe = true
        override val usbCleanupSafe get() = safe
        override var memoryReleased = true
        override suspend fun run(): UsbIsoTransportResult { events += "native"; events += "nativeStopped"; memoryReleased = safe; return result }
        override fun connectionClosed(succeeded: Boolean) { if (succeeded && !safe) { events += "poolReleasedAfterClose"; memoryReleased = true } }
    }
    private inner class ConfigurationFake : UsbAudioConfigurationTransport {
        val events = mutableListOf<String>(); var claimAs = true; var reportedAlternate: Int? = null; var rate = 48000L; var alt = 0
        var restoreFails = false; var speed = UsbFsSpeed.HIGH; var ac = false; var streaming = false; var closed = false; var now = 0L
        override fun interfaces() = android(model).mapIndexed { index, it -> UsbAudioClaimInterface(index, it.number, it.alternate, it.interfaceClass, it.subclass, it.protocol) }
        override fun connectionValid() = !closed
        override fun snapshot() = UsbFsOwnershipDiagnostics(17, configurationPlan.probe, UsbFsProbeStatus.RECORDED,
            configurationPlan.probe.interfaces.map { UsbFsDriverBinding(it, UsbFsIoctlResult(UsbFsIoctlOperation.GETDRIVER, 0, 0, null), if (ac || streaming) "usbfs" else "synthetic") },
            UsbFsSpeedEvidence(UsbFsIoctlResult(UsbFsIoctlOperation.GET_SPEED, 3, 0, null), speed), androidAudio = frameworkSnapshot())
        override fun frameworkSnapshot() = UsbAndroidAudioDiagnostics()
        override fun lastControlEvidence(): UsbFsControlEvidence? = null
        override fun claim(request: UsbAudioForcedClaimRequest): Boolean { events += "claimAC"; ac = true; return true }
        override fun claimStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean { check(ac); events += "claimAS"; streaming = claimAs; return claimAs }
        override fun releaseStreaming(request: UsbAudioConfigurationPlan.UsbAudioStreamingClaimRequest): Boolean { events += "releaseAS"; streaming = false; return true }
        override fun release(request: UsbAudioForcedClaimRequest): Boolean { events += "releaseAC"; ac = false; return true }
        private fun u32(value: Long) = ByteArray(4) { (value shr (it * 8)).toByte() }
        override fun read(request: UsbClockReadRequest, buffer: ByteArray): Int {
            check(ac)
            val data = when { request.rangeHeaderOnly -> byteArrayOf(3, 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> byteArrayOf(3, 0) + listOf(44100L, 48000L, 96000L).flatMap { (u32(it) + u32(it) + u32(0)).toList() }.toByteArray()
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(rate)
                else -> byteArrayOf(1) }
            data.copyInto(buffer); return data.size
        }
        override fun control(request: UsbConfigurationControlRequest): UsbConfigurationControlEvidence {
            check(ac && streaming)
            val evidence = if (request.operation == UsbConfigurationControlOperation.GET_INTERFACE) UsbFsControlEvidence(1, 1, 0, null, listOf(reportedAlternate ?: alt))
                else { val restore = request.operation == UsbConfigurationControlOperation.ORIGINAL_CLOCK_RESTORE
                    events += if (restore) "restoreClock" else "targetClock"
                    if (!restore || !restoreFails) rate = request.rateHz!!
                    UsbFsControlEvidence(4, 4, 0, null, request.payload) }
            return UsbConfigurationControlEvidence.decode(request, evidence)
        }
        override fun setAlternate(request: UsbStreamingAlternateRequest): UsbStreamingAlternateEvidence { check(ac && streaming); events += "setAlt:${request.alternate}"; alt = request.alternate
            return UsbStreamingAlternateEvidence(request, UsbFsIoctlResult(UsbFsIoctlOperation.SETINTERFACE, 0, 0, null), UsbClockQueryStatus.SUCCESS) }
        override fun close() { events += "close"; closed = true }
        override fun monotonicMs() = now
        override suspend fun pause(milliseconds: Long) { now += milliseconds }
    }
    private fun descriptors(): UsbAudioDescriptorModel {
        val ac = UsbInterfaceDescriptor(UsbInterfaceKey(18, 10, 0), 0, 1, 1, 0x20, audioControl = UsbAudioControlDescriptor(0x0200,
            entities = listOf(UsbAudioEntity(43, UsbAudioEntityKind.CLOCK_SOURCE, controls = 7),
                UsbAudioEntity(44, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(43), terminalType = 0x0101),
                UsbAudioEntity(45, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(44), clockSources = listOf(43), terminalType = 0x0301))))
        val idle = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 0), 0, 1, 2, 0x20)
        val target = UsbInterfaceDescriptor(UsbInterfaceKey(18, 11, 9), 2, 1, 2, 0x20,
            endpoints = listOf(UsbEndpointDescriptor(11, 5, 776, 1, synchronizationAddress = 0x8e, audio = UsbAudioEndpointDescriptor(0, 0, 0, 0)),
                UsbEndpointDescriptor(0x8e, 0x11, 4, 4)), audioStreaming = UsbAudioStreamingDescriptor(44, 0, 1, 1, 2, 3), audioFormat = UsbAudioFormatDescriptor(1, 2, 16))
        return UsbAudioDescriptorModel(200, null, listOf(UsbConfigurationDescriptor(18, 1, 182, 3, 0x80, 50)), listOf(UsbInterfaceAssociation(18, 10, 2, 1, 0, 0x20)),
            listOf(UsbInterfaceDescriptor(UsbInterfaceKey(18, 3, 0), 0, 3, 0, 0), ac, idle, target), emptyList(), emptyList())
    }
}
