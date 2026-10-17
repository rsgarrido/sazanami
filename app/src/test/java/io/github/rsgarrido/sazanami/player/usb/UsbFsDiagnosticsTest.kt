package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic UAPI packets and descriptor facts only. No assertion about KA15 driver names/errno. */
class UsbFsDiagnosticsTest {
    private val identity = UsbAudioDeviceIdentity("/dev/bus/usb/private-test-key", 42, 0x1234, 0x5678)
    private val model = descriptorModel()
    private val androidInterfaces = model.interfaces.map {
        UsbAndroidInterfaceSnapshot(it.key.number, it.key.alternate, it.interfaceClass, it.subclass, it.protocol, emptyList())
    }
    private val plan = UsbFsProbePlan.create(model, androidInterfaces)

    @Test fun planInspectsAllExposedInterfaceNumbersOnceAndDerivesRolesFromDescriptors() {
        assertEquals(listOf(4, 7, 8, 9, 10), plan.interfaces.map { it.number })
        assertEquals(listOf(UsbFsInterfaceRole.HID, UsbFsInterfaceRole.AUDIOCONTROL,
            UsbFsInterfaceRole.AUDIOSTREAMING, UsbFsInterfaceRole.MIDI, UsbFsInterfaceRole.OTHER), plan.interfaces.map { it.roles.single() })
        assertEquals(listOf(1, 2, 3), plan.interfaces.single { it.number == 8 }.alternates)
        assertEquals(7, plan.audioControl!!.number)
        assertEquals(29, plan.resolution.target!!.clockEntityId)
    }

    @Test fun ambiguousOwnershipSkipsRawClaimButKeepsReadOnlyInterfaceInspection() {
        val ambiguous = UsbFsProbePlan.create(model.copy(associations = emptyList()), androidInterfaces)
        assertNull(ambiguous.audioControl)
        assertEquals(plan.interfaces, ambiguous.interfaces)
        assertTrue(ambiguous.warnings.isNotEmpty())
        val decoded = UsbFsEvidence.decode(packet(ambiguous), ambiguous, 3)
        assertEquals(UsbFsProbeStatus.RECORDED, decoded.status)
        assertNull(decoded.claim)
        assertNull(decoded.release)
    }

    @Test fun missingAndroidAcOrStreamingSubstitutionCannotCreateRawClaimTarget() {
        val missing = UsbFsProbePlan.create(model, androidInterfaces.filterNot { it.number == 7 })
        assertNull(missing.audioControl)
        val wrong = UsbFsProbePlan.create(model, androidInterfaces.map { if (it.number == 7) it.copy(subclass = 2) else it })
        assertNull(wrong.audioControl)
        assertTrue(wrong.warnings.any { it.contains("raw claim skipped") })
    }

    @Test fun androidExposedInterfaceMissingFromParsedDescriptorsStillGetsInspectionWithUnknownRole() {
        val expanded = UsbFsProbePlan.create(model, androidInterfaces + UsbAndroidInterfaceSnapshot(20, 0, 3, 0, 0, emptyList()))
        assertEquals(listOf(UsbFsInterfaceRole.OTHER), expanded.interfaces.single { it.number == 20 }.roles)
        assertTrue(expanded.warnings.any { it.contains("Interface 20") })
    }

    @Test fun knownKernelSpeedValuesMapWithoutDescriptorInference() {
        listOf(0 to UsbFsSpeed.UNKNOWN, 1 to UsbFsSpeed.LOW, 2 to UsbFsSpeed.FULL,
            3 to UsbFsSpeed.HIGH, 4 to UsbFsSpeed.WIRELESS, 5 to UsbFsSpeed.SUPER,
            6 to UsbFsSpeed.SUPER_PLUS).forEach { (raw, expected) ->
            assertEquals(expected, UsbFsEvidence.speed(raw))
            val result = decode(packet(speedRaw = raw, speedLabel = expected.name))
            assertEquals(raw, result.speed!!.rawSpeed)
            assertEquals(expected, result.speed!!.mapped)
        }
    }

    @Test fun unknownSpeedValueIsPreservedExactly() {
        val result = decode(packet(speedRaw = 77, speedLabel = "UNKNOWN"))
        assertEquals(77, result.speed!!.rawSpeed)
        assertEquals(UsbFsSpeed.UNKNOWN, result.speed!!.mapped)
        val report = report(result)
        assertTrue(report.contains("raw speed: 77; mapped: UNKNOWN"))
    }

    @Test fun failedSpeedIoctlIsNotMistakenForARawSpeedValue() {
        val result = decode(packet(speedRaw = -1, speedErrno = 25, speedErrnoName = "ENOTTY", speedLabel = "UNKNOWN"))
        assertNull(result.speed!!.rawSpeed)
        assertEquals(-1, result.speed!!.result.returnValue)
        assertEquals(25, result.speed!!.result.errno)
        assertEquals("ENOTTY", result.speed!!.result.errnoName)
        assertTrue(report(result).contains("raw speed: unavailable; mapped: UNKNOWN"))
    }

    @Test fun numericErrnoAndNativeSymbolArePreservedIncludingBusyAndUnknown() {
        listOf(16 to "EBUSY", 19 to "ENODEV", 2 to "ENOENT", 22 to "EINVAL", 1 to "EPERM", 13 to "EACCES").forEach { (number, name) ->
            assertEquals(name, UsbFsEvidence.errnoName(number, name))
            val result = decode(packet(claimErrno = number, claimErrnoName = name))
            assertEquals(number, result.claim!!.errno)
            assertEquals(name, result.claim!!.errnoName)
        }
        val unknown = decode(packet(claimErrno = 777, claimErrnoName = "UNKNOWN"))
        assertEquals(777, unknown.claim!!.errno)
        assertEquals("UNKNOWN", unknown.claim!!.errnoName)
        assertNull(UsbFsEvidence.errnoName(0, "EBUSY"))
        assertEquals("UNKNOWN", UsbFsEvidence.errnoName(777, "private text"))
    }

    @Test fun busyClaimAndSameInterfaceNamedDriverAreReportedTogetherWithoutExclusiveCausation() {
        val result = decode(packet())
        val explanation = UsbFsEvidence.claimExplanation(result)
        assertTrue(explanation.contains("EBUSY"))
        assertTrue(explanation.contains("same AC interface"))
        assertTrue(explanation.contains("do not establish exclusive causation"))
        val report = report(result)
        assertTrue(report.contains("driver: synthetic_control_driver"))
        assertTrue(report.contains("USBDEVFS_CLAIMINTERFACE: return=-1 errno=16 errno name=EBUSY"))
        assertFalse(report.contains("definitely caused"))
    }

    @Test fun busyWithoutAcDriverEvidenceDoesNotInferOwnerFromOtherInterfaceBindings() {
        val original = packet()
        val acIndex = plan.interfaces.indexOfFirst { it.number == 7 }
        val numbers = original.numbers.copyOf().apply { this[3 + acIndex * 3] = -1; this[4 + acIndex * 3] = 61 }
        val names = original.errnoNames.copyOf().apply { this[acIndex] = "ENODATA" }
        val result = decode(original.copy(numbers = numbers, errnoNames = names))
        assertTrue(UsbFsEvidence.claimExplanation(result).contains("owner is not inferred"))
        assertNull(result.driverBindings[acIndex].driver)
    }

    @Test fun nonBusyClaimErrorRemainsNeutralEvenWithNamedDriver() {
        val result = decode(packet(claimErrno = 13, claimErrnoName = "EACCES"))
        assertTrue(UsbFsEvidence.claimExplanation(result).contains("EACCES"))
        assertFalse(UsbFsEvidence.claimExplanation(result).contains("currently busy/owned"))
        assertTrue(report(result).contains("errno=13 errno name=EACCES"))
    }

    @Test fun noDriverDataPreservesFailedGetdriverRatherThanInventingAnEmptySuccessfulBinding() {
        val original = packet()
        val numbers = original.numbers.copyOf().apply { this[3] = -1; this[4] = 61 }
        val names = original.errnoNames.copyOf().apply { this[0] = "ENODATA" }
        val result = decode(original.copy(numbers = numbers, errnoNames = names))
        assertEquals(-1, result.driverBindings.first().result.returnValue)
        assertNull(result.driverBindings.first().driver)
        assertTrue(UsbFsEvidence.driverExplanation(result.driverBindings.first()).contains("unbound or unavailable"))
        assertTrue(report(result).contains("USBDEVFS_GETDRIVER: return=-1 errno=61 errno name=ENODATA"))
    }

    @Test fun successfulRawClaimRequiresImmediateReleaseEvidence() {
        val result = decode(packet(claimReturn = 0, claimErrno = 0, claimErrnoName = "", releaseAttempted = true))
        assertEquals(UsbFsProbeStatus.RECORDED, result.status)
        assertTrue(result.claim!!.succeeded)
        assertTrue(result.release!!.succeeded)
        assertEquals(UsbFsIoctlOperation.RELEASEINTERFACE, result.release!!.operation)
        val missing = decode(packet(claimReturn = 0, claimErrno = 0, claimErrnoName = "", releaseAttempted = false))
        assertEquals(UsbFsProbeStatus.MALFORMED_NATIVE_RESULT, missing.status)
        assertTrue(missing.claim!!.succeeded)
        assertNull(missing.release)
        assertTrue(missing.detail!!.contains("release evidence"))
    }

    @Test fun releaseFailurePreservesItsOwnReturnAndErrno() {
        val result = decode(packet(claimReturn = 0, claimErrno = 0, claimErrnoName = "", releaseAttempted = true,
            releaseReturn = -1, releaseErrno = 5, releaseErrnoName = "EIO"))
        assertEquals(UsbFsProbeStatus.RECORDED, result.status)
        assertTrue(result.claim!!.succeeded)
        assertFalse(result.release!!.succeeded)
        assertTrue(report(result).contains("release attempted: yes"))
        assertTrue(report(result).contains("USBDEVFS_RELEASEINTERFACE: return=-1 errno=5 errno name=EIO"))
    }

    @Test fun kernelEnodevInvalidatesAuthorityBeforeAndroidBroadcastOrEnumerationChanges() {
        val result = decode(packet(claimErrno = 19, claimErrnoName = "ENODEV"))
        assertEquals(UsbFsProbeStatus.DEVICE_DISCONNECTED, result.status)
        assertEquals(19, result.claim!!.errno) // Raw call evidence is retained in the result, not promoted to current truth.
        val report = report(result.copy(androidAudio = androidAudio()))
        assertTrue(report.contains("Ownership evidence is non-authoritative and omitted"))
        assertFalse(report.contains("synthetic_control_driver"))
        assertFalse(report.contains("USB output candidate 1"))
        val detachedRelease = decode(packet(claimReturn = 0, claimErrno = 0, claimErrnoName = "", releaseAttempted = true,
            releaseReturn = -1, releaseErrno = 19, releaseErrnoName = "ENODEV"))
        assertEquals(UsbFsProbeStatus.DEVICE_DISCONNECTED, detachedRelease.status)
        assertTrue(detachedRelease.claim!!.succeeded)
        assertEquals(19, detachedRelease.release!!.errno)
    }

    @Test fun malformedNativeVersionLengthsOrderAndFlagsCannotBecomeOwnershipTruth() {
        val original = packet()
        val variants = listOf(original.copy(numbers = intArrayOf()),
            original.copy(numbers = original.numbers.copyOf().apply { this[0] = 99 }),
            original.copy(numbers = original.numbers.copyOf().apply { this[2] = 99 }),
            original.copy(driverNames = emptyArray()), original.copy(errnoNames = emptyArray()),
            original.copy(speedName = "LOW"), original.copy(numbers = original.numbers.copyOf().apply { this[size - 6] = 2 }))
        variants.forEach { assertEquals(UsbFsProbeStatus.MALFORMED_NATIVE_RESULT, decode(it).status) }
    }

    @Test fun staleGenerationRejectsPublicationAndReportOmitsPriorEvidence() {
        val gate = UsbAudioGenerationGate()
        val token = gate.select(identity)!!
        val result = UsbFsEvidence.decode(packet(), plan, token.generation)
        gate.select(null)
        val replacement = gate.select(identity)!!
        assertFalse(gate.accepts(token))
        assertEquals(UsbClockQueryStatus.STALE_GENERATION, gate.clockAccessFailure(token, true, true))
        val report = UsbAudioDiagnosticReport.format(state(result).copy(generation = replacement.generation))
        assertTrue(report.contains("Stale generation; prior ownership/speed evidence omitted"))
        assertFalse(report.contains("synthetic_control_driver"))
        assertFalse(report.contains("USBDEVFS_CLAIMINTERFACE: return="))
    }

    @Test fun detachAndPermissionLossRemoveAuthorityAndAllSecondaryCandidates() {
        val result = decode(packet()).copy(androidAudio = androidAudio())
        listOf(UsbClockQueryStatus.DEVICE_DISCONNECTED, UsbClockQueryStatus.PERMISSION_LOST,
            UsbClockQueryStatus.STALE_GENERATION, UsbClockQueryStatus.TRANSPORT_ERROR).forEach { failure ->
            val invalid = result.invalidate(failure, "Access changed")
            assertTrue(invalid.driverBindings.isEmpty())
            assertNull(invalid.speed)
            assertNull(invalid.claim)
            assertNull(invalid.androidAudio)
            val report = report(invalid)
            assertTrue(report.contains("non-authoritative"))
            assertFalse(report.contains("synthetic_control_driver"))
            assertFalse(report.contains("USB output candidate 1"))
        }
    }

    @Test fun audioManagerCapabilitiesStaySecondaryAndDoNotCreateClockOrDirectRateEvidence() {
        val result = decode(packet()).copy(androidAudio = androidAudio())
        val report = report(result)
        assertTrue(report.contains("USBFS Ownership Diagnostics"))
        assertTrue(report.contains("Android AudioDeviceInfo"))
        assertTrue(report.contains("SECONDARY framework evidence"))
        assertTrue(report.contains("sample rates: [384000]"))
        assertTrue(report.contains("encodings: [2, 4, 21]"))
        assertTrue(report.contains("channel counts: [2]"))
        assertTrue(report.contains("0x80000003"))
        assertTrue(report.contains("Rate range not yet queried"))
        assertFalse(report.contains("384000 Hz: supported by returned range"))
        assertFalse(report.contains("BitPerfectActive"))
        assertFalse(report.contains("BitPerfectEnabled"))
    }

    @Test fun reportStatesNoForcedDetachAndKeepsRawKernelAndFrameworkSectionsDistinct() {
        val report = report(decode(packet()).copy(androidAudio = androidAudio()))
        assertTrue(report.contains("Forced detach attempted: NO"))
        assertTrue(report.contains("raw speed: 3; mapped: HIGH"))
        assertTrue(report.contains("Interface 7 (AUDIOCONTROL)"))
        assertTrue(report.contains("USBDEVFS_GETDRIVER: return=0 errno=0"))
        assertTrue(report.indexOf("USBFS Ownership Diagnostics") < report.indexOf("Android AudioDeviceInfo"))
        assertTrue(report.contains("no unique association with the selected UsbDevice"))
        assertFalse(report.contains(identity.deviceName))
        assertFalse(report.contains("snd-usb-audio definitely"))
    }

    @Test fun addressPrivacyAllowsOnlyNumericAlsaCoordinatesAndReportSanitizesInjection() {
        assertEquals("card=3;device=0", UsbFsEvidence.safeAudioAddress("card=3;device=0"))
        assertEquals("card=3;device=0;", UsbFsEvidence.safeAudioAddress("card=3;device=0;"))
        listOf("/dev/bus/usb/private", "serial=PRIVATE", "card=1;device=0;serial=PRIVATE", "AA:BB:CC:DD:EE:FF", "card=1;device=0\nPRIVATE").forEach {
            assertNull(UsbFsEvidence.safeAudioAddress(it))
        }
        val audio = androidAudio().copy(outputs = androidAudio().outputs.map { it.copy(product = "Synthetic\nINJECTED", safeAddress = "/music/private.flac") })
        val report = report(decode(packet()).copy(androidAudio = audio))
        assertTrue(report.contains("Synthetic INJECTED"))
        assertFalse(report.contains("\nINJECTED"))
        assertFalse(report.contains("/music/private.flac"))
    }

    @Test fun emptyFrameworkArraysAreNotLabeledAsUnsupportedUsbCapabilities() {
        val output = androidAudio().outputs.single().copy(sampleRates = emptyList(), encodings = emptyList(), channelCounts = emptyList())
        val report = report(decode(packet()).copy(androidAudio = UsbAndroidAudioDiagnostics(listOf(output))))
        assertTrue(report.contains("Android reports arbitrary rates"))
        assertTrue(report.contains("Android reports arbitrary encodings"))
        assertTrue(report.contains("Android reports arbitrary counts"))
    }

    @Test fun nativeProbeStateRetainsSameGenerationClockEvidenceAndBlocksCompetingActions() {
        val clock = UsbAudioClockControl.unavailable(3, plan.resolution, UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR, "Synthetic baseline")
        val before = state(decode(packet())).copy(clockDiagnostics = clock)
        val during = before.copy(usbFsDiagnostics = UsbFsOwnershipDiagnostics(3, plan, UsbFsProbeStatus.QUERYING))
        assertTrue(during.diagnosticOperationRunning)
        assertTrue(during.clockDiagnostics === clock)
        assertEquals(3L, during.generation)
        val after = during.copy(usbFsDiagnostics = decode(packet()))
        assertFalse(after.diagnosticOperationRunning)
        val report = UsbAudioDiagnosticReport.format(after)
        assertTrue(report.contains("Synthetic baseline"))
        assertTrue(report.contains("USBDEVFS_CLAIMINTERFACE"))
    }

    private fun decode(packet: UsbFsNativeResult) = UsbFsEvidence.decode(packet, plan, 3)
    private fun report(result: UsbFsOwnershipDiagnostics) = UsbAudioDiagnosticReport.format(state(result))
    private fun state(result: UsbFsOwnershipDiagnostics) = UsbAudioDiagnosticState(serviceAvailable = true,
        status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ, devices = listOf(UsbAudioDeviceSnapshot(identity,
            "Test manufacturer", "Synthetic USB device", 0, androidInterfaces, true)), selectedIdentity = identity,
        generation = result.generation, descriptors = model, usbFsDiagnostics = result)
    private fun androidAudio() = UsbAndroidAudioDiagnostics(listOf(UsbAndroidAudioOutput("Synthetic USB output", 22, 80,
        "card=3;device=0", listOf(384_000), listOf(2, 4, 21), listOf(2), listOf(0x80000003.toInt()), listOf(3))))

    private fun packet(selectedPlan: UsbFsProbePlan = plan, speedRaw: Int = 3, speedErrno: Int = 0,
        speedErrnoName: String = "", speedLabel: String = "HIGH", claimReturn: Int = -1,
        claimErrno: Int = 16, claimErrnoName: String = "EBUSY", releaseAttempted: Boolean = false,
        releaseReturn: Int = 0, releaseErrno: Int = 0, releaseErrnoName: String = ""): UsbFsNativeResult {
        val claimAttempted = selectedPlan.audioControl != null
        val numbers = listOf(UsbFsEvidence.NATIVE_API_VERSION, selectedPlan.interfaces.size) + selectedPlan.interfaces.flatMap { listOf(it.number, 0, 0) } +
            listOf(speedRaw, speedErrno, if (claimAttempted) 1 else 0, if (claimAttempted) claimReturn else 0,
                if (claimAttempted) claimErrno else 0, if (releaseAttempted) 1 else 0, releaseReturn, releaseErrno)
        val drivers = selectedPlan.interfaces.map { if (it.number == 7) "synthetic_control_driver" else "synthetic_driver_${it.number}" }.toTypedArray()
        val errors = (List(selectedPlan.interfaces.size) { "" } + listOf(speedErrnoName,
            if (claimAttempted) claimErrnoName else "", releaseErrnoName)).toTypedArray()
        return UsbFsNativeResult(numbers.toIntArray(), drivers, errors, speedLabel)
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
        val interfaces = listOf(UsbInterfaceDescriptor(UsbInterfaceKey(18, 4, 0), 0, 3, 0, 0), ac) + streams +
            UsbInterfaceDescriptor(UsbInterfaceKey(18, 9, 0), 0, 1, 3, 0) + UsbInterfaceDescriptor(UsbInterfaceKey(18, 10, 0), 0, 0xff, 0, 0)
        return UsbAudioDescriptorModel(300, null, listOf(UsbConfigurationDescriptor(18, 1, 282, 5, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 7, 2, 1, 0, 0x20)), interfaces, emptyList(), emptyList())
    }
}
