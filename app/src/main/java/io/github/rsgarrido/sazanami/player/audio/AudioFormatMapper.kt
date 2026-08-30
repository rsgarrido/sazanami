package io.github.rsgarrido.sazanami.player.audio

import androidx.annotation.OptIn
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi

@OptIn(UnstableApi::class)
internal fun mapAudioSourceFormat(format: Format): AudioSourceFormat? {
    val mimeType = sanitizeKnownText(format.sampleMimeType)
    val mapped = AudioSourceFormat(
        sampleMimeType = mimeType,
        codecs = sanitizeKnownText(format.codecs),
        sampleRateHz = sanitizeKnownPositive(format.sampleRate),
        channelCount = sanitizeKnownPositive(format.channelCount),
        bitrateBitsPerSecond = sanitizeKnownPositive(format.bitrate),
        pcmEncoding = format.pcmEncoding
            .takeIf { mimeType == MimeTypes.AUDIO_RAW && it != Format.NO_VALUE },
        encoderDelayFrames = sanitizeKnownPositive(format.encoderDelay),
        encoderPaddingFrames = sanitizeKnownPositive(format.encoderPadding)
    )
    return mapped.takeIf { it.hasKnownValue }
}
