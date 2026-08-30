package com.example.cdplaya.player.equalizer

import android.content.Context
import androidx.annotation.OptIn
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Builds Sazanami's audio sink with one persistent equalizer processor.
 *
 * Float output is deliberately disabled because Media3 bypasses ordinary audio
 * processors on its float-output path.
 */
@OptIn(UnstableApi::class)
internal class EqualizerRenderersFactory(
    context: Context,
    private val equalizerAudioProcessor: EqualizerAudioProcessor
) : DefaultRenderersFactory(context) {
    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioOutputPlaybackParams: Boolean
    ): AudioSink {
        return DefaultAudioSink.Builder(context)
            .setAudioProcessors(
                arrayOf<AudioProcessor>(equalizerAudioProcessor)
            )
            .setEnableFloatOutput(false)
            .setEnableAudioOutputPlaybackParameters(
                enableAudioOutputPlaybackParams
            )
            .build()
    }

    internal fun processorInstanceForTest(): EqualizerAudioProcessor {
        return equalizerAudioProcessor
    }

    internal fun buildAudioSinkForTest(context: Context): AudioSink {
        return buildAudioSink(
            context = context,
            enableFloatOutput = true,
            enableAudioOutputPlaybackParams = false
        )
    }
}
