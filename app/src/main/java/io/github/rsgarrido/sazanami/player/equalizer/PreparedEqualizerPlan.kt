package io.github.rsgarrido.sazanami.player.equalizer

import io.github.rsgarrido.sazanami.player.equalizer.dsp.AutomaticHeadroomResult
import io.github.rsgarrido.sazanami.player.equalizer.dsp.EqualizerEngineCapacity
import io.github.rsgarrido.sazanami.player.equalizer.dsp.KotlinEqualizerDspEngine
import io.github.rsgarrido.sazanami.player.equalizer.dsp.PreparedEqualizerCascade
import java.util.Collections

internal enum class IgnoredEqualizerFilterReason {
    INVALID_FREQUENCY,
    AT_OR_ABOVE_NYQUIST,
    INVALID_PARAMETERS
}
internal data class IgnoredEqualizerFilter(
    val sourceIndex: Int,
    val frequencyHz: Double,
    val reason: IgnoredEqualizerFilterReason
)

internal class PreparedEqualizerPlan(
    val sourceSnapshotVersion: Long,
    val sourceMode: EqualizerMode,
    val processorFormat: EqualizerProcessorFormat,
    val cascade: PreparedEqualizerCascade,
    val automaticHeadroomResult: AutomaticHeadroomResult,
    val validFilterCount: Int,
    ignoredFilters: List<IgnoredEqualizerFilter>,
    val bypassed: Boolean
) {
    val ignoredFilters: List<IgnoredEqualizerFilter> =
        Collections.unmodifiableList(ignoredFilters.toList())

    init {
        require(sourceSnapshotVersion >= 0L) {
            "sourceSnapshotVersion must be non-negative"
        }
        require(cascade.sampleRateHz == processorFormat.sampleRateHz) {
            "cascade sample rate must match processor format"
        }
        require(cascade.channelCount == processorFormat.channelCount) {
            "cascade channel count must match processor format"
        }
        require(validFilterCount == cascade.sectionCount) {
            "validFilterCount must match cascade section count"
        }
    }

    fun createProcessingPath(): PreparedEqualizerProcessingPath {
        val engine = if (bypassed) {
            null
        } else {
            KotlinEqualizerDspEngine().also { configuredEngine ->
                configuredEngine.configure(
                    preparedCascade = cascade,
                    minimumSectionCapacity = MINIMUM_REALTIME_SECTION_CAPACITY,
                    minimumChannelCapacity = MINIMUM_REALTIME_CHANNEL_CAPACITY
                )
            }
        }
        return PreparedEqualizerProcessingPath(
            plan = this,
            engine = engine
        )
    }

    companion object {
        const val MINIMUM_REALTIME_SECTION_CAPACITY = 10
        const val MINIMUM_REALTIME_CHANNEL_CAPACITY = 8
    }
}

internal class PreparedEqualizerProcessingPath(
    val plan: PreparedEqualizerPlan,
    private val engine: KotlinEqualizerDspEngine?
) {
    val bypassed: Boolean
        get() = plan.bypassed

    fun process(
        input: FloatArray,
        output: FloatArray,
        frameCount: Int
    ) {
        check(!bypassed) {
            "Bypassed plans must be copied without float processing"
        }
        checkNotNull(engine).processInterleaved(
            input = input,
            inputOffset = 0,
            output = output,
            outputOffset = 0,
            frameCount = frameCount
        )
    }

    fun reset() {
        engine?.reset()
    }

    fun capacitySnapshot(): EqualizerEngineCapacity? {
        return engine?.capacitySnapshot()
    }
}
