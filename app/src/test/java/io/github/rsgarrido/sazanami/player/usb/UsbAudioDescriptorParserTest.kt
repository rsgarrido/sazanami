package io.github.rsgarrido.sazanami.player.usb

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic USB-IF layouts, not fabricated KA15 descriptors. No Android APIs or hardware. */
class UsbAudioDescriptorParserTest {
    @Test fun standardDescriptorsUseLittleEndianAndRetainAllAlternates() {
        val model = parse(iface(5, 0, 1, 2, 0x20, 0),
            iface(5, 1, 1, 2, 0x20, 1), endpoint(2, 5, 0x0120))
        val device = requireNotNull(model.device)
        assertEquals(0x1234, device.vendorId)
        assertEquals(0x5678, device.productId)
        assertEquals(listOf(0, 1), model.interfaces.map { it.key.alternate })
        val ep = model.interfaces.last().endpoints.single()
        assertEquals(288, ep.packetPayloadBytes)
        assertEquals(1, ep.interval)
        assertFalse(ep.isIn)
        assertEquals(1, ep.synchronizationType)
        assertTrue(model.warnings.isEmpty())
    }

    @Test fun deviceClassZeroStillContainsAudioAndHid() {
        val model = completeComposite()
        assertEquals(0, model.device!!.deviceClass)
        assertTrue(model.interfaces.any { it.isAudioControl })
        assertTrue(model.interfaces.any { it.interfaceClass == 3 })
        assertEquals(2, model.interfaces.count { it.isAudioStreaming })
        val paths = UsbAudioTopology.candidates(model)
        assertEquals(1, paths.count { it.direction == UsbAudioPathDirection.PLAYBACK })
        assertEquals(1, paths.count { it.direction == UsbAudioPathDirection.CAPTURE })
        assertEquals(5, paths.first { it.direction == UsbAudioPathDirection.PLAYBACK }.interfaceKey.number)
        assertEquals(6, paths.first { it.direction == UsbAudioPathDirection.CAPTURE }.interfaceKey.number)
    }

    @Test fun topologyUsesIadAndTerminalLinksRatherThanOrdering() {
        val model = completeComposite(streamsBeforeControl = true)
        val playback = UsbAudioTopology.candidates(model).single { it.direction == UsbAudioPathDirection.PLAYBACK }
        assertEquals(4, playback.audioControlOwners.single().number)
        assertTrue(playback.associationEvidence.contains("IAD establishes"))
        assertTrue(playback.signalPaths.single().contains("OUTPUT_TERMINAL #3 -> FEATURE_UNIT #2 -> INPUT_TERMINAL #1"))
        assertTrue(playback.clockPaths.any { it.contains("CLOCK_SELECTOR #12 -> CLOCK_SOURCE #10") })
        assertTrue(playback.clockPaths.any { it.contains("CLOCK_SELECTOR #12 -> CLOCK_SOURCE #11") })
        assertTrue(playback.warnings.any { it.contains("selected pin was not queried") })
    }

    @Test fun validBitsAreIndependentOfSubslotWidth() {
        val model = completeComposite()
        val stream = model.interfaces.single { it.key.number == 5 }
        val format = requireNotNull(stream.audioFormat)
        val general = requireNotNull(stream.audioStreaming)
        assertEquals(4, format.subslotBytes)
        assertEquals(24, format.validBits)
        assertEquals(2, general.channelCount)
        assertEquals(3L, general.channelConfiguration)
        assertEquals(1L, general.formats)
    }

    @Test fun onlyFeedbackUsageInThePlaybackAlternateEstablishesExplicitFeedback() {
        val paths = UsbAudioTopology.candidates(completeComposite())
        assertTrue(paths.single { it.direction == UsbAudioPathDirection.PLAYBACK }.feedbackEvidence.contains("Explicit feedback 0x82"))
        assertTrue(paths.single { it.direction == UsbAudioPathDirection.CAPTURE }.feedbackEvidence.contains("not labeled playback feedback"))
        assertFalse(paths.any { it.endpointAddress == 0x82 })
    }

    @Test fun dataInEndpointIsCaptureEvenAlongsidePlayback() {
        val model = parse(iface(5, 1, 1, 2, 0x20, 2), endpoint(2, 5), endpoint(0x83, 5))
        val paths = UsbAudioTopology.candidates(model)
        assertEquals(2, paths.size)
        assertTrue(paths.single { it.direction == UsbAudioPathDirection.PLAYBACK }.feedbackEvidence.startsWith("No explicit"))
        assertEquals(0x83, paths.single { it.direction == UsbAudioPathDirection.CAPTURE }.endpointAddress)
    }

    @Test fun multipleFeedbackOrOutEndpointsAreReportedAsAmbiguous() {
        val model = parse(iface(5, 1, 1, 2, 0x20, 3), endpoint(2, 5), endpoint(3, 5), endpoint(0x82, 0x11))
        assertTrue(UsbAudioTopology.candidates(model).all { it.feedbackEvidence.startsWith("Possible feedback") })
    }

    @Test fun missingIadDoesNotPromoteMatchingTerminalIdToProvenOwnership() {
        val model = parse(audioControl(), playback())
        val path = UsbAudioTopology.candidates(model).single()
        assertTrue(path.associationEvidence.contains("not proven"))
        assertTrue(path.warnings.any { it.contains("ownership is ambiguous") })
    }

    @Test fun zeroAndOneLengthStopSafelyAndPreserveTheRemainder() {
        for (length in listOf(0, 1)) {
            val model = UsbAudioDescriptorParser.parse(bytes(length, 4, 9, 8, 7))
            assertTrue(model.interfaces.isEmpty())
            assertEquals(1, model.rawDescriptors.size)
            assertTrue(model.rawDescriptors.single().hex.endsWith("09 08 07"))
            assertTrue(model.warnings.single().contains("parsing stopped"))
        }
    }

    @Test fun truncatedDescriptorPreservesTailWithoutReadingPastIt() {
        val model = UsbAudioDescriptorParser.parse(iface(5, 1, 1, 2, 0x20, 1) + bytes(7, 5, 2))
        assertTrue(model.interfaces.single().endpoints.isEmpty())
        assertTrue(model.warnings.any { it.contains("truncated descriptor") })
        assertEquals("07 05 02", model.rawDescriptors.last().hex)
    }

    @Test fun everyShortUac2LayoutIsBounded() {
        val descriptors = listOf(inputTerminal(), outputTerminal(), feature(), clock(10), selector(), multiplier(),
            bytes(16, 0x24, 1, 1, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0), bytes(6, 0x24, 2, 1, 4, 24))
        descriptors.forEach { descriptor ->
            for (size in 2 until descriptor.size) {
                val shortened = descriptor.copyOf(size).also { it[0] = size.toByte() }
                val control = descriptor[2].toInt() in listOf(2, 3, 6, 10, 11, 12) && descriptor.size != 6
                val model = parse(iface(4, 0, 1, if (control) 1 else 2, 0x20, 0), shortened)
                assertNotNull(model.rawDescriptors.last())
            }
        }
    }

    @Test fun unknownClassSpecificDescriptorRetainsItsInterfaceAndRawBytes() {
        val model = parse(iface(8, 2, 1, 2, 0x20, 0), bytes(5, 0x24, 0x7f, 0xab, 0xcd))
        val raw = model.rawDescriptors.last()
        val key = requireNotNull(raw.interfaceKey)
        assertEquals(8, key.number)
        assertEquals(2, key.alternate)
        assertEquals("05 24 7F AB CD", raw.hex)
    }

    @Test fun classSpecificEndpointBelongsToTheImmediatelyPrecedingEndpoint() {
        val model = parse(iface(5, 1, 1, 2, 0x20, 2), endpoint(2, 5),
            bytes(8, 0x25, 1, 0, 0x0c, 2, 0x34, 0x12), endpoint(0x82, 0x11))
        val endpoints = model.interfaces.single().endpoints
        assertEquals(0x1234, endpoints[0].audio!!.lockDelay)
        assertEquals(2, endpoints[0].audio!!.lockDelayUnits)
        assertNull(endpoints[1].audio)
    }

    @Test fun missingEntityAndMalformedSelectorStayUnknown() {
        val model = parse(iface(4, 0, 1, 1, 0x20, 0), outputTerminal(source = 99),
            bytes(8, 0x24, 11, 12, 3, 10, 11, 0))
        assertTrue(model.warnings.any { it.contains("unresolved entity 99") })
        assertTrue(model.warnings.any { it.contains("Clock Selector pin array") })
        assertFalse(model.interfaces.single().audioControl!!.entities.any { it.kind == UsbAudioEntityKind.CLOCK_SELECTOR })
    }

    @Test fun duplicateEntityIdsAreNotResolvedByTakingTheFirst() {
        val model = parse(iad(), audioControl(clock(10), clock(10), inputTerminal(), outputTerminal(source = 1)), playback())
        assertTrue(model.warnings.any { it.contains("duplicate entity ID 10") })
        assertTrue(UsbAudioTopology.candidates(model).single().clockPaths.any { it.contains("unresolved/duplicate") })
    }

    @Test fun cyclesTerminateAndAreNotPresentedAsACompleteSignalPath() {
        val model = parse(iad(), audioControl(clock(10), inputTerminal(),
            feature(id = 2, source = 3), feature(id = 3, source = 2), outputTerminal(id = 4, source = 2)), playback())
        val path = UsbAudioTopology.candidates(model).single()
        assertTrue(path.signalPaths.isEmpty())
        assertTrue(path.warnings.any { it.contains("no complete known signal path") })
    }

    @Test fun uac1EntitiesArePreservedWithoutApplyingUac2Offsets() {
        val model = parse(iface(4, 0, 1, 1, 0, 0), bytes(9, 0x24, 1, 0, 1, 9, 0, 1, 5), inputTerminal())
        assertEquals(0x0100, model.interfaces.single().audioControl!!.version)
        assertTrue(model.interfaces.single().audioControl!!.entities.isEmpty())
        assertTrue(model.rawDescriptors.last().hex.isNotEmpty())
    }

    @Test fun truncatedNewInterfaceCannotAttachFollowingEndpointToTheOldInterface() {
        val model = parse(iface(4, 0, 1, 1, 0x20, 0), bytes(3, 4, 5), endpoint(2, 5))
        assertTrue(model.interfaces.single().endpoints.isEmpty())
        assertTrue(model.warnings.any { it.contains("endpoint without interface") })
    }

    @Test fun multipleConfigurationsHaveSeparateEntityAndInterfaceNamespaces() {
        val model = UsbAudioDescriptorParser.parse(configuration(iface(4, 0, 1, 1, 0x20, 0), clock(10)) +
            configuration(iface(4, 0, 1, 1, 0x20, 0), clock(10)))
        assertEquals(2, model.configurations.size)
        assertEquals(2, model.interfaces.map { it.key.configurationOffset }.distinct().size)
        assertFalse(model.warnings.any { it.contains("duplicate") })
    }

    @Test fun clockMultiplierAndFeatureChannelControlsAreRetained() {
        val model = completeComposite()
        val entities = model.interfaces.single { it.isAudioControl }.audioControl!!.entities
        assertEquals(listOf(10), entities.single { it.kind == UsbAudioEntityKind.CLOCK_MULTIPLIER }.clockSources)
        assertEquals(listOf(0xfL, 0xfL, 0xfL), entities.single { it.kind == UsbAudioEntityKind.FEATURE_UNIT }.channelControls)
    }

    @Test fun descriptorAfterConfigurationBoundaryCannotBelongToTheOldInterface() {
        val config = configuration(iface(4, 0, 1, 1, 0x20, 0))
        val model = UsbAudioDescriptorParser.parse(config + endpoint(2, 5))
        assertTrue(model.interfaces.single().endpoints.isEmpty())
        assertTrue(model.warnings.any { it.contains("outside the preceding configuration") })
    }

    private fun completeComposite(streamsBeforeControl: Boolean = false): UsbAudioDescriptorModel {
        val streams = playback() + iface(6, 1, 1, 2, 0x20, 1) + endpoint(0x83, 5)
        val ac = audioControl()
        return parse(iad(), if (streamsBeforeControl) streams + ac else ac + streams,
            iface(7, 0, 3, 0, 0, 1), endpoint(0x84, 3))
    }

    private fun parse(vararg descriptors: ByteArray): UsbAudioDescriptorModel = UsbAudioDescriptorParser.parse(
        bytes(18, 1, 0, 2, 0, 0, 0, 64, 0x34, 0x12, 0x78, 0x56, 0, 1, 1, 2, 3, 1) + configuration(*descriptors))

    private fun configuration(vararg descriptors: ByteArray): ByteArray {
        val body = descriptors.fold(byteArrayOf()) { result, d -> result + d }
        val total = body.size + 9
        return bytes(9, 2, total and 0xff, total shr 8, 4, 1, 0, 0x80, 50) + body
    }

    private fun iface(number: Int, alternate: Int, clazz: Int, subclass: Int, protocol: Int, endpoints: Int) =
        bytes(9, 4, number, alternate, endpoints, clazz, subclass, protocol, 0)
    private fun endpoint(address: Int, attributes: Int, packetSize: Int = 288) =
        bytes(7, 5, address, attributes, packetSize and 0xff, packetSize shr 8, 1)
    private fun iad() = bytes(8, 11, 4, 3, 1, 0, 0x20, 0)
    private fun clock(id: Int) = bytes(8, 0x24, 10, id, 3, 5, 0, 0)
    private fun selector() = bytes(9, 0x24, 11, 12, 2, 10, 11, 1, 0)
    private fun multiplier() = bytes(7, 0x24, 12, 13, 10, 5, 0)
    private fun inputTerminal() = bytes(17, 0x24, 2, 1, 1, 1, 0, 12, 2, 3, 0, 0, 0, 0, 0, 0, 0)
    private fun outputTerminal(id: Int = 3, source: Int = 2) = bytes(12, 0x24, 3, id, 1, 3, 0, source, 12, 0, 0, 0)
    private fun feature(id: Int = 2, source: Int = 1) = bytes(18, 0x24, 6, id, source, 15, 0, 0, 0, 15, 0, 0, 0, 15, 0, 0, 0, 0)
    private fun audioControl(vararg entities: ByteArray): ByteArray {
        val actual = if (entities.isEmpty()) arrayOf(clock(10), clock(11), selector(), multiplier(), inputTerminal(), feature(), outputTerminal()) else entities
        val total = actual.sumOf { it.size } + 9
        return iface(4, 0, 1, 1, 0x20, 0) + bytes(9, 0x24, 1, 0, 2, 1, total and 0xff, total shr 8, 0) +
            actual.fold(byteArrayOf()) { result, d -> result + d }
    }
    private fun playback() = iface(5, 1, 1, 2, 0x20, 2) +
        bytes(16, 0x24, 1, 1, 0, 1, 1, 0, 0, 0, 2, 3, 0, 0, 0, 0) + bytes(6, 0x24, 2, 1, 4, 24) +
        endpoint(2, 5) + endpoint(0x82, 0x11)
    private fun bytes(vararg values: Int): ByteArray = values.map { it.toByte() }.toByteArray()
}
