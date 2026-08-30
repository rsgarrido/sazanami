package io.github.rsgarrido.sazanami.player.equalizer.limiter

internal data class LimitedSignal(
    val samples: FloatArray,
    val firstCallProducedFrames: Int,
    val totalProducedFrames: Int,
    val meter: LimiterMeterSnapshot,
    val engine: LookaheadLimiterEngine
)

internal fun limitSignal(
    input: FloatArray,
    sampleRateHz: Int = 48_000,
    channelCount: Int = 1,
    ceilingDbfs: Double = -1.0
): LimitedSignal {
    val telemetry = LimiterTelemetryAccumulator()
    val prepared = LimiterPreparedConfiguration.prepare(
        configuration = LimiterConfiguration(
            enabled = true,
            ceilingDbfs = ceilingDbfs
        ),
        sampleRateHz = sampleRateHz,
        channelCount = channelCount,
        configurationVersion = 1L
    )
    val engine = LookaheadLimiterEngine(prepared, telemetry)
    val frameCount = input.size / channelCount
    val output = FloatArray(input.size)
    telemetry.beginProcessingCall()
    val firstFrames = engine.process(
        input = input,
        inputOffset = 0,
        frameCount = frameCount,
        output = output,
        outputOffset = 0
    )
    var totalFrames = firstFrames
    while (!engine.isDrained) {
        totalFrames += engine.drain(
            output = output,
            outputOffset = totalFrames * channelCount,
            maximumFrameCount = frameCount - totalFrames
        )
    }
    return LimitedSignal(
        samples = output,
        firstCallProducedFrames = firstFrames,
        totalProducedFrames = totalFrames,
        meter = telemetry.snapshot(),
        engine = engine
    )
}
