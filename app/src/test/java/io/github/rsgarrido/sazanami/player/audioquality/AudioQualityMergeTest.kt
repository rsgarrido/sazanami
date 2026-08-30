package io.github.rsgarrido.sazanami.player.audioquality

import org.junit.Assert.assertEquals
import org.junit.Test

class AudioQualityMergeTest {
    @Test
    fun api29FallbackPreservesAndCombinesTruthfulPartialValues() {
        val tagReader = AudioQualityInfo(
            format = "FLAC",
            bitDepth = 16,
            sampleRateHz = null,
            bitrateKbps = null
        )
        val frameworkExtractor = AudioQualityInfo(
            format = "audio/flac",
            bitDepth = null,
            sampleRateHz = 44_100,
            bitrateKbps = null
        )
        val metadataRetriever = AudioQualityInfo(
            format = null,
            bitDepth = null,
            sampleRateHz = null,
            bitrateKbps = 1_024
        )

        assertEquals(
            AudioQualityInfo("FLAC", 16, 44_100, 1_024),
            mergeAudioQualityInfo(tagReader, frameworkExtractor, metadataRetriever)
        )
    }

    @Test
    fun missingFieldsRemainOmittedInsteadOfBeingInvented() {
        assertEquals(
            AudioQualityInfo("FLAC", null, 44_100, null),
            mergeAudioQualityInfo(
                AudioQualityInfo("FLAC", null, 44_100, null)
            )
        )
    }
}
