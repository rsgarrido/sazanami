package io.github.rsgarrido.sazanami.player.equalizer.limiter

import io.github.rsgarrido.sazanami.player.equalizer.performance.HotSpotThreadAllocationReader
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeNotNull
import org.junit.Assume.assumeTrue
import org.junit.Test

class LimiterTelemetryPublicationBenchmarkTest {
    @Test
    fun exchangeRemovesTheFormerPerPublicationSnapshotAllocation() {
        assumeTrue(
            "Run with -Dequalizer.performance=true",
            java.lang.Boolean.getBoolean("equalizer.performance")
        )
        val reader = HotSpotThreadAllocationReader.create()
        assumeNotNull(reader)
        val allocationReader = checkNotNull(reader)
        val telemetry = LimiterTelemetryAccumulator().apply {
            beginProcessingCall()
            observePreLimiterSample(1.2f)
            observePostLimiterSample(0.8f)
            observeLimiterFrame(0.75)
        }
        val exchange = LimiterTelemetryExchange()
        repeat(WARM_UP_COUNT) {
            telemetry.snapshot()
            telemetry.publishTo(exchange)
        }

        val snapshotBefore =
            allocationReader.currentThreadBytes()
        repeat(MEASURED_COUNT) {
            telemetry.snapshot()
        }
        val snapshotAfter =
            allocationReader.currentThreadBytes()
        val snapshotBytesPerPublication =
            (snapshotAfter - snapshotBefore).toDouble() /
                MEASURED_COUNT

        val exchangeBefore =
            allocationReader.currentThreadBytes()
        repeat(MEASURED_COUNT) {
            telemetry.publishTo(exchange)
        }
        val exchangeAfter =
            allocationReader.currentThreadBytes()
        val exchangeBytesPerPublication =
            (exchangeAfter - exchangeBefore).toDouble() /
                MEASURED_COUNT

        println(
            "PHASE_F_TELEMETRY_ALLOCATION " +
                "former_snapshot_bytes_per_publication=" +
                snapshotBytesPerPublication +
                " exchange_bytes_per_publication=" +
                exchangeBytesPerPublication
        )
        assertTrue(snapshotBytesPerPublication >= 32.0)
        assertTrue(exchangeBytesPerPublication <= 1.0)
    }

    private companion object {
        const val WARM_UP_COUNT = 1_000
        const val MEASURED_COUNT = 20_000
    }
}
