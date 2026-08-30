package io.github.rsgarrido.sazanami.player.equalizer

import kotlin.math.roundToInt

internal class EqualizerTransitionState(
    val durationMillis: Int = DEFAULT_DURATION_MILLIS
) {
    var totalFrameCount: Int = 0
        private set
    var processedFrameCount: Int = 0
        private set

    val isActive: Boolean
        get() = totalFrameCount > 0 &&
            processedFrameCount < totalFrameCount

    init {
        require(durationMillis in MINIMUM_DURATION_MILLIS..MAXIMUM_DURATION_MILLIS) {
            "durationMillis must be between 15 and 30 milliseconds"
        }
    }

    fun start(sampleRateHz: Int) {
        require(sampleRateHz > 0) {
            "sampleRateHz must be greater than 0"
        }
        totalFrameCount = maxOf(
            1,
            (sampleRateHz * durationMillis / 1_000.0).roundToInt()
        )
        processedFrameCount = 0
    }

    fun progressForNextFrame(): Double {
        check(isActive) {
            "transition is not active"
        }
        return (processedFrameCount + 1).toDouble() / totalFrameCount
    }

    fun advanceFrame() {
        check(isActive) {
            "transition is not active"
        }
        processedFrameCount++
    }

    fun cancel() {
        totalFrameCount = 0
        processedFrameCount = 0
    }

    companion object {
        const val DEFAULT_DURATION_MILLIS = 20
        const val MINIMUM_DURATION_MILLIS = 15
        const val MAXIMUM_DURATION_MILLIS = 30
    }
}
