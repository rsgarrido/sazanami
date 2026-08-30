package com.example.cdplaya.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.example.cdplaya.R
import com.example.cdplaya.BuildConfig
import com.example.cdplaya.data.PlayerTheme
import com.example.cdplaya.data.Song
import com.example.cdplaya.player.replaygain.ReplayGainMode
import com.example.cdplaya.player.audio.AudioOutputUiState
import com.example.cdplaya.player.audio.formatAudioCompatibility
import com.example.cdplaya.player.audio.formatAudioOffloadStatus
import com.example.cdplaya.player.audio.formatAudioRoute
import com.example.cdplaya.player.audio.formatAudioSource
import com.example.cdplaya.player.audio.formatEqualizerProcessorFormat
import com.example.cdplaya.player.audio.formatEqualizerPlanApplication
import com.example.cdplaya.player.audio.formatEqualizerPlanLatency
import com.example.cdplaya.player.audio.formatEqualizerStatus
import com.example.cdplaya.player.equalizer.EqualizerRuntimeBridge
import com.example.cdplaya.player.equalizer.EqualizerProcessorMeasuredConfiguration
import com.example.cdplaya.player.waveform.WaveformCache
import com.example.cdplaya.player.waveform.WaveformCacheStats
import com.example.cdplaya.player.waveform.WaveformRepository
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

internal data class DiagnosticsSnapshot(
    val appVersionName: String,
    val appVersionCode: Long,
    val librarySongCount: Int,
    val selectedFolderCount: Int,
    val playerTheme: String,
    val replayGainMode: String,
    val isPlaybackConnected: Boolean,
    val currentSongTitle: String?,
    val currentSongArtist: String?,
    val isPlaying: Boolean,
    val currentPositionMs: Int,
    val durationMs: Int,
    val queueCount: Int,
    val upcomingCount: Int,
    val previousCount: Int,
    val forwardCount: Int,
    val waveformFileCount: Int,
    val waveformTotalBytes: Long,
    val unresolvedFavoriteCount: Int = 0,
    val unresolvedPlaylistRowCount: Int = 0,
    val unresolvedListeningHistoryCount: Int = 0,
    val audioOutputUiState: AudioOutputUiState = AudioOutputUiState()
)

internal fun formatDiagnosticsSummary(snapshot: DiagnosticsSnapshot): String = buildString {
    appendLine("Sazanami diagnostics")
    appendLine("App: ${snapshot.appVersionName} (${snapshot.appVersionCode})")
    appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
    appendLine("Device: ${Build.MANUFACTURER} ${Build.MODEL}")
    appendLine("Library songs: ${snapshot.librarySongCount}")
    appendLine("Selected folders: ${snapshot.selectedFolderCount}")
    appendLine("Unresolved favorites: ${snapshot.unresolvedFavoriteCount}")
    appendLine("Unresolved playlist rows: ${snapshot.unresolvedPlaylistRowCount}")
    appendLine("Unresolved history rows: ${snapshot.unresolvedListeningHistoryCount}")
    appendLine("Player theme: ${snapshot.playerTheme}")
    appendLine("ReplayGain: ${snapshot.replayGainMode}")
    appendLine("Playback connected: ${snapshot.isPlaybackConnected}")
    appendLine("Current media: ${if (snapshot.currentSongTitle == null) "None" else "Present"}")
    appendLine("Playback state: ${if (snapshot.isPlaying) "Playing" else "Paused"}")
    appendLine("Position: ${snapshot.currentPositionMs} / ${snapshot.durationMs} ms")
    appendLine("Queue / upcoming: ${snapshot.queueCount} / ${snapshot.upcomingCount}")
    appendLine("Previous / forward: ${snapshot.previousCount} / ${snapshot.forwardCount}")
    appendLine("Audio source: ${formatAudioSource(snapshot.audioOutputUiState.sourceFormat)}")
    appendLine("Audio route: ${formatAudioRoute(snapshot.audioOutputUiState.routeInfo)}")
    appendLine(
        "Audio route scope: " +
            if (snapshot.audioOutputUiState.routeInfo.isLocalPlayback) "Local" else "Remote"
    )
    appendLine(
        "Offload preference: " +
            snapshot.audioOutputUiState.offloadState.requestedPreference.displayName
    )
    appendLine(
        "Offload actual: ${formatAudioOffloadStatus(snapshot.audioOutputUiState.offloadState)}"
    )
    appendLine(
        "Sleeping for offload: " +
            snapshot.audioOutputUiState.offloadState.isSleepingForOffload
    )
    val equalizer = snapshot.audioOutputUiState.equalizerRuntimeState
    appendLine("Equalizer: ${formatEqualizerStatus(equalizer)}")
    appendLine(
        "Equalizer processor: ${formatEqualizerProcessorFormat(equalizer)}"
    )
    appendLine(
        "Equalizer requested/prepared/applied version: " +
            "${equalizer.configurationVersion} / " +
            (equalizer.preparedPlanVersion?.toString() ?: "None") +
            " / " +
            (equalizer.appliedPlanVersion?.toString() ?: "None")
    )
    appendLine(
        "Equalizer DSP adoption: " +
            formatEqualizerPlanApplication(equalizer)
    )
    appendLine(
        "Equalizer control-to-DSP timing: " +
            formatEqualizerPlanLatency(equalizer)
    )
    appendLine(
        "Equalizer valid/ignored filters: " +
            "${equalizer.validFilterCount} / ${equalizer.ignoredFilterCount}"
    )
    appendLine(
        "Automatic headroom: " +
            String.format(
                Locale.ROOT,
                "%.2f dB",
                equalizer.automaticHeadroomDb
            )
    )
    appendLine(
        "Equalizer audio path: " +
            if (equalizer.requiresDecodedPcm) {
                "Decoded PCM required"
            } else {
                "User offload preference allowed"
            }
    )
    val performance = equalizer.processorPerformance
    appendLine(
        "Equalizer processor timing enabled: " +
            equalizer.processorPerformanceTelemetryEnabled
    )
    appendLine(
        "Equalizer processor timing state: " +
            when {
                equalizer.processorPerformanceTelemetryEnabled ->
                    "Running"
                performance.totalCallCount > 0L ->
                    "Stopped (completed window retained)"
                else -> "Stopped (no retained window)"
            }
    )
    appendLine(
        "Equalizer processor calls/frames/deadline misses: " +
            "${performance.totalCallCount} / " +
            "${performance.totalFrameCount} / " +
            performance.deadlineMissCount
    )
    appendLine(
        "Equalizer frozen timing configuration first/last/changes: " +
            formatMeasuredConfiguration(
                performance.firstMeasuredConfiguration
            ) +
            " / " +
            formatMeasuredConfiguration(
                performance.lastMeasuredConfiguration
            ) +
            " / " +
            performance.measuredConfigurationChangeCount
    )
    appendLine(
        "Equalizer stale prepared plans discarded: " +
            equalizer.stalePreparedPlanDiscardCount
    )
    appendLine(
        "Equalizer processor median/p90/p95/p99/max: " +
            String.format(
                Locale.ROOT,
                "%.3f / %.3f / %.3f / %.3f / %.3f ms",
                performance.medianProcessingMillis,
                performance.p90ProcessingMillis,
                performance.p95ProcessingMillis,
                performance.p99ProcessingMillis,
                performance.maximumProcessingMillis
            )
    )
    appendLine(
        "Equalizer processor median/p95/p99/max real-time factor: " +
            String.format(
                Locale.ROOT,
                "%.4f / %.4f / %.4f / %.4f",
                performance.medianRealTimeFactor,
                performance.p95RealTimeFactor,
                performance.p99RealTimeFactor,
                performance.maximumRealTimeFactor
            )
    )
    appendLine(
        "Limiter requested/active/primed: " +
            "${equalizer.limiterRequestedEnabled} / " +
            "${equalizer.limiterEffectivelyActive} / " +
            equalizer.limiterPrimed
    )
    appendLine(
        "Limiter ceiling/lookahead/release: " +
            String.format(
                Locale.ROOT,
                "%.1f dBFS / %d frames (%.2f ms) / %.1f ms",
                equalizer.limiterCeilingDbfs,
                equalizer.limiterLookaheadFrames,
                equalizer.limiterLookaheadMilliseconds,
                equalizer.limiterReleaseMilliseconds
            )
    )
    appendLine(
        "Limiter pre/post peak: " +
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dBFS",
                equalizer.preLimiterPeakDbfs,
                equalizer.postLimiterPeakDbfs
            )
    )
    appendLine(
        "Limiter current/recent max gain reduction: " +
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dB",
                equalizer.currentGainReductionDb,
                equalizer.maximumRecentGainReductionDb
            )
    )
    appendLine(
        "Limiter over-range/saturated samples: " +
            "${equalizer.overRangeSampleCount} / " +
            equalizer.saturatedSampleCount
    )
    appendLine(
        "Limiter active/reduced frames: " +
            "${equalizer.limiterActiveFrameCount} / " +
            equalizer.limiterReducedFrameCount
    )
    appendLine(
        "Limiter reprimes: ${equalizer.limiterReprimeCount}"
    )
    appendLine("Audio compatibility: ${formatAudioCompatibility(snapshot.audioOutputUiState)}")
    snapshot.audioOutputUiState.audioSessionId?.let { appendLine("Audio session: $it") }
    appendLine(
        "Audio note: Source information describes the current file/renderer input; " +
            "Android or the connected device may mix, process, resample, or transmit it differently."
    )
    appendLine(
        "EQ timing note: DSP application timing excludes PCM already buffered by " +
            "Media3, AudioTrack, or the output route."
    )
    appendLine("Waveform cache: ${snapshot.waveformFileCount} files, ${snapshot.waveformTotalBytes} bytes")
    appendLine("Waveform format: ${WaveformCache.CACHE_FORMAT_VERSION}")
    append("Waveform buckets: ${WaveformRepository.DEFAULT_ANALYZED_BAR_COUNT}")
}

private fun formatMeasuredConfiguration(
    configuration: EqualizerProcessorMeasuredConfiguration?
): String {
    configuration ?: return "None"
    val mode = configuration.mode.name
        .lowercase()
        .replaceFirstChar(Char::uppercase)
    val sampleRate = String.format(
        Locale.ROOT,
        "%.1f kHz",
        configuration.sampleRateHz / 1_000.0
    )
    val channels = when (configuration.channelCount) {
        1 -> "mono"
        2 -> "stereo"
        else -> "${configuration.channelCount} channels"
    }
    val limiter = if (configuration.limiterActive) {
        "limiter active"
    } else {
        "limiter inactive"
    }
    return "v${configuration.version} $mode, " +
        "${configuration.validFilterCount} valid filters, " +
        "$sampleRate $channels, $limiter"
}

@Composable
internal fun DiagnosticsScreen(
    librarySongCount: Int,
    selectedFolderCount: Int,
    selectedPlayerTheme: PlayerTheme,
    selectedReplayGainMode: ReplayGainMode,
    audioOutputUiState: AudioOutputUiState,
    isPlaybackConnected: Boolean,
    currentSong: Song?,
    isPlaying: Boolean,
    currentPosition: Int,
    duration: Int,
    queueCount: Int,
    upcomingCount: Int,
    previousCount: Int,
    forwardCount: Int,
    unresolvedFavoriteCount: Int = 0,
    unresolvedPlaylistRowCount: Int = 0,
    unresolvedListeningHistoryCount: Int = 0,
    onBackClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val appContext = context.applicationContext
    val repository = remember(appContext) { WaveformRepository.shared(appContext) }
    val scope = rememberCoroutineScope()
    var cacheStats by remember { mutableStateOf(WaveformCacheStats(0, 0L)) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var isCacheOperationRunning by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val version = remember(context) { context.appVersion() }
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val cacheClearedMessage = stringResource(R.string.diagnostics_cache_cleared)

    val snapshot = DiagnosticsSnapshot(
        appVersionName = version.first,
        appVersionCode = version.second,
        librarySongCount = librarySongCount,
        selectedFolderCount = selectedFolderCount,
        playerTheme = selectedPlayerTheme.displayName,
        replayGainMode = selectedReplayGainMode.displayName,
        isPlaybackConnected = isPlaybackConnected,
        currentSongTitle = currentSong?.title,
        currentSongArtist = currentSong?.artist,
        isPlaying = isPlaying,
        currentPositionMs = currentPosition,
        durationMs = duration,
        queueCount = queueCount,
        upcomingCount = upcomingCount,
        previousCount = previousCount,
        forwardCount = forwardCount,
        waveformFileCount = cacheStats.fileCount,
        waveformTotalBytes = cacheStats.totalBytes,
        unresolvedFavoriteCount = unresolvedFavoriteCount,
        unresolvedPlaylistRowCount = unresolvedPlaylistRowCount,
        unresolvedListeningHistoryCount = unresolvedListeningHistoryCount,
        audioOutputUiState = audioOutputUiState
    )

    LaunchedEffect(repository, refreshRequest) {
        cacheStats = repository.getCacheStats()
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        Row(modifier = Modifier.fillMaxWidth()) {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.diagnostics_back))
            }
            Text(
                text = stringResource(R.string.diagnostics_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(top = 12.dp)
            )
        }

        DiagnosticValue(stringResource(R.string.diagnostics_app_version), "${version.first} (${version.second})")
        DiagnosticValue(
            stringResource(R.string.diagnostics_library),
            pluralStringResource(R.plurals.diagnostics_song_count, librarySongCount, librarySongCount)
        )
        DiagnosticValue(stringResource(R.string.diagnostics_selected_folders), selectedFolderCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_favorites), unresolvedFavoriteCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_playlist_rows), unresolvedPlaylistRowCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_history_rows), unresolvedListeningHistoryCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_player_theme), selectedPlayerTheme.displayName)
        DiagnosticValue(stringResource(R.string.diagnostics_replay_gain), selectedReplayGainMode.displayName)
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = "Audio output",
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        DiagnosticValue("Source format", formatAudioSource(audioOutputUiState.sourceFormat))
        DiagnosticValue("Route", formatAudioRoute(audioOutputUiState.routeInfo))
        DiagnosticValue(
            "Route scope",
            if (audioOutputUiState.routeInfo.isLocalPlayback) "Local" else "Remote"
        )
        DiagnosticValue(
            "Audio offload preference",
            audioOutputUiState.offloadState.requestedPreference.displayName
        )
        DiagnosticValue(
            "Audio offload",
            formatAudioOffloadStatus(audioOutputUiState.offloadState)
        )
        DiagnosticValue("Compatibility", formatAudioCompatibility(audioOutputUiState))
        val equalizer = audioOutputUiState.equalizerRuntimeState
        DiagnosticValue("Equalizer", formatEqualizerStatus(equalizer))
        DiagnosticValue(
            "Equalizer processor format",
            formatEqualizerProcessorFormat(equalizer)
        )
        DiagnosticValue(
            "Equalizer requested/prepared/applied version",
            "${equalizer.configurationVersion} / " +
                (equalizer.preparedPlanVersion?.toString() ?: "None") +
                " / " +
                (equalizer.appliedPlanVersion?.toString() ?: "None")
        )
        DiagnosticValue(
            "Equalizer DSP adoption",
            formatEqualizerPlanApplication(equalizer)
        )
        DiagnosticValue(
            "Equalizer control → DSP timing",
            formatEqualizerPlanLatency(equalizer)
        )
        DiagnosticValue(
            "Equalizer valid/ignored filters",
            "${equalizer.validFilterCount} / ${equalizer.ignoredFilterCount}"
        )
        DiagnosticValue(
            "Automatic headroom",
            String.format(
                Locale.ROOT,
                "%.2f dB",
                equalizer.automaticHeadroomDb
            )
        )
        DiagnosticValue(
            "Audio path",
            if (equalizer.requiresDecodedPcm) {
                "Decoded PCM required by equalizer"
            } else {
                "User offload preference allowed"
            }
        )
        DiagnosticValue(
            "Equalizer scratch growth",
            equalizer.scratchBufferGrowthCount.toString()
        )
        DiagnosticValue(
            "Stale prepared plans discarded",
            equalizer.stalePreparedPlanDiscardCount.toString()
        )
        if (BuildConfig.DEBUG) {
            val processorPerformance =
                equalizer.processorPerformance
            val hasProcessorPerformance =
                equalizer
                    .processorPerformanceTelemetryEnabled ||
                    processorPerformance.totalCallCount > 0L
            DiagnosticValue(
                "Processor timing",
                when {
                    equalizer
                        .processorPerformanceTelemetryEnabled ->
                        "Running (explicit diagnostics opt-in)"
                    processorPerformance.totalCallCount > 0L ->
                        "Stopped (completed window retained)"
                    else -> "Stopped (no retained window)"
                }
            )
            if (hasProcessorPerformance) {
                DiagnosticValue(
                    "Processor window / calls / frames",
                    "${processorPerformance.windowSampleCount} / " +
                        "${processorPerformance.totalCallCount} / " +
                        processorPerformance.totalFrameCount
                )
                DiagnosticValue(
                    "Processor median / p90 / p95 / p99 / max",
                    String.format(
                        Locale.ROOT,
                        "%.3f / %.3f / %.3f / %.3f / %.3f ms",
                        processorPerformance.medianProcessingMillis,
                        processorPerformance.p90ProcessingMillis,
                        processorPerformance.p95ProcessingMillis,
                        processorPerformance.p99ProcessingMillis,
                        processorPerformance.maximumProcessingMillis
                    )
                )
                DiagnosticValue(
                    "Processor median / p95 / p99 / max RTF",
                    String.format(
                        Locale.ROOT,
                        "%.4f / %.4f / %.4f / %.4f",
                        processorPerformance.medianRealTimeFactor,
                        processorPerformance.p95RealTimeFactor,
                        processorPerformance.p99RealTimeFactor,
                        processorPerformance.maximumRealTimeFactor
                    )
                )
                DiagnosticValue(
                    "Processor deadline misses",
                    processorPerformance.deadlineMissCount.toString()
                )
                DiagnosticValue(
                    "Processor bypass / EQ / transition / limiter calls",
                    "${processorPerformance.exactBypassCallCount} / " +
                        "${processorPerformance.equalizedCallCount} / " +
                        "${processorPerformance.transitionCallCount} / " +
                        processorPerformance.limiterCallCount
                )
                DiagnosticValue(
                    "Processor configure / flush preparation",
                    String.format(
                        Locale.ROOT,
                        "%.3f / %.3f ms",
                        processorPerformance.configurePreparationMillis,
                        processorPerformance.flushPreparationMillis
                    )
                )
                DiagnosticValue(
                    "Processor configure / synchronous format preparations",
                    "${processorPerformance.configurePreparationCount} / " +
                        processorPerformance
                            .synchronousFormatPreparationCount
                )
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        horizontal = 16.dp,
                        vertical = 4.dp
                    ),
                horizontalArrangement =
                    Arrangement.spacedBy(8.dp)
            ) {
                OutlinedButton(
                    onClick = {
                        EqualizerRuntimeBridge
                            .setProcessorPerformanceTelemetryEnabled(
                                !equalizer
                                    .processorPerformanceTelemetryEnabled
                            )
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        if (
                            equalizer
                                .processorPerformanceTelemetryEnabled
                        ) {
                            "Stop timing"
                        } else {
                            "Start timing"
                        }
                    )
                }
                OutlinedButton(
                    onClick = {
                        EqualizerRuntimeBridge
                            .requestProcessorPerformanceTelemetryReset()
                    },
                    enabled =
                        equalizer
                            .processorPerformanceTelemetryEnabled ||
                            processorPerformance.totalCallCount > 0L,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Reset timing")
                }
            }
        } else {
            DiagnosticValue(
                "Processor timing",
                "Unavailable in release builds"
            )
        }
        DiagnosticValue(
            "Limiter requested / active / primed",
            "${equalizer.limiterRequestedEnabled} / " +
                "${equalizer.limiterEffectivelyActive} / " +
                equalizer.limiterPrimed
        )
        DiagnosticValue(
            "Limiter ceiling",
            String.format(
                Locale.ROOT,
                "%.1f dBFS",
                equalizer.limiterCeilingDbfs
            )
        )
        DiagnosticValue(
            "Limiter lookahead / release",
            String.format(
                Locale.ROOT,
                "%d frames (%.2f ms) / %.1f ms",
                equalizer.limiterLookaheadFrames,
                equalizer.limiterLookaheadMilliseconds,
                equalizer.limiterReleaseMilliseconds
            )
        )
        DiagnosticValue(
            "Limiter pre / post peak",
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dBFS",
                equalizer.preLimiterPeakDbfs,
                equalizer.postLimiterPeakDbfs
            )
        )
        DiagnosticValue(
            "Limiter current / recent max reduction",
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dB",
                equalizer.currentGainReductionDb,
                equalizer.maximumRecentGainReductionDb
            )
        )
        DiagnosticValue(
            "Limiter over-range / saturated samples",
            "${equalizer.overRangeSampleCount} / " +
                equalizer.saturatedSampleCount
        )
        DiagnosticValue(
            "Limiter active / reduced frames",
            "${equalizer.limiterActiveFrameCount} / " +
                equalizer.limiterReducedFrameCount
        )
        DiagnosticValue(
            "Limiter reprimes",
            equalizer.limiterReprimeCount.toString()
        )
        Text(
            text = "DSP timing ends when the processor writes the transition. " +
                "PCM already buffered by Media3, AudioTrack, or the active route " +
                "can delay when that 20 ms crossfade is heard. Switching offload " +
                "eligibility can additionally cause a Media3 flush.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )
        audioOutputUiState.replayGainDb?.let { gain ->
            DiagnosticValue("ReplayGain value", String.format(Locale.ROOT, "%.2f dB", gain))
        }
        audioOutputUiState.appliedVolumeMultiplier?.let { multiplier ->
            DiagnosticValue(
                "Applied player volume",
                String.format(Locale.ROOT, "%.3fx", multiplier)
            )
        }
        audioOutputUiState.audioSessionId?.let { sessionId ->
            DiagnosticValue("Audio session", sessionId.toString())
        }
        Text(
            text = "Source information describes the current audio file/renderer input. " +
                "Android and the connected device may still mix, process, resample, or " +
                "transmit audio through a different format.",
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        DiagnosticValue(
            stringResource(R.string.diagnostics_connection),
            stringResource(if (isPlaybackConnected) R.string.diagnostics_connected else R.string.diagnostics_disconnected)
        )
        DiagnosticValue(
            stringResource(R.string.diagnostics_current_song),
            currentSong?.let { "${it.title} — ${it.artist}" } ?: stringResource(R.string.diagnostics_none)
        )
        DiagnosticValue(
            stringResource(R.string.diagnostics_playback_state),
            stringResource(if (isPlaying) R.string.diagnostics_playing else R.string.diagnostics_paused)
        )
        DiagnosticValue(stringResource(R.string.diagnostics_position), "${formatDuration(currentPosition)} / ${formatDuration(duration)}")
        DiagnosticValue(stringResource(R.string.diagnostics_queue), queueCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_upcoming), upcomingCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_history), "$previousCount / $forwardCount")

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.diagnostics_waveform_cache),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        DiagnosticValue(
            stringResource(R.string.diagnostics_cache_files),
            pluralStringResource(R.plurals.diagnostics_file_count, cacheStats.fileCount, cacheStats.fileCount)
        )
        DiagnosticValue(stringResource(R.string.diagnostics_cache_size), formatBytes(cacheStats.totalBytes))
        DiagnosticValue(stringResource(R.string.diagnostics_cache_format), WaveformCache.CACHE_FORMAT_VERSION.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_analysis_buckets), WaveformRepository.DEFAULT_ANALYZED_BAR_COUNT.toString())

        statusMessage?.let { message ->
            Text(
                text = message,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = { refreshRequest++ },
                enabled = !isCacheOperationRunning,
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.diagnostics_refresh)) }
            OutlinedButton(
                onClick = {
                    context.copyToClipboard(formatDiagnosticsSummary(snapshot))
                    statusMessage = copiedMessage
                },
                modifier = Modifier.weight(1f)
            ) { Text(stringResource(R.string.diagnostics_copy)) }
        }
        Button(
            onClick = { showClearConfirmation = true },
            enabled = !isCacheOperationRunning,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
        ) { Text(stringResource(R.string.diagnostics_clear_cache)) }
    }

    if (showClearConfirmation) {
        AlertDialog(
            onDismissRequest = { if (!isCacheOperationRunning) showClearConfirmation = false },
            title = { Text(stringResource(R.string.diagnostics_clear_cache_title)) },
            text = { Text(stringResource(R.string.diagnostics_clear_cache_message)) },
            confirmButton = {
                TextButton(
                    enabled = !isCacheOperationRunning,
                    onClick = {
                        showClearConfirmation = false
                        isCacheOperationRunning = true
                        scope.launch {
                            try {
                                cacheStats = repository.clearDiskCache()
                                statusMessage = cacheClearedMessage
                            } catch (cancellation: CancellationException) {
                                throw cancellation
                            } finally {
                                isCacheOperationRunning = false
                            }
                        }
                    }
                ) { Text(stringResource(R.string.diagnostics_clear)) }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmation = false }) {
                    Text(stringResource(R.string.diagnostics_cancel))
                }
            }
        )
    }
}

@Composable
private fun DiagnosticValue(label: String, value: String) {
    ListItem(
        headlineContent = { Text(label) },
        supportingContent = { Text(value) }
    )
}

private fun Context.appVersion(): Pair<String, Long> {
    return runCatching {
        val info = packageManager.getPackageInfo(packageName, 0)
        val code = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            info.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            info.versionCode.toLong()
        }
        info.versionName.orEmpty().ifBlank { getString(R.string.diagnostics_unknown) } to code
    }.getOrDefault(getString(R.string.diagnostics_unknown) to 0L)
}

private fun Context.copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
    clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.diagnostics_clip_label), text))
}

private fun formatDuration(milliseconds: Int): String {
    val totalSeconds = milliseconds.coerceAtLeast(0) / 1_000
    return String.format(Locale.ROOT, "%d:%02d", totalSeconds / 60, totalSeconds % 60)
}

private fun formatBytes(bytes: Long): String {
    val safeBytes = bytes.coerceAtLeast(0L)
    return if (safeBytes < 1024L) "$safeBytes B" else String.format(
        Locale.ROOT,
        "%.1f MiB",
        safeBytes / (1024.0 * 1024.0)
    )
}
