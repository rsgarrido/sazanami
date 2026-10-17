package io.github.rsgarrido.sazanami.player.nativeaudio

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNull
import io.github.rsgarrido.sazanami.player.usb.UsbFsDiagnostics
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NativeAudioBridgeTest {
    @Test
    fun nativeLibraryLoadsAndMatchesExpectedApi() {
        assertTrue(NativeAudioBridge.isAvailable)
    }

    @Test
    fun liveUsbDiagnosticJniRejectsAbsentSessionsWithoutCreatingResources() {
        assertTrue(UsbFsDiagnostics.silentTransportIdle()) // Loads the actual USB JNI boundary.
        repeat(20) { generation ->
            assertNull(UsbFsDiagnostics.liveDiagnostics(Long.MAX_VALUE, generation + 1L, generation + 2L))
        }
        assertTrue(UsbFsDiagnostics.silentTransportIdle())
    }
}
