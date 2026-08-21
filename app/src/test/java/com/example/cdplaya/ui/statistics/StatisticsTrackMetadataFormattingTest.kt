package com.example.cdplaya.ui.statistics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class StatisticsTrackMetadataFormattingTest {
    @Test fun missingAlbumHasNoDanglingSeparator() {
        val text = formatStatisticsTrackMetadata("The Gathering", "")
        assertEquals("The Gathering", text)
        assertFalse(text.endsWith("·"))
    }

    @Test fun artistAndAlbumKeepReadableSeparator() {
        assertEquals(
            "The Warning · Keep Me Fed",
            formatStatisticsTrackMetadata("The Warning", "Keep Me Fed")
        )
    }
}
