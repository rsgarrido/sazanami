package io.github.rsgarrido.sazanami.ui.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.platform.LocalContext
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import io.github.rsgarrido.sazanami.player.*
import io.github.rsgarrido.sazanami.player.usb.*
import io.github.rsgarrido.sazanami.ui.AppShellIcons
import io.github.rsgarrido.sazanami.ui.readableContentWidth
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
internal fun UsbAudioSettingsScreen(onBack: () -> Unit, onDiagnostics: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val preferences = remember(context.applicationContext) { AppPreferencesRepository.getInstance(context.applicationContext) }
    val preferenceState by preferences.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var savingPreference by remember { mutableStateOf(false) }
    var preferenceError by remember { mutableStateOf(false) }
    val usb by UsbAudioDiagnosticBridge.state.collectAsStateWithLifecycle()
    val output by IntegratedDirectPlaybackBridge.state.collectAsStateWithLifecycle()
    LaunchedEffect(preferenceState.experimentalUsbDirectEnabled) {
        if (preferenceState.experimentalUsbDirectEnabled) UsbAudioDiagnosticBridge.refresh()
    }
    fun saveExperimentalPreference(acknowledged: Boolean) {
        if (savingPreference) return
        savingPreference = true
        preferenceError = false
        scope.launch {
            try {
                if (acknowledged) preferences.acknowledgeExperimentalUsbDirect()
                else preferences.disableExperimentalUsbDirect()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                preferenceError = true
            } finally {
                savingPreference = false
            }
        }
    }
    UsbAudioSettingsContent(usb, output, onBack, onDiagnostics,
        onSelectDevice = UsbAudioDiagnosticBridge::select,
        onPermission = UsbAudioDiagnosticBridge::requestPermission,
        onRefresh = UsbAudioDiagnosticBridge::refresh,
        onOutput = { direct, identity, generation -> IntegratedDirectPlaybackBridge.select(direct, identity, generation) },
        experimentalEnabled = preferenceState.experimentalUsbDirectEnabled,
        experimentalPreferenceAvailable = preferenceState.isLoaded && !savingPreference,
        onAcknowledgeExperimental = { saveExperimentalPreference(true) },
        onDisableExperimental = { saveExperimentalPreference(false) },
        preferenceError = preferenceError,
        modifier = modifier)
}

/** The disclosures remain uncommitted until the user explicitly confirms them. */
@Composable
internal fun UsbAudioSettingsContent(
    usb: UsbAudioDiagnosticState, output: IntegratedDirectPlaybackState,
    onBack: () -> Unit, onDiagnostics: () -> Unit,
    onSelectDevice: (UsbAudioDeviceIdentity) -> Unit,
    onPermission: (UsbAudioDeviceIdentity) -> Unit, onRefresh: () -> Unit,
    onOutput: (Boolean, UsbAudioDeviceIdentity?, Long) -> Unit,
    experimentalEnabled: Boolean = false,
    experimentalPreferenceAvailable: Boolean = true,
    onAcknowledgeExperimental: () -> Unit = {},
    onDisableExperimental: () -> Unit = {},
    preferenceError: Boolean = false,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)
    var confirmation by remember(experimentalEnabled) { mutableStateOf<UsbAudioInspectionToken?>(null) }
    var experimentalConfirmation by remember(experimentalEnabled) { mutableStateOf(false) }
    val switching = output.switching != null
    val usbPending = usb.status == UsbAudioDiagnosticStatus.REQUESTING_PERMISSION || usb.diagnosticOperationRunning
    val progressText = usbAudioSettingsProgressText(usb, output, experimentalEnabled)
    val releasedBeforeHandoff = !output.confirmedDirect && output.activation?.reservation == UsbActivationReservation.RELEASED
    val editable = experimentalEnabled && output.serviceAvailable && usb.serviceAvailable && (!output.enabled || releasedBeforeHandoff) && !switching && !usbPending
    val canActivate = editable && !output.enabled && usb.activationFailure(usb.selectedIdentity, usb.generation) == null
    if (experimentalConfirmation && !experimentalEnabled) {
        AlertDialog(
            onDismissRequest = { experimentalConfirmation = false },
            title = { Text(stringResource(R.string.usb_settings_experimental)) },
            text = { Text(stringResource(R.string.usb_settings_experimental_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    experimentalConfirmation = false
                    onAcknowledgeExperimental()
                }, enabled = experimentalPreferenceAvailable) {
                    Text(stringResource(R.string.usb_settings_experimental_enable))
                }
            },
            dismissButton = { TextButton(onClick = { experimentalConfirmation = false }) { Text(stringResource(R.string.usb_settings_cancel)) } }
        )
    }
    confirmation?.takeIf { experimentalEnabled }?.let { token ->
        AlertDialog(
            onDismissRequest = { confirmation = null },
            title = { Text(stringResource(R.string.usb_settings_volume_title)) },
            text = { Text(stringResource(R.string.usb_settings_volume_warning)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmation = null
                    onOutput(true, token.identity, token.generation)
                }, enabled = canActivate && token.identity == usb.selectedIdentity && token.generation == usb.generation) {
                    Text(stringResource(R.string.usb_settings_activate))
                }
            },
            dismissButton = { TextButton(onClick = { confirmation = null }) { Text(stringResource(R.string.usb_settings_cancel)) } }
        )
    }
    Column(modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)
        .readableContentWidth()
        .verticalScroll(rememberScrollState())) {
        SettingsSubpageHeader(stringResource(R.string.usb_settings_title), onBack)
        SettingsSection(stringResource(R.string.usb_settings_advanced), stringResource(R.string.usb_settings_experimental_summary), AppShellIcons.AudioRoute) {
            val experimentalLabel = stringResource(R.string.usb_settings_experimental)
            SettingsRow(title = experimentalLabel,
                summary = stringResource(if (experimentalEnabled) R.string.usb_settings_experimental_enabled else R.string.usb_settings_experimental_disabled),
                icon = AppShellIcons.AudioRoute,
                trailingContent = {
                    Switch(checked = experimentalEnabled, enabled = experimentalPreferenceAvailable,
                        onCheckedChange = { requested ->
                            confirmation = null
                            if (requested) experimentalConfirmation = true else onDisableExperimental()
                        }, modifier = Modifier.semantics { contentDescription = experimentalLabel })
                })
            if (preferenceError) StatusText(R.string.usb_settings_preference_failed, error = true)
        }
        SettingsSectionSpacer()
        SettingsSection(stringResource(R.string.usb_settings_output), stringResource(R.string.usb_settings_explanation), AppShellIcons.AudioRoute) {
            Column(Modifier.selectableGroup()) {
                OutputChoice(stringResource(R.string.usb_settings_android),
                    selected = output.serviceAvailable && !output.enabled && !switching,
                    enabled = output.serviceAvailable && !switching,
                    onClick = { onOutput(false, null, usb.generation) })
                if (experimentalEnabled) {
                    SettingsDivider()
                    OutputChoice(stringResource(R.string.usb_settings_direct), selected = output.confirmedDirect && !switching,
                        enabled = canActivate || (output.confirmedDirect && !switching),
                        onClick = { if (!output.enabled) usb.selectedIdentity?.let { confirmation = UsbAudioInspectionToken(it, usb.generation) } })
                }
            }
            if (experimentalEnabled) Text(stringResource(R.string.usb_settings_session), Modifier.padding(16.dp), style = MaterialTheme.typography.bodySmall)
            if (!output.serviceAvailable || !usb.serviceAvailable) StatusText(R.string.usb_settings_service_unavailable)
            if (progressText != null) {
                LinearProgressIndicator(Modifier.fillMaxWidth().padding(horizontal = 16.dp))
                StatusText(progressText)
            }
            if (output.confirmedDirect && !switching && output.error == null) StatusText(if (output.phase in setOf(
                    IntegratedDirectPhase.IDLE, IntegratedDirectPhase.STOPPED, IntegratedDirectPhase.ENDED))
                R.string.usb_settings_active else R.string.usb_settings_playing)
            if (output.serviceAvailable && !output.enabled && !switching) StatusText(R.string.usb_settings_android_active)
            if (output.confirmedDirect && output.nativeTransportPhase in setOf(UsbFullTransportPhase.PLAYING, UsbFullTransportPhase.PAUSED,
                    UsbFullTransportPhase.PAUSING, UsbFullTransportPhase.RESUMING, UsbFullTransportPhase.SEEK_DRAINING,
                    UsbFullTransportPhase.SOURCE_REPOSITIONING, UsbFullTransportPhase.SEEK_PRIMING) && output.sampleRateHz > 0) {
                Text(stringResource(R.string.usb_settings_track_configured, output.sampleRateHz), Modifier.padding(16.dp))
            }
            output.error?.let { StatusText(usbSettingsErrorText(it), error = true) }
        }
        if (experimentalEnabled) {
            SettingsSectionSpacer()
            SettingsSection(stringResource(R.string.usb_settings_device), stringResource(R.string.usb_settings_candidate_note), AppShellIcons.AudioRoute) {
                val devices = usb.devices.filter { it.hasAudioInterface }
                if (devices.isEmpty()) StatusText(R.string.usb_settings_no_device)
                else StatusText(R.string.usb_settings_detected)
                Column(Modifier.selectableGroup()) {
                    devices.forEach { device ->
                        val name = device.product ?: device.manufacturer ?: stringResource(R.string.usb_settings_unnamed)
                        OutputChoice(name,
                            device.identity == usb.selectedIdentity, editable,
                            onClick = { onSelectDevice(device.identity) })
                    }
                }
                usb.selectedDevice?.takeIf { it.hasAudioInterface }?.let { selected ->
                    StatusText(R.string.usb_settings_selected)
                    StatusText(if (selected.hasPermission) R.string.usb_settings_permission_granted else R.string.usb_settings_permission_required)
                    if (!selected.hasPermission) {
                        if (usb.status == UsbAudioDiagnosticStatus.PERMISSION_DENIED) StatusText(R.string.usb_settings_permission_denied, error = true)
                        OutlinedButton(onClick = { onPermission(selected.identity) }, enabled = editable,
                            modifier = Modifier.padding(horizontal = 16.dp)) { Text(stringResource(R.string.usb_settings_grant)) }
                    }
                    if (usb.status == UsbAudioDiagnosticStatus.DESCRIPTORS_READ && !output.confirmedDirect) {
                        StatusText(if (usb.integratedCandidate() != null) R.string.usb_settings_candidate else R.string.usb_settings_incompatible,
                            error = usb.integratedCandidate() == null)
                    }
                }
                if (usb.status == UsbAudioDiagnosticStatus.ERROR) StatusText(R.string.usb_settings_inspection_failed, error = true)
                OutlinedButton(onClick = onRefresh, enabled = editable, modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.usb_settings_refresh))
                }
            }
            SettingsSectionSpacer()
            SettingsSection(stringResource(R.string.usb_settings_formats_title), stringResource(R.string.usb_settings_formats), AppShellIcons.MusicNote) {
                Text(stringResource(R.string.usb_settings_features), Modifier.padding(16.dp))
            }
        }
        TextButton(onClick = onDiagnostics, modifier = Modifier.padding(horizontal = 8.dp)) {
            Text(stringResource(R.string.usb_settings_troubleshooting))
        }
        Spacer(Modifier.height(24.dp))
    }
}

/** Progress presentation is separate from the USB ownership/busy gate used by activation controls. */
internal fun usbAudioSettingsProgressText(usb: UsbAudioDiagnosticState, output: IntegratedDirectPlaybackState,
    experimentalEnabled: Boolean): Int? {
    if (!output.serviceAvailable) return null
    when (output.switching) {
        DirectOutputSwitch.TO_ANDROID -> return R.string.usb_settings_returning
        DirectOutputSwitch.TO_USB -> return R.string.usb_settings_activating
        null -> Unit
    }
    if (!experimentalEnabled || !usb.serviceAvailable || usb.selectedDevice == null) return null
    if (usb.status == UsbAudioDiagnosticStatus.REQUESTING_PERMISSION) return R.string.usb_settings_requesting
    if (usb.status == UsbAudioDiagnosticStatus.OPENING) return R.string.usb_settings_inspecting
    if (usb.status != UsbAudioDiagnosticStatus.DESCRIPTORS_READ || usb.selectedDevice?.hasPermission != true) return null

    // A real, separate inspection must remain visible, even when Direct is already active.
    // Retained evidence from an older attachment/check cannot restart the indicator.
    val separateCheck = usb.clockDiagnostics?.let { it.generation == usb.generation && it.phase == UsbClockDiagnosticPhase.QUERYING } == true ||
        usb.usbFsDiagnostics?.let { it.generation == usb.generation && it.status == UsbFsProbeStatus.QUERYING } == true ||
        usb.forcedExperiment?.let { it.generation == usb.generation && it.phase == UsbForcedPhase.QUERYING } == true ||
        usb.configurationExperiment?.let { it.generation == usb.generation && it.phase == UsbConfigurationPhase.QUERYING } == true ||
        listOf(usb.silentExperiment, usb.referenceExperiment, usb.flacUsbExperiment, usb.fullFlacUsbExperiment)
            .any { it != null && it.generation == usb.generation && it.phase != UsbSilentPhase.FINISHED }
    if (separateCheck) return R.string.usb_settings_inspecting

    // The integrated transaction remains STARTING_TRANSPORT throughout playback. Its lifetime
    // protects USB ownership; the service's playback phase tells us when preparation is finished.
    return if (output.confirmedDirect && output.phase == IntegratedDirectPhase.PREPARING && output.error == null)
        R.string.usb_settings_preparing else null
}

@Composable
private fun OutputChoice(label: String, selected: Boolean, enabled: Boolean, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth().heightIn(min = 56.dp)
        .selectable(selected, enabled = enabled, role = Role.RadioButton, onClick = onClick)
        .padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
        RadioButton(selected, onClick = null, enabled = enabled)
        Text(label, Modifier.weight(1f).padding(start = 12.dp), style = MaterialTheme.typography.bodyLarge)
    }
}

@Composable
private fun StatusText(text: Int, error: Boolean = false) {
    Text(stringResource(text), Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)
        .semantics { liveRegion = LiveRegionMode.Polite },
        color = if (error) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface,
        style = MaterialTheme.typography.bodyMedium)
}

internal fun usbOutputErrorText(reason: String): Int = when (reason) {
    "DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED" -> R.string.usb_settings_experimental_required
    "DIRECT_SERVICE_UNAVAILABLE" -> R.string.usb_settings_service_unavailable
    "DIRECT_USB_DEVICE_UNAVAILABLE" -> R.string.usb_settings_disconnected
    "DIRECT_USB_SELECTION_CHANGED" -> R.string.usb_settings_selection_changed
    "DIRECT_USB_SELECTION_REQUIRED" -> R.string.usb_activation_selection_required
    "DIRECT_USB_PERMISSION_PENDING" -> R.string.usb_settings_requesting
    "DIRECT_USB_INSPECTION_RUNNING" -> R.string.usb_settings_inspecting
    "DIRECT_USB_INSPECTION_REQUIRED" -> R.string.usb_activation_inspection_required
    "DIRECT_USB_OPERATION_RUNNING" -> R.string.usb_activation_operation_running
    "DIRECT_USB_RESERVATION_HELD" -> R.string.usb_activation_reservation_held
    "DIRECT_USB_RESERVATION_LOST" -> R.string.usb_activation_reservation_lost
    "DIRECT_NATIVE_UNAVAILABLE" -> R.string.usb_activation_native_unavailable
    "DIRECT_NATIVE_BUSY", "DIRECT_NATIVE_IDLE_CHECK_FAILED" -> R.string.usb_activation_native_busy
    "DIRECT_NORMAL_SNAPSHOT_FAILED" -> R.string.usb_activation_snapshot_failed
    "DIRECT_BACKEND_SELECTION_FAILED" -> R.string.usb_activation_backend_failed
    "DIRECT_AUTHORIZATION_FAILED" -> R.string.usb_activation_authorization_failed
    "DIRECT_SWITCH_CANCELLED" -> R.string.usb_activation_cancelled
    "DIRECT_RESERVATION_ROLLBACK_FAILED", "DIRECT_RESERVATION_RELEASE_UNVERIFIED" -> R.string.usb_settings_cleanup_failed
    "DIRECT_USB_PERMISSION_FAILED" -> R.string.usb_settings_permission_required
    "DIRECT_USB_INCOMPATIBLE", "DIRECT_USB_CONFIGURATION_FAILED" -> R.string.usb_settings_incompatible
    "UNSUPPORTED_TRACK" -> R.string.usb_settings_unsupported_track
    "DIRECT_RESOURCE_CLEANUP_UNVERIFIED", "DIRECT_RESOURCE_CLEANUP_FAILED", "NORMAL_BACKEND_RELEASE_UNVERIFIED",
    "NORMAL_BACKEND_RECREATION_FAILED" -> R.string.usb_settings_cleanup_failed
    "DIRECT_PREDECESSOR_CLEANUP_PENDING", "DIRECT_USB_RESOURCES_BUSY" -> R.string.usb_settings_usb_busy
    else -> R.string.usb_settings_activation_failed
}

/** Keep technical rejection details in the advanced report; retain actionable recovery states here. */
private fun usbSettingsErrorText(reason: String): Int = when (reason) {
    "DIRECT_EXPERIMENTAL_OPT_IN_REQUIRED", "DIRECT_SERVICE_UNAVAILABLE", "DIRECT_USB_DEVICE_UNAVAILABLE", "DIRECT_USB_SELECTION_CHANGED",
    "DIRECT_USB_PERMISSION_PENDING", "DIRECT_USB_INSPECTION_RUNNING", "DIRECT_USB_PERMISSION_FAILED",
    "DIRECT_USB_INCOMPATIBLE", "DIRECT_USB_CONFIGURATION_FAILED", "UNSUPPORTED_TRACK",
    "DIRECT_RESOURCE_CLEANUP_UNVERIFIED", "DIRECT_RESOURCE_CLEANUP_FAILED", "NORMAL_BACKEND_RELEASE_UNVERIFIED",
    "NORMAL_BACKEND_RECREATION_FAILED", "DIRECT_RESERVATION_ROLLBACK_FAILED", "DIRECT_RESERVATION_RELEASE_UNVERIFIED",
    "DIRECT_PREDECESSOR_CLEANUP_PENDING", "DIRECT_USB_RESOURCES_BUSY" -> usbOutputErrorText(reason)
    else -> R.string.usb_settings_activation_failed
}

/** Secondary, release-visible evidence, including rejections before any track was prepared. */
@Composable
internal fun UsbActivationResultSection(evidence: UsbActivationEvidence, showFailure: Boolean = true) {
    val context = LocalContext.current
    val copyLabel = stringResource(R.string.usb_activation_copy)
    Text(stringResource(R.string.usb_activation_result_title, evidence.serial), Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        style = MaterialTheme.typography.labelLarge)
    evidence.failure?.takeIf { showFailure }?.let {
        Text(stringResource(usbOutputErrorText(it)), Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
    }
    TextButton(onClick = {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
        clipboard?.setPrimaryClip(ClipData.newPlainText(copyLabel, formatUsbActivationEvidence(evidence)))
        if (clipboard != null) Toast.makeText(context, R.string.usb_activation_copied, Toast.LENGTH_SHORT).show()
    }, modifier = Modifier.padding(horizontal = 8.dp)) { Text(stringResource(R.string.usb_activation_copy)) }
}
