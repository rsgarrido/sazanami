package io.github.rsgarrido.sazanami.player.spectrum

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong
import java.util.concurrent.atomic.AtomicReference
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** Read-only callback invoked from Media3's PCM16 processor boundary. */
internal interface Pcm16Observer {
    fun onPcm16(
        source: ByteBuffer,
        offsetBytes: Int,
        byteCount: Int,
        sampleRateHz: Int,
        channelCount: Int
    )

    fun onDiscontinuity()

    fun setSourceActive(active: Boolean)

    fun setPlaybackActive(active: Boolean)

    fun setPcmAvailable(available: Boolean)

    fun close()
}

/**
 * Service-owned coordinator. The audio path only copies into fixed storage; this worker performs
 * downmixing, FFT, smoothing, and immutable frame publication at a bounded cadence.
 */
internal class SpectrumAnalyzerRuntime(
    private val analyzer: SpectrumAnalyzer = SpectrumAnalyzer(),
    private val clockNanos: () -> Long = System::nanoTime,
    private val scheduleWorker: Boolean = true
) : AutoCloseable {
    private val handoff = LatestPcmWindowHandoff(
        fftSize = analyzer.config.fftSize,
        maximumChannelCount = MAXIMUM_CHANNEL_COUNT
    )
    private val activeSourceId = AtomicLong(NO_SOURCE)
    private val sourceReady = AtomicBoolean(false)
    private val resetVersion = AtomicLong(0L)
    private val nextSourceId = AtomicLong(0L)
    private val consumerCount = AtomicInteger(0)
    private val publisher = AtomicReference<((SpectrumFrame) -> Unit)?>(null)
    private val closed = AtomicBoolean(false)
    private val workerLock = Any()
    private val worker = Executors.newSingleThreadScheduledExecutor { task ->
        Thread(task, "SazanamiSpectrumAnalyzer").apply {
            isDaemon = true
            priority = (Thread.NORM_PRIORITY - 1).coerceAtLeast(Thread.MIN_PRIORITY)
        }
    }

    private var workerTask: ScheduledFuture<*>? = null
    private var observedResetVersion = 0L
    private var lastPcmNanos = 0L
    private var unavailablePublished = true

    fun createPcmObserver(): Pcm16Observer = SpectrumPcmObservationTap(
        sourceId = nextSourceId.incrementAndGet(),
        runtime = this,
        fftSize = analyzer.config.fftSize,
        maximumChannelCount = MAXIMUM_CHANNEL_COUNT
    )

    fun setPublisher(framePublisher: ((SpectrumFrame) -> Unit)?) {
        publisher.set(framePublisher)
    }

    fun setConsumerCount(count: Int) {
        if (closed.get()) return
        val boundedCount = count.coerceAtLeast(0)
        val previous = consumerCount.getAndSet(boundedCount)
        if (previous == boundedCount) return
        reconcileWorker()
    }

    internal fun isObservationEnabled(sourceId: Long): Boolean =
        !closed.get() && consumerCount.get() > 0 && sourceReady.get() &&
            activeSourceId.get() == sourceId

    internal fun selectSource(sourceId: Long, active: Boolean) {
        if (active) {
            if (activeSourceId.getAndSet(sourceId) != sourceId) {
                sourceReady.set(false)
                requestReset()
                reconcileWorker()
            }
        } else if (activeSourceId.compareAndSet(sourceId, NO_SOURCE)) {
            sourceReady.set(false)
            requestReset()
            reconcileWorker()
        }
    }

    internal fun setSourceReady(sourceId: Long, ready: Boolean) {
        if (activeSourceId.get() != sourceId) return
        if (sourceReady.getAndSet(ready) == ready) return
        if (!ready) requestResetForSource(sourceId)
        reconcileWorker()
    }

    internal fun offer(
        sourceId: Long,
        samples: ShortArray,
        frameCount: Int,
        sampleRateHz: Int,
        channelCount: Int
    ): Boolean {
        if (!isObservationEnabled(sourceId)) return false
        return handoff.offer(
            sourceId = sourceId,
            resetVersion = resetVersion.get(),
            samples = samples,
            frameCount = frameCount,
            sampleRateHz = sampleRateHz,
            channelCount = channelCount
        )
    }

    internal fun requestReset() {
        resetVersion.incrementAndGet()
        handoff.discardReady()
    }

    internal fun requestResetForSource(sourceId: Long) {
        if (activeSourceId.get() != sourceId) return
        requestReset()
    }

    internal fun processLatestForTest(nowNanos: Long = clockNanos()): SpectrumFrame? =
        processLatest(nowNanos)

    internal fun droppedWindowCountForTest(): Long = handoff.droppedWindowCount()

    override fun close() {
        if (!closed.compareAndSet(false, true)) return
        synchronized(workerLock) {
            workerTask?.cancel(false)
            workerTask = null
        }
        consumerCount.set(0)
        activeSourceId.set(NO_SOURCE)
        sourceReady.set(false)
        handoff.discardReady()
        publisher.set(null)
        worker.shutdownNow()
    }

    private fun startWorker() {
        synchronized(workerLock) {
            if (closed.get() || workerTask != null) return
            val periodMillis = (1_000L / analyzer.config.updateCadenceHz).coerceAtLeast(1L)
            workerTask = worker.scheduleAtFixedRate(
                { processLatest(clockNanos()) },
                0L,
                periodMillis,
                TimeUnit.MILLISECONDS
            )
        }
    }

    private fun reconcileWorker() {
        val shouldRun = scheduleWorker && !closed.get() &&
            consumerCount.get() > 0 && sourceReady.get() &&
            activeSourceId.get() != NO_SOURCE
        if (shouldRun) startWorker() else stopWorkerAndReset()
    }

    private fun stopWorkerAndReset() {
        val stopped = synchronized(workerLock) {
            val task = workerTask ?: return@synchronized false
            task.cancel(false)
            workerTask = null
            true
        }
        if (!stopped) return
        requestReset()
        lastPcmNanos = 0L
        unavailablePublished = true
        publisher.get()?.invoke(
            SpectrumFrame.unavailable(
                bandCount = analyzer.config.bandCount,
                timestampNanos = clockNanos()
            )
        )
    }

    private fun processLatest(nowNanos: Long): SpectrumFrame? {
        if (closed.get() || consumerCount.get() <= 0) return null
        val resetAtStart = resetVersion.get()
        if (resetAtStart != observedResetVersion) {
            observedResetVersion = resetAtStart
            lastPcmNanos = 0L
            unavailablePublished = true
            val resetFrame = analyzer.reset(nowNanos)
            publisher.get()?.invoke(resetFrame)
        }

        val sourceId = activeSourceId.get()
        val slot = handoff.takeLatest(sourceId, resetAtStart)
        if (slot != null) {
            val processedSourceId = slot.sourceId
            val frame = try {
                analyzer.analyzeInterleavedPcm16(
                    samples = slot.samples,
                    frameCount = slot.frameCount,
                    sampleRateHz = slot.sampleRateHz,
                    channelCount = slot.channelCount,
                    timestampNanos = nowNanos
                )
            } finally {
                handoff.release(slot)
            }
            if (
                resetVersion.get() == resetAtStart &&
                activeSourceId.get() == processedSourceId &&
                consumerCount.get() > 0
            ) {
                lastPcmNanos = nowNanos
                unavailablePublished = false
                publisher.get()?.invoke(frame)
                return frame
            }
            return null
        }

        if (
            !unavailablePublished &&
            (lastPcmNanos == 0L || nowNanos - lastPcmNanos >= PCM_TIMEOUT_NANOS)
        ) {
            unavailablePublished = true
            val frame = analyzer.reset(nowNanos)
            publisher.get()?.invoke(frame)
            return frame
        }
        return null
    }

    private companion object {
        const val MAXIMUM_CHANNEL_COUNT = 8
        const val NO_SOURCE = -1L
        const val PCM_TIMEOUT_NANOS = 250_000_000L
    }
}

/** Process-local future-consumer facade; no theme registers during this session. */
internal object SpectrumAnalyzerRuntimeBridge {
    private val lock = Any()
    private val _state = MutableStateFlow(SpectrumFrame.unavailable())
    val state: StateFlow<SpectrumFrame> = _state.asStateFlow()

    private var runtime: SpectrumAnalyzerRuntime? = null
    private var consumerCount = 0

    fun attach(serviceRuntime: SpectrumAnalyzerRuntime) {
        synchronized(lock) {
            if (runtime !== serviceRuntime) {
                runtime?.setConsumerCount(0)
                runtime?.setPublisher(null)
            }
            runtime = serviceRuntime
            serviceRuntime.setPublisher { frame -> _state.value = frame }
            serviceRuntime.setConsumerCount(consumerCount)
        }
    }

    fun detach(serviceRuntime: SpectrumAnalyzerRuntime) {
        synchronized(lock) {
            if (runtime !== serviceRuntime) return
            serviceRuntime.setConsumerCount(0)
            serviceRuntime.setPublisher(null)
            runtime = null
            _state.value = SpectrumFrame.unavailable()
        }
    }

    fun acquireConsumer(): AutoCloseable {
        synchronized(lock) {
            consumerCount++
            runtime?.setConsumerCount(consumerCount)
        }
        return ConsumerRegistration()
    }

    internal fun consumerCountForTest(): Int = synchronized(lock) { consumerCount }

    private class ConsumerRegistration : AutoCloseable {
        private val closed = AtomicBoolean(false)

        override fun close() {
            if (!closed.compareAndSet(false, true)) return
            synchronized(lock) {
                consumerCount = (consumerCount - 1).coerceAtLeast(0)
                runtime?.setConsumerCount(consumerCount)
                if (consumerCount == 0) {
                    _state.value = SpectrumFrame.unavailable()
                }
            }
        }
    }
}

internal class SpectrumPcmObservationTap(
    private val sourceId: Long,
    private val runtime: SpectrumAnalyzerRuntime,
    private val fftSize: Int,
    private val maximumChannelCount: Int
) : Pcm16Observer {
    private val staging = ShortArray(fftSize * maximumChannelCount)

    @Volatile private var sourceActive = false
    @Volatile private var playbackActive = false
    @Volatile private var pcmAvailable = true
    @Volatile private var closed = false

    private var stagedFrameCount = 0
    private var stagedSampleRateHz = 0
    private var stagedChannelCount = 0

    override fun onPcm16(
        source: ByteBuffer,
        offsetBytes: Int,
        byteCount: Int,
        sampleRateHz: Int,
        channelCount: Int
    ) {
        if (
            closed || !sourceActive || !playbackActive || !pcmAvailable ||
            !runtime.isObservationEnabled(sourceId)
        ) {
            return
        }
        if (
            sampleRateHz <= 0 || channelCount !in 1..maximumChannelCount ||
            offsetBytes < 0 || byteCount <= 0 ||
            offsetBytes > source.limit() || byteCount > source.limit() - offsetBytes ||
            source.order() != ByteOrder.nativeOrder()
        ) {
            return
        }
        val frameSizeBytes = channelCount * Short.SIZE_BYTES
        if (byteCount % frameSizeBytes != 0) return
        val incomingFrameCount = byteCount / frameSizeBytes
        if (incomingFrameCount <= 0) return

        if (
            stagedSampleRateHz != sampleRateHz ||
            stagedChannelCount != channelCount
        ) {
            stagedFrameCount = 0
            stagedSampleRateHz = sampleRateHz
            stagedChannelCount = channelCount
            runtime.requestResetForSource(sourceId)
        }

        if (incomingFrameCount >= fftSize) {
            val firstFrame = incomingFrameCount - fftSize
            copyFrames(
                source = source,
                sourceOffsetBytes = offsetBytes + firstFrame * frameSizeBytes,
                destinationFrame = 0,
                frameCount = fftSize,
                channelCount = channelCount
            )
            stagedFrameCount = fftSize
        } else {
            val overflowFrames = (stagedFrameCount + incomingFrameCount - fftSize)
                .coerceAtLeast(0)
            if (overflowFrames > 0) {
                val retainedFrames = stagedFrameCount - overflowFrames
                staging.copyInto(
                    destination = staging,
                    destinationOffset = 0,
                    startIndex = overflowFrames * channelCount,
                    endIndex = stagedFrameCount * channelCount
                )
                stagedFrameCount = retainedFrames
            }
            copyFrames(
                source = source,
                sourceOffsetBytes = offsetBytes,
                destinationFrame = stagedFrameCount,
                frameCount = incomingFrameCount,
                channelCount = channelCount
            )
            stagedFrameCount += incomingFrameCount
        }

        if (stagedFrameCount == fftSize) {
            runtime.offer(
                sourceId = sourceId,
                samples = staging,
                frameCount = fftSize,
                sampleRateHz = sampleRateHz,
                channelCount = channelCount
            )
            stagedFrameCount = 0
        }
    }

    override fun onDiscontinuity() {
        stagedFrameCount = 0
        runtime.requestResetForSource(sourceId)
    }

    override fun setSourceActive(active: Boolean) {
        if (sourceActive == active) return
        sourceActive = active
        stagedFrameCount = 0
        runtime.selectSource(sourceId, active)
        runtime.setSourceReady(
            sourceId,
            active && playbackActive && pcmAvailable && !closed
        )
    }

    override fun setPlaybackActive(active: Boolean) {
        if (playbackActive == active) return
        playbackActive = active
        runtime.setSourceReady(
            sourceId,
            sourceActive && active && pcmAvailable && !closed
        )
        if (!active) onDiscontinuity()
    }

    override fun setPcmAvailable(available: Boolean) {
        if (pcmAvailable == available) return
        pcmAvailable = available
        runtime.setSourceReady(
            sourceId,
            sourceActive && playbackActive && available && !closed
        )
        onDiscontinuity()
    }

    override fun close() {
        if (closed) return
        closed = true
        stagedFrameCount = 0
        runtime.selectSource(sourceId, false)
    }

    private fun copyFrames(
        source: ByteBuffer,
        sourceOffsetBytes: Int,
        destinationFrame: Int,
        frameCount: Int,
        channelCount: Int
    ) {
        var sourceByteIndex = sourceOffsetBytes
        var destinationSample = destinationFrame * channelCount
        val sampleCount = frameCount * channelCount
        val destinationEnd = destinationSample + sampleCount
        while (destinationSample < destinationEnd) {
            staging[destinationSample] = source.getShort(sourceByteIndex)
            destinationSample++
            sourceByteIndex += Short.SIZE_BYTES
        }
    }
}

internal class LatestPcmWindowHandoff(
    fftSize: Int,
    maximumChannelCount: Int,
    slotCount: Int = 3
) {
    internal class Slot(sampleCapacity: Int) {
        val samples = ShortArray(sampleCapacity)
        val state = AtomicInteger(FREE)
        @Volatile var sourceId = -1L
        @Volatile var resetVersion = 0L
        @Volatile var sequence = 0L
        @Volatile var frameCount = 0
        @Volatile var sampleRateHz = 0
        @Volatile var channelCount = 0
    }

    private val slots = Array(slotCount.coerceAtLeast(2)) {
        Slot(fftSize * maximumChannelCount)
    }
    private val nextSequence = AtomicLong(0L)
    private val droppedWindows = AtomicLong(0L)

    fun offer(
        sourceId: Long,
        resetVersion: Long,
        samples: ShortArray,
        frameCount: Int,
        sampleRateHz: Int,
        channelCount: Int
    ): Boolean {
        val sampleCount = frameCount * channelCount
        if (
            frameCount <= 0 || sampleRateHz <= 0 || channelCount <= 0 ||
            sampleCount > samples.size || sampleCount > slots[0].samples.size
        ) {
            return false
        }
        val slot = acquireWritableSlot() ?: run {
            droppedWindows.incrementAndGet()
            return false
        }
        samples.copyInto(slot.samples, endIndex = sampleCount)
        slot.sourceId = sourceId
        slot.resetVersion = resetVersion
        slot.frameCount = frameCount
        slot.sampleRateHz = sampleRateHz
        slot.channelCount = channelCount
        slot.sequence = nextSequence.incrementAndGet()
        slot.state.set(READY)
        return true
    }

    fun takeLatest(activeSourceId: Long, activeResetVersion: Long): Slot? {
        var selected: Slot? = null
        for (slot in slots) {
            if (
                slot.state.get() == READY && slot.sourceId == activeSourceId &&
                slot.resetVersion == activeResetVersion &&
                (selected == null || slot.sequence > selected.sequence)
            ) {
                selected = slot
            }
        }
        val claimed = selected?.takeIf { it.state.compareAndSet(READY, READING) }
            ?: return null
        for (slot in slots) {
            if (slot !== claimed && slot.state.compareAndSet(READY, FREE)) {
                droppedWindows.incrementAndGet()
            }
        }
        return claimed
    }

    fun release(slot: Slot) {
        slot.state.compareAndSet(READING, FREE)
    }

    fun discardReady() {
        for (slot in slots) {
            slot.state.compareAndSet(READY, FREE)
        }
    }

    fun droppedWindowCount(): Long = droppedWindows.get()

    private fun acquireWritableSlot(): Slot? {
        for (slot in slots) {
            if (slot.state.compareAndSet(FREE, WRITING)) return slot
        }
        var oldestReady: Slot? = null
        for (slot in slots) {
            if (
                slot.state.get() == READY &&
                (oldestReady == null || slot.sequence < oldestReady.sequence)
            ) {
                oldestReady = slot
            }
        }
        return oldestReady?.takeIf { it.state.compareAndSet(READY, WRITING) }
            ?.also { droppedWindows.incrementAndGet() }
    }

    private companion object {
        const val FREE = 0
        const val WRITING = 1
        const val READY = 2
        const val READING = 3
    }
}
