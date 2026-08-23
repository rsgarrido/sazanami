package com.example.cdplaya.player.nativeaudio

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.os.ParcelFileDescriptor
import com.example.cdplaya.data.Song
import java.io.File
import kotlinx.coroutines.CancellationException

internal fun interface NativeWaveformDecoder {
    suspend fun decodeWaveform(song: Song, barCount: Int): List<Float>?
}

/**
 * Opens a local song as a stable file descriptor and delegates the complete offline
 * decode + waveform reduction to the C++ engine.
 */
internal class NativeAudioDecoder(
    context: Context
) : NativeWaveformDecoder {
    private val appContext = context.applicationContext

    override suspend fun decodeWaveform(song: Song, barCount: Int): List<Float>? {
        if (!NativeAudioBridge.isAvailable || barCount <= 0) return null

        val source = openSource(song) ?: return null
        return try {
            NativeAudioBridge.decodeWaveform(
                descriptor = source.descriptor,
                offset = source.offset,
                length = source.length,
                fallbackDurationUs = song.duration
                    .coerceIn(1L, Long.MAX_VALUE / MICROS_PER_MILLISECOND) *
                        MICROS_PER_MILLISECOND,
                barCount = barCount
            )?.toList()
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }
    }

    private fun openSource(song: Song): NativeAudioSource? {
        val assetDescriptor = runCatching {
            appContext.contentResolver.openAssetFileDescriptor(song.uri, "r")
        }.getOrNull()
        if (assetDescriptor != null) {
            try {
                NativeAudioSource.from(assetDescriptor)?.let { return it }
            } finally {
                runCatching { assetDescriptor.close() }
            }
        }

        val audioFile = File(song.filePath)
        if (!audioFile.isFile) return null
        val descriptor = runCatching {
            ParcelFileDescriptor.open(audioFile, ParcelFileDescriptor.MODE_READ_ONLY)
        }.getOrNull() ?: return null
        val length = descriptor.statSize.takeIf { it > 0L }
        if (length == null) {
            runCatching { descriptor.close() }
            return null
        }
        return NativeAudioSource(
            descriptor = descriptor,
            offset = 0L,
            length = length
        )
    }

    private data class NativeAudioSource(
        val descriptor: ParcelFileDescriptor,
        val offset: Long,
        val length: Long
    ) {
        companion object {
            fun from(assetDescriptor: AssetFileDescriptor): NativeAudioSource? {
                val original = assetDescriptor.parcelFileDescriptor
                val offset = assetDescriptor.startOffset.coerceAtLeast(0L)
                val length = assetDescriptor.length.takeIf { it > 0L }
                    ?: original.statSize.takeIf { it > offset }?.minus(offset)
                    ?: return null
                val duplicate = runCatching {
                    ParcelFileDescriptor.dup(original.fileDescriptor)
                }.getOrNull() ?: return null
                return NativeAudioSource(
                    descriptor = duplicate,
                    offset = offset,
                    length = length
                )
            }
        }
    }

    companion object {
        private const val MICROS_PER_MILLISECOND = 1_000L
    }
}
