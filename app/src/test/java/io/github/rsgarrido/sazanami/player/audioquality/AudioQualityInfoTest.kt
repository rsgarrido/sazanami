package io.github.rsgarrido.sazanami.player.audioquality

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AudioQualityInfoTest {

    @Test
    fun toDisplayText_formatsAllKnownValues() {
        val audioQualityInfo = AudioQualityInfo(
            format = "flac",
            bitDepth = 24,
            sampleRateHz = 96_000,
            bitrateKbps = 3_315
        )

        assertEquals(
            "24 bit  96 kHz  3315 kbps  FLAC",
            audioQualityInfo.toDisplayText()
        )
    }

    @Test
    fun toDisplayText_returnsNullWhenMetadataIsEmptyOrInvalid() {
        val audioQualityInfo = AudioQualityInfo(
            format = "unknown",
            bitDepth = 0,
            sampleRateHz = 0,
            bitrateKbps = 0
        )

        assertNull(audioQualityInfo.toDisplayText())
    }

    @Test
    fun toDisplayText_omitsMissingValues() {
        val audioQualityInfo = AudioQualityInfo(
            format = "AAC",
            bitDepth = null,
            sampleRateHz = null,
            bitrateKbps = 256
        )

        assertEquals("256 kbps  AAC", audioQualityInfo.toDisplayText())
    }

    @Test
    fun toDisplayText_formatsCommonSampleRates() {
        assertEquals("44.1 kHz", infoWithSampleRate(44_100).toDisplayText())
        assertEquals("48 kHz", infoWithSampleRate(48_000).toDisplayText())
        assertEquals("96 kHz", infoWithSampleRate(96_000).toDisplayText())
        assertEquals("22.05 kHz", infoWithSampleRate(22_050).toDisplayText())
    }

    @Test
    fun toDisplayText_formatsBitrate() {
        val audioQualityInfo = AudioQualityInfo(
            format = null,
            bitDepth = null,
            sampleRateHz = null,
            bitrateKbps = 320
        )

        assertEquals("320 kbps", audioQualityInfo.toDisplayText())
    }

    @Test
    fun toDisplayText_formatsBitDepth() {
        val audioQualityInfo = AudioQualityInfo(
            format = null,
            bitDepth = 16,
            sampleRateHz = null,
            bitrateKbps = null
        )

        assertEquals("16 bit", audioQualityInfo.toDisplayText())
    }

    @Test
    fun normalizeAudioFormat_returnsCleanSupportedLabels() {
        assertEquals("FLAC", normalizeAudioFormat(" flac "))
        assertEquals("MP3", normalizeAudioFormat("MPEG-1 Layer 3"))
        assertEquals("AAC", normalizeAudioFormat("Advanced Audio Coding"))
        assertEquals("M4A", normalizeAudioFormat(".m4a"))
        assertEquals("OGG", normalizeAudioFormat("Ogg Vorbis"))
        assertEquals("WAV", normalizeAudioFormat("WAVE"))
        assertNull(normalizeAudioFormat("unknown"))
        assertNull(normalizeAudioFormat(""))
    }

    private fun infoWithSampleRate(sampleRateHz: Int): AudioQualityInfo {
        return AudioQualityInfo(
            format = null,
            bitDepth = null,
            sampleRateHz = sampleRateHz,
            bitrateKbps = null
        )
    }
}
