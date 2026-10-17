package io.github.rsgarrido.sazanami.ui.settings

import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.DirectOutputSwitch
import io.github.rsgarrido.sazanami.player.IntegratedDirectPhase
import io.github.rsgarrido.sazanami.player.IntegratedDirectPlaybackState
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticReport
import io.github.rsgarrido.sazanami.player.usb.UsbAudioConfigurationResult
import io.github.rsgarrido.sazanami.player.usb.UsbAudioConfigurationResolution
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticState
import io.github.rsgarrido.sazanami.player.usb.UsbTelemetryCaptureOutcome
import java.nio.file.Files
import java.nio.file.Paths
import org.junit.Assert.*
import org.junit.Test

/** Presentation and report scope only: no Android, USB, or native operations are executed. */
class UsbDiagnosticPresentationTest {
    private fun source(path: String) = listOf(Paths.get("src/main", path), Paths.get("app/src/main", path))
        .first { Files.isRegularFile(it) }.toFile().readText()

    @Test fun liveOwnershipIsActiveWhileOnlyTerminalUnsafeCleanupIsAnError() {
        val usb = UsbAudioDiagnosticState()
        val live = IntegratedDirectPlaybackState(enabled = true, normalReleased = true,
            phase = IntegratedDirectPhase.PLAYING, cleanupSafe = false)
        assertEquals(R.string.usb_diagnostics_output_active, usbDirectDiagnosticStatus(usb, live, true))
        assertEquals(R.string.usb_settings_cleanup_failed,
            usbDirectDiagnosticStatus(usb, live.copy(phase = IntegratedDirectPhase.ERROR), true))
        assertEquals(R.string.usb_settings_returning,
            usbDirectDiagnosticStatus(usb, live.copy(switching = DirectOutputSwitch.TO_ANDROID), false))
        assertEquals(R.string.usb_diagnostics_output_disabled,
            usbDirectDiagnosticStatus(usb, IntegratedDirectPlaybackState(), false))
        assertEquals(R.string.usb_diagnostics_output_unsupported,
            usbDirectDiagnosticStatus(usb, IntegratedDirectPlaybackState(error = "DIRECT_USB_INCOMPATIBLE"), true))
    }

    @Test fun currentTechnicalReportOmitsLegacyExperimentsWithoutRemovingFullEvidence() {
        val current = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState(), includeRaw = false, includeLegacy = false)
        assertTrue(current.contains("Native USB transport diagnostics"))
        assertTrue(current.contains("Capture status: IDLE"))
        assertTrue(current.contains("Connection generation:"))
        assertTrue(current.contains("UAC2 Clock Queries"))
        assertTrue(current.contains("USBFS Ownership Diagnostics"))
        assertFalse(current.contains("Direct USB playback is not implemented"))
        assertFalse(current.contains("Session "))
        assertFalse(current.contains("Forced AudioControl Ownership Experiment"))
        val full = UsbAudioDiagnosticReport.format(UsbAudioDiagnosticState())
        assertTrue(full.contains("Developer / legacy experiments — historical diagnostic scope"))
        for (section in listOf("AudioControl Claim Experiment", "Forced AudioControl Ownership Experiment",
            "USB Configuration Experiment", "Silent Direct USB Transport", "Full FLAC Direct USB Transport")) {
            assertTrue(section, full.contains(section))
            assertFalse(section, current.contains(section))
        }
    }

    @Test fun integratedConfigurationEvidenceUsesCurrentScopeAndRetainsRestorationStatus() {
        val report = UsbAudioDiagnosticReport.formatIntegratedConfiguration(
            UsbAudioConfigurationResult(17, UsbAudioConfigurationResolution()))
        assertTrue(report.contains("Integrated USB configuration and resource release"))
        assertTrue(report.contains("Generation: 17"))
        assertTrue(report.contains("Configuration restoration: NOT_NEEDED"))
        assertFalse(report.contains("Session "))
        assertFalse(report.contains("Bit-perfect active: NO"))
    }

    @Test fun captureMessagesAreExplicitAndDifferentFromCopyConfirmation() {
        assertEquals(R.string.usb_diagnostics_capture_success, usbTelemetryCaptureMessage(UsbTelemetryCaptureOutcome.CAPTURED))
        assertEquals(R.string.usb_diagnostics_capture_historical, usbTelemetryCaptureMessage(UsbTelemetryCaptureOutcome.HISTORICAL))
        assertEquals(R.string.usb_diagnostics_capture_unavailable, usbTelemetryCaptureMessage(UsbTelemetryCaptureOutcome.UNAVAILABLE))
        assertEquals(R.string.usb_diagnostics_capture_failed, usbTelemetryCaptureMessage(UsbTelemetryCaptureOutcome.FAILED))
        assertEquals(4, UsbTelemetryCaptureOutcome.entries.map(::usbTelemetryCaptureMessage).toSet().size)
        assertFalse(UsbTelemetryCaptureOutcome.entries.map(::usbTelemetryCaptureMessage).contains(R.string.usb_diagnostics_copied))
    }

    @Test fun normalViewIsCompactAndOnlyDeveloperLegacyActionsCanMutateUsb() {
        val section = source("java/io/github/rsgarrido/sazanami/ui/settings/UsbAudioDiagnosticsSection.kt")
        val current = section.substringBefore("private fun LegacyUsbAudioDiagnosticsSection()")
        assertTrue(current.contains("var advanced by remember { mutableStateOf(false) }"))
        assertTrue(current.contains("var legacy by remember { mutableStateOf(false) }"))
        assertTrue(current.contains("if (BuildConfig.DEBUG)"))
        assertTrue(current.contains("if (legacy)"))
        assertTrue(current.contains("liveRegion = LiveRegionMode.Polite"))
        assertTrue(current.contains("captureResult().outcome"))
        assertTrue(current.contains("includeLegacy = false"))
        for (operation in listOf("LaunchedEffect(", "::refresh", "::select", "requestPermission(", "queryClockCapabilities(",
            "forceOwnAudioControl(", "configurePcm16(", "streamSilentPcm16(", "playReferencePcm16(")) {
            assertFalse(operation, current.contains(operation))
        }
        val screen = source("java/io/github/rsgarrido/sazanami/ui/settings/DiagnosticsScreen.kt")
        assertTrue(screen.contains("UsbAudioDiagnosticsSection()"))
        assertFalse(screen.contains("IntegratedDirectPlaybackSection()"))
    }
}
