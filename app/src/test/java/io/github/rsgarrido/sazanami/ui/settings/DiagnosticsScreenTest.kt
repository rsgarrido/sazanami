package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerMode
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorMeasuredConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorPerformanceSnapshot
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeState
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DiagnosticsScreenTest {
    @Test
    fun copiedSummaryContainsUsefulStateWithoutFilePaths() {
        val summary = formatDiagnosticsSummary(
            DiagnosticsSnapshot(
                appVersionName = "1.0",
                appVersionCode = 1,
                librarySongCount = 42,
                selectedFolderCount = 2,
                playerTheme = "Retro Rack",
                replayGainMode = "Track",
                isPlaybackConnected = true,
                currentSongTitle = "Example",
                currentSongArtist = "Artist",
                isPlaying = true,
                currentPositionMs = 12_000,
                durationMs = 180_000,
                queueCount = 3,
                upcomingCount = 9,
                previousCount = 2,
                forwardCount = 1,
                waveformFileCount = 5,
                waveformTotalBytes = 4096,
                unresolvedFavoriteCount = 1,
                unresolvedPlaylistRowCount = 2,
                unresolvedListeningHistoryCount = 3
            )
        )

        assertTrue(summary.contains("Library songs: 42"))
        assertTrue(summary.contains("Current media: Present"))
        assertFalse(summary.contains("Example"))
        assertFalse(summary.contains("Artist"))
        assertTrue(summary.contains("Audio source: Unknown"))
        assertTrue(summary.contains("Offload preference: Disabled"))
        assertTrue(summary.contains("Equalizer: Bypassed"))
        assertTrue(summary.contains("Equalizer processor: Unconfigured"))
        assertTrue(
            summary.contains(
                "Equalizer requested/prepared/applied version"
            )
        )
        assertTrue(
            summary.contains(
                "DSP application timing excludes PCM already buffered"
            )
        )
        assertTrue(
            summary.contains("User offload preference allowed")
        )
        assertTrue(summary.contains("Source information describes"))
        assertTrue(summary.contains("Waveform cache: 5 files, 4096 bytes"))
        assertTrue(summary.contains("Unresolved favorites: 1"))
        assertTrue(summary.contains("Unresolved playlist rows: 2"))
        assertTrue(summary.contains("Unresolved history rows: 3"))
        assertFalse(summary.contains("/music/"))
        assertFalse(summary.contains("filePath"))
        assertFalse(summary.contains("bit-perfect", ignoreCase = true))
        assertFalse(summary.contains("hardware output", ignoreCase = true))
        assertTrue(
            summary.contains(
                "Limiter requested/active/primed: false / false / false"
            )
        )
        assertFalse(summary.contains("true-peak", ignoreCase = true))
        assertFalse(summary.contains("Bluetooth address", ignoreCase = true))
    }

    @Test
    fun copiedSummaryRetainsAStoppedProcessorTimingWindow() {
        val summary = formatDiagnosticsSummary(
            DiagnosticsSnapshot(
                appVersionName = "1.0",
                appVersionCode = 1,
                librarySongCount = 0,
                selectedFolderCount = 0,
                playerTheme = "System",
                replayGainMode = "Off",
                isPlaybackConnected = true,
                currentSongTitle = null,
                currentSongArtist = null,
                isPlaying = false,
                currentPositionMs = 0,
                durationMs = 0,
                queueCount = 0,
                upcomingCount = 0,
                previousCount = 0,
                forwardCount = 0,
                waveformFileCount = 0,
                waveformTotalBytes = 0L,
                audioOutputUiState = AudioOutputUiState(
                    equalizerRuntimeState =
                        EqualizerRuntimeState(
                            processorPerformanceTelemetryEnabled =
                                false,
                            processorPerformance =
                                EqualizerProcessorPerformanceSnapshot(
                                    windowSampleCount = 12,
                                    totalCallCount = 12L,
                                    totalFrameCount = 3_456L,
                                    deadlineMissCount = 2L,
                                    medianProcessingMillis = 0.125,
                                    p90ProcessingMillis = 0.2,
                                    p95ProcessingMillis = 0.25,
                                    p99ProcessingMillis = 0.5,
                                    maximumProcessingMillis = 1.0,
                                    medianRealTimeFactor = 0.01,
                                    p95RealTimeFactor = 0.02,
                                    p99RealTimeFactor = 0.03,
                                    maximumRealTimeFactor = 0.04,
                                    firstMeasuredConfiguration =
                                        EqualizerProcessorMeasuredConfiguration(
                                            version = 22L,
                                            mode = EqualizerMode.PARAMETRIC,
                                            validFilterCount = 10,
                                            sampleRateHz = 44_100,
                                            channelCount = 2,
                                            limiterActive = false
                                        ),
                                    lastMeasuredConfiguration =
                                        EqualizerProcessorMeasuredConfiguration(
                                            version = 23L,
                                            mode = EqualizerMode.PARAMETRIC,
                                            validFilterCount = 10,
                                            sampleRateHz = 44_100,
                                            channelCount = 2,
                                            limiterActive = true
                                        ),
                                    measuredConfigurationChangeCount = 1L
                                )
                        )
                )
            )
        )

        assertTrue(
            summary.contains(
                "Equalizer processor timing enabled: false"
            )
        )
        assertTrue(
            summary.contains(
                "Stopped (completed window retained)"
            )
        )
        assertTrue(
            summary.contains(
                "calls/frames/deadline misses: 12 / 3456 / 2"
            )
        )
        assertTrue(
            summary.contains(
                "0.125 / 0.200 / 0.250 / 0.500 / 1.000 ms"
            )
        )
        assertTrue(summary.contains("0.0100 / 0.0200 / 0.0300 / 0.0400"))
        assertTrue(
            summary.contains(
                "frozen timing configuration first/last/changes: " +
                    "v22 Parametric, 10 valid filters, 44.1 kHz stereo, " +
                    "limiter inactive / v23 Parametric"
            )
        )
    }
}
