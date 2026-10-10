package io.github.rsgarrido.sazanami.player.usb

import kotlinx.coroutines.CancellationException

internal data class UsbAudioClockTarget(
    val audioControl: UsbInterfaceKey,
    val clockEntityId: Int,
    val controls: Long,
    val playbackPaths: List<UsbInterfaceKey>
)

internal data class UsbAudioClockResolution(
    val target: UsbAudioClockTarget? = null,
    val playbackCandidateCount: Int = 0,
    val issues: List<String> = emptyList()
)

internal enum class UsbClockQuery { CURRENT_FREQUENCY, FREQUENCY_RANGE, CLOCK_VALIDITY }
internal enum class UsbClockQueryStatus {
    SUCCESS, UNSUPPORTED, REJECTED_OR_TRANSPORT_ERROR, SHORT_RESPONSE, MALFORMED_RESPONSE,
    DEVICE_DISCONNECTED, STALE_GENERATION, PERMISSION_LOST, AMBIGUOUS_TARGET, TRANSPORT_ERROR,
    STALL_LIKE, INTERFACE_BUSY, INVALID_REQUEST
}
internal enum class UsbClockDiagnosticPhase { QUERYING, COMPLETE, PARTIAL, UNAVAILABLE }
internal enum class UsbClockClaimMode { NOT_ATTEMPTED, UNCLAIMED, AUDIOCONTROL_NON_FORCED, AUDIOCONTROL_FORCED_NATIVE, USB_CONFIGURATION_NATIVE }
internal enum class UsbClockRateSupport { REPRESENTED, NOT_REPRESENTED, INDETERMINATE }

internal data class UsbSamplingFrequencyRange(val minimumHz: Long, val maximumHz: Long, val resolutionHz: Long)

/** No caller can supply request direction, recipient, selector, or a write request code. */
internal class UsbClockReadRequest private constructor(
    val query: UsbClockQuery,
    val interfaceNumber: Int,
    val entityId: Int,
    val length: Int,
    val rangeHeaderOnly: Boolean = false
) {
    val requestType: Int get() = 0xA1 // device-to-host, class, interface, endpoint zero
    val request: Int get() = if (query == UsbClockQuery.FREQUENCY_RANGE) 0x02 else 0x01
    val controlSelector: Int get() = if (query == UsbClockQuery.CLOCK_VALIDITY) 0x02 else 0x01
    val value: Int get() = controlSelector shl 8 // channel zero
    val index: Int get() = (entityId shl 8) or interfaceNumber

    companion object {
        fun currentFrequency(target: UsbAudioClockTarget) = create(target, UsbClockQuery.CURRENT_FREQUENCY, 4)
        fun clockValidity(target: UsbAudioClockTarget) = create(target, UsbClockQuery.CLOCK_VALIDITY, 1)
        fun rangeHeader(target: UsbAudioClockTarget) = create(target, UsbClockQuery.FREQUENCY_RANGE, 2, true)
        fun rangeBody(target: UsbAudioClockTarget, count: Int): UsbClockReadRequest {
            require(count in 1..UsbAudioClockControl.MAX_SUBRANGES)
            return create(target, UsbClockQuery.FREQUENCY_RANGE, 2 + count * 12)
        }
        private fun create(target: UsbAudioClockTarget, query: UsbClockQuery, length: Int, header: Boolean = false): UsbClockReadRequest {
            require(target.audioControl.number in 0..255 && target.clockEntityId in 1..255)
            return UsbClockReadRequest(query, target.audioControl.number, target.clockEntityId, length, header)
        }
    }
}

internal data class UsbClockReadAttempt(
    val request: UsbClockReadRequest,
    val returnedByteCount: Int?,
    val rawBytes: List<Int>,
    val status: UsbClockQueryStatus,
    val detail: String? = null,
    val nativeResult: UsbFsControlEvidence? = null
)

internal data class UsbClockQueryResult(
    val query: UsbClockQuery,
    val status: UsbClockQueryStatus,
    val attempts: List<UsbClockReadAttempt> = emptyList(),
    val currentFrequencyHz: Long? = null,
    val clockValid: Boolean? = null,
    val ranges: List<UsbSamplingFrequencyRange> = emptyList(),
    val declaredSubrangeCount: Int? = null,
    val detail: String? = null
)

internal data class UsbAudioClockDiagnostics(
    val generation: Long,
    val resolution: UsbAudioClockResolution,
    val phase: UsbClockDiagnosticPhase,
    val claimMode: UsbClockClaimMode = UsbClockClaimMode.NOT_ATTEMPTED,
    val results: List<UsbClockQueryResult> = emptyList(),
    val claimExperiment: UsbAudioClaimExperimentResult? = null
)

internal fun interface UsbClockReadTransport {
    /** Writes response bytes into buffer; only the returned byte count is evidence. */
    fun read(request: UsbClockReadRequest, buffer: ByteArray): Int
}

/** Pure UAC2 request/response handling; caller owns the connection and worker lifetime.
 * USB-IF Audio Devices 2.0 §§5.2.2, 5.2.3.3, 5.2.5.1 (no external driver code).
 */
internal object UsbAudioClockControl {
    const val MAX_SUBRANGES = 64
    val milestoneRatesHz: List<Long> = listOf(44_100L, 48_000L, 96_000L, 192_000L)

    fun query(
        resolution: UsbAudioClockResolution,
        generation: Long,
        transport: UsbClockReadTransport,
        accessFailure: () -> UsbClockQueryStatus? = { null }
    ): UsbAudioClockDiagnostics = queryInOrder(resolution, generation, transport, UsbClockQuery.entries, accessFailure)

    fun queryInOrder(
        resolution: UsbAudioClockResolution,
        generation: Long,
        transport: UsbClockReadTransport,
        queryOrder: List<UsbClockQuery>,
        accessFailure: () -> UsbClockQueryStatus? = { null }
    ): UsbAudioClockDiagnostics {
        val target = resolution.target ?: return unavailable(generation, resolution,
            UsbClockQueryStatus.AMBIGUOUS_TARGET, "No unambiguous playback Clock Source target; no reads issued.")
        require(queryOrder.size == UsbClockQuery.entries.size && queryOrder.toSet() == UsbClockQuery.entries.toSet())
        val results = queryOrder.map { kind -> queryOne(target, kind, transport, accessFailure) }
        val successes = results.count { it.status == UsbClockQueryStatus.SUCCESS }
        return UsbAudioClockDiagnostics(generation, resolution, when (successes) {
            results.size -> UsbClockDiagnosticPhase.COMPLETE
            0 -> UsbClockDiagnosticPhase.UNAVAILABLE
            else -> UsbClockDiagnosticPhase.PARTIAL
        }, UsbClockClaimMode.UNCLAIMED, results)
    }

    /** The same decoder/control bitmap checks for bounded Session 6 readbacks. */
    fun queryOne(target: UsbAudioClockTarget, kind: UsbClockQuery, transport: UsbClockReadTransport,
        accessFailure: () -> UsbClockQueryStatus? = { null }): UsbClockQueryResult {
        val selector = if (kind == UsbClockQuery.CLOCK_VALIDITY) 2 else 1
        val pair = ((target.controls shr ((selector - 1) * 2)) and 3L).toInt()
        return when {
            pair == 0 -> UsbClockQueryResult(kind, UsbClockQueryStatus.UNSUPPORTED,
                detail = "Clock descriptor does not advertise this readable control; no request issued.")
            pair == 2 -> UsbClockQueryResult(kind, UsbClockQueryStatus.MALFORMED_RESPONSE,
                detail = "Clock descriptor uses a reserved control bit pair; no request issued.")
            kind == UsbClockQuery.FREQUENCY_RANGE -> queryRange(target, transport, accessFailure)
            else -> {
                val request = if (kind == UsbClockQuery.CURRENT_FREQUENCY)
                    UsbClockReadRequest.currentFrequency(target) else UsbClockReadRequest.clockValidity(target)
                val attempt = read(request, transport, accessFailure)
                val decoded = decode(kind, attempt.rawBytes.toByteArray(), attempt.returnedByteCount ?: -1)
                if (attempt.status != UsbClockQueryStatus.SUCCESS) UsbClockQueryResult(kind, attempt.status,
                    listOf(attempt), detail = attempt.detail)
                else decoded.copy(attempts = listOf(attempt.copy(status = decoded.status, detail = decoded.detail)))
            }
        }
    }

    fun unavailable(generation: Long, resolution: UsbAudioClockResolution, status: UsbClockQueryStatus, detail: String) =
        UsbAudioClockDiagnostics(generation, resolution, UsbClockDiagnosticPhase.UNAVAILABLE,
            results = UsbClockQuery.entries.map { UsbClockQueryResult(it, status, detail = detail) })

    private fun queryRange(target: UsbAudioClockTarget, transport: UsbClockReadTransport,
        accessFailure: () -> UsbClockQueryStatus?): UsbClockQueryResult {
        // UAC2 Get permits truncation to wLength: first read only wNumSubRanges, then
        // request the bounded exact parameter-block length. Neither request modifies state.
        val header = read(UsbClockReadRequest.rangeHeader(target), transport, accessFailure)
        if (header.status != UsbClockQueryStatus.SUCCESS) return UsbClockQueryResult(
            UsbClockQuery.FREQUENCY_RANGE, header.status, listOf(header), detail = header.detail)
        val count = u16(header.rawBytes, 0)
        if (count !in 1..MAX_SUBRANGES) return UsbClockQueryResult(UsbClockQuery.FREQUENCY_RANGE,
            UsbClockQueryStatus.MALFORMED_RESPONSE, listOf(header), declaredSubrangeCount = count,
            detail = "Subrange count $count is zero or exceeds the diagnostic cap $MAX_SUBRANGES; no body read issued.")
        val body = read(UsbClockReadRequest.rangeBody(target, count), transport, accessFailure)
        if (body.status !in setOf(UsbClockQueryStatus.SUCCESS, UsbClockQueryStatus.SHORT_RESPONSE)) return UsbClockQueryResult(
            UsbClockQuery.FREQUENCY_RANGE, body.status, listOf(header, body),
            declaredSubrangeCount = count, detail = body.detail)
        val result = decode(UsbClockQuery.FREQUENCY_RANGE, body.rawBytes.toByteArray(), body.returnedByteCount ?: -1)
        val changed = result.declaredSubrangeCount != null && result.declaredSubrangeCount != count
        val final = if (changed) result.copy(status = UsbClockQueryStatus.MALFORMED_RESPONSE,
            detail = "Subrange count changed between header ($count) and body (${result.declaredSubrangeCount}); re-query required.") else result
        return final.copy(attempts = listOf(header, body.copy(status = final.status, detail = final.detail)))
    }

    private fun read(request: UsbClockReadRequest, transport: UsbClockReadTransport,
        accessFailure: () -> UsbClockQueryStatus?): UsbClockReadAttempt {
        accessFailure()?.let { return UsbClockReadAttempt(request, null, emptyList(), it, "Read not issued: access/generation no longer current.") }
        val buffer = ByteArray(request.length)
        return try {
            val count = transport.read(request, buffer)
            val native = (transport as? UsbClockNativeReadTransport)?.lastControlEvidence()
            val raw = if (count > 0) buffer.take(count.coerceAtMost(buffer.size)).map { it.toInt() and 0xff } else emptyList()
            val access = accessFailure()
            val status = access ?: when {
                count < 0 -> native?.failureStatus() ?: UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR
                count < request.length -> UsbClockQueryStatus.SHORT_RESPONSE
                count > buffer.size -> UsbClockQueryStatus.MALFORMED_RESPONSE
                else -> UsbClockQueryStatus.SUCCESS
            }
            val detail = native?.takeIf { count < 0 }?.errorExplanation() ?: when (status) {
                UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR -> "Android returned $count; STALL/rejection and transport failure cannot be distinguished by this API."
                UsbClockQueryStatus.SHORT_RESPONSE -> "Returned $count of ${request.length} requested bytes."
                UsbClockQueryStatus.MALFORMED_RESPONSE -> "Returned byte count exceeds response buffer."
                UsbClockQueryStatus.SUCCESS -> null
                else -> "Response arrived after access/generation changed; it is not authoritative."
            }
            UsbClockReadAttempt(request, count, raw, status, detail, native)
        } catch (cancelled: CancellationException) { throw cancelled }
        catch (_: SecurityException) { UsbClockReadAttempt(request, null, emptyList(),
            accessFailure() ?: UsbClockQueryStatus.PERMISSION_LOST, "USB access denied during control read.") }
        catch (_: Exception) { UsbClockReadAttempt(request, null, emptyList(),
            accessFailure() ?: UsbClockQueryStatus.TRANSPORT_ERROR, "Control read threw an exception; no device data inferred.") }
    }

    /** Decodes only the returned bytes, preserves complete tuples even on truncated/malformed input. */
    fun decode(query: UsbClockQuery, buffer: ByteArray, returnedCount: Int): UsbClockQueryResult {
        if (returnedCount < 0) return UsbClockQueryResult(query, UsbClockQueryStatus.REJECTED_OR_TRANSPORT_ERROR,
            detail = "Negative Android transfer result; cause unavailable.")
        if (returnedCount > buffer.size) return UsbClockQueryResult(query, UsbClockQueryStatus.MALFORMED_RESPONSE,
            detail = "Returned length exceeds buffer.")
        val bytes = buffer.take(returnedCount).map { it.toInt() and 0xff }
        val required = when (query) { UsbClockQuery.CURRENT_FREQUENCY -> 4; UsbClockQuery.CLOCK_VALIDITY -> 1; else -> 2 }
        if (bytes.size < required) return UsbClockQueryResult(query, UsbClockQueryStatus.SHORT_RESPONSE,
            detail = "Expected at least $required bytes, received ${bytes.size}.")
        if (query == UsbClockQuery.CURRENT_FREQUENCY) return if (bytes.size == 4)
            UsbClockQueryResult(query, UsbClockQueryStatus.SUCCESS, currentFrequencyHz = u32(bytes, 0))
        else UsbClockQueryResult(query, UsbClockQueryStatus.MALFORMED_RESPONSE, detail = "Frequency CUR is exactly four bytes.")
        if (query == UsbClockQuery.CLOCK_VALIDITY) return when {
            bytes.size != 1 || bytes[0] !in 0..1 -> UsbClockQueryResult(query, UsbClockQueryStatus.MALFORMED_RESPONSE,
                detail = "Clock validity CUR must contain exactly one Boolean byte (0 or 1).")
            else -> UsbClockQueryResult(query, UsbClockQueryStatus.SUCCESS, clockValid = bytes[0] == 1)
        }
        val count = u16(bytes, 0)
        if (count !in 1..MAX_SUBRANGES) return UsbClockQueryResult(query, UsbClockQueryStatus.MALFORMED_RESPONSE,
            declaredSubrangeCount = count, detail = "Subrange count $count is outside 1..$MAX_SUBRANGES.")
        val expected = 2 + count * 12 // bounded before multiplication
        val complete = minOf(count, (bytes.size - 2) / 12)
        val ranges = (0 until complete).map { i ->
            val offset = 2 + i * 12
            UsbSamplingFrequencyRange(u32(bytes, offset), u32(bytes, offset + 4), u32(bytes, offset + 8))
        }
        val semanticIssues = ranges.mapIndexedNotNull { i, range -> when {
            range.maximumHz < range.minimumHz -> "Tuple ${i + 1}: MIN exceeds MAX."
            range.minimumHz == range.maximumHz && range.resolutionHz != 0L -> "Tuple ${i + 1}: singleton RES must be zero."
            range.minimumHz < range.maximumHz && range.resolutionHz == 0L -> "Tuple ${i + 1}: non-singleton RES=0 has indeterminate semantics."
            range.resolutionHz > 0 && (range.maximumHz - range.minimumHz) % range.resolutionHz != 0L -> "Tuple ${i + 1}: MAX is not on the resolution grid."
            i > 0 && ranges[i - 1].maximumHz >= range.minimumHz -> "Tuple ${i + 1}: ranges are unordered or overlap."
            else -> null
        } }
        val status = when {
            bytes.size < expected -> UsbClockQueryStatus.SHORT_RESPONSE
            bytes.size > expected || semanticIssues.isNotEmpty() -> UsbClockQueryStatus.MALFORMED_RESPONSE
            else -> UsbClockQueryStatus.SUCCESS
        }
        val detail = when {
            bytes.size != expected -> "Declared $count tuples require $expected bytes; received ${bytes.size}. "
            else -> ""
        } + semanticIssues.joinToString(" ")
        return UsbClockQueryResult(query, status, ranges = ranges, declaredSubrangeCount = count,
            detail = detail.takeIf { it.isNotBlank() })
    }

    fun assessRate(rateHz: Long, rangeResult: UsbClockQueryResult?): UsbClockRateSupport {
        val result = rangeResult ?: return UsbClockRateSupport.INDETERMINATE
        if (result.query != UsbClockQuery.FREQUENCY_RANGE || result.status != UsbClockQueryStatus.SUCCESS ||
            result.ranges.isEmpty()) return UsbClockRateSupport.INDETERMINATE
        val represented = result.ranges.any { range ->
            rateHz in range.minimumHz..range.maximumHz &&
                (if (range.minimumHz == range.maximumHz) range.resolutionHz == 0L
                else range.resolutionHz > 0 && (rateHz - range.minimumHz) % range.resolutionHz == 0L)
        }
        return if (represented) UsbClockRateSupport.REPRESENTED else UsbClockRateSupport.NOT_REPRESENTED
    }

    private fun u16(bytes: List<Int>, offset: Int): Int = bytes[offset] or (bytes[offset + 1] shl 8)
    private fun u32(bytes: List<Int>, offset: Int): Long = (0..3).fold(0L) { value, i ->
        value or (bytes[offset + i].toLong() shl (8 * i))
    }
    private fun List<Int>.toByteArray(): ByteArray = map { it.toByte() }.toByteArray()
}
