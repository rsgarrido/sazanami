package io.github.rsgarrido.sazanami.player.spectrum

import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.hypot
import kotlin.math.ln
import kotlin.math.log10
import kotlin.math.min

internal data class SpectrumAnalyzerConfig(
    val fftSize: Int = DEFAULT_FFT_SIZE,
    val bandCount: Int = DEFAULT_BAND_COUNT,
    val minimumFrequencyHz: Float = DEFAULT_MINIMUM_FREQUENCY_HZ,
    val maximumFrequencyHz: Float = DEFAULT_MAXIMUM_FREQUENCY_HZ,
    val noiseFloorDb: Float = DEFAULT_NOISE_FLOOR_DB,
    val ceilingDb: Float = DEFAULT_CEILING_DB,
    val attackMillis: Float = DEFAULT_ATTACK_MILLIS,
    val releaseMillis: Float = DEFAULT_RELEASE_MILLIS,
    val updateCadenceHz: Int = DEFAULT_UPDATE_CADENCE_HZ
) {
    init {
        require(fftSize >= 2 && fftSize and (fftSize - 1) == 0)
        require(bandCount > 0)
        require(minimumFrequencyHz > 0f && maximumFrequencyHz > minimumFrequencyHz)
        require(noiseFloorDb < ceilingDb)
        require(attackMillis > 0f && releaseMillis > 0f)
        require(updateCadenceHz > 0)
    }

    companion object {
        const val DEFAULT_FFT_SIZE = 1_024
        const val DEFAULT_BAND_COUNT = 28
        const val DEFAULT_MINIMUM_FREQUENCY_HZ = 60f
        const val DEFAULT_MAXIMUM_FREQUENCY_HZ = 16_000f
        const val DEFAULT_NOISE_FLOOR_DB = -72f
        const val DEFAULT_CEILING_DB = -3f
        const val DEFAULT_ATTACK_MILLIS = 30f
        const val DEFAULT_RELEASE_MILLIS = 240f
        const val DEFAULT_UPDATE_CADENCE_HZ = 30
    }
}

/** Compose-independent PCM-to-canonical-spectrum analyzer with reusable DSP storage. */
internal class SpectrumAnalyzer(
    val config: SpectrumAnalyzerConfig = SpectrumAnalyzerConfig()
) {
    private val fft = Radix2Fft(config.fftSize)
    private val hannWindow = FloatArray(config.fftSize)
    private val real = FloatArray(config.fftSize)
    private val imaginary = FloatArray(config.fftSize)
    private val monoScratch = FloatArray(config.fftSize)
    private val smoothedBands = FloatArray(config.bandCount)
    private val bandStartBins = IntArray(config.bandCount)
    private val bandEndBinsExclusive = IntArray(config.bandCount)
    private val bandCenterFrequencies = FloatArray(config.bandCount)
    private val windowAmplitudeSum: Float

    private var mappedSampleRateHz = 0
    private var lastAnalysisNanos = 0L

    init {
        var coefficientSum = 0f
        for (index in hannWindow.indices) {
            val coefficient = (0.5 - 0.5 * cos(2.0 * PI * index / (config.fftSize - 1)))
                .toFloat()
            hannWindow[index] = coefficient
            coefficientSum += coefficient
        }
        windowAmplitudeSum = coefficientSum.coerceAtLeast(1f)
    }

    fun analyzeMono(
        samples: FloatArray,
        sampleRateHz: Int,
        timestampNanos: Long
    ): SpectrumFrame {
        require(sampleRateHz > 0)
        val copied = min(samples.size, config.fftSize)
        for (index in 0 until copied) {
            val sample = samples[index]
            monoScratch[index] = if (sample.isFinite()) sample.coerceIn(-1f, 1f) else 0f
        }
        java.util.Arrays.fill(monoScratch, copied, config.fftSize, 0f)
        return analyzePreparedMono(sampleRateHz, timestampNanos)
    }

    fun analyzeInterleavedPcm16(
        samples: ShortArray,
        frameCount: Int,
        sampleRateHz: Int,
        channelCount: Int,
        timestampNanos: Long
    ): SpectrumFrame {
        require(sampleRateHz > 0)
        require(channelCount > 0)
        val safeFrameCount = min(min(frameCount, config.fftSize), samples.size / channelCount)
        var frame = 0
        var sampleIndex = 0
        while (frame < safeFrameCount) {
            var sum = 0f
            var channel = 0
            while (channel < channelCount) {
                sum += samples[sampleIndex + channel].toInt() / PCM16_SCALE
                channel++
            }
            monoScratch[frame] = (sum / channelCount).coerceIn(-1f, 1f)
            frame++
            sampleIndex += channelCount
        }
        java.util.Arrays.fill(monoScratch, safeFrameCount, config.fftSize, 0f)
        return analyzePreparedMono(sampleRateHz, timestampNanos)
    }

    fun reset(timestampNanos: Long = 0L): SpectrumFrame {
        java.util.Arrays.fill(smoothedBands, 0f)
        java.util.Arrays.fill(real, 0f)
        java.util.Arrays.fill(imaginary, 0f)
        java.util.Arrays.fill(monoScratch, 0f)
        lastAnalysisNanos = 0L
        mappedSampleRateHz = 0
        return SpectrumFrame.unavailable(
            bandCount = config.bandCount,
            timestampNanos = timestampNanos
        )
    }

    internal fun bandCenterFrequencyHz(index: Int, sampleRateHz: Int): Float {
        ensureBandMapping(sampleRateHz)
        return bandCenterFrequencies[index]
    }

    private fun analyzePreparedMono(sampleRateHz: Int, timestampNanos: Long): SpectrumFrame {
        ensureBandMapping(sampleRateHz)
        for (index in 0 until config.fftSize) {
            real[index] = monoScratch[index] * hannWindow[index]
            imaginary[index] = 0f
        }
        fft.transform(real, imaginary)

        val elapsedSeconds = if (lastAnalysisNanos > 0L && timestampNanos > lastAnalysisNanos) {
            (timestampNanos - lastAnalysisNanos) / NANOS_PER_SECOND
        } else {
            1.0 / config.updateCadenceHz
        }
        lastAnalysisNanos = timestampNanos
        val output = FloatArray(config.bandCount)
        for (band in 0 until config.bandCount) {
            var peakMagnitude = 0f
            var bin = bandStartBins[band]
            while (bin < bandEndBinsExclusive[band]) {
                val magnitude = (2.0 * hypot(real[bin].toDouble(), imaginary[bin].toDouble()) /
                    windowAmplitudeSum).toFloat()
                if (magnitude.isFinite() && magnitude > peakMagnitude) {
                    peakMagnitude = magnitude
                }
                bin++
            }
            val decibels = if (peakMagnitude > MINIMUM_MAGNITUDE) {
                (20.0 * log10(peakMagnitude.toDouble())).toFloat()
            } else {
                config.noiseFloorDb
            }
            val normalized = ((decibels - config.noiseFloorDb) /
                (config.ceilingDb - config.noiseFloorDb)).coerceIn(0f, 1f)
            val previous = smoothedBands[band]
            val timeConstantMillis = if (normalized > previous) {
                config.attackMillis
            } else {
                config.releaseMillis
            }
            val coefficient = (1.0 - exp(-elapsedSeconds / (timeConstantMillis / 1_000.0)))
                .toFloat()
                .coerceIn(0f, 1f)
            val candidate = previous + (normalized - previous) * coefficient
            val smoothed = if (candidate.isFinite()) {
                candidate.coerceIn(0f, 1f)
            } else {
                0f
            }
            smoothedBands[band] = smoothed
            output[band] = smoothed
        }
        return SpectrumFrame(
            monoBands = output,
            leftBands = null,
            rightBands = null,
            sampleRateHz = sampleRateHz,
            availability = SpectrumAvailability.AVAILABLE,
            timestampNanos = timestampNanos
        )
    }

    private fun ensureBandMapping(sampleRateHz: Int) {
        if (mappedSampleRateHz == sampleRateHz) return
        val nyquist = sampleRateHz / 2f
        val maximum = min(config.maximumFrequencyHz, nyquist * 0.95f)
            .coerceAtLeast(config.minimumFrequencyHz * 1.01f)
        val logarithmicRange = ln(maximum / config.minimumFrequencyHz)
        val binWidthHz = sampleRateHz.toFloat() / config.fftSize
        val maximumBinExclusive = config.fftSize / 2 + 1
        for (band in 0 until config.bandCount) {
            val lowerFraction = band.toFloat() / config.bandCount
            val upperFraction = (band + 1f) / config.bandCount
            val lowerHz = config.minimumFrequencyHz * exp(logarithmicRange * lowerFraction)
            val upperHz = config.minimumFrequencyHz * exp(logarithmicRange * upperFraction)
            val start = (lowerHz / binWidthHz).toInt().coerceIn(1, maximumBinExclusive - 1)
            val end = ceil(upperHz / binWidthHz).toInt()
                .coerceIn(start + 1, maximumBinExclusive)
            bandStartBins[band] = start
            bandEndBinsExclusive[band] = end
            bandCenterFrequencies[band] = kotlin.math.sqrt(lowerHz * upperHz)
        }
        mappedSampleRateHz = sampleRateHz
    }

    private companion object {
        const val PCM16_SCALE = 32_768f
        const val NANOS_PER_SECOND = 1_000_000_000.0
        const val MINIMUM_MAGNITUDE = 1.0e-12f
    }
}
