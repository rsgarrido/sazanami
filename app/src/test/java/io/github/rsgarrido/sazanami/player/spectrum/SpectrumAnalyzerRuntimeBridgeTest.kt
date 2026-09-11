package io.github.rsgarrido.sazanami.player.spectrum

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumAnalyzerRuntimeBridgeTest {
    @Test
    fun `consumer demand remains active until the final registration closes`() {
        assertEquals(0, SpectrumAnalyzerRuntimeBridge.consumerCountForTest())
        assertFalse(SpectrumAnalyzerRuntimeBridge.hasActiveConsumers.value)
        val first = SpectrumAnalyzerRuntimeBridge.acquireConsumer()
        val second = SpectrumAnalyzerRuntimeBridge.acquireConsumer()

        try {
            assertEquals(2, SpectrumAnalyzerRuntimeBridge.consumerCountForTest())
            assertTrue(SpectrumAnalyzerRuntimeBridge.hasActiveConsumers.value)

            first.close()
            assertEquals(1, SpectrumAnalyzerRuntimeBridge.consumerCountForTest())
            assertTrue(SpectrumAnalyzerRuntimeBridge.hasActiveConsumers.value)

            first.close()
            assertEquals(1, SpectrumAnalyzerRuntimeBridge.consumerCountForTest())
            assertTrue(SpectrumAnalyzerRuntimeBridge.hasActiveConsumers.value)
        } finally {
            first.close()
            second.close()
        }

        assertEquals(0, SpectrumAnalyzerRuntimeBridge.consumerCountForTest())
        assertFalse(SpectrumAnalyzerRuntimeBridge.hasActiveConsumers.value)
    }
}
