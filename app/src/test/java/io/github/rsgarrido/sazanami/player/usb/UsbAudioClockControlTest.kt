package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Synthetic protocol replies only; no assumption about a particular DAC's rate list. */
class UsbAudioClockControlTest {
    private val target = UsbAudioClockTarget(UsbInterfaceKey(18, 7, 0), 29, 5L,
        listOf(UsbInterfaceKey(18, 8, 1)))
    private val resolution = UsbAudioClockResolution(target, 1)

    @Test fun readRequestsUseResolvedInterfaceAndEntityWithSpecSelectors() {
        val requests = listOf(UsbClockReadRequest.currentFrequency(target),
            UsbClockReadRequest.rangeHeader(target), UsbClockReadRequest.rangeBody(target, 3),
            UsbClockReadRequest.clockValidity(target))
        assertEquals(listOf(4, 2, 38, 1), requests.map { it.length })
        assertEquals(listOf(1, 2, 2, 1), requests.map { it.request })
        assertEquals(listOf(0x0100, 0x0100, 0x0100, 0x0200), requests.map { it.value })
        requests.forEach {
            assertEquals(0xA1, it.requestType)
            assertEquals(0x1D07, it.index)
            assertEquals(7, it.interfaceNumber)
            assertEquals(29, it.entityId)
        }
        assertEquals(770, UsbClockReadRequest.rangeBody(target, 64).length)
    }

    @Test fun frequencyIsLittleEndianAndRetainsFullUnsigned32BitRange() {
        assertEquals(48_000L, decode(UsbClockQuery.CURRENT_FREQUENCY, u32(48_000)).currentFrequencyHz)
        assertEquals(4_294_967_295L, decode(UsbClockQuery.CURRENT_FREQUENCY, u32(0xffff_ffffL)).currentFrequencyHz)
        assertEquals(0L, decode(UsbClockQuery.CURRENT_FREQUENCY, u32(0)).currentFrequencyHz)
    }

    @Test fun validityAcceptsOnlyExactlyOneBooleanByte() {
        assertEquals(true, decode(UsbClockQuery.CLOCK_VALIDITY, byteArrayOf(1)).clockValid)
        assertEquals(false, decode(UsbClockQuery.CLOCK_VALIDITY, byteArrayOf(0)).clockValid)
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, decode(UsbClockQuery.CLOCK_VALIDITY, byteArrayOf(2)).status)
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, decode(UsbClockQuery.CLOCK_VALIDITY, byteArrayOf(1, 0)).status)
    }

    @Test fun singleDiscreteAndMultipleRangesRetainEveryTuple() {
        val single = listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0))
        val multiple = single + UsbSamplingFrequencyRange(96_000, 96_000, 0)
        assertEquals(single, decodeRange(rangeBytes(single)).ranges)
        val result = decodeRange(rangeBytes(multiple))
        assertEquals(UsbClockQueryStatus.SUCCESS, result.status)
        assertEquals(2, result.declaredSubrangeCount)
        assertEquals(multiple, result.ranges)
    }

    @Test fun integerHzAndSteppedIntervalsArePreservedWithoutExpansion() {
        listOf(UsbSamplingFrequencyRange(44_100, 192_000, 1),
            UsbSamplingFrequencyRange(48_000, 192_000, 48_000)).forEach { tuple ->
            val result = decodeRange(rangeBytes(listOf(tuple)))
            assertEquals(UsbClockQueryStatus.SUCCESS, result.status)
            assertEquals(listOf(tuple), result.ranges)
        }
    }

    @Test fun zeroAndOneByteRangeHeadersAreShort() {
        listOf(byteArrayOf(), byteArrayOf(1)).forEach {
            assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, decodeRange(it).status)
        }
    }

    @Test fun truncatedRangePreservesOnlyCompleteReturnedTuples() {
        val tuples = listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0), UsbSamplingFrequencyRange(96_000, 96_000, 0))
        val full = rangeBytes(tuples)
        val result = UsbAudioClockControl.decode(UsbClockQuery.FREQUENCY_RANGE, full, full.size - 1)
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, result.status)
        assertEquals(2, result.declaredSubrangeCount)
        assertEquals(tuples.take(1), result.ranges)
    }

    @Test fun unreasonableCountsEmptyAndExtraPayloadsAreRejectedBeforeAllocation() {
        listOf(0, 65, 65535).forEach { count ->
            val result = decodeRange(byteArrayOf(count.toByte(), (count shr 8).toByte()))
            assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.status)
            assertTrue(result.ranges.isEmpty())
        }
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, decodeRange(rangeBytes(
            listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0))) + byteArrayOf(0)).status)
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE,
            UsbAudioClockControl.decode(UsbClockQuery.FREQUENCY_RANGE, byteArrayOf(1), 2).status)
    }

    @Test fun shortFrequencyCannotUseUnreturnedBufferBytes() {
        val result = UsbAudioClockControl.decode(UsbClockQuery.CURRENT_FREQUENCY, u32(48_000), 3)
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, result.status)
        assertNull(result.currentFrequencyHz)
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, decode(UsbClockQuery.CURRENT_FREQUENCY, byteArrayOf()).status)
    }

    @Test fun invalidSemanticsPreserveEvidenceAndMakeRateAssessmentIndeterminate() {
        val cases = listOf(
            listOf(UsbSamplingFrequencyRange(96_000, 48_000, 0)),
            listOf(UsbSamplingFrequencyRange(48_000, 48_000, 1)),
            listOf(UsbSamplingFrequencyRange(48_000, 96_000, 0)),
            listOf(UsbSamplingFrequencyRange(48_000, 96_001, 48_000)),
            listOf(UsbSamplingFrequencyRange(48_000, 96_000, 1), UsbSamplingFrequencyRange(96_000, 192_000, 1)))
        cases.forEach { tuples ->
            val result = decodeRange(rangeBytes(tuples))
            assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.status)
            assertEquals(tuples, result.ranges)
            assertEquals(UsbClockRateSupport.INDETERMINATE, UsbAudioClockControl.assessRate(48_000, result))
        }
    }

    @Test fun rangeReadUsesBoundedHeaderThenExactBodyAndKeepsRawBytes() {
        val requests = mutableListOf<UsbClockReadRequest>()
        val diagnostics = successfulQuery(requests)
        assertEquals(UsbClockDiagnosticPhase.COMPLETE, diagnostics.phase)
        assertEquals(UsbClockClaimMode.UNCLAIMED, diagnostics.claimMode)
        assertEquals(listOf(4, 2, 14, 1), requests.map { it.length })
        assertEquals(2, diagnostics.results[1].attempts.size)
        assertEquals(listOf(1, 0), diagnostics.results[1].attempts[0].rawBytes)
        assertEquals(listOf(0x80, 0xBB, 0, 0), diagnostics.results[0].attempts.single().rawBytes)
    }

    @Test fun excessiveHeaderCountNeverIssuesRangeBody() {
        val requests = mutableListOf<UsbClockReadRequest>()
        val result = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
            requests += request
            if (request.rangeHeaderOnly) { buffer[0] = 65; 2 } else -1
        })
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.results[1].status)
        assertEquals(1, requests.count { it.query == UsbClockQuery.FREQUENCY_RANGE })
    }

    @Test fun truncatedHeaderStopsBodyAndTruncatedBodyPreservesItsActualStatus() {
        var rangeReads = 0
        val shortHeader = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
            if (request.query == UsbClockQuery.FREQUENCY_RANGE) {
                rangeReads++
                buffer[0] = 1
                1
            } else -1
        }).results[1]
        assertEquals(1, rangeReads)
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, shortHeader.status)
        assertEquals(listOf(1), shortHeader.attempts.single().rawBytes)
        val shortBody = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
            when {
                request.rangeHeaderOnly -> { buffer[0] = 1; 2 }
                request.query == UsbClockQuery.FREQUENCY_RANGE -> { buffer[0] = 1; 1 }
                else -> -1
            }
        }).results[1]
        assertEquals(UsbClockQueryStatus.SHORT_RESPONSE, shortBody.status)
        assertEquals(listOf(1), shortBody.attempts.last().rawBytes)
        assertTrue(shortBody.ranges.isEmpty())
    }

    @Test fun changingCountBetweenHeaderAndBodyIsNotAuthoritative() {
        val result = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
            when {
                request.rangeHeaderOnly -> { buffer[0] = 1; 2 }
                request.query == UsbClockQuery.FREQUENCY_RANGE -> {
                    rangeBytes(listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0))).copyInto(buffer)
                    buffer[0] = 2
                    buffer.size
                }
                else -> -1
            }
        }).results[1]
        assertEquals(UsbClockQueryStatus.MALFORMED_RESPONSE, result.status)
        assertTrue(result.detail!!.contains("changed"))
        assertEquals(UsbClockRateSupport.INDETERMINATE, UsbAudioClockControl.assessRate(48_000, result))
    }

    @Test fun failuresDistinguishNegativeShortExceptionAndPermissionWithoutGuessingStall() {
        val transports = listOf(
            UsbClockReadTransport { _, _ -> -1 } to UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR,
            UsbClockReadTransport { _, _ -> 0 } to UsbClockQueryStatus.SHORT_RESPONSE,
            UsbClockReadTransport { _, buffer -> buffer.size + 1 } to UsbClockQueryStatus.MALFORMED_RESPONSE,
            UsbClockReadTransport { _, _ -> throw IllegalStateException("private path") } to UsbClockQueryStatus.TRANSPORT_ERROR,
            UsbClockReadTransport { _, _ -> throw SecurityException("private serial") } to UsbClockQueryStatus.PERMISSION_LOST)
        transports.forEach { (transport, expected) ->
            val result = UsbAudioClockControl.query(resolution, 3, transport)
            assertTrue(result.results.all { it.status == expected })
            assertFalse(result.results.any { it.detail.orEmpty().contains("private") })
        }
    }

    @Test fun lateDetachStaleGenerationAndPermissionLossPreventFurtherReadsAndDecodedValues() {
        listOf(UsbClockQueryStatus.DEVICE_DISCONNECTED, UsbClockQueryStatus.STALE_GENERATION,
            UsbClockQueryStatus.PERMISSION_LOST).forEach { failure ->
            var lost = false
            var calls = 0
            val result = UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { _, buffer ->
                calls++
                u32(48_000).copyInto(buffer)
                lost = true
                4
            }, { if (lost) failure else null })
            assertEquals(1, calls)
            assertTrue(result.results.all { it.status == failure })
            assertNull(result.results[0].currentFrequencyHz)
            assertEquals(listOf(0x80, 0xBB, 0, 0), result.results[0].attempts.single().rawBytes)
        }
    }

    @Test(expected = CancellationException::class) fun cancellationIsRethrownToConnectionOwner() {
        UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { _, _ -> throw CancellationException("cancel") })
    }

    @Test fun unresolvedTargetAndUnadvertisedOrReservedControlsIssueNoReads() {
        var calls = 0
        val transport = UsbClockReadTransport { _, _ -> calls++; -1 }
        val ambiguous = UsbAudioClockControl.query(UsbAudioClockResolution(issues = listOf("ambiguous")), 3, transport)
        assertTrue(ambiguous.results.all { it.status == UsbClockQueryStatus.AMBIGUOUS_TARGET })
        val unsupported = UsbAudioClockControl.query(resolution.copy(target = target.copy(controls = 0)), 3, transport)
        assertTrue(unsupported.results.all { it.status == UsbClockQueryStatus.UNSUPPORTED })
        val reserved = UsbAudioClockControl.query(resolution.copy(target = target.copy(controls = 10)), 3, transport)
        assertTrue(reserved.results.all { it.status == UsbClockQueryStatus.MALFORMED_RESPONSE })
        assertEquals(0, calls)
    }

    @Test fun milestoneRatesUseOnlyReturnedSingletonsOrResolutionGrid() {
        val result = decodeRange(rangeBytes(listOf(UsbSamplingFrequencyRange(44_100, 44_100, 0),
            UsbSamplingFrequencyRange(48_000, 96_000, 48_000))))
        assertEquals(listOf(UsbClockRateSupport.REPRESENTED, UsbClockRateSupport.REPRESENTED,
            UsbClockRateSupport.REPRESENTED, UsbClockRateSupport.NOT_REPRESENTED),
            UsbAudioClockControl.milestoneRatesHz.map { UsbAudioClockControl.assessRate(it, result) })
        assertEquals(UsbClockRateSupport.NOT_REPRESENTED, UsbAudioClockControl.assessRate(88_200, result))
        assertEquals(UsbClockRateSupport.INDETERMINATE, UsbAudioClockControl.assessRate(48_000, null))
        val interval = decodeRange(rangeBytes(listOf(UsbSamplingFrequencyRange(44_100, 192_000, 1))))
        assertTrue(UsbAudioClockControl.milestoneRatesHz.all {
            UsbAudioClockControl.assessRate(it, interval) == UsbClockRateSupport.REPRESENTED
        })
    }

    private fun successfulQuery(requests: MutableList<UsbClockReadRequest>) =
        UsbAudioClockControl.query(resolution, 3, UsbClockReadTransport { request, buffer ->
            requests += request
            val reply = when {
                request.rangeHeaderOnly -> byteArrayOf(1, 0)
                request.query == UsbClockQuery.FREQUENCY_RANGE -> rangeBytes(listOf(UsbSamplingFrequencyRange(48_000, 48_000, 0)))
                request.query == UsbClockQuery.CURRENT_FREQUENCY -> u32(48_000)
                else -> byteArrayOf(1)
            }
            reply.copyInto(buffer)
            reply.size
        })

    private fun decode(query: UsbClockQuery, bytes: ByteArray) = UsbAudioClockControl.decode(query, bytes, bytes.size)
    private fun decodeRange(bytes: ByteArray) = decode(UsbClockQuery.FREQUENCY_RANGE, bytes)
    private fun u32(value: Long) = ByteArray(4) { (value shr (8 * it)).toByte() }
    private fun rangeBytes(tuples: List<UsbSamplingFrequencyRange>): ByteArray =
        byteArrayOf(tuples.size.toByte(), (tuples.size shr 8).toByte()) + tuples.flatMap { tuple ->
            (u32(tuple.minimumHz) + u32(tuple.maximumHz) + u32(tuple.resolutionHz)).toList()
        }.toByteArray()
}
