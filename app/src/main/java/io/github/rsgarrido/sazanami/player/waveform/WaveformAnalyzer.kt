package io.github.rsgarrido.sazanami.player.waveform

import android.content.Context
import android.media.AudioFormat
import android.media.MediaCodec
import android.media.MediaExtractor
import android.media.MediaFormat
import io.github.rsgarrido.sazanami.data.Song
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.coroutines.coroutineContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.ensureActive

fun interface WaveformAnalyzer {
    suspend fun analyze(
        song: Song,
        sourceKey: String,
        barCount: Int
    ): WaveformData?
}

class AndroidWaveformAnalyzer(
    context: Context
) : WaveformAnalyzer {
    private val appContext = context.applicationContext

    override suspend fun analyze(
        song: Song,
        sourceKey: String,
        barCount: Int
    ): WaveformData? {
        if (barCount <= 0) return null

        return try {
            analyzeTrack(song, sourceKey, barCount)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private suspend fun analyzeTrack(
        song: Song,
        sourceKey: String,
        barCount: Int
    ): WaveformData? {
        val extractor = MediaExtractor()
        var decoder: MediaCodec? = null
        var decoderStarted = false

        try {
            setExtractorDataSource(extractor, song)
            val trackIndex = findAudioTrack(extractor) ?: return null
            extractor.selectTrack(trackIndex)

            val inputFormat = extractor.getTrackFormat(trackIndex)
            val mimeType = inputFormat.getString(MediaFormat.KEY_MIME) ?: return null
            val durationUs = inputFormat.longOrNull(MediaFormat.KEY_DURATION)
                ?.takeIf { duration -> duration > 0L }
                ?: (song.duration.coerceAtLeast(1L) * 1_000L)

            decoder = MediaCodec.createDecoderByType(mimeType)
            decoder.configure(inputFormat, null, null, 0)
            decoder.start()
            decoderStarted = true

            val accumulator = PcmWaveformAccumulator(
                barCount = barCount,
                durationUs = durationUs
            )
            val bufferInfo = MediaCodec.BufferInfo()
            var outputFormat = inputFormat
            var inputEnded = false
            var outputEnded = false
            var idleIterations = 0
            val startedAtNanos = System.nanoTime()

            while (!outputEnded) {
                coroutineContext.ensureActive()
                if (System.nanoTime() - startedAtNanos > MAX_ANALYSIS_NANOS) return null

                var madeProgress = false
                if (!inputEnded) {
                    val inputIndex = decoder.dequeueInputBuffer(CODEC_TIMEOUT_US)
                    if (inputIndex >= 0) {
                        val inputBuffer = decoder.getInputBuffer(inputIndex) ?: return null
                        inputBuffer.clear()
                        val sampleSize = extractor.readSampleData(inputBuffer, 0)
                        if (sampleSize < 0) {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                0,
                                0L,
                                MediaCodec.BUFFER_FLAG_END_OF_STREAM
                            )
                            inputEnded = true
                        } else {
                            decoder.queueInputBuffer(
                                inputIndex,
                                0,
                                sampleSize,
                                extractor.sampleTime.coerceAtLeast(0L),
                                0
                            )
                            extractor.advance()
                        }
                        madeProgress = true
                    }
                }

                when (val outputIndex = decoder.dequeueOutputBuffer(bufferInfo, CODEC_TIMEOUT_US)) {
                    MediaCodec.INFO_OUTPUT_FORMAT_CHANGED -> {
                        outputFormat = decoder.outputFormat
                        madeProgress = true
                    }

                    MediaCodec.INFO_TRY_AGAIN_LATER -> Unit

                    else -> if (outputIndex >= 0) {
                        if (bufferInfo.size > 0) {
                            decoder.getOutputBuffer(outputIndex)?.let { outputBuffer ->
                                accumulator.add(
                                    buffer = outputBuffer,
                                    info = bufferInfo,
                                    format = outputFormat
                                )
                            }
                        }
                        outputEnded = bufferInfo.flags and
                            MediaCodec.BUFFER_FLAG_END_OF_STREAM != 0
                        decoder.releaseOutputBuffer(outputIndex, false)
                        madeProgress = true
                    }
                }

                idleIterations = if (madeProgress) 0 else idleIterations + 1
                if (idleIterations > MAX_IDLE_ITERATIONS) return null
            }

            val amplitudes = normalizeWaveformAmplitudes(accumulator.amplitudes())
            return amplitudes.takeIf(List<Float>::isNotEmpty)?.let { values ->
                WaveformData(amplitudes = values, sourceKey = sourceKey)
            }
        } finally {
            if (decoderStarted) runCatching { decoder?.stop() }
            runCatching { decoder?.release() }
            extractor.release()
        }
    }

    private fun setExtractorDataSource(extractor: MediaExtractor, song: Song) {
        val uriResult = runCatching {
            extractor.setDataSource(appContext, song.uri, null)
        }
        if (uriResult.isFailure) {
            extractor.setDataSource(song.filePath)
        }
    }

    private fun findAudioTrack(extractor: MediaExtractor): Int? {
        return (0 until extractor.trackCount).firstOrNull { trackIndex ->
            extractor.getTrackFormat(trackIndex)
                .getString(MediaFormat.KEY_MIME)
                ?.startsWith("audio/") == true
        }
    }

    private fun MediaFormat.longOrNull(key: String): Long? {
        return if (containsKey(key)) runCatching { getLong(key) }.getOrNull() else null
    }

    companion object {
        private const val CODEC_TIMEOUT_US = 10_000L
        private const val MAX_IDLE_ITERATIONS = 1_000
        private const val MAX_ANALYSIS_NANOS = 45_000_000_000L
    }
}

private class PcmWaveformAccumulator(
    barCount: Int,
    private val durationUs: Long
) {
    private val squaredAmplitudeTotals = DoubleArray(barCount)
    private val sampleCounts = LongArray(barCount)

    fun add(
        buffer: ByteBuffer,
        info: MediaCodec.BufferInfo,
        format: MediaFormat
    ) {
        val channelCount = format.intOrNull(MediaFormat.KEY_CHANNEL_COUNT)
            ?.coerceAtLeast(1) ?: 1
        val sampleRate = format.intOrNull(MediaFormat.KEY_SAMPLE_RATE)
            ?.coerceAtLeast(1) ?: return
        val encoding = format.intOrNull(MediaFormat.KEY_PCM_ENCODING)
            ?: AudioFormat.ENCODING_PCM_16BIT
        val bytesPerSample = bytesPerSample(encoding) ?: return
        val bytesPerFrame = bytesPerSample * channelCount
        val frameCount = info.size / bytesPerFrame
        if (frameCount <= 0) return

        val pcm = buffer.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val startOffset = info.offset.coerceAtLeast(0)
        val endOffset = (startOffset + info.size).coerceAtMost(pcm.limit())
        if (endOffset - startOffset < bytesPerFrame) return

        val sampleStride = max(1, frameCount / MAX_SAMPLED_FRAMES_PER_BUFFER)
        var frameIndex = 0
        while (frameIndex < frameCount) {
            val frameOffset = startOffset + frameIndex * bytesPerFrame
            if (frameOffset + bytesPerFrame > endOffset) break

            var channelTotal = 0.0
            repeat(channelCount) { channelIndex ->
                val sampleOffset = frameOffset + channelIndex * bytesPerSample
                channelTotal += readAmplitude(pcm, sampleOffset, encoding)
            }
            val amplitude = channelTotal / channelCount
            val frameTimeUs = info.presentationTimeUs +
                frameIndex.toLong() * MICROS_PER_SECOND / sampleRate
            val bucket = ((frameTimeUs.coerceAtLeast(0L) * sampleCounts.size) /
                durationUs.coerceAtLeast(1L)).toInt().coerceIn(sampleCounts.indices)
            squaredAmplitudeTotals[bucket] += amplitude * amplitude
            sampleCounts[bucket]++
            frameIndex += sampleStride
        }
    }

    fun amplitudes(): List<Float> {
        if (sampleCounts.none { count -> count > 0L }) return emptyList()

        return List(sampleCounts.size) { index ->
            val count = sampleCounts[index]
            if (count == 0L) 0f else sqrt(squaredAmplitudeTotals[index] / count).toFloat()
        }
    }

    private fun MediaFormat.intOrNull(key: String): Int? {
        return if (containsKey(key)) runCatching { getInteger(key) }.getOrNull() else null
    }

    private fun bytesPerSample(encoding: Int): Int? = when (encoding) {
        AudioFormat.ENCODING_PCM_8BIT -> 1
        AudioFormat.ENCODING_PCM_16BIT -> 2
        AudioFormat.ENCODING_PCM_24BIT_PACKED -> 3
        AudioFormat.ENCODING_PCM_32BIT, AudioFormat.ENCODING_PCM_FLOAT -> 4
        else -> null
    }

    private fun readAmplitude(buffer: ByteBuffer, offset: Int, encoding: Int): Double {
        return when (encoding) {
            AudioFormat.ENCODING_PCM_8BIT ->
                abs((buffer.get(offset).toInt() and 0xFF) - 128) / 128.0

            AudioFormat.ENCODING_PCM_16BIT ->
                abs(buffer.getShort(offset).toInt()) / 32_768.0

            AudioFormat.ENCODING_PCM_24BIT_PACKED -> {
                val packed = (buffer.get(offset).toInt() and 0xFF) or
                    ((buffer.get(offset + 1).toInt() and 0xFF) shl 8) or
                    ((buffer.get(offset + 2).toInt() and 0xFF) shl 16)
                val signed = if (packed and 0x800000 != 0) packed or -0x1000000 else packed
                abs(signed.toDouble()) / 8_388_608.0
            }

            AudioFormat.ENCODING_PCM_32BIT ->
                abs(buffer.getInt(offset).toDouble()) / 2_147_483_648.0

            AudioFormat.ENCODING_PCM_FLOAT ->
                abs(buffer.getFloat(offset).takeIf(Float::isFinite)?.toDouble() ?: 0.0)

            else -> 0.0
        }.coerceIn(0.0, 1.0)
    }

    companion object {
        private const val MICROS_PER_SECOND = 1_000_000L
        private const val MAX_SAMPLED_FRAMES_PER_BUFFER = 512
    }
}
