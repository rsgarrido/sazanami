package io.github.rsgarrido.sazanami.player.waveform

import android.content.Context
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.nativeaudio.NativeAudioDecoder
import io.github.rsgarrido.sazanami.player.nativeaudio.NativeWaveformDecoder
import kotlinx.coroutines.CancellationException

/**
 * Native-first waveform analyzer with the existing Android implementation as a
 * compatibility fallback for formats/devices the NDK path cannot decode.
 */
class NativeWaveformAnalyzer internal constructor(
    private val nativeDecoder: NativeWaveformDecoder,
    private val fallbackAnalyzer: WaveformAnalyzer
) : WaveformAnalyzer {
    constructor(context: Context) : this(
        nativeDecoder = NativeAudioDecoder(context),
        fallbackAnalyzer = AndroidWaveformAnalyzer(context)
    )

    override suspend fun analyze(
        song: Song,
        sourceKey: String,
        barCount: Int
    ): WaveformData? {
        if (barCount <= 0) return null

        val nativeAmplitudes = try {
            nativeDecoder.decodeWaveform(song, barCount)
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (_: Exception) {
            null
        }

        val nativeData = nativeAmplitudes
            ?.takeIf { values -> values.size == barCount }
            ?.map { amplitude ->
                if (amplitude.isFinite()) amplitude.coerceIn(0f, 1f) else 0f
            }
            ?.let { amplitudes -> WaveformData(amplitudes, sourceKey) }
        if (nativeData != null) return nativeData

        return fallbackAnalyzer.analyze(song, sourceKey, barCount)
    }
}
