package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.DirectOutputSwitch
import io.github.rsgarrido.sazanami.player.IntegratedDirectPhase
import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.usb.*
import org.junit.Assert.*
import org.junit.Test

/** Immutable presentation inputs only; USB ownership and activation policy are not UI progress. */
class UsbAudioSettingsProgressTest {
    private val identity = UsbAudioDeviceIdentity("test", 1, 2, 3)
    private val device = UsbAudioDeviceSnapshot(identity, null, "DAC", 0,
        listOf(UsbAndroidInterfaceSnapshot(1, 0, 1, 1, 32, emptyList())), true)
    private val ready = UsbAudioDiagnosticState(serviceAvailable = true, generation = 7,
        status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ, devices = listOf(device), selectedIdentity = identity)
    private val android = IntegratedDirectPlaybackState(serviceAvailable = true)
    private val direct = android.copy(enabled = true, normalReleased = true)
    private fun stream(phase: UsbSilentPhase) = UsbSilentTransportExperimentResult(7, 11,
        UsbSilentTransportResolution(), phase = phase, mode = UsbPcmDiagnosticMode.FLAC_FULL)
    private fun progress(usb: UsbAudioDiagnosticState, output: IntegratedDirectPlaybackState = android,
        enabled: Boolean = true) = usbAudioSettingsProgressText(usb, output, enabled)

    @Test fun permissionInspectionAndNativePreparationHaveDistinctPendingStates() {
        assertEquals(R.string.usb_settings_requesting, progress(ready.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION)))
        assertEquals(R.string.usb_settings_inspecting, progress(ready.copy(status = UsbAudioDiagnosticStatus.OPENING)))
        for (phase in listOf(UsbSilentPhase.PRIMING_SOURCE, UsbSilentPhase.CONFIGURING, UsbSilentPhase.STARTING_TRANSPORT)) {
            assertEquals(R.string.usb_settings_preparing,
                progress(ready.copy(integratedFlacUsbExperiment = stream(phase)), direct.copy(phase = IntegratedDirectPhase.PREPARING)))
        }
        assertNull(progress(ready))
    }

    @Test fun configuredPlaybackStopsProgressWithoutWeakeningTheOwnershipGate() {
        val usb = ready.copy(integratedFlacUsbExperiment = stream(UsbSilentPhase.STARTING_TRANSPORT))
        assertEquals(R.string.usb_settings_preparing, progress(usb, direct.copy(phase = IntegratedDirectPhase.PREPARING)))
        for (phase in listOf(IntegratedDirectPhase.PLAYING, IntegratedDirectPhase.PAUSED,
            IntegratedDirectPhase.PAUSING, IntegratedDirectPhase.RESUMING, IntegratedDirectPhase.SEEKING)) {
            assertNull(phase.name, progress(usb, direct.copy(phase = phase, sampleRateHz = 44100)))
        }
        assertTrue(usb.diagnosticOperationRunning)
        assertEquals("DIRECT_USB_OPERATION_RUNNING", usb.activationFailure(identity, 7))
        assertEquals(UsbSilentPhase.STARTING_TRANSPORT, usb.integratedFlacUsbExperiment?.phase)
    }

    @Test fun terminalOrIdleOutputDoesNotReviveRetainedIntegratedSetup() {
        for (retained in listOf(UsbSilentPhase.PRIMING_SOURCE, UsbSilentPhase.CONFIGURING,
            UsbSilentPhase.STARTING_TRANSPORT, UsbSilentPhase.RESTORING_USB, UsbSilentPhase.FINISHED)) {
            val usb = ready.copy(integratedFlacUsbExperiment = stream(retained))
            for (phase in listOf(IntegratedDirectPhase.IDLE, IntegratedDirectPhase.STOPPED,
                IntegratedDirectPhase.ENDED, IntegratedDirectPhase.ERROR)) {
                assertNull("$retained / $phase", progress(usb, direct.copy(phase = phase)))
            }
            assertNull(progress(usb, direct.copy(phase = IntegratedDirectPhase.PREPARING, error = "UNSUPPORTED_TRACK")))
        }
    }

    @Test fun denialFailureRemovalAndServiceLossEndChecking() {
        val staleQuery = UsbAudioClockDiagnostics(7, UsbAudioClockResolution(), UsbClockDiagnosticPhase.QUERYING)
        for (status in listOf(UsbAudioDiagnosticStatus.PERMISSION_DENIED, UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
            UsbAudioDiagnosticStatus.NO_DEVICE, UsbAudioDiagnosticStatus.SELECT_DEVICE,
            UsbAudioDiagnosticStatus.ERROR, UsbAudioDiagnosticStatus.SERVICE_UNAVAILABLE)) {
            assertNull(status.name, progress(ready.copy(status = status, clockDiagnostics = staleQuery)))
        }
        assertNull(progress(ready.copy(status = UsbAudioDiagnosticStatus.OPENING, devices = emptyList(), selectedIdentity = null)))
        assertNull(progress(ready.copy(status = UsbAudioDiagnosticStatus.OPENING, serviceAvailable = false)))
        assertNull(progress(ready.copy(status = UsbAudioDiagnosticStatus.OPENING), android.copy(serviceAvailable = false)))
    }

    @Test fun repeatedChecksUseCurrentGenerationAndKeepSeparateInspectionHonest() {
        val oldQuery = UsbAudioClockDiagnostics(7, UsbAudioClockResolution(), UsbClockDiagnosticPhase.QUERYING)
        assertEquals(R.string.usb_settings_inspecting, progress(ready.copy(clockDiagnostics = oldQuery)))
        assertNull(progress(ready.copy(clockDiagnostics = oldQuery.copy(phase = UsbClockDiagnosticPhase.COMPLETE))))
        val refreshed = ready.copy(generation = 8, status = UsbAudioDiagnosticStatus.OPENING)
        assertEquals(R.string.usb_settings_inspecting, progress(refreshed))
        // A retained old pending query cannot restart progress after the refreshed read completes.
        val completed = refreshed.copy(status = UsbAudioDiagnosticStatus.DESCRIPTORS_READ, clockDiagnostics = oldQuery)
        assertNull(progress(completed))
        val playing = direct.copy(phase = IntegratedDirectPhase.PLAYING)
        assertEquals(R.string.usb_settings_inspecting,
            progress(completed.copy(clockDiagnostics = oldQuery.copy(generation = 8)), playing))
        assertNull(progress(completed.copy(clockDiagnostics = oldQuery.copy(generation = 8,
            phase = UsbClockDiagnosticPhase.PARTIAL)), playing))
    }

    @Test fun optOutEndsInspectionButPreservesHonestOutputCleanupProgress() {
        assertNull(progress(ready.copy(status = UsbAudioDiagnosticStatus.OPENING), enabled = false))
        assertNull(progress(ready.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION), enabled = false))
        val playing = direct.copy(phase = IntegratedDirectPhase.PLAYING)
        assertNull(progress(ready.copy(integratedFlacUsbExperiment = stream(UsbSilentPhase.STARTING_TRANSPORT)), playing, false))
        assertEquals(R.string.usb_settings_returning,
            progress(ready.copy(devices = emptyList()), playing.copy(switching = DirectOutputSwitch.TO_ANDROID), false))
        assertNull(progress(ready, direct.copy(phase = IntegratedDirectPhase.ERROR,
            cleanupSafe = false, error = "DIRECT_RESOURCE_CLEANUP_UNVERIFIED"), false))
        assertEquals(R.string.usb_settings_activating, progress(ready, android.copy(switching = DirectOutputSwitch.TO_USB)))
    }

    @Test fun standaloneConfigurationHasProgressOnlyForItsCurrentPendingTransaction() {
        val configuration = UsbAudioConfigurationResult(7, UsbAudioConfigurationResolution(), phase = UsbConfigurationPhase.QUERYING)
        assertEquals(R.string.usb_settings_inspecting, progress(ready.copy(configurationExperiment = configuration)))
        assertNull(progress(ready.copy(configurationExperiment = configuration.copy(phase = UsbConfigurationPhase.FINISHED))))
        assertNull(progress(ready.copy(configurationExperiment = configuration.copy(generation = 6))))
    }
}
