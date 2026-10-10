package io.github.rsgarrido.sazanami.player.usb

internal data class UsbSilentTransportResolution(val plan: UsbSilentTransportPlan? = null, val issues: List<String> = emptyList())

/** Session 7 accepts only the existing PCM16 configuration and a unique explicit-feedback pair. */
internal class UsbSilentTransportPlan private constructor(val configuration: UsbAudioConfigurationResolution,
    val data: UsbEndpointDescriptor, val feedback: UsbEndpointDescriptor) {
    val configurationPlan get() = requireNotNull(configuration.plan)
    companion object {
        fun resolve(model: UsbAudioDescriptorModel, android: List<UsbAndroidInterfaceSnapshot>): UsbSilentTransportResolution {
            val configuration = UsbAudioConfigurationPlan.resolve(model, android)
            val plan = configuration.plan ?: return UsbSilentTransportResolution(issues = configuration.issues)
            fun blocked(message: String) = UsbSilentTransportResolution(issues = configuration.issues + message)
            val target = model.interfaces.singleOrNull { it.key == plan.streaming } ?: return blocked("Target AS descriptor missing.")
            val data = target.endpoints.singleOrNull { it.address == plan.dataEndpoint && !it.isIn && it.transferType == 1 &&
                it.usageType == 0 && it.synchronizationType == 1 && it.attributes and 0xc0 == 0 }
                ?: return blocked("Session 7 requires one asynchronous isochronous OUT data endpoint.")
            val feedback = target.endpoints.singleOrNull { it.isIn && it.transferType == 1 && it.usageType == 1 &&
                it.synchronizationType == 0 && it.attributes and 0xc0 == 0 }
                ?: return blocked("Session 7 requires unique isochronous IN explicit feedback on the target AS alternate.")
            if (target.endpoints.size != 2 || data.address !in 1..15 || feedback.address !in 0x81..0x8f ||
                data.synchronizationAddress?.let { it != 0 && it != feedback.address } == true)
                return blocked("Endpoint pairing/addresses are ambiguous; implicit feedback is unsupported.")
            if (data.interval !in 1..3 || feedback.interval !in 1..7 || UsbIsoTiming.highSpeedCapacity(data.maxPacketSizeRaw) == null ||
                (data.additionalTransactions > 0 && data.interval != 1) || feedback.maxPacketSizeRaw != 4 || data.audio?.attributes?.let { it != 0 } == true)
                return blocked("Unsupported endpoint capacity/interval or MaxPacketsOnly; four-byte explicit feedback and variable PCM packet lengths required.")
            // A missing class-specific endpoint descriptor is also unresolved, rather than assumed safe.
            if (data.audio == null) return blocked("Class-specific UAC2 data endpoint descriptor is required.")
            val exposed = android.singleOrNull { it.number == plan.streaming.number && it.alternate == plan.streaming.alternate }
            if (exposed == null || exposed.endpoints.size != 2 || listOf(data, feedback).any { descriptor ->
                    exposed.endpoints.count { it.address == descriptor.address && it.attributes == descriptor.attributes &&
                        it.maxPacketSize == descriptor.maxPacketSizeRaw && it.interval == descriptor.interval } != 1 })
                return blocked("Android endpoint facts do not match the parsed target alternate.")
            return UsbSilentTransportResolution(UsbSilentTransportPlan(configuration, data, feedback))
        }
    }
    fun timing(speed: UsbFsSpeed?): UsbIsoTiming? = UsbIsoTiming.resolve(speed, data, feedback)
}

internal data class UsbIsoTiming(val dataIntervalUs: Long, val feedbackIntervalUs: Long, val capacity: Int,
    val feedbackFormat: String = "UAC2 HIGH speed: unsigned little-endian 16.16 frames/microframe") {
    val feedbackHoldoverMs get() = maxOf(20L, feedbackIntervalUs * 4 / 1000)
    companion object {
        const val BYTES_PER_FRAME = 4
        const val DURATION_MS = 1000L
        const val BOOTSTRAP_MS = 100L
        const val OUT_POOL = 8
        const val OUT_PACKETS = 8
        const val FEEDBACK_POOL = 2
        const val INVALID_FEEDBACK_LIMIT = 3
        // USB 2.0 §§5.12.4.2 / 9.6.6; UAC2 §3.16.2.2 references these rules.
        fun serviceIntervalUs(speed: UsbFsSpeed?, interval: Int): Long? =
            if (speed == UsbFsSpeed.HIGH && interval in 1..16) 125L * (1L shl (interval - 1)) else null
        fun highSpeedCapacity(raw: Int): Int? {
            val bytes = raw and 0x7ff
            val extra = (raw shr 11) and 3
            return if (raw in 1..0xffff && raw and 0xe000 == 0 && bytes in 1..1024 && extra < 3)
                bytes * (extra + 1) else null
        }
        fun resolve(speed: UsbFsSpeed?, data: UsbEndpointDescriptor, feedback: UsbEndpointDescriptor): UsbIsoTiming? {
            val dataUs = serviceIntervalUs(speed, data.interval) ?: return null
            val feedbackUs = serviceIntervalUs(speed, feedback.interval) ?: return null
            val capacity = highSpeedCapacity(data.maxPacketSizeRaw) ?: return null
            if (dataUs > 500 || feedbackUs > 8000 || feedback.maxPacketSizeRaw != 4) return null
            val scaledSecond = 100L * 1_000_000L
            val worstFrames = (UsbExplicitFeedback.TARGET_HZ * dataUs * (100 + UsbExplicitFeedback.PLAUSIBILITY_PERCENT) + scaledSecond - 1) / scaledSecond
            if (capacity < worstFrames * BYTES_PER_FRAME || (data.additionalTransactions > 0 && data.interval != 1)) return null
            return UsbIsoTiming(dataUs, feedbackUs, capacity)
        }
    }
}

internal enum class UsbFeedbackValidity { VALID, WRONG_LENGTH, ZERO, RESERVED_BITS, IMPLAUSIBLE, COMPLETION_ERROR, STALE }
internal data class UsbFeedbackSample(val bytes: List<Int>, val raw: Long?, val validity: UsbFeedbackValidity) {
    val rateHz get() = raw?.toDouble()?.times(8000.0 / 65536.0)
    fun framesPerServiceInterval(dataIntervalUs: Long) = raw?.toDouble()?.times(dataIntervalUs / 125.0 / 65536.0)
}
internal object UsbExplicitFeedback {
    const val PLAUSIBILITY_PERCENT = 5L
    const val TARGET_HZ = 44100L
    fun decode(bytes: List<Int>, packetStatus: Int = 0): UsbFeedbackSample {
        if (packetStatus != 0) return UsbFeedbackSample(bytes, null, UsbFeedbackValidity.COMPLETION_ERROR)
        if (bytes.size != 4 || bytes.any { it !in 0..255 }) return UsbFeedbackSample(bytes, null, UsbFeedbackValidity.WRONG_LENGTH)
        val raw = bytes.withIndex().fold(0L) { value, (index, byte) -> value or (byte.toLong() shl (index * 8)) }
        val status = when {
            raw == 0L -> UsbFeedbackValidity.ZERO
            raw and 0xf0000000L != 0L -> UsbFeedbackValidity.RESERVED_BITS
            raw * 8000L * 100L !in (TARGET_HZ * 65536L * (100 - PLAUSIBILITY_PERCENT))..
                (TARGET_HZ * 65536L * (100 + PLAUSIBILITY_PERCENT)) -> UsbFeedbackValidity.IMPLAUSIBLE
            else -> UsbFeedbackValidity.VALID
        }
        return UsbFeedbackSample(bytes.toList(), raw, status)
    }
}

/** Reference model for native arithmetic. Common denominator preserves the nominal 44100/8000 exactly. */
internal class UsbIsoFrameAccumulator {
    companion object { const val DENOMINATOR = 8000L * 65536L }
    var remainder = 0L; private set
    var frames = 0L; private set
    fun next(microframes: Int, feedbackRaw: Long? = null): Int {
        require(microframes in 1..4)
        require(feedbackRaw == null || feedbackRaw in 1..0x0fffffffL)
        val increment = (feedbackRaw?.times(8000L) ?: (44100L * 65536L)) * microframes
        remainder += increment
        val count = remainder / DENOMINATOR
        remainder %= DENOMINATOR
        frames += count
        return count.toInt()
    }
}
