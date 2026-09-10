package io.github.rsgarrido.sazanami.player.spectrum

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Small reusable in-place FFT specialized for the analyzer's fixed power-of-two size. */
internal class Radix2Fft(val size: Int) {
    private val bitReversed = IntArray(size)
    private val cosine = FloatArray(size / 2)
    private val sine = FloatArray(size / 2)

    init {
        require(size >= 2 && size and (size - 1) == 0) {
            "FFT size must be a power of two"
        }
        val bitCount = Integer.numberOfTrailingZeros(size)
        for (index in 0 until size) {
            bitReversed[index] = Integer.reverse(index) ushr (Int.SIZE_BITS - bitCount)
        }
        for (index in cosine.indices) {
            val angle = -2.0 * PI * index / size
            cosine[index] = cos(angle).toFloat()
            sine[index] = sin(angle).toFloat()
        }
    }

    fun transform(real: FloatArray, imaginary: FloatArray) {
        require(real.size >= size && imaginary.size >= size)

        for (index in 0 until size) {
            val reversed = bitReversed[index]
            if (reversed > index) {
                val realValue = real[index]
                real[index] = real[reversed]
                real[reversed] = realValue
                val imaginaryValue = imaginary[index]
                imaginary[index] = imaginary[reversed]
                imaginary[reversed] = imaginaryValue
            }
        }

        var length = 2
        while (length <= size) {
            val halfLength = length / 2
            val tableStep = size / length
            var blockStart = 0
            while (blockStart < size) {
                var offset = 0
                while (offset < halfLength) {
                    val tableIndex = offset * tableStep
                    val upper = blockStart + offset
                    val lower = upper + halfLength
                    val lowerReal = real[lower]
                    val lowerImaginary = imaginary[lower]
                    val twiddleReal =
                        lowerReal * cosine[tableIndex] - lowerImaginary * sine[tableIndex]
                    val twiddleImaginary =
                        lowerReal * sine[tableIndex] + lowerImaginary * cosine[tableIndex]
                    real[lower] = real[upper] - twiddleReal
                    imaginary[lower] = imaginary[upper] - twiddleImaginary
                    real[upper] += twiddleReal
                    imaginary[upper] += twiddleImaginary
                    offset++
                }
                blockStart += length
            }
            length = length shl 1
        }
    }
}
