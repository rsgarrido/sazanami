package io.github.rsgarrido.sazanami.player.usb

/** Closed native test vectors only. No application-supplied PCM. */
internal enum class UsbPcmDiagnosticMode { ZERO, REFERENCE, FLAC_PREFIX }

internal fun pcmHashHex(value: Long) = value.toULong().toString(16).padStart(16, '0')

internal data class UsbReferencePreflight(val values: List<Long> = emptyList(), val exceptionType: String? = null) {
    val passed get() = exceptionType == null && values.size == 12 && values[0] == 1L && values[1] == 0L &&
        values[2] == 100L && values[3] == EXPECTED_HASH && values[4] == EXPECTED_HASH &&
        values[5] == 1024L && values[6] == -1024L && values[7] == 1024L &&
        values[8] == 1L && values[9] == 1L && values[10] == 1L && values[11] in 1L..100L
    fun value(index: Int) = values.getOrNull(index)
    companion object { val EXPECTED_HASH = 0xbbd1384f8c503ee1uL.toLong() }
}
