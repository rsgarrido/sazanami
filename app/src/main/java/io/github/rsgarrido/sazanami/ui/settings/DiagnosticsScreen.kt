package io.github.rsgarrido.sazanami.ui.settings

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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.player.replaygain.ReplayGainMode
import io.github.rsgarrido.sazanami.player.audio.AudioOutputUiState
import io.github.rsgarrido.sazanami.player.audio.formatAudioCompatibility
import io.github.rsgarrido.sazanami.player.audio.formatAudioOffloadStatus
import io.github.rsgarrido.sazanami.player.audio.formatAudioRoute
import io.github.rsgarrido.sazanami.player.audio.formatAudioSource
import io.github.rsgarrido.sazanami.player.audio.formatEqualizerProcessorFormat
import io.github.rsgarrido.sazanami.player.audio.formatEqualizerPlanApplication
import io.github.rsgarrido.sazanami.player.audio.formatEqualizerPlanLatency
import io.github.rsgarrido.sazanami.player.audio.formatEqualizerStatus
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerProcessorMeasuredConfiguration
import io.github.rsgarrido.sazanami.player.waveform.WaveformCache
import io.github.rsgarrido.sazanami.player.waveform.WaveformCacheStats
import io.github.rsgarrido.sazanami.player.waveform.WaveformRepository
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
    val configuration = LocalConfiguration.current
    val reportLocale = configuration.locales[0]
    val appContext = context.applicationContext
    val repository = remember(appContext) { WaveformRepository.shared(appContext) }
    val scope = rememberCoroutineScope()
    var cacheStats by remember { mutableStateOf(WaveformCacheStats(0, 0L)) }
    var refreshRequest by remember { mutableIntStateOf(0) }
    var isCacheOperationRunning by remember { mutableStateOf(false) }
    var showClearConfirmation by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    val version = remember(context) { context.installedAppVersion() }
    val copiedMessage = stringResource(R.string.diagnostics_copied)
    val cacheClearedMessage = stringResource(R.string.diagnostics_cache_cleared)

    val snapshot = DiagnosticsSnapshot(
        appVersionName = version.first,
        appVersionCode = version.second,
        librarySongCount = librarySongCount,
        selectedFolderCount = selectedFolderCount,
        playerTheme = selectedPlayerTheme.displayName,
        replayGainMode = selectedReplayGainMode.diagnosticDisplayName,
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
        IntegratedDirectPlaybackSection()
        UsbAudioDiagnosticsSection()
        DiagnosticValue(
            stringResource(R.string.diagnostics_library),
            pluralStringResource(R.plurals.diagnostics_song_count, librarySongCount, librarySongCount)
        )
        DiagnosticValue(stringResource(R.string.diagnostics_selected_folders), selectedFolderCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_favorites), unresolvedFavoriteCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_playlist_rows), unresolvedPlaylistRowCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_unresolved_history_rows), unresolvedListeningHistoryCount.toString())
        DiagnosticValue(stringResource(R.string.diagnostics_player_theme), stringResource(selectedPlayerTheme.labelRes))
        DiagnosticValue(stringResource(R.string.diagnostics_replay_gain), stringResource(selectedReplayGainMode.labelRes))
        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
        Text(
            text = stringResource(R.string.diag_audio_output),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        DiagnosticValue(stringResource(R.string.diag_source_format), formatAudioSourceUi(audioOutputUiState.sourceFormat))
        DiagnosticValue(stringResource(R.string.diag_route), formatAudioRouteUi(audioOutputUiState.routeInfo))
        DiagnosticValue(
            stringResource(R.string.diag_route_scope),
            stringResource(if (audioOutputUiState.routeInfo.isLocalPlayback) R.string.diag_local else R.string.diag_remote)
        )
        DiagnosticValue(
            stringResource(R.string.diag_offload_preference),
            stringResource(audioOutputUiState.offloadState.requestedPreference.labelRes)
        )
        DiagnosticValue(
            stringResource(R.string.diag_offload),
            formatAudioOffloadStatusUi(audioOutputUiState.offloadState)
        )
        DiagnosticValue(stringResource(R.string.diag_compatibility), formatAudioCompatibilityUi(audioOutputUiState))
        val equalizer = audioOutputUiState.equalizerRuntimeState
        DiagnosticValue(stringResource(R.string.diag_equalizer), formatEqualizerStatusUi(equalizer))
        DiagnosticValue(
            stringResource(R.string.diag_eq_processor_format),
            formatEqualizerProcessorFormatUi(equalizer)
        )
        DiagnosticValue(
            stringResource(R.string.diag_eq_versions),
            "${equalizer.configurationVersion} / " +
                (equalizer.preparedPlanVersion?.toString() ?: stringResource(R.string.diag_none)) +
                " / " +
                (equalizer.appliedPlanVersion?.toString() ?: stringResource(R.string.diag_none))
        )
        DiagnosticValue(
            stringResource(R.string.diag_eq_dsp_adoption),
            formatEqualizerPlanApplicationUi(equalizer)
        )
        DiagnosticValue(
            stringResource(R.string.diag_eq_control_timing),
            formatEqualizerPlanLatencyUi(equalizer)
        )
        DiagnosticValue(
            stringResource(R.string.diag_eq_filters),
            "${equalizer.validFilterCount} / ${equalizer.ignoredFilterCount}"
        )
        DiagnosticValue(
            stringResource(R.string.diag_automatic_headroom),
            String.format(
                Locale.ROOT,
                "%.2f dB",
                equalizer.automaticHeadroomDb
            )
        )
        DiagnosticValue(
            stringResource(R.string.diag_audio_path),
            if (equalizer.requiresDecodedPcm) {
                stringResource(R.string.diag_decoded_pcm_required)
            } else {
                stringResource(R.string.diag_offload_allowed)
            }
        )
        DiagnosticValue(
            stringResource(R.string.diag_eq_scratch_growth),
            equalizer.scratchBufferGrowthCount.toString()
        )
        DiagnosticValue(
            stringResource(R.string.diag_stale_plans_discarded),
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
                stringResource(R.string.diag_processor_timing),
                when {
                    equalizer
                        .processorPerformanceTelemetryEnabled ->
                        stringResource(R.string.diag_timing_running)
                    processorPerformance.totalCallCount > 0L ->
                        stringResource(R.string.diag_timing_retained)
                    else -> stringResource(R.string.diag_timing_stopped)
                }
            )
            if (hasProcessorPerformance) {
                DiagnosticValue(
                    stringResource(R.string.diag_processor_window_calls_frames),
                    "${processorPerformance.windowSampleCount} / " +
                        "${processorPerformance.totalCallCount} / " +
                        processorPerformance.totalFrameCount
                )
                DiagnosticValue(
                    stringResource(R.string.diag_processor_latency),
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
                    stringResource(R.string.diag_processor_rtf),
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
                    stringResource(R.string.diag_processor_deadline_misses),
                    processorPerformance.deadlineMissCount.toString()
                )
                DiagnosticValue(
                    stringResource(R.string.diag_processor_call_types),
                    "${processorPerformance.exactBypassCallCount} / " +
                        "${processorPerformance.equalizedCallCount} / " +
                        "${processorPerformance.transitionCallCount} / " +
                        processorPerformance.limiterCallCount
                )
                DiagnosticValue(
                    stringResource(R.string.diag_processor_prepare_time),
                    String.format(
                        Locale.ROOT,
                        "%.3f / %.3f ms",
                        processorPerformance.configurePreparationMillis,
                        processorPerformance.flushPreparationMillis
                    )
                )
                DiagnosticValue(
                    stringResource(R.string.diag_processor_prepare_count),
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
                            stringResource(R.string.diag_stop_timing)
                        } else {
                            stringResource(R.string.diag_start_timing)
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
                    Text(stringResource(R.string.diag_reset_timing))
                }
            }
        } else {
            DiagnosticValue(
                stringResource(R.string.diag_processor_timing),
                stringResource(R.string.diag_timing_unavailable)
            )
        }
        DiagnosticValue(
            stringResource(R.string.diag_limiter_states),
            "${equalizer.limiterRequestedEnabled} / " +
                "${equalizer.limiterEffectivelyActive} / " +
                equalizer.limiterPrimed
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_ceiling),
            String.format(
                Locale.ROOT,
                "%.1f dBFS",
                equalizer.limiterCeilingDbfs
            )
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_lookahead_release),
            String.format(
                Locale.ROOT,
                "%d frames (%.2f ms) / %.1f ms",
                equalizer.limiterLookaheadFrames,
                equalizer.limiterLookaheadMilliseconds,
                equalizer.limiterReleaseMilliseconds
            )
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_peaks),
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dBFS",
                equalizer.preLimiterPeakDbfs,
                equalizer.postLimiterPeakDbfs
            )
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_reduction),
            String.format(
                Locale.ROOT,
                "%.1f / %.1f dB",
                equalizer.currentGainReductionDb,
                equalizer.maximumRecentGainReductionDb
            )
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_samples),
            "${equalizer.overRangeSampleCount} / " +
                equalizer.saturatedSampleCount
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_frames),
            "${equalizer.limiterActiveFrameCount} / " +
                equalizer.limiterReducedFrameCount
        )
        DiagnosticValue(
            stringResource(R.string.diag_limiter_reprimes),
            equalizer.limiterReprimeCount.toString()
        )
        Text(
            text = stringResource(R.string.diag_dsp_timing_note),
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(
                horizontal = 16.dp,
                vertical = 8.dp
            )
        )
        audioOutputUiState.replayGainDb?.let { gain ->
            DiagnosticValue(stringResource(R.string.diag_replay_gain_value), String.format(Locale.ROOT, "%.2f dB", gain))
        }
        audioOutputUiState.appliedVolumeMultiplier?.let { multiplier ->
            DiagnosticValue(
                stringResource(R.string.diag_applied_volume),
                String.format(Locale.ROOT, "%.3fx", multiplier)
            )
        }
        audioOutputUiState.audioSessionId?.let { sessionId ->
            DiagnosticValue(stringResource(R.string.diag_audio_session), sessionId.toString())
        }
        Text(
            text = stringResource(R.string.diag_source_note),
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
                    context.copyToClipboard(formatDiagnosticsSummary(snapshot) + if(BuildConfig.DEBUG) "\n"+io.github.rsgarrido.sazanami.player.formatIntegratedDirectPlayback(io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackBridge.reportState,reportLocale) else "")
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
