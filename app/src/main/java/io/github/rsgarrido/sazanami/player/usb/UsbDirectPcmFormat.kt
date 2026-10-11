package io.github.rsgarrido.sazanami.player.usb

/** Session 13 boundary only. No coercion; silence/reference diagnostics retain their 44.1 rate. */
internal object UsbDirectPcmFormat {
    const val REFERENCE_RATE_HZ = 44100L
    fun supportsRate(rateHz: Long) = rateHz == 44100L || rateHz == 48000L
    fun supports(rateHz: Long, channels: Long, bits: Long) = supportsRate(rateHz) && channels == 2L && bits == 16L
}
