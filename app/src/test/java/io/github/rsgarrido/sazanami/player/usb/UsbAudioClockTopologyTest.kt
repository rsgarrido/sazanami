package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Typed, synthetic descriptor facts; interface/entity numbers deliberately differ from KA15. */
class UsbAudioClockTopologyTest {
    private val acKey = UsbInterfaceKey(18, 7, 0)
    private val clockId = 29
    private val inputId = 33
    private val featureId = 41
    private val outputId = 47

    @Test fun allPlaybackAlternatesSharingOneClockResolveOneTargetAndOneQuerySet() {
        val resolution = UsbAudioTopology.resolveClockTarget(model())
        assertTrue(resolution.issues.isEmpty())
        assertEquals(3, resolution.playbackCandidateCount)
        val target = resolution.target!!
        assertEquals(acKey, target.audioControl)
        assertEquals(clockId, target.clockEntityId)
        assertEquals(3, target.playbackPaths.size)
        var calls = 0
        UsbAudioClockControl.query(resolution, 1, UsbClockReadTransport { _, _ -> calls++; -1 })
        // CUR frequency + failed RANGE header + CUR validity, once for the shared target.
        assertEquals(3, calls)
    }

    @Test fun descriptorResolvedAcTargetIsTheOnlyEligibleAndroidClaim() {
        val target = UsbAudioTopology.resolveClockTarget(model()).target!!
        val interfaces = listOf(UsbAudioClaimInterface(0, 0, 0, 3, 0, 0),
            UsbAudioClaimInterface(1, 8, 1, 1, 2, 0x20), UsbAudioClaimInterface(2, 7, 0, 1, 1, 0x20),
            UsbAudioClaimInterface(3, 9, 0, 1, 1, 0x20))
        val request = UsbAudioControlClaimRequest.resolve(target, interfaces)!!
        assertEquals(acKey.number, request.audioControl.number)
        assertEquals(0, request.audioControl.alternate)
        assertEquals(false, request.force)
    }

    @Test fun missingIadAndOverlappingAssociationsAreAmbiguousAndIssueNoReads() {
        val original = model()
        assertNoReads(original.copy(associations = emptyList()))
        assertNoReads(original.copy(associations = original.associations + original.associations))
    }

    @Test fun multipleAcOwnersInOneFunctionAreAmbiguous() {
        val original = model()
        val second = original.interfaces.first().copy(key = UsbInterfaceKey(18, 9, 0))
        assertNoReads(original.copy(interfaces = original.interfaces + second,
            associations = listOf(UsbInterfaceAssociation(18, 7, 3, 1, 0, 0x20))))
    }

    @Test fun selectorAndMultiplierReferencesAreNotGuessed() {
        listOf(UsbAudioEntityKind.CLOCK_SELECTOR, UsbAudioEntityKind.CLOCK_MULTIPLIER).forEach { kind ->
            val entities = entities().map { if (it.id == inputId || it.id == outputId)
                it.copy(clockSources = listOf(52)) else it } +
                UsbAudioEntity(52, kind, clockSources = listOf(clockId), controls = 1)
            assertNoReads(model(entities))
        }
    }

    @Test fun differentInputAndOutputClocksAreAmbiguous() {
        val entities = entities().map { if (it.id == outputId) it.copy(clockSources = listOf(53)) else it } +
            UsbAudioEntity(53, UsbAudioEntityKind.CLOCK_SOURCE, controls = 5)
        assertNoReads(model(entities))
    }

    @Test fun missingClockSignalCyclesAndBrokenSignalReferencesAreRejected() {
        assertNoReads(model(entities().filterNot { it.id == clockId }))
        assertNoReads(model(entities().map { if (it.id == featureId) it.copy(signalSources = listOf(99)) else it }))
        assertNoReads(model(entities().map { if (it.id == featureId) it.copy(signalSources = listOf(outputId)) else it }))
        assertNoReads(model(entities().map { if (it.id == clockId) it.copy(controls = null) else it }))
    }

    @Test fun duplicateEntityIdsMultipleConfigurationsAndParserWarningsPreventReads() {
        val original = model()
        assertNoReads(model(entities() + entities().first()))
        assertNoReads(original.copy(configurations = original.configurations + original.configurations.first().copy(offset = 500, value = 2)))
        assertNoReads(original.copy(warnings = listOf("Incomplete descriptor")))
    }

    @Test fun unknownTerminalLinksAndNonUac2OwnersPreventReads() {
        val original = model()
        assertNoReads(original.copy(interfaces = original.interfaces.map { stream ->
            if (stream.isAudioStreaming) stream.copy(audioStreaming = stream.audioStreaming!!.copy(terminalLink = 99)) else stream
        }))
        assertNoReads(original.copy(interfaces = original.interfaces.map {
            if (it.isAudioControl) it.copy(protocol = 0) else it
        }))
        assertNoReads(original.copy(interfaces = original.interfaces.map {
            if (it.isAudioStreaming) it.copy(endpoints = listOf(UsbEndpointDescriptor(5, 0x35, 400, 1))) else it
        }))
    }

    @Test fun captureAndHidInterfacesDoNotCreateClockReadTargets() {
        val original = model()
        val captureOnly = original.copy(interfaces = original.interfaces.map { stream ->
            if (stream.isAudioStreaming) stream.copy(endpoints = listOf(UsbEndpointDescriptor(0x85, 5, 400, 1))) else stream
        } + UsbInterfaceDescriptor(UsbInterfaceKey(18, 0, 0), 1, 3, 0, 0))
        assertNoReads(captureOnly)
        assertEquals(0, UsbAudioTopology.resolveClockTarget(captureOnly).playbackCandidateCount)
    }

    @Test fun addingHidAndFeedbackDoesNotChangeTheResolvedClock() {
        val original = model()
        val expanded = original.copy(interfaces = original.interfaces.map { stream ->
            if (stream.isAudioStreaming) stream.copy(endpoints = stream.endpoints + UsbEndpointDescriptor(0x86, 0x11, 4, 4)) else stream
        } + UsbInterfaceDescriptor(UsbInterfaceKey(18, 0, 0), 1, 3, 0, 0))
        assertNotNull(UsbAudioTopology.resolveClockTarget(expanded).target)
        assertEquals(clockId, UsbAudioTopology.resolveClockTarget(expanded).target!!.clockEntityId)
    }

    private fun assertNoReads(model: UsbAudioDescriptorModel) {
        val resolution = UsbAudioTopology.resolveClockTarget(model)
        assertNull(resolution.target)
        assertTrue(resolution.issues.isNotEmpty())
        var calls = 0
        val diagnostics = UsbAudioClockControl.query(resolution, 1, UsbClockReadTransport { _, _ -> calls++; -1 })
        assertEquals(0, calls)
        assertTrue(diagnostics.results.all { it.status == UsbClockQueryStatus.AMBIGUOUS_TARGET })
    }

    private fun entities() = listOf(
        UsbAudioEntity(clockId, UsbAudioEntityKind.CLOCK_SOURCE, controls = 5),
        UsbAudioEntity(inputId, UsbAudioEntityKind.INPUT_TERMINAL, clockSources = listOf(clockId), terminalType = 0x0101),
        UsbAudioEntity(featureId, UsbAudioEntityKind.FEATURE_UNIT, signalSources = listOf(inputId)),
        UsbAudioEntity(outputId, UsbAudioEntityKind.OUTPUT_TERMINAL, signalSources = listOf(featureId),
            clockSources = listOf(clockId), terminalType = 0x0301))

    private fun model(entities: List<UsbAudioEntity> = entities()): UsbAudioDescriptorModel {
        val owner = UsbInterfaceDescriptor(acKey, 0, 1, 1, 0x20,
            audioControl = UsbAudioControlDescriptor(0x0200, entities = entities))
        val streams = (1..3).map { alt ->
            UsbInterfaceDescriptor(UsbInterfaceKey(18, 8, alt), 1, 1, 2, 0x20,
                endpoints = listOf(UsbEndpointDescriptor(5, 5, 400, 1)),
                audioStreaming = UsbAudioStreamingDescriptor(inputId, 0, 1, 1, 2, 3),
                audioFormat = UsbAudioFormatDescriptor(1, alt + 1, (alt + 1) * 8))
        }
        return UsbAudioDescriptorModel(300, null,
            listOf(UsbConfigurationDescriptor(18, 1, 282, 2, 0x80, 50)),
            listOf(UsbInterfaceAssociation(18, 7, 2, 1, 0, 0x20)),
            listOf(owner) + streams, emptyList(), emptyList())
    }
}
