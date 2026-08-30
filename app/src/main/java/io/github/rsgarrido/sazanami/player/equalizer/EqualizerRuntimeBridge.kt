package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterMeterSnapshot
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterPreparedConfiguration
import io.github.rsgarrido.sazanami.player.equalizer.limiter.LimiterTelemetryAccumulator
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Canonical application-level EQ request shared by every physical DSP runtime. */
internal class EqualizerSharedConfiguration {
    private val versionCounter = AtomicLong(0L)
    private val requested = AtomicReference(
        EqualizerConfigurationRequest(
            snapshot = EqualizerRuntimeSnapshot.DEFAULT,
            requestNanos = 0L
        )
    )
    private val comparisonState = AtomicReference(false to false)
    private val processorPerformanceEnabled = AtomicBoolean(false)

    @Synchronized
    fun request(
        configuration: EqualizerConfiguration,
        automaticHeadroomEnabled: Boolean,
        mode: EqualizerMode,
        limiterConfiguration: LimiterConfiguration
    ): EqualizerConfigurationRequest {
        val snapshot = EqualizerRuntimeSnapshot(
            version = versionCounter.incrementAndGet(),
            configuration = configuration,
            automaticHeadroomEnabled = automaticHeadroomEnabled,
            mode = mode,
            limiterConfiguration = limiterConfiguration
        )
        val now = System.nanoTime()
        return EqualizerConfigurationRequest(snapshot, now).also(requested::set)
    }

    fun currentRequest(): EqualizerConfigurationRequest =
        requested.get()

    fun setComparisonState(sessionActive: Boolean, bypassed: Boolean) {
        comparisonState.set(sessionActive to (sessionActive && bypassed))
    }

    fun comparisonState(): Pair<Boolean, Boolean> =
        comparisonState.get()

    fun setProcessorPerformanceEnabled(enabled: Boolean) {
        processorPerformanceEnabled.set(enabled)
    }

    fun processorPerformanceEnabled(): Boolean =
        processorPerformanceEnabled.get()

    @Synchronized
    fun reset() {
        versionCounter.set(0L)
        requested.set(
            EqualizerConfigurationRequest(
                snapshot = EqualizerRuntimeSnapshot.DEFAULT,
                requestNanos = 0L
            )
        )
        comparisonState.set(false to false)
        processorPerformanceEnabled.set(false)
    }
}

internal data class EqualizerConfigurationRequest(
    val snapshot: EqualizerRuntimeSnapshot,
    val requestNanos: Long
)

/** Routes application-facing diagnostics from one explicitly selected physical runtime. */
internal class EqualizerTelemetrySelector {
    private val selected = AtomicReference<EqualizerDspRuntime?>(null)
    private val _state = MutableStateFlow(EqualizerRuntimeState())
    val state: StateFlow<EqualizerRuntimeState> = _state.asStateFlow()

    fun select(runtime: EqualizerDspRuntime?) {
        selected.set(runtime)
        _state.value = runtime?.state?.value ?: EqualizerRuntimeState()
    }

    fun selectedRuntime(): EqualizerDspRuntime? = selected.get()

    fun publish(runtime: EqualizerDspRuntime, state: EqualizerRuntimeState) {
        if (selected.get() === runtime && _state.value != state) {
            _state.value = state
        }
    }

    fun publishSharedConfiguration(
        snapshot: EqualizerRuntimeSnapshot,
        comparisonSessionActive: Boolean,
        comparisonBypassed: Boolean
    ) {
        if (selected.get() != null) return
        val equalizerRequested =
            snapshot.configuration.enabled &&
                !snapshot.configuration.isEffectivelyFlat
        val requiresDecodedPcm =
            equalizerRequested ||
                snapshot.limiterConfiguration.enabled ||
                comparisonSessionActive
        _state.value = EqualizerRuntimeState(
            requestedEnabled = snapshot.configuration.enabled,
            effectivelyActive = requiresDecodedPcm,
            bypassed = !requiresDecodedPcm,
            comparisonSessionActive = comparisonSessionActive,
            comparisonBypassed = comparisonBypassed,
            requestedMode = snapshot.mode,
            activeMode = snapshot.mode,
            parametricFilterCount = if (snapshot.mode == EqualizerMode.PARAMETRIC) {
                snapshot.configuration.filters.size
            } else {
                0
            },
            parametricEnabledFilterCount = if (
                snapshot.mode == EqualizerMode.PARAMETRIC
            ) {
                snapshot.configuration.filters.count { filter -> filter.enabled }
            } else {
                0
            },
            configurationVersion = snapshot.version,
            requiresDecodedPcm = requiresDecodedPcm,
            limiterRequestedEnabled = snapshot.limiterConfiguration.enabled,
            limiterCeilingDbfs = snapshot.limiterConfiguration.ceilingDbfs
        )
    }
}

/**
 * Application-level EQ configuration and telemetry facade.
 *
 * Mutable stream preparation and processor telemetry live in [EqualizerDspRuntime]. Production
 * creates its runtime explicitly at the ExoPlayer construction boundary.
 */
internal object EqualizerRuntimeBridge {
    private val lock = Any()
    private val sharedConfiguration = EqualizerSharedConfiguration()
    private val telemetrySelector = EqualizerTelemetrySelector()
    private val runtimes = linkedSetOf<EqualizerDspRuntime>()

    val state: StateFlow<EqualizerRuntimeState> = telemetrySelector.state

    fun createRuntime(): EqualizerDspRuntime {
        val runtime = EqualizerDspRuntime(telemetrySelector)
        synchronized(lock) {
            initializeRuntime(runtime)
            runtimes += runtime
        }
        return runtime
    }

    /** Preserves no-argument processor construction in tests without sharing runtime state. */
    internal fun createSelectedRuntimeForCompatibility(): EqualizerDspRuntime =
        createRuntime().also(telemetrySelector::select)

    fun selectTelemetryRuntime(runtime: EqualizerDspRuntime) {
        require(synchronized(lock) { runtime in runtimes }) {
            "Telemetry runtime must be created by EqualizerRuntimeBridge"
        }
        telemetrySelector.select(runtime)
    }

    fun releaseRuntime(runtime: EqualizerDspRuntime) {
        val wasSelected = telemetrySelector.selectedRuntime() === runtime
        val removed = synchronized(lock) {
            runtimes.remove(runtime)
        }
        if (!removed) return
        runtime.release()
        if (wasSelected) {
            telemetrySelector.select(runtimeSnapshot().firstOrNull())
            publishSharedConfigurationIfNeeded()
        }
    }

    internal fun selectedTelemetryRuntime(): EqualizerDspRuntime? =
        telemetrySelector.selectedRuntime()

    internal fun registeredRuntimeCountForTest(): Int =
        synchronized(lock) { runtimes.size }

    internal fun isRuntimeRegisteredForTest(
        runtime: EqualizerDspRuntime
    ): Boolean =
        synchronized(lock) { runtime in runtimes }

    fun start(scope: CoroutineScope) {
        selectedOrCompatibilityRuntime().start(scope)
    }

    fun start(runtime: EqualizerDspRuntime, scope: CoroutineScope) {
        require(synchronized(lock) { runtime in runtimes }) {
            "DSP runtime must be created by EqualizerRuntimeBridge"
        }
        runtime.start(scope)
    }

    fun release() {
        val released = synchronized(lock) {
            runtimes.toList().also {
                runtimes.clear()
            }
        }
        released.forEach(EqualizerDspRuntime::release)
        sharedConfiguration.reset()
        telemetrySelector.select(null)
    }

    fun requestConfiguration(
        configuration: EqualizerConfiguration,
        automaticHeadroomEnabled: Boolean,
        mode: EqualizerMode = EqualizerMode.GRAPHIC,
        limiterConfiguration: LimiterConfiguration = LimiterConfiguration()
    ): EqualizerRuntimeSnapshot {
        val request = sharedConfiguration.request(
            configuration = configuration,
            automaticHeadroomEnabled = automaticHeadroomEnabled,
            mode = mode,
            limiterConfiguration = limiterConfiguration
        )
        val targets = runtimeSnapshot()
        targets.forEach { runtime ->
            runtime.acceptConfiguration(request.snapshot, request.requestNanos)
        }
        publishSharedConfigurationIfNeeded()
        return request.snapshot
    }

    fun setComparisonState(sessionActive: Boolean, bypassed: Boolean) {
        sharedConfiguration.setComparisonState(sessionActive, bypassed)
        runtimeSnapshot().forEach { runtime ->
            runtime.setComparisonState(sessionActive, bypassed)
        }
        publishSharedConfigurationIfNeeded()
    }

    fun requestedSnapshot(): EqualizerRuntimeSnapshot =
        sharedConfiguration.currentRequest().snapshot

    fun requestLimiterMeterReset() {
        runtimeSnapshot().forEach(EqualizerDspRuntime::requestLimiterMeterReset)
    }

    fun setProcessorPerformanceTelemetryEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        sharedConfiguration.setProcessorPerformanceEnabled(enabled)
        runtimeSnapshot().forEach { runtime ->
            runtime.setProcessorPerformanceTelemetryEnabled(enabled)
        }
    }

    fun processorPerformanceTelemetryEnabled(): Boolean =
        BuildConfig.DEBUG && sharedConfiguration.processorPerformanceEnabled()

    fun requestProcessorPerformanceTelemetryReset() {
        runtimeSnapshot().forEach(
            EqualizerDspRuntime::requestProcessorPerformanceTelemetryReset
        )
    }

    fun publishProcessorFormat(format: EqualizerProcessorFormat?) =
        selectedOrCompatibilityRuntime().publishProcessorFormat(format)

    fun prepareForProcessorFormat(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath =
        selectedOrCompatibilityRuntime().prepareForProcessorFormat(format)

    fun latestCompatiblePath(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath? =
        selectedOrCompatibilityRuntime().latestCompatiblePath(format)

    fun latestCompatibleLimiterConfiguration(
        format: EqualizerProcessorFormat
    ): LimiterPreparedConfiguration? =
        selectedOrCompatibilityRuntime()
            .latestCompatibleLimiterConfiguration(format)

    fun isLimiterPreparationPending(format: EqualizerProcessorFormat): Boolean =
        selectedOrCompatibilityRuntime().isLimiterPreparationPending(format)

    fun isEqualizerPreparationPending(format: EqualizerProcessorFormat): Boolean =
        selectedOrCompatibilityRuntime().isEqualizerPreparationPending(format)

    fun publishProcessorConfigured(configured: Boolean, bypassed: Boolean) =
        selectedOrCompatibilityRuntime()
            .publishProcessorConfigured(configured, bypassed)

    fun publishAppliedPlan(
        plan: PreparedEqualizerPlan?,
        applicationMode: EqualizerPlanApplicationMode
    ) = selectedOrCompatibilityRuntime()
        .publishAppliedPlan(plan, applicationMode)

    fun publishTransitionStarted(totalFrameCount: Int, sampleRateHz: Int) =
        selectedOrCompatibilityRuntime()
            .publishTransitionStarted(totalFrameCount, sampleRateHz)

    fun publishTransitionInProgress(inProgress: Boolean) =
        selectedOrCompatibilityRuntime().publishTransitionInProgress(inProgress)

    fun publishScratchBufferGrowthCount(growthCount: Int) =
        selectedOrCompatibilityRuntime().publishScratchBufferGrowthCount(growthCount)

    fun publishLimiterProcessorState(
        effectivelyActive: Boolean,
        primed: Boolean,
        reprimeCount: Int
    ) = selectedOrCompatibilityRuntime().publishLimiterProcessorState(
        effectivelyActive,
        primed,
        reprimeCount
    )

    fun publishLimiterMeterSnapshot(snapshot: LimiterMeterSnapshot) =
        selectedOrCompatibilityRuntime().publishLimiterMeterSnapshot(snapshot)

    fun publishLimiterTelemetry(accumulator: LimiterTelemetryAccumulator) =
        selectedOrCompatibilityRuntime().publishLimiterTelemetry(accumulator)

    fun limiterMeterResetVersion(): Long =
        selectedOrCompatibilityRuntime().limiterMeterResetVersion()

    fun processorPerformanceTelemetryResetVersion(): Long =
        selectedOrCompatibilityRuntime()
            .processorPerformanceTelemetryResetVersion()

    fun performanceTelemetry(): EqualizerProcessorPerformanceTelemetry =
        selectedOrCompatibilityRuntime().performanceTelemetry()

    fun performanceTelemetryIfEnabled(): EqualizerProcessorPerformanceTelemetry? =
        selectedOrCompatibilityRuntime().performanceTelemetryIfEnabled()

    fun clearProcessorTelemetry() =
        selectedOrCompatibilityRuntime().clearProcessorTelemetry()

    internal fun coordinatorStartCount(): Int =
        selectedOrCompatibilityRuntime().coordinatorStartCount()

    internal fun isCoordinatorRunning(): Boolean =
        selectedOrCompatibilityRuntime().isCoordinatorRunning()

    internal fun publishStateForTest() {
        val selected = telemetrySelector.selectedRuntime()
        if (selected != null) {
            selected.publishStateForTest()
        } else {
            publishSharedConfigurationIfNeeded()
        }
    }

    internal fun installPreparedPathForTest(path: PreparedEqualizerProcessingPath) {
        selectedOrCompatibilityRuntime().installPreparedPathForTest(path)
    }

    private fun initializeRuntime(runtime: EqualizerDspRuntime) {
        val request = sharedConfiguration.currentRequest()
        runtime.acceptConfiguration(request.snapshot, request.requestNanos)
        val comparison = sharedConfiguration.comparisonState()
        runtime.setComparisonState(comparison.first, comparison.second)
        runtime.setProcessorPerformanceTelemetryEnabled(
            sharedConfiguration.processorPerformanceEnabled()
        )
    }

    private fun selectedOrCompatibilityRuntime(): EqualizerDspRuntime =
        telemetrySelector.selectedRuntime()
            ?: createSelectedRuntimeForCompatibility()

    private fun runtimeSnapshot(): List<EqualizerDspRuntime> =
        synchronized(lock) { runtimes.toList() }

    private fun publishSharedConfigurationIfNeeded() {
        val request = sharedConfiguration.currentRequest()
        val comparison = sharedConfiguration.comparisonState()
        telemetrySelector.publishSharedConfiguration(
            snapshot = request.snapshot,
            comparisonSessionActive = comparison.first,
            comparisonBypassed = comparison.second
        )
    }
}
