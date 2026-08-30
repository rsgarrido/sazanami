package io.github.rsgarrido.sazanami.player.replaygain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ReplayGainHelpersTest {

    @Test
    fun parseReplayGainDb_readsNegativeDbValue() {
        val result = parseReplayGainDb("-7.23 dB")

        assertEquals(
            -7.23f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun parseReplayGainDb_readsPositiveDbValue() {
        val result = parseReplayGainDb("+1.50 dB")

        assertEquals(
            1.50f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun parseReplayGainDb_returnsNullForBlankValue() {
        val result = parseReplayGainDb("")

        assertNull(result)
    }

    @Test
    fun parseReplayGainDb_returnsNullForInvalidValue() {
        val result = parseReplayGainDb("not a gain value")

        assertNull(result)
    }

    @Test
    fun parseReplayGainPeak_readsPeakValue() {
        val result = parseReplayGainPeak("0.987654")

        assertEquals(
            0.987654f,
            result ?: 0f,
            0.000001f
        )
    }

    @Test
    fun replayGainDbToVolumeMultiplier_returnsOneForZeroDb() {
        val result = replayGainDbToVolumeMultiplier(0f)

        assertEquals(
            1f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainDbToVolumeMultiplier_lowersVolumeForNegativeDb() {
        val result = replayGainDbToVolumeMultiplier(-6.0206f)

        assertEquals(
            0.5f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainDbToVolumeMultiplier_clampsPositiveDbToOne() {
        val result = replayGainDbToVolumeMultiplier(3f)

        assertEquals(
            1f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainTrackMultiplier_returnsOneWhenReplayGainIsOff() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -8f,
            trackPeak = null,
            albumGainDb = null,
            albumPeak = null
        )

        val result = replayGainTrackMultiplier(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.OFF
        )

        assertEquals(
            1f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainTrackMultiplier_appliesTrackGainInTrackMode() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6.0206f,
            trackPeak = null,
            albumGainDb = null,
            albumPeak = null
        )

        val result = replayGainTrackMultiplier(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.TRACK
        )

        assertEquals(
            0.5f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainTrackMultiplier_returnsOneWhenTrackGainIsMissing() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = null,
            trackPeak = null,
            albumGainDb = -7f,
            albumPeak = null
        )

        val result = replayGainTrackMultiplier(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.TRACK
        )

        assertEquals(
            1f,
            result,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_returnsTrackGainForTrackMode() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = -3f,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.TRACK,
            isAlbumPlaybackContext = true
        )

        assertEquals(
            -6f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_returnsAlbumGainForAlbumMode() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = -3f,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.ALBUM,
            isAlbumPlaybackContext = false
        )

        assertEquals(
            -3f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_fallsBackToTrackGainForAlbumModeWhenAlbumGainIsMissing() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = null,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.ALBUM,
            isAlbumPlaybackContext = false
        )

        assertEquals(
            -6f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_returnsAlbumGainForSmartModeInAlbumContext() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = -3f,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = true
        )

        assertEquals(
            -3f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_returnsTrackGainForSmartModeInMixedContext() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = -3f,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = false
        )

        assertEquals(
            -6f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_fallsBackToTrackGainForSmartModeInAlbumContextWhenAlbumGainIsMissing() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6f,
            trackPeak = null,
            albumGainDb = null,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = true
        )

        assertEquals(
            -6f,
            result ?: 0f,
            0.001f
        )
    }

    @Test
    fun selectReplayGainDb_returnsNullForSmartModeInMixedContextWhenTrackGainIsMissing() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = null,
            trackPeak = null,
            albumGainDb = -3f,
            albumPeak = null
        )

        val result = selectReplayGainDb(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = false
        )

        assertNull(result)
    }

    @Test
    fun replayGainVolumeMultiplier_usesAlbumGainForSmartModeInAlbumContext() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6.0206f,
            trackPeak = null,
            albumGainDb = -12.0412f,
            albumPeak = null
        )

        val result = replayGainVolumeMultiplier(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = true
        )

        assertEquals(
            0.25f,
            result,
            0.001f
        )
    }

    @Test
    fun replayGainVolumeMultiplier_usesTrackGainForSmartModeInMixedContext() {
        val replayGainInfo = ReplayGainInfo(
            trackGainDb = -6.0206f,
            trackPeak = null,
            albumGainDb = -12.0412f,
            albumPeak = null
        )

        val result = replayGainVolumeMultiplier(
            replayGainInfo = replayGainInfo,
            replayGainMode = ReplayGainMode.SMART,
            isAlbumPlaybackContext = false
        )

        assertEquals(
            0.5f,
            result,
            0.001f
        )
    }
}