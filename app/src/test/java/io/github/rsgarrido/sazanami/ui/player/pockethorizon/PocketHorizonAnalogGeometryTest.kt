package io.github.rsgarrido.sazanami.ui.player.pockethorizon

import androidx.compose.ui.geometry.Offset
import org.junit.Assert.*
import org.junit.Test

class PocketHorizonAnalogGeometryTest {
    @Test fun circularTravelPreservesDirectionAndDeadZoneSuppressesJitter() {
        for (x in -4..4) for (y in -4..4) {
            val input = Offset(x.toFloat(), y.toFloat())
            val result = clampHorizonAnalog(input)
            assertTrue(result.getDistance() <= 1.0001f)
            if (input.getDistance() > 1f) {
                assertEquals(1f, result.getDistance(), .0001f)
                assertEquals(0f, input.x * result.y - input.y * result.x, .0001f)
            }
        }
        assertEquals(0, horizonAnalogDirection(Offset(.1f, .1f)))
        assertEquals(2, horizonAnalogDirection(Offset(.9f, .2f)))
        assertEquals(-2, horizonAnalogDirection(Offset(-.9f, .2f)))
        assertEquals(1, horizonAnalogDirection(Offset(.2f, .9f)))
        assertEquals(-1, horizonAnalogDirection(Offset(.2f, -.9f)))
        assertEquals(Offset.Zero, clampHorizonAnalog(Offset(Float.NaN, 0f)))
    }
}
