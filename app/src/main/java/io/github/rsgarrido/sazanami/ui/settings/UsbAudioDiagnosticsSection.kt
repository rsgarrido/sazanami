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
import io.github.rsgarrido.sazanami.player.usb.UsbFsProbeStatus
import io.github.rsgarrido.sazanami.player.usb.UsbForcedPhase
import io.github.rsgarrido.sazanami.player.usb.UsbKernelBindingRecovery
import io.github.rsgarrido.sazanami.player.usb.UsbFrameworkEnumerationRecovery
import io.github.rsgarrido.sazanami.player.usb.UsbConfigurationPhase
import io.github.rsgarrido.sazanami.player.usb.UsbConfigurationRestorationStatus
import io.github.rsgarrido.sazanami.player.usb.usbHex

/** Presentation only. Receivers, connection ownership, parsing and commands live in player/usb. */
@Composable
internal fun UsbAudioDiagnosticsSection() {
    val state by UsbAudioDiagnosticBridge.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val diagnosticsTitle = stringResource(R.string.usb_diagnostics_title)
    var copied by remember(state) { mutableStateOf(false) }
    val parsedReport = remember(state) { UsbAudioDiagnosticReport.format(state, includeRaw = false) }
    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
    Column(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(diagnosticsTitle, style = MaterialTheme.typography.titleMedium)
        Text(stringResource(R.string.usb_diagnostics_note), style = MaterialTheme.typography.bodySmall)
        if (!state.serviceAvailable) {
            Text(stringResource(R.string.usb_diagnostics_service_unavailable))
        }
        OutlinedButton(onClick = UsbAudioDiagnosticBridge::refresh,
            enabled = state.serviceAvailable && state.configurationExperiment?.phase != UsbConfigurationPhase.QUERYING) {
            Text(stringResource(R.string.usb_diagnostics_refresh))
        }
        state.devices.forEach { device ->
            val selected = device.identity == state.selectedIdentity
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.select(device.identity) },
                enabled = state.serviceAvailable && !selected && state.configurationExperiment?.phase != UsbConfigurationPhase.QUERYING,
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
                    !state.diagnosticOperationRunning
            ) { Text(stringResource(R.string.usb_diagnostics_permission)) }
        }
        state.selectedDevice?.let { selected ->
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.queryClockCapabilities(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null &&
                    !state.diagnosticOperationRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.clockDiagnostics?.phase == UsbClockDiagnosticPhase.QUERYING)
                    R.string.usb_diagnostics_querying_clock else R.string.usb_diagnostics_query_clock))
            }
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.testAudioControlClaim(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null &&
                    !state.diagnosticOperationRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.clockDiagnostics?.phase == UsbClockDiagnosticPhase.QUERYING &&
                    state.clockDiagnostics?.claimExperiment != null) R.string.usb_diagnostics_testing_claim
                    else R.string.usb_diagnostics_test_claim))
            }
            Text(stringResource(R.string.usb_diagnostics_claim_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.inspectKernelOwnership(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null && !state.diagnosticOperationRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.usbFsDiagnostics?.status == UsbFsProbeStatus.QUERYING)
                    R.string.usb_diagnostics_inspecting_kernel else R.string.usb_diagnostics_inspect_kernel))
            }
            Text(stringResource(R.string.usb_diagnostics_kernel_note), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.usb_diagnostics_force_note), style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.error)
            // Known manual evidence is visible without asking for another forced run.
            Text(stringResource(R.string.usb_diagnostics_manual_ka15_note), style = MaterialTheme.typography.bodySmall)
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.forceOwnAudioControl(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null &&
                    !state.diagnosticOperationRunning && state.usbFsDiagnostics?.let {
                        it.generation == state.generation && it.status == UsbFsProbeStatus.RECORDED && it.plan.audioControl != null
                    } == true,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.forcedExperiment?.phase == UsbForcedPhase.QUERYING)
                    R.string.usb_diagnostics_forcing else R.string.usb_diagnostics_force))
            }
            Text(stringResource(R.string.usb_diagnostics_configure_note), style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error)
            OutlinedButton(
                onClick = { UsbAudioDiagnosticBridge.configurePcm16(selected.identity) },
                enabled = state.serviceAvailable && selected.hasPermission && state.descriptors != null && !state.diagnosticOperationRunning,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(if (state.configurationExperiment?.phase == UsbConfigurationPhase.QUERYING)
                    R.string.usb_diagnostics_configuring else R.string.usb_diagnostics_configure))
            }
        }
        state.configurationExperiment?.takeIf { it.generation == state.generation && it.phase == UsbConfigurationPhase.FINISHED }?.let { result ->
            Text(stringResource(R.string.usb_diagnostics_configuration_result, result.targetStatus.name),
                style = MaterialTheme.typography.titleSmall)
            result.originalAlternate?.let { query ->
                Text(stringResource(R.string.usb_diagnostics_alternate_probe,
                    query.alternate?.toString() ?: "—", query.descriptorValidation.name,
                    (query.authoritative && result.accessFailure == null).toString()),
                    style = MaterialTheme.typography.bodySmall)
            }
            listOf(R.string.usb_diagnostics_idle_alternate to result.idleMutation,
                R.string.usb_diagnostics_target_alternate to result.targetMutation,
                R.string.usb_diagnostics_restored_alternate to result.restorationMutation).forEach { (label, evidence) ->
                Text(stringResource(label, evidence.mutation?.request?.alternate?.toString() ?: "—",
                    evidence.mutation?.status?.name ?: "—", evidence.corroboration.name),
                    style = MaterialTheme.typography.bodySmall)
            }
            val restorationComplete = result.restorationStatus in setOf(UsbConfigurationRestorationStatus.RESTORED, UsbConfigurationRestorationStatus.NOT_NEEDED)
            Text(stringResource(R.string.usb_diagnostics_restoration_result, result.restorationStatus.name),
                color = if (restorationComplete) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.usb_diagnostics_kernel_result, result.kernelRecovery.name))
            Text(stringResource(R.string.usb_diagnostics_framework_result, result.frameworkRecovery.name))
            Text(stringResource(R.string.usb_diagnostics_functional_not_verified), color = MaterialTheme.colorScheme.error)
            Text(stringResource(R.string.usb_diagnostics_configuration_reconnect),
                style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
        }
        state.forcedExperiment?.takeIf {
            it.generation == state.generation && it.phase == UsbForcedPhase.FINISHED
        }?.let { result ->
            val kernelText = stringResource(when (result.kernelRecovery) {
                UsbKernelBindingRecovery.RESTORED -> R.string.usb_diagnostics_bindings_restored
                UsbKernelBindingRecovery.NOT_RESTORED -> R.string.usb_diagnostics_bindings_not_restored
                UsbKernelBindingRecovery.INDETERMINATE -> R.string.diagnostics_unknown
            })
            val frameworkText = stringResource(when (result.frameworkRecovery) {
                UsbFrameworkEnumerationRecovery.CANDIDATE_PRESENT -> R.string.usb_diagnostics_candidate_present
                UsbFrameworkEnumerationRecovery.CANDIDATE_MISSING -> R.string.usb_diagnostics_candidate_missing
                UsbFrameworkEnumerationRecovery.AMBIGUOUS -> R.string.usb_diagnostics_candidate_ambiguous
                UsbFrameworkEnumerationRecovery.INDETERMINATE -> R.string.diagnostics_unknown
            })
            Text(stringResource(R.string.usb_diagnostics_kernel_result, kernelText))
            Text(stringResource(R.string.usb_diagnostics_framework_result, frameworkText))
            Text(stringResource(R.string.usb_diagnostics_functional_not_verified),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
            if (result.claimReturned == true) {
                Text(stringResource(R.string.usb_diagnostics_post_force_warning),
                    style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.error)
            }
        }
        if (state.forcedExperiment == null || state.forcedExperiment?.generation != state.generation ||
            state.forcedExperiment?.phase == UsbForcedPhase.QUERYING) {
            Text(stringResource(R.string.usb_diagnostics_functional_not_verified),
                style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
        }
        OutlinedButton(
            onClick = {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                clipboard?.setPrimaryClip(ClipData.newPlainText(
                    diagnosticsTitle, UsbAudioDiagnosticReport.format(state)))
                copied = clipboard != null
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(stringResource(R.string.usb_diagnostics_copy)) }
        if (copied) Text(stringResource(R.string.usb_diagnostics_copied), color = MaterialTheme.colorScheme.primary)
        Text(parsedReport, style = MaterialTheme.typography.bodySmall, fontFamily = FontFamily.Monospace)
    }
}
