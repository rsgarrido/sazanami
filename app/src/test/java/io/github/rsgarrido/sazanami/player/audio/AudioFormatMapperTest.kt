package io.github.rsgarrido.sazanami.player.audio

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioFormatMapperTest {
    @Test
    fun rendererInputFormatMapsOnlyKnownValues() {
        val mapped = mapAudioSourceFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_FLAC)
                .setCodecs("flac")
                .setSampleRate(96_000)
                .setChannelCount(2)
                .setAverageBitrate(2_304_000)
                .setEncoderDelay(12)
                .setEncoderPadding(24)
                .build()
        )

        requireNotNull(mapped)
        assertEquals(MimeTypes.AUDIO_FLAC, mapped.sampleMimeType)
        assertEquals(96_000, mapped.sampleRateHz)
        assertEquals(2, mapped.channelCount)
        assertEquals(2_304_000, mapped.bitrateBitsPerSecond)
        assertNull(mapped.pcmEncoding)
    }

    @Test
    fun unknownRendererValuesAreOmitted() {
        assertNull(mapAudioSourceFormat(Format.Builder().build()))
    }

    @Test
    fun pcmEncodingIsReportedOnlyForRawPcm() {
        val raw = mapAudioSourceFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_RAW)
                .setPcmEncoding(C.ENCODING_PCM_24BIT)
                .build()
        )
        val compressed = mapAudioSourceFormat(
            Format.Builder()
                .setSampleMimeType(MimeTypes.AUDIO_AAC)
                .setPcmEncoding(C.ENCODING_PCM_24BIT)
                .build()
        )

        assertEquals(C.ENCODING_PCM_24BIT, raw?.pcmEncoding)
        assertNull(compressed?.pcmEncoding)
    }
}
