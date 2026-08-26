package com.example.cdplaya.player.equalizer

import com.example.cdplaya.BuildConfig
import com.example.cdplaya.player.equalizer.limiter.LIMITER_RELEASE_MILLISECONDS
import com.example.cdplaya.player.equalizer.limiter.LimiterMath
import com.example.cdplaya.player.equalizer.limiter.LimiterMeterSnapshot
import com.example.cdplaya.player.equalizer.limiter.LimiterPreparedConfiguration
import com.example.cdplaya.player.equalizer.limiter.LimiterTelemetryAccumulator
import com.example.cdplaya.player.equalizer.limiter.LimiterTelemetryExchange
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Per-physical-player handoff between shared requests, background plan preparation, and one
 * audio processor. Every field in this class is stream-local mutable state.
 */
internal class EqualizerDspRuntime(
    private val telemetrySelector: EqualizerTelemetrySelector
) : EqualizerProcessorRuntime {
    private val requestedSnapshot =
        AtomicReference(EqualizerRuntimeSnapshot.DEFAULT)
    private val processorFormat =
        AtomicReference<EqualizerProcessorFormat?>(null)
    private val preparedPath =
        AtomicReference<PreparedEqualizerProcessingPath?>(null)
    private val preparedLimiterConfiguration =
        AtomicReference<LimiterPreparedConfiguration?>(null)
    private val latestRequestVersion = AtomicLong(0L)
    private val latestRequestNanos = AtomicLong(0L)
    private val latestPreparedVersion = AtomicLong(-1L)
    private val latestPreparedNanos = AtomicLong(0L)
    private val stalePreparedPlanDiscardCount =
        AtomicLong(0L)

    private val processorConfigured = AtomicBoolean(false)
    private val processorBypassed = AtomicBoolean(true)
    private val transitionInProgress = AtomicBoolean(false)
    private val comparisonSessionActive = AtomicBoolean(false)
    private val comparisonBypassed = AtomicBoolean(false)
    private val appliedPlan = AtomicReference<PreparedEqualizerPlan?>(null)
    private val latestAppliedVersion = AtomicLong(-1L)
    private val latestAppliedNanos = AtomicLong(0L)
    private val lastPlanApplicationMode =
        AtomicReference(EqualizerPlanApplicationMode.NONE)
    private val lastTransitionFrameCount = AtomicInteger(0)
    private val lastTransitionSampleRateHz = AtomicInteger(0)
    private val scratchBufferGrowthCount = AtomicInteger(0)
    private val limiterEffectivelyActive = AtomicBoolean(false)
    private val limiterPrimed = AtomicBoolean(false)
    private val limiterReprimeCount = AtomicInteger(0)
    private val limiterMeterResetVersion = AtomicLong(0L)
    private val limiterTelemetryExchange =
        LimiterTelemetryExchange()
    @Volatile
    private var observedLimiterTelemetrySequence = 0L
    @Volatile
    private var preLimiterPeakHold = LimiterPeakHold()
    @Volatile
    private var postLimiterPeakHold = LimiterPeakHold()
    @Volatile
    private var limiterMaximumHold = LimiterMaximumHold()
    @Volatile
    private var processorPerformanceTelemetry:
        EqualizerProcessorPerformanceTelemetry? = null
    private val processorPerformanceEnabled =
        AtomicBoolean(false)
    private val processorPerformanceResetVersion =
        AtomicLong(0L)
    private val retainedProcessorPerformanceSnapshot =
        AtomicReference(EqualizerProcessorPerformanceSnapshot())

    private val _state = MutableStateFlow(EqualizerRuntimeState())
    val state: StateFlow<EqualizerRuntimeState> = _state.asStateFlow()

    private var coordinatorJob: Job? = null
    private var coordinatorStartCount = 0

    fun start(scope: CoroutineScope) {
        if (coordinatorJob != null) return
        coordinatorStartCount += 1
        coordinatorJob = scope.launch {
            runCoordinator()
        }
    }

    internal fun release() {
        coordinatorJob?.cancel()
        coordinatorJob = null
        requestedSnapshot.set(EqualizerRuntimeSnapshot.DEFAULT)
        latestRequestVersion.set(0L)
        latestRequestNanos.set(0L)
        processorFormat.set(null)
        preparedPath.set(null)
        preparedLimiterConfiguration.set(null)
        latestPreparedVersion.set(-1L)
        latestPreparedNanos.set(0L)
        stalePreparedPlanDiscardCount.set(0L)
        comparisonSessionActive.set(false)
        comparisonBypassed.set(false)
        limiterMeterResetVersion.set(0L)
        processorPerformanceEnabled.set(false)
        processorPerformanceResetVersion.set(0L)
        processorPerformanceTelemetry = null
        retainedProcessorPerformanceSnapshot.set(
            EqualizerProcessorPerformanceSnapshot()
        )
        clearProcessorTelemetry()
        _state.value = EqualizerRuntimeState()
        telemetrySelector.publish(this, _state.value)
    }

    internal fun acceptConfiguration(
        snapshot: EqualizerRuntimeSnapshot,
        requestNanos: Long
    ) {
        latestRequestVersion.set(snapshot.version)
        latestRequestNanos.set(requestNanos)
        requestedSnapshot.set(snapshot)
        publishState()
    }

    internal fun setComparisonState(
        sessionActive: Boolean,
        bypassed: Boolean
    ) {
        comparisonSessionActive.set(sessionActive)
        comparisonBypassed.set(sessionActive && bypassed)
        publishState()
    }

    override fun requestedSnapshot(): EqualizerRuntimeSnapshot {
        return requestedSnapshot.get()
    }

    override fun publishProcessorFormat(format: EqualizerProcessorFormat?) {
        processorFormat.set(format)
        if (format == null) {
            preparedPath.set(null)
            preparedLimiterConfiguration.set(null)
        }
    }

    /**
     * Prepares the latest requested EQ and limiter state for a newly
     * configured sink format before Media3 can submit its first PCM buffer.
     *
     * Ordinary curve changes remain background-prepared by the coordinator.
     * A format boundary is different: copying audio while coefficients for
     * the new rate are pending would expose an audible exact-bypass segment.
     */
    override fun prepareForProcessorFormat(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath {
        processorFormat.set(format)
        var lastPreparedPath:
            PreparedEqualizerProcessingPath? = null
        repeat(MAXIMUM_SYNCHRONOUS_PREPARATION_ATTEMPTS) {
            val snapshot = requestedSnapshot.get()
            val existingPath = preparedPath.get()
            val existingLimiter =
                preparedLimiterConfiguration.get()
            if (
                existingPath?.plan?.sourceSnapshotVersion ==
                snapshot.version &&
                existingPath.plan.processorFormat == format &&
                existingLimiter?.configurationVersion ==
                snapshot.version &&
                existingLimiter.sampleRateHz ==
                format.sampleRateHz &&
                existingLimiter.channelCount ==
                format.channelCount
            ) {
                return existingPath
            }

            val path = EqualizerPlanPreparer.prepare(
                snapshot = snapshot,
                processorFormat = format
            ).createProcessingPath()
            val limiter = LimiterPreparedConfiguration.prepare(
                configuration = snapshot.limiterConfiguration,
                sampleRateHz = format.sampleRateHz,
                channelCount = format.channelCount,
                configurationVersion = snapshot.version
            )
            lastPreparedPath = path
            if (
                requestedSnapshot.get() === snapshot &&
                processorFormat.get() == format
            ) {
                preparedPath.set(path)
                preparedLimiterConfiguration.set(limiter)
                latestPreparedVersion.set(snapshot.version)
                latestPreparedNanos.set(System.nanoTime())
                publishState()
                return path
            }
            stalePreparedPlanDiscardCount.incrementAndGet()
        }
        // A continuously changing editor must not make Media3 configuration
        // spin forever. The first-input gate will hold PCM until the existing
        // coordinator publishes the newest matching plan.
        return checkNotNull(lastPreparedPath)
    }

    override fun latestCompatiblePath(
        format: EqualizerProcessorFormat
    ): PreparedEqualizerProcessingPath? {
        val path = preparedPath.get() ?: return null
        return path.takeIf { candidate ->
            candidate.plan.processorFormat == format
        }
    }

    override fun latestCompatibleLimiterConfiguration(
        format: EqualizerProcessorFormat
    ): LimiterPreparedConfiguration? {
        val configuration =
            preparedLimiterConfiguration.get() ?: return null
        return configuration.takeIf { candidate ->
            candidate.sampleRateHz == format.sampleRateHz &&
                candidate.channelCount == format.channelCount
        }
    }

    override fun isLimiterPreparationPending(
        format: EqualizerProcessorFormat
    ): Boolean {
        val snapshot = requestedSnapshot.get()
        if (!snapshot.limiterConfiguration.enabled) return false
        val prepared = preparedLimiterConfiguration.get()
        return prepared == null ||
            prepared.configurationVersion != snapshot.version ||
            prepared.sampleRateHz != format.sampleRateHz ||
            prepared.channelCount != format.channelCount
    }

    override fun isEqualizerPreparationPending(
        format: EqualizerProcessorFormat
    ): Boolean {
        val snapshot = requestedSnapshot.get()
        val prepared = preparedPath.get()
        return prepared == null ||
            prepared.plan.sourceSnapshotVersion != snapshot.version ||
            prepared.plan.processorFormat != format
    }

    override fun publishProcessorConfigured(
        configured: Boolean,
        bypassed: Boolean
    ) {
        processorConfigured.set(configured)
        processorBypassed.set(bypassed)
    }

    override fun publishAppliedPlan(
        plan: PreparedEqualizerPlan?,
        applicationMode: EqualizerPlanApplicationMode
    ) {
        val previousPlan = appliedPlan.get()
        val previousVersion = previousPlan?.sourceSnapshotVersion
        appliedPlan.set(plan)
        processorBypassed.set(plan?.bypassed ?: true)
        if (
            plan != null &&
            (
                plan.sourceSnapshotVersion != previousVersion ||
                    plan.processorFormat !=
                    previousPlan?.processorFormat
                )
        ) {
            latestAppliedVersion.set(plan.sourceSnapshotVersion)
            latestAppliedNanos.set(System.nanoTime())
            lastPlanApplicationMode.set(applicationMode)
            if (
                applicationMode !=
                EqualizerPlanApplicationMode.CROSSFADE
            ) {
                lastTransitionFrameCount.set(0)
                lastTransitionSampleRateHz.set(0)
            }
        }
    }

    override fun publishTransitionStarted(
        totalFrameCount: Int,
        sampleRateHz: Int
    ) {
        lastTransitionFrameCount.set(totalFrameCount)
        lastTransitionSampleRateHz.set(sampleRateHz)
        transitionInProgress.set(true)
    }

    override fun publishTransitionInProgress(inProgress: Boolean) {
        transitionInProgress.set(inProgress)
    }

    override fun publishScratchBufferGrowthCount(growthCount: Int) {
        scratchBufferGrowthCount.set(growthCount)
    }

    override fun publishLimiterProcessorState(
        effectivelyActive: Boolean,
        primed: Boolean,
        reprimeCount: Int
    ) {
        limiterEffectivelyActive.set(effectivelyActive)
        limiterPrimed.set(primed)
        limiterReprimeCount.set(reprimeCount)
    }

    override fun publishLimiterMeterSnapshot(snapshot: LimiterMeterSnapshot) {
        limiterTelemetryExchange.publish(snapshot)
    }

    override fun publishLimiterTelemetry(
        accumulator: LimiterTelemetryAccumulator
    ) {
        accumulator.publishTo(limiterTelemetryExchange)
    }

    fun requestLimiterMeterReset() {
        limiterMeterResetVersion.incrementAndGet()
        limiterTelemetryExchange.reset()
        observedLimiterTelemetrySequence = 0L
        preLimiterPeakHold = LimiterPeakHold()
        postLimiterPeakHold = LimiterPeakHold()
        limiterMaximumHold = LimiterMaximumHold()
        publishState()
    }

    override fun limiterMeterResetVersion(): Long =
        limiterMeterResetVersion.get()

    fun setProcessorPerformanceTelemetryEnabled(enabled: Boolean) {
        if (!BuildConfig.DEBUG) return
        if (enabled) {
            processorPerformanceEnabled.set(false)
            resetProcessorPerformanceTelemetry()
            processorPerformanceEnabled.set(true)
        } else if (processorPerformanceEnabled.getAndSet(false)) {
            val captured =
                processorPerformanceTelemetry?.snapshot()
            if (
                captured != null &&
                (
                    captured.totalCallCount > 0L ||
                        retainedProcessorPerformanceSnapshot.get()
                            .totalCallCount == 0L
                    )
            ) {
                retainedProcessorPerformanceSnapshot.set(captured)
            }
        }
        publishState()
    }

    fun processorPerformanceTelemetryEnabled(): Boolean =
        BuildConfig.DEBUG &&
            processorPerformanceEnabled.get()

    fun requestProcessorPerformanceTelemetryReset() {
        if (!BuildConfig.DEBUG) return
        resetProcessorPerformanceTelemetry()
        publishState()
    }

    override fun processorPerformanceTelemetryResetVersion(): Long =
        processorPerformanceResetVersion.get()

    override fun performanceTelemetry():
        EqualizerProcessorPerformanceTelemetry =
        checkNotNull(processorPerformanceTelemetry) {
            "Processor performance telemetry is not enabled"
        }

    override fun performanceTelemetryIfEnabled():
        EqualizerProcessorPerformanceTelemetry? {
        if (
            !BuildConfig.DEBUG ||
            !processorPerformanceEnabled.get()
        ) {
            return null
        }
        return processorPerformanceTelemetry
    }

    private fun resetProcessorPerformanceTelemetry() {
        processorPerformanceTelemetry =
            EqualizerProcessorPerformanceTelemetry()
        retainedProcessorPerformanceSnapshot.set(
            EqualizerProcessorPerformanceSnapshot()
        )
        processorPerformanceResetVersion.incrementAndGet()
    }

    override fun clearProcessorTelemetry() {
        processorConfigured.set(false)
        processorBypassed.set(true)
        transitionInProgress.set(false)
        appliedPlan.set(null)
        latestAppliedVersion.set(-1L)
        latestAppliedNanos.set(0L)
        lastPlanApplicationMode.set(EqualizerPlanApplicationMode.NONE)
        lastTransitionFrameCount.set(0)
        lastTransitionSampleRateHz.set(0)
        scratchBufferGrowthCount.set(0)
        stalePreparedPlanDiscardCount.set(0L)
        limiterEffectivelyActive.set(false)
        limiterPrimed.set(false)
        limiterReprimeCount.set(0)
        limiterTelemetryExchange.reset()
        observedLimiterTelemetrySequence = 0L
        preLimiterPeakHold = LimiterPeakHold()
        postLimiterPeakHold = LimiterPeakHold()
        limiterMaximumHold = LimiterMaximumHold()
        processorPerformanceTelemetry?.reset()
    }

    internal fun coordinatorStartCount(): Int = coordinatorStartCount

    internal fun isCoordinatorRunning(): Boolean {
        return coordinatorJob?.isActive == true
    }

    internal fun publishStateForTest() {
        publishState()
    }

    internal fun installPreparedPathForTest(
        path: PreparedEqualizerProcessingPath
    ) {
        processorFormat.set(path.plan.processorFormat)
        preparedPath.set(path)
        preparedLimiterConfiguration.set(
            LimiterPreparedConfiguration.prepare(
                configuration =
                    requestedSnapshot.get().limiterConfiguration,
                sampleRateHz =
                    path.plan.processorFormat.sampleRateHz,
                channelCount =
                    path.plan.processorFormat.channelCount,
                configurationVersion =
                    path.plan.sourceSnapshotVersion
            )
        )
        publishState()
    }

    private suspend fun runCoordinator() {
        var preparedSnapshotVersion = Long.MIN_VALUE
        var preparedFormat: EqualizerProcessorFormat? = null

        while (currentCoroutineContext().isActive) {
            val snapshot = requestedSnapshot.get()
            val format = processorFormat.get()
            val publishedPath = preparedPath.get()
            val publishedLimiter =
                preparedLimiterConfiguration.get()
            if (
                format != null &&
                publishedPath?.plan?.sourceSnapshotVersion ==
                snapshot.version &&
                publishedPath.plan.processorFormat == format &&
                publishedLimiter?.configurationVersion ==
                snapshot.version &&
                publishedLimiter.sampleRateHz ==
                format.sampleRateHz &&
                publishedLimiter.channelCount ==
                format.channelCount
            ) {
                preparedSnapshotVersion = snapshot.version
                preparedFormat = format
            }
            if (
                format != null &&
                (
                    snapshot.version != preparedSnapshotVersion ||
                        format != preparedFormat
                    )
            ) {
                val path = withContext(Dispatchers.Default) {
                    val preparedPath = EqualizerPlanPreparer.prepare(
                        snapshot = snapshot,
                        processorFormat = format
                    ).createProcessingPath()
                    val limiter =
                        LimiterPreparedConfiguration.prepare(
                            configuration =
                                snapshot.limiterConfiguration,
                            sampleRateHz = format.sampleRateHz,
                            channelCount = format.channelCount,
                            configurationVersion =
                                snapshot.version
                        )
                    preparedPath to limiter
                }
                if (
                    requestedSnapshot.get() === snapshot &&
                    processorFormat.get() == format
                ) {
                    latestPreparedVersion.set(snapshot.version)
                    latestPreparedNanos.set(System.nanoTime())
                    preparedPath.set(path.first)
                    preparedLimiterConfiguration.set(path.second)
                    preparedSnapshotVersion = snapshot.version
                    preparedFormat = format
                } else {
                    stalePreparedPlanDiscardCount.incrementAndGet()
                }
            }
            publishState()
            delay(COORDINATOR_POLL_MILLIS)
        }
    }

    private fun publishState() {
        val snapshot = requestedSnapshot.get()
        val format = processorFormat.get()
        val latestPlan = preparedPath.get()?.plan
        val applied = appliedPlan.get()
        val preparedLimiter =
            preparedLimiterConfiguration.get()
        val requestVersion = latestRequestVersion.get()
        val requestNanos = latestRequestNanos.get()
        val transitionFrameCount = lastTransitionFrameCount.get()
        val transitionSampleRateHz =
            lastTransitionSampleRateHz.get()
        val latestMatchesRequest =
            latestPlan?.sourceSnapshotVersion == snapshot.version &&
                latestPlan.processorFormat == format
        val plannedActive =
            latestMatchesRequest && latestPlan?.bypassed == false
        val awaitingActivePlan =
            !latestMatchesRequest &&
                snapshot.configuration.enabled &&
                !snapshot.configuration.isEffectivelyFlat
        val requiresDecodedPcm =
            plannedActive || awaitingActivePlan ||
                applied?.bypassed == false ||
                comparisonSessionActive.get() ||
                snapshot.limiterConfiguration.enabled ||
                limiterEffectivelyActive.get()
        val diagnosticPlan = if (latestMatchesRequest) {
            latestPlan
        } else {
            applied
        }
        val limiterExchangeSnapshot =
            limiterTelemetryExchange.snapshot()
        val limiterMeter = limiterExchangeSnapshot.meter
        updateLimiterHolds(
            exchangeSequence = limiterExchangeSnapshot.sequence,
            snapshot = limiterMeter
        )
        val limiterIsActive = limiterEffectivelyActive.get()
        val displayedPreLimiterPeakDbfs = decayedPeakDbfs(
            preLimiterPeakHold
        )
        val displayedPostLimiterPeakDbfs = if (limiterIsActive) {
            decayedPeakDbfs(postLimiterPeakHold)
        } else {
            // In bypass both labels describe the same signal point. Reusing one
            // visual hold prevents independent decay histories from implying
            // gain or attenuation that the processor did not apply.
            displayedPreLimiterPeakDbfs
        }
        val performanceEnabled =
            processorPerformanceEnabled.get()
        val performanceSnapshot = if (BuildConfig.DEBUG) {
            if (performanceEnabled) {
                checkNotNull(processorPerformanceTelemetry)
                    .snapshot()
                    .also(
                        retainedProcessorPerformanceSnapshot::set
                    )
            } else {
                retainedProcessorPerformanceSnapshot.get()
            }
        } else {
            EqualizerProcessorPerformanceSnapshot()
        }
        val nextState = EqualizerRuntimeState(
            processorConfigured = processorConfigured.get(),
            requestedEnabled = snapshot.configuration.enabled,
            effectivelyActive = requiresDecodedPcm,
            bypassed = processorBypassed.get() && !requiresDecodedPcm,
            transitionInProgress = transitionInProgress.get(),
            comparisonSessionActive =
                comparisonSessionActive.get(),
            comparisonBypassed = comparisonBypassed.get(),
            requestedMode = snapshot.mode,
            activeMode =
                diagnosticPlan?.sourceMode ?: snapshot.mode,
            parametricFilterCount =
                if (snapshot.mode == EqualizerMode.PARAMETRIC) {
                    snapshot.configuration.filters.size
                } else {
                    0
                },
            parametricEnabledFilterCount =
                if (snapshot.mode == EqualizerMode.PARAMETRIC) {
                    snapshot.configuration.filters.count { filter ->
                        filter.enabled
                    }
                } else {
                    0
                },
            configurationVersion = snapshot.version,
            preparedPlanVersion = latestPlan?.sourceSnapshotVersion,
            appliedPlanVersion = applied?.sourceSnapshotVersion,
            planPreparationLatencyMillis = matchingLatencyMillis(
                snapshotVersion = snapshot.version,
                requestVersion = requestVersion,
                requestNanos = requestNanos,
                eventVersion = latestPreparedVersion.get(),
                eventNanos = latestPreparedNanos.get()
            ),
            planApplicationLatencyMillis = matchingLatencyMillis(
                snapshotVersion = snapshot.version,
                requestVersion = requestVersion,
                requestNanos = requestNanos,
                eventVersion = latestAppliedVersion.get(),
                eventNanos = latestAppliedNanos.get()
            ),
            lastPlanApplicationMode =
                lastPlanApplicationMode.get(),
            lastTransitionFrameCount =
                transitionFrameCount,
            lastTransitionDurationMillis =
                transitionDurationMillis(
                    frameCount = transitionFrameCount,
                    sampleRateHz = transitionSampleRateHz
                ),
            lastTransitionSampleRateHz =
                transitionSampleRateHz.takeIf { it > 0 },
            sampleRateHz = format?.sampleRateHz,
            channelCount = format?.channelCount,
            validFilterCount = diagnosticPlan?.validFilterCount ?: 0,
            ignoredFilterCount =
                diagnosticPlan?.ignoredFilters?.size ?: 0,
            automaticHeadroomDb =
                diagnosticPlan
                    ?.automaticHeadroomResult
                    ?.attenuationDb
                    ?: 0.0,
            requiresDecodedPcm = requiresDecodedPcm,
            scratchBufferGrowthCount = scratchBufferGrowthCount.get(),
            stalePreparedPlanDiscardCount =
                stalePreparedPlanDiscardCount.get(),
            processorPerformanceTelemetryEnabled =
                performanceEnabled,
            processorPerformance =
                performanceSnapshot,
            limiterRequestedEnabled =
                snapshot.limiterConfiguration.enabled,
            limiterEffectivelyActive = limiterIsActive,
            limiterCeilingDbfs =
                snapshot.limiterConfiguration.ceilingDbfs,
            limiterLookaheadFrames =
                preparedLimiter?.lookaheadFrames ?: 0,
            limiterLookaheadMilliseconds =
                preparedLimiter?.let { limiter ->
                    limiter.lookaheadFrames * 1_000.0 /
                        limiter.sampleRateHz
                } ?: 0.0,
            limiterReleaseMilliseconds =
                LIMITER_RELEASE_MILLISECONDS,
            limiterPrimed = limiterPrimed.get(),
            preLimiterPeakDbfs = displayedPreLimiterPeakDbfs,
            postLimiterPeakDbfs = displayedPostLimiterPeakDbfs,
            currentGainReductionDb =
                limiterMeter.currentGainReductionDb,
            maximumRecentGainReductionDb =
                recentMaximumGainReductionDb(),
            overRangeSampleCount =
                limiterMeter.overRangeSampleCount,
            saturatedSampleCount =
                limiterMeter.saturatedSampleCount,
            limiterActiveFrameCount =
                limiterMeter.limiterActiveFrameCount,
            limiterReducedFrameCount =
                limiterMeter.limiterReducedFrameCount,
            limiterReprimeCount = limiterReprimeCount.get()
        )
        if (_state.value != nextState) {
            _state.value = nextState
            telemetrySelector.publish(this, nextState)
        }
    }

    private fun matchingLatencyMillis(
        snapshotVersion: Long,
        requestVersion: Long,
        requestNanos: Long,
        eventVersion: Long,
        eventNanos: Long
    ): Long? {
        if (
            snapshotVersion != requestVersion ||
            snapshotVersion != eventVersion ||
            requestNanos <= 0L ||
            eventNanos < requestNanos
        ) {
            return null
        }
        return (eventNanos - requestNanos) / NANOS_PER_MILLISECOND
    }

    private fun transitionDurationMillis(
        frameCount: Int,
        sampleRateHz: Int
    ): Double {
        if (frameCount <= 0 || sampleRateHz <= 0) return 0.0
        return frameCount * 1_000.0 / sampleRateHz
    }

    private fun decayedPeakDbfs(hold: LimiterPeakHold): Double {
        if (hold.timestampNanos <= 0L) {
            return LimiterMath.SILENCE_FLOOR_DBFS
        }
        val elapsedSeconds =
            (System.nanoTime() - hold.timestampNanos)
                .coerceAtLeast(0L) / 1_000_000_000.0
        return (
            hold.peakDbfs -
                elapsedSeconds * METER_DECAY_DB_PER_SECOND
            ).coerceAtLeast(LimiterMath.SILENCE_FLOOR_DBFS)
    }

    private fun updateLimiterHolds(
        exchangeSequence: Long,
        snapshot: LimiterMeterSnapshot
    ) {
        if (
            exchangeSequence == observedLimiterTelemetrySequence
        ) {
            return
        }
        observedLimiterTelemetrySequence = exchangeSequence
        val now = System.nanoTime()
        preLimiterPeakHold = updatedPeakHold(
            previous = preLimiterPeakHold,
            newPeakDbfs = snapshot.preLimiterPeakDbfs,
            nowNanos = now
        )
        postLimiterPeakHold = updatedPeakHold(
            previous = postLimiterPeakHold,
            newPeakDbfs = snapshot.postLimiterPeakDbfs,
            nowNanos = now
        )
        if (
            snapshot.maximumGainReductionDb > 0.0 &&
            (
                now - limiterMaximumHold.timestampNanos >
                    MAXIMUM_HOLD_NANOS ||
                    snapshot.maximumGainReductionDb >=
                    limiterMaximumHold.reductionDb
                )
        ) {
            limiterMaximumHold = LimiterMaximumHold(
                reductionDb = snapshot.maximumGainReductionDb,
                timestampNanos = now
            )
        }
    }

    private fun updatedPeakHold(
        previous: LimiterPeakHold,
        newPeakDbfs: Double,
        nowNanos: Long
    ): LimiterPeakHold {
        val decayedPrevious = if (
            previous.timestampNanos <= 0L
        ) {
            LimiterMath.SILENCE_FLOOR_DBFS
        } else {
            (
                previous.peakDbfs -
                    (
                        nowNanos - previous.timestampNanos
                        ).coerceAtLeast(0L) /
                    1_000_000_000.0 *
                    METER_DECAY_DB_PER_SECOND
                ).coerceAtLeast(
                LimiterMath.SILENCE_FLOOR_DBFS
            )
        }
        return LimiterPeakHold(
            peakDbfs = maxOf(newPeakDbfs, decayedPrevious),
            timestampNanos = nowNanos
        )
    }

    private fun recentMaximumGainReductionDb(): Double {
        val hold = limiterMaximumHold
        if (
            hold.timestampNanos <= 0L ||
            System.nanoTime() - hold.timestampNanos >
            MAXIMUM_HOLD_NANOS
        ) {
            return limiterTelemetryExchange
                .snapshot()
                .meter
                .currentGainReductionDb
        }
        return maxOf(
            hold.reductionDb,
            limiterTelemetryExchange
                .snapshot()
                .meter
                .currentGainReductionDb
        )
    }

    private data class LimiterMaximumHold(
        val reductionDb: Double = 0.0,
        val timestampNanos: Long = 0L
    )

    private data class LimiterPeakHold(
        val peakDbfs: Double =
            LimiterMath.SILENCE_FLOOR_DBFS,
        val timestampNanos: Long = 0L
    )

    private companion object {
        const val COORDINATOR_POLL_MILLIS = 20L
        const val NANOS_PER_MILLISECOND = 1_000_000L
        const val MAXIMUM_SYNCHRONOUS_PREPARATION_ATTEMPTS = 3
        const val MAXIMUM_HOLD_NANOS =
            1_500L * NANOS_PER_MILLISECOND
        const val METER_DECAY_DB_PER_SECOND = 18.0
    }
}
