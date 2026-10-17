package io.github.rsgarrido.sazanami.ui.settings

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.BuildConfig
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.usb.UsbTransportTelemetryBridge
import kotlinx.coroutines.launch

/** Requests only; playback/cleanup lifetime belongs to PlaybackService. */
@Composable internal fun IntegratedDirectPlaybackSection(showModeSwitch: Boolean=true) {
    val state by IntegratedDirectPlaybackBridge.state.collectAsState()
    val latest by IntegratedDirectPlaybackBridge.latestAttempt.collectAsState()
    val context=LocalContext.current
    val reportScope=rememberCoroutineScope()
    if(!showModeSwitch && !state.enabled) return
    Text(stringResource(R.string.usb_integrated_title),style=MaterialTheme.typography.titleMedium)
    if(showModeSwitch && BuildConfig.DEBUG) {
        Text(stringResource(R.string.usb_integrated_warning))
        OutlinedButton(onClick={ IntegratedDirectPlaybackBridge.select(!state.enabled) }) {
            Text(stringResource(if(state.enabled) R.string.usb_integrated_disable else R.string.usb_integrated_enable))
        }
    }
    state.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    state.activation?.let { UsbActivationResultSection(it, showFailure = state.error == null) }
    if(state.enabled) {
        Text("DIRECT_USB: ${state.phase}; ${state.positionMs}/${state.durationMs} ms")
        state.notice?.let { Text(it) }
        if(state.busy) OutlinedButton(onClick=IntegratedDirectPlaybackBridge::stop) { Text(stringResource(R.string.usb_integrated_stop)) }
        else if(state.libraryId!=null && state.cleanupSafe && state.normalReleased)
            OutlinedButton(onClick=IntegratedDirectPlaybackBridge::start) { Text(stringResource(R.string.usb_integrated_restart)) }
        Text(stringResource(R.string.usb_integrated_commands),style=MaterialTheme.typography.bodySmall)
    }
    latest?.let { attempt ->
        Text(stringResource(R.string.usb_integrated_latest_attempt),style=MaterialTheme.typography.labelLarge)
        Text("${attempt.phase}; ${attempt.positionMs}/${attempt.durationMs} ms",style=MaterialTheme.typography.bodySmall)
        attempt.error?.let { Text(it,color=MaterialTheme.colorScheme.error) }
    }
    if (state.enabled || latest!=null) {
        OutlinedButton(onClick={ reportScope.launch { context.copyIntegratedDirectReport() } }) {
            Text(stringResource(R.string.usb_integrated_copy_report))
        }
    }
}

/** Inserted above the player/navigation host; no overlay, Surface touch shield, or UI-owned job. */
@Composable internal fun IntegratedDirectPlaybackBanner(modifier: Modifier=Modifier) {
    if(!BuildConfig.DEBUG) return
    val state by IntegratedDirectPlaybackBridge.state.collectAsState()
    if(!state.enabled) return
    val context=LocalContext.current
    val reportScope=rememberCoroutineScope()
    Column(modifier=modifier.fillMaxWidth().heightIn(max=144.dp)
        .clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.surfaceContainerHigh)
        .padding(horizontal=12.dp,vertical=4.dp)) {
        Text("Experimental DIRECT_USB · ${state.phase}",style=MaterialTheme.typography.labelLarge,
            maxLines=1,overflow=TextOverflow.Ellipsis)
        Text("${state.positionMs} / ${state.durationMs} ms",style=MaterialTheme.typography.bodySmall,
            maxLines=1,overflow=TextOverflow.Ellipsis)
        val summary=state.error ?: state.notice ?: state.engine?.status?.name
        summary?.let { Text(it,style=MaterialTheme.typography.bodySmall,
            color=if(state.error!=null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines=1,overflow=TextOverflow.Ellipsis) }
        Row(modifier=Modifier.fillMaxWidth()) {
            TextButton(onClick={ reportScope.launch { context.copyIntegratedDirectReport() } },modifier=Modifier.weight(1f)) {
                Text(stringResource(R.string.usb_integrated_copy_report),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
            if(state.busy) TextButton(onClick=IntegratedDirectPlaybackBridge::stop,modifier=Modifier.weight(1f)) {
                Text(stringResource(R.string.usb_integrated_stop),maxLines=1,overflow=TextOverflow.Ellipsis)
            }
        }
    }
}

private suspend fun Context.copyIntegratedDirectReport() {
    UsbTransportTelemetryBridge.capture()
    val copied = runCatching {
        val clipboard=getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        if (clipboard == null) false else {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.usb_integrated_title),
                formatIntegratedDirectPlayback(IntegratedDirectPlaybackBridge.reportState,resources.configuration.locales[0])))
            true
        }
    }.getOrDefault(false)
    Toast.makeText(this,if (copied) R.string.diagnostics_copied else R.string.usb_diagnostics_copy_failed,Toast.LENGTH_SHORT).show()
}
