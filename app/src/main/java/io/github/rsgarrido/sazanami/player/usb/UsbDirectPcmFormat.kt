package io.github.rsgarrido.sazanami.player.usb

/** Exact source policy; no coercion. Silence/reference diagnostics retain 16/44.1. */
internal object UsbDirectPcmFormat {
    const val REFERENCE_RATE_HZ = 44100L
    // Clock/scheduler/progress only; source acceptance requires a complete tuple.
    fun supportsRate(rateHz: Long) = rateHz == 44100L || rateHz == 48000L || rateHz == 96000L
    fun supports(rateHz: Long, channels: Long, bits: Long) = channels == 2L &&
        (((rateHz == 44100L || rateHz == 48000L) && bits == 16L) ||
            ((rateHz == 48000L || rateHz == 96000L) && bits == 24L))
    fun resolve(rateHz: Long, channels: Long, bits: Long): UsbDirectFormat? =
        if (supports(rateHz,channels,bits)) UsbDirectFormat(rateHz,channels.toInt(),bits.toInt()) else null
}

internal data class UsbDirectFormat internal constructor(val sampleRateHz: Long, val channelCount: Int, val bitsPerSample: Int) {
    init { require(UsbDirectPcmFormat.supports(sampleRateHz,channelCount.toLong(),bitsPerSample.toLong())) }
    val bytesPerSample get()=bitsPerSample/8
    val bytesPerFrame get()=channelCount*bytesPerSample
    val subslotBytes get()=bytesPerSample
    val validBits get()=bitsPerSample
    val label get()="PCM${bitsPerSample}LE packed"
}
internal data class UsbSelectedPcmFormat(val source: UsbDirectFormat, val streaming: UsbInterfaceKey)

/** One fixed-size JNI example row: index, signed L/R, six canonical-byte slots (unused slots zero). */
internal fun decodeDirectPcmExample(row: List<Long>, format: UsbDirectFormat): UsbPcmFrameEvidence {
    require(row.size==9 && row[0]>=0 && row.drop(3).all { it in 0L..255L })
    val limit=1L shl (format.bitsPerSample-1)
    require(row[1] in -limit until limit && row[2] in -limit until limit)
    val bytes=row.drop(3).take(format.bytesPerFrame).map(Long::toInt)
    require(row.drop(3+format.bytesPerFrame).all { it==0L })
    for(channel in 0..1) for(byte in 0 until format.bytesPerSample)
        require(bytes[channel*format.bytesPerSample+byte]==((row[1+channel] ushr (byte*8)) and 255L).toInt())
    return UsbPcmFrameEvidence(row[0],row[1].toInt(),row[2].toInt(),bytes)
}
