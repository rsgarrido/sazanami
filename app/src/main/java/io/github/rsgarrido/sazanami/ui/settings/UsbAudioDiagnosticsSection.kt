package io.github.rsgarrido.sazanami.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticBridge
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticReport
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticStatus
import io.github.rsgarrido.sazanami.player.usb.UsbClockDiagnosticPhase
import io.github.rsgarrido.sazanami.player.usb.usbHex

/** Presentation only. Receivers, connection ownership, parsing and commands live in player/usb. */
@Composable
internal fun UsbAudioDiagnosticsSection() {
    val state by UsbAudioDiagnosticBridge.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var copied by remember(state) { mutableStateOf(false) }
    val parsedReport = remember(state) { UsbAudioDiagnosticReport.format(state, includeRaw = false) }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(stringResource(R.string.usb_diagnostics_title), style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.usb_diagnostics_note), style = MaterialTheme.typography.bodySmall)
        if (!state.serviceAvailable) {
            Text(stringResource(R.string.usb_diagnostics_service_unavailable))
        }
        OutlinedButton(onClick = UsbAudioDiagnosticBridge::refresh, enabled = state.serviceAvailable) {
            Text(stringResource(R.string.usb_diagnostics_refresh))
        }
        state.devices.forEach { device ->
            val selected = device.identity == state.selectedIdentity
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.select(device.identity) },
                enabled = state.serviceAvailable && !selected,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text("${device.product ?: stringResource(R.string.usb_diagnostics_unnamed)} " +
                    "[${usbHex(device.identity.vendorId, 4)}:${usbHex(device.identity.productId, 4)}] " +
                    "#${device.identity.deviceId}" + (if (selected) " ✓" else ""))
            }
        }
        state.selectedDevice?.takeIf { !it.hasPermission }?.let { selected ->
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.requestPermission(selected.identity) },
                enabled = state.serviceAvailable && state.status != UsbAudioDiagnosticStatus.REQUESTING_PERMISSION &&
                    state.status != UsbAudioDiagnosticStatus.OPENING
            ) { Text(stringResource(R.string.usb_diagnostics_permission)) }
        }
        state.selectedDevice?.let { selected ->
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.queryClockCapabilities(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null &&
                    state.clockDiagnostics?.phase != UsbClockDiagnosticPhase.QUERYING,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.clockDiagnostics?.phase == UsbClockDiagnosticPhase.QUERYING)
                    R.string.usb_diagnostics_querying_clock else R.string.usb_diagnostics_query_clock))
            }
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(
                    context.getString(R.string.usb_diagnostics_title), UsbAudioDiagnosticReport.format(state)))
                copied = clipboard != null
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.usb_diagnostics_copy)) }
        if (copied) Text(stringResource(R.string.usb_diagnostics_copied), color = MaterialTheme.colorScheme.primary)
        Text(parsedReport, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
