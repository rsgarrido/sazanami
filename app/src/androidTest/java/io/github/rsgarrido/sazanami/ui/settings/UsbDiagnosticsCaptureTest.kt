package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.usb.UsbTelemetrySession
import io.github.rsgarrido.sazanami.player.usb.UsbTransportTelemetryBridge
import org.junit.After
import org.junit.Rule
import org.junit.Test

/** Uses an unbound telemetry session: opening or capturing diagnostics cannot touch USB hardware. */
class UsbDiagnosticsCaptureTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val session = UsbTelemetrySession(Long.MAX_VALUE, Long.MAX_VALUE)
    private fun text(id: Int) = compose.activity.getString(id)

    @After fun finishUnboundSession() { UsbTransportTelemetryBridge.store.finish(session) }

    @Test fun detailsStartCollapsedAndCaptureAcknowledgesUnavailableEvidence() {
        UsbTransportTelemetryBridge.store.begin(session)
        compose.setContent {
            MaterialTheme {
                Column(Modifier.verticalScroll(rememberScrollState())) { UsbAudioDiagnosticsSection() }
            }
        }
        compose.onNodeWithText(text(R.string.usb_diagnostics_current_title)).assertIsDisplayed()
        compose.onNodeWithText(text(R.string.usb_diagnostics_capture_live)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_diagnostics_legacy_show)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_diagnostics_advanced)).performClick()
        compose.onNodeWithText(text(R.string.usb_diagnostics_capture_live)).performScrollTo().performClick()
        compose.waitUntil(5_000) {
            compose.onAllNodesWithText(text(R.string.usb_diagnostics_capture_unavailable)).fetchSemanticsNodes().isNotEmpty()
        }
        compose.onNodeWithText(text(R.string.usb_diagnostics_capture_unavailable)).performScrollTo().assertIsDisplayed()
            .assert(SemanticsMatcher.expectValue(SemanticsProperties.LiveRegion, LiveRegionMode.Polite))
        compose.onNodeWithText(text(R.string.usb_diagnostics_capture_success)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_diagnostics_copied)).assertDoesNotExist()
        compose.onNodeWithText(text(R.string.usb_diagnostics_force)).assertDoesNotExist()
    }
}
