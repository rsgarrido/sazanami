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

@Composable internal fun UsbFullFlacDiagnosticsSection(state: UsbAudioDiagnosticState) {
    if (!BuildConfig.DEBUG) return
    var request by remember { mutableStateOf<UsbAudioInspectionToken?>(null) }
    val picker=rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        val token=request; request=null
        if (uri!=null && token!=null) UsbAudioDiagnosticBridge.playFullFlac(token.identity,token.generation,uri)
    }
    Text(stringResource(R.string.usb_full_flac_title),style=MaterialTheme.typography.titleMedium)
    Text(stringResource(R.string.usb_full_flac_warning),color=MaterialTheme.colorScheme.error)
    OutlinedButton(onClick={
        state.selectedIdentity?.let { request=UsbAudioInspectionToken(it,state.generation) }
        picker.launch(arrayOf("audio/flac","audio/x-flac","application/octet-stream"))
    },enabled=state.serviceAvailable && state.selectedDevice?.hasPermission==true && state.descriptors!=null &&
        !state.diagnosticOperationRunning && request==null,modifier=Modifier.fillMaxWidth()) {
        Text(stringResource(R.string.usb_full_flac_action))
    }
    state.fullFlacUsbExperiment?.takeIf { it.generation==state.generation }?.let { result ->
        Text(stringResource(R.string.usb_full_flac_result,if (result.phase==UsbSilentPhase.FINISHED) result.status.name else result.phase.name))
        if (result.phase in setOf(UsbSilentPhase.PRIMING_SOURCE,UsbSilentPhase.CONFIGURING,UsbSilentPhase.STARTING_TRANSPORT)) {
            OutlinedButton(onClick={ state.selectedIdentity?.let {
                UsbAudioDiagnosticBridge.stopFullFlac(it,state.generation,result.streamGeneration)
            } },modifier=Modifier.fillMaxWidth()) { Text(stringResource(R.string.usb_full_flac_stop)) }
        }
        result.flacSource?.let { source ->
            Text(stringResource(R.string.usb_full_flac_summary,source.count("totalSamples").toString(),
                source.count("framesConsumedFromQueue").toString(),source.md5Report,
                source.decoder?.counters?.get("pcmFnv1a")?.let(::pcmHashHex) ?: "—"))
            if (source.reasons.isNotEmpty()) Text(source.reasons.joinToString(),color=MaterialTheme.colorScheme.error)
        }
    }
    Text(stringResource(R.string.usb_full_flac_scope),style=MaterialTheme.typography.bodySmall)
}
