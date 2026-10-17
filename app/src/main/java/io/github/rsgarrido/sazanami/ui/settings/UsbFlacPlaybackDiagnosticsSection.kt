package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticBridge
import io.github.rsgarrido.sazanami.player.usb.UsbAudioDiagnosticState
import io.github.rsgarrido.sazanami.player.usb.UsbAudioInspectionToken
import io.github.rsgarrido.sazanami.player.usb.UsbSilentPhase
import io.github.rsgarrido.sazanami.player.usb.pcmHashHex

/** Explicit picker interaction is the only start path. No position, gain or normal-player controls. */
@Composable internal fun UsbFlacPlaybackDiagnosticsSection(state: UsbAudioDiagnosticState) {
    if (!BuildConfig.DEBUG) return
    var request by remember { mutableStateOf<UsbAudioInspectionToken?>(null) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token = request
        request = null
        if (uri != null && token != null) UsbAudioDiagnosticBridge.playFlacPrefix(token.identity, token.generation, uri)
    }
    Text(stringResource(R.string.usb_flac_title), style = MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.usb_flac_warning), color = MaterialTheme.colorScheme.error)
    OutlinedButton(onClick = {
        state.selectedIdentity?.let { request = UsbAudioInspectionToken(it, state.generation) }
        picker.launch(arrayOf("audio/flac", "audio/x-flac", "application/octet-stream"))
    }, enabled = state.serviceAvailable && state.selectedDevice?.hasPermission == true && state.descriptors != null &&
        !state.diagnosticOperationRunning && request == null, modifier = Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.usb_flac_action))
    }
    state.flacUsbExperiment?.takeIf { it.generation == state.generation }?.let { result ->
        Text(stringResource(R.string.usb_flac_result, if (result.phase == UsbSilentPhase.FINISHED) result.status.name else result.phase.name))
        result.flacSource?.let { source ->
            Text(stringResource(R.string.usb_flac_queue, source.count("initialPrimedFrames").toString(),
                source.count("framesConsumedFromQueue").toString(), source.count("queueUnderruns").toString()))
            Text(stringResource(R.string.usb_flac_hashes,
                source.counters["consumedSourcePcmHash"]?.let(::pcmHashHex) ?: "—",
                result.transport?.counters?.get("submittedPcmHash")?.let(::pcmHashHex) ?: "—"))
            if (source.reasons.isNotEmpty()) Text(source.reasons.joinToString(), color = MaterialTheme.colorScheme.error)
        }
        Text(stringResource(R.string.usb_flac_cleanup, result.configuration?.restorationStatus?.name ?: "—",
            result.configuration?.asRelease?.returned?.toString() ?: "—", result.configuration?.acRelease?.returned?.toString() ?: "—",
            result.originalFlacFdClosed?.toString() ?: "—"))
    }
    Text(stringResource(R.string.usb_flac_scope), style = MaterialTheme.typography.bodySmall)
}
