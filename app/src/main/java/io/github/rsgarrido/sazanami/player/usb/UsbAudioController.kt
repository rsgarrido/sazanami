package io.github.rsgarrido.sazanami.player.usb

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbDeviceConnection
import android.hardware.usb.UsbManager
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID
import java.util.concurrent.atomic.AtomicLong
import io.github.rsgarrido.sazanami.BuildConfig
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Service-owned USB diagnostics, including explicit temporary forced AC clock inspection. */
internal class UsbAudioController(context: Context) : FullUsbPlaybackEngineClient {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = UsbAudioGenerationGate()
    private val connectionLock = Any()
    private val ioMutex = diagnosticIoMutex
    private var connection: UsbDeviceConnection? = null
    // Keep claimed/native borrowed-fd operations open through worker cleanup. Cancellation
    // invalidates authority immediately, but must not close/reuse an fd still in native code.
    private var workerOwnedConnection: UsbDeviceConnection? = null
    private var integratedClient: UsbFullPlaybackClient?=null
    private var fullStop: (() -> Unit)? = null
    @Volatile private var userStoppedStream=0L
    private var endpointStop: (() -> Unit)? = null // Accessed under connectionLock; never closes fd.
    @Volatile private var inspectionToken: UsbAudioInspectionToken? = null
    @Volatile private var closed = false
    private var registered = false
    private var readJob: Job? = null
    private var devices: Map<UsbAudioDeviceIdentity, UsbDevice> = emptyMap()
    private var state = UsbAudioDiagnosticState(serviceAvailable = true, status = UsbAudioDiagnosticStatus.NO_DEVICE)
    private var pending: UsbAudioPermissionRequest? = null
    private var permissionIntent: PendingIntent? = null
    private var permissionTimeout: Runnable? = null

    private data class InspectionResult(val model: UsbAudioDescriptorModel? = null, val error: String? = null)

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val identity = runCatching { intent.usbDevice()?.diagnosticIdentity() }.getOrNull()
            if (intent.action == UsbManager.ACTION_USB_DEVICE_DETACHED) {
                if (identity == gate.current?.identity) invalidate()
                refreshOnMain(excluded = identity)
            } else if (intent.action == UsbManager.ACTION_USB_DEVICE_ATTACHED) {
                // Even a reused Android bus ID must represent a fresh attachment generation.
                if (identity == gate.current?.identity) invalidate()
                refreshOnMain()
            }
        }
    }

    fun start() = onMain {
        UsbAudioDiagnosticBridge.attach(this)
        try {
            ContextCompat.registerReceiver(appContext, receiver,
                IntentFilter().apply {
                    addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED)
                    addAction(UsbManager.ACTION_USB_DEVICE_DETACHED)
                }, ContextCompat.RECEIVER_NOT_EXPORTED)
            registered = true
            refreshOnMain()
        } catch (_: RuntimeException) {
            publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR,
                message = "USB event registration unavailable; use Refresh to enumerate devices."))
        }
    }

    fun refresh() = onMain { refreshOnMain(forceInspection = true) }
    fun select(identity: UsbAudioDeviceIdentity) = onMain {
        if (devices.containsKey(identity) && gate.current?.identity != identity) selectOnMain(identity)
    }

    private fun refreshOnMain(forceInspection: Boolean = false, excluded: UsbAudioDeviceIdentity? = null) {
        if (closed) return
        val usb = manager
        if (usb == null) {
            invalidate()
            publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR, message = "USB host API unavailable."))
            return
        }
        try {
            devices = usb.deviceList.values.associateBy { it.diagnosticIdentity() }.filterKeys { it != excluded }
            val snapshots = devices.values.map { snapshot(it) }.sortedBy { it.identity.deviceId }
            state = state.copy(devices = snapshots)
            val current = gate.current
            if (current != null && devices.containsKey(current.identity)) {
                val granted = snapshots.first { it.identity == current.identity }.hasPermission
                when {
                    pending != null -> publish(state)
                    !granted -> {
                        readJob?.cancel()
                        closeConnection()
                        readJob = null
                        val token = gate.select(current.identity)
                        inspectionToken = token
                        publish(state.copy(descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, generation = gate.generation,
                            status = if (state.status == UsbAudioDiagnosticStatus.PERMISSION_DENIED)
                                UsbAudioDiagnosticStatus.PERMISSION_DENIED else UsbAudioDiagnosticStatus.PERMISSION_REQUIRED))
                    }
                    state.status == UsbAudioDiagnosticStatus.OPENING -> publish(state)
                    forceInspection || state.descriptors == null -> inspect(current)
                    else -> publish(state)
                }
                return
            }
            if (current != null) invalidate()
            val audio = snapshots.filter { it.hasAudioInterface }
            if (audio.size == 1) selectOnMain(audio.single().identity)
            else publish(state.copy(selectedIdentity = null, descriptors = null,
                generation = gate.generation, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null,
                status = if (snapshots.isEmpty()) UsbAudioDiagnosticStatus.NO_DEVICE else UsbAudioDiagnosticStatus.SELECT_DEVICE,
                message = if (snapshots.isEmpty()) null else "Select a device to inspect; ${audio.size} devices expose audio interfaces."))
        } catch (_: RuntimeException) {
            invalidate()
            devices = emptyMap()
            publish(state.copy(devices = emptyList(), status = UsbAudioDiagnosticStatus.ERROR,
                message = "USB enumeration failed. Refresh to retry."))
        }
    }

    private fun selectOnMain(identity: UsbAudioDeviceIdentity) {
        invalidate()
        val device = devices[identity] ?: return
        val token = gate.select(identity) ?: return
        inspectionToken = token
        publish(state.copy(selectedIdentity = identity, generation = token.generation,
            descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, message = null, status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED))
        if (manager?.hasPermission(device) == true) inspect(token)
    }

    fun requestPermission(identity: UsbAudioDeviceIdentity) = onMain {
        val usb = manager ?: return@onMain
        val token = gate.current ?: return@onMain
        if (identity != token.identity || pending != null || state.diagnosticOperationRunning) return@onMain
        val device = runCatching { usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == identity } }.getOrNull()
        if (device == null) { refreshOnMain(); return@onMain }
        try {
            if (usb.hasPermission(device)) { inspect(token); return@onMain }
            val request = UsbAudioPermissionRequest(UUID.randomUUID().toString(), token)
            val intent = Intent(appContext, UsbAudioPermissionReceiver::class.java)
                .setAction(appContext.packageName + UsbAudioPermissionReceiver.ACTION_SUFFIX)
                .setData(Uri.parse("sazanami-usb-permission:${request.id}"))
                .putExtra(UsbAudioPermissionReceiver.EXTRA_REQUEST_ID, request.id)
            // System fills EXTRA_DEVICE / EXTRA_PERMISSION_GRANTED. Mutable target is explicit,
            // non-exported and single-use; unique nonce + generation prevent stale authority.
            val flags = PendingIntent.FLAG_ONE_SHOT or
                (if (Build.VERSION.SDK_INT >= 31) PendingIntent.FLAG_MUTABLE else 0)
            permissionIntent = PendingIntent.getBroadcast(appContext, 0, intent, flags)
            pending = request
            publish(state.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION, message = null, descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null))
            usb.requestPermission(device, permissionIntent)
            permissionTimeout = Runnable {
                if (pending == request && gate.accepts(token)) {
                    cancelPermission()
                    publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR,
                        message = "USB permission result not received. Refresh or request access again."))
                }
            }.also { handler.postDelayed(it, 120_000L) }
        } catch (_: RuntimeException) {
            cancelPermission()
            publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR,
                message = "USB permission request unavailable. The device may have disconnected."))
        }
    }

    fun permissionResult(requestId: String, identity: UsbAudioDeviceIdentity?, granted: Boolean) = onMain {
        val request = pending ?: return@onMain
        if (!request.matches(requestId, identity, gate)) return@onMain
        cancelPermission()
        val device = runCatching { manager?.deviceList?.values?.firstOrNull { it.diagnosticIdentity() == identity } }.getOrNull()
        if (device == null) { invalidate(); refreshOnMain(); return@onMain }
        val actualPermission = runCatching { manager?.hasPermission(device) == true }.getOrDefault(false)
        state = state.copy(devices = state.devices.map { if (it.identity == identity) it.copy(hasPermission = actualPermission) else it })
        if (!actualPermission) publish(state.copy(status = UsbAudioDiagnosticStatus.PERMISSION_DENIED,
            descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, message = "USB access was not granted. No device configuration was changed."))
        else if (!granted) publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR, descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null,
            message = "Permission result disagrees with current Android access. Refresh to recheck."))
        else inspect(request.token)
    }

    private fun inspect(selection: UsbAudioInspectionToken) {
        if (!gate.accepts(selection)) return
        readJob?.cancel()
        // Refresh creates a new inspection generation too: a cancelled IO worker cannot
        // register its late openDevice result over a newer connection for the same device.
        val token = gate.select(selection.identity) ?: return
        inspectionToken = token
        closeConnection()
        publish(state.copy(status = UsbAudioDiagnosticStatus.OPENING, generation = token.generation,
            descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, message = null))
        readJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    ioMutex.withLock {
                        val usb = manager ?: return@withLock InspectionResult(error = "USB host API unavailable.")
                        val device = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == token.identity }
                            ?: return@withLock InspectionResult(error = "Device disconnected before inspection.")
                        if (!usb.hasPermission(device)) return@withLock InspectionResult(error = "USB permission no longer available.")
                        val opened = usb.openDevice(device)
                            ?: return@withLock InspectionResult(error = "Android could not open the USB device for inspection.")
                        try {
                            val accepted = synchronized(connectionLock) {
                                if (closed || inspectionToken != token) false else { connection = opened; true }
                            }
                            if (!accepted) return@withLock InspectionResult()
                            // Descriptor inspection remains separate from explicit clock reads.
                            // No interface is claimed, selected, reset or detached here.
                            val bytes = opened.rawDescriptors?.copyOf() ?: byteArrayOf()
                            InspectionResult(model = UsbAudioDescriptorParser.parse(bytes))
                        } finally {
                            synchronized(connectionLock) { if (connection === opened) connection = null }
                            runCatching { opened.close() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (_: Exception) {
                InspectionResult(error = "USB descriptor inspection failed. Refresh to retry.")
            }
            if (!closed && gate.accepts(token)) {
                val stillPresent = runCatching {
                    manager?.deviceList?.values?.any { it.diagnosticIdentity() == token.identity } == true
                }.getOrDefault(false)
                if (!stillPresent) {
                    invalidate()
                    refreshOnMain(excluded = token.identity)
                } else publish(state.copy(
                    status = if (result.model != null) UsbAudioDiagnosticStatus.DESCRIPTORS_READ else UsbAudioDiagnosticStatus.ERROR,
                    descriptors = result.model, message = result.error))
            }
        }
    }

    fun queryClockCapabilities(identity: UsbAudioDeviceIdentity) = onMain { queryClockOnMain(identity, false) }
    fun testAudioControlClaim(identity: UsbAudioDeviceIdentity) = onMain { queryClockOnMain(identity, true) }

    private fun queryClockOnMain(identity: UsbAudioDeviceIdentity, withClaimExperiment: Boolean) {
        val selection = gate.current ?: return
        val model = state.descriptors ?: return
        if (selection.identity != identity || pending != null || state.diagnosticOperationRunning) return
        val resolution = UsbAudioTopology.resolveClockTarget(model)
        if (resolution.target == null) {
            publish(state.copy(clockDiagnostics = unavailableClockDiagnostics(gate.generation, resolution,
                UsbClockQueryStatus.AMBIGUOUS_TARGET, "Playback clock target is ambiguous; no control request issued.", withClaimExperiment)))
            return
        }
        readJob?.cancel()
        val token = gate.select(identity) ?: return
        inspectionToken = token
        closeConnection()
        publish(state.copy(generation = token.generation, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null,
            clockDiagnostics = UsbAudioClockDiagnostics(token.generation, resolution, UsbClockDiagnosticPhase.QUERYING,
                claimExperiment = if (withClaimExperiment) UsbAudioClaimExperimentResult(token.generation, resolution) else null)))
        readJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    ioMutex.withLock {
                        val coroutine = currentCoroutineContext()
                        fun accessFailure(): UsbClockQueryStatus? {
                            coroutine.ensureActive()
                            return usbAccessFailure(token)
                        }
                        accessFailure()?.let { return@withLock unavailableClockDiagnostics(token.generation,
                            resolution, it, "Clock query access check failed; no reads issued.", withClaimExperiment) }
                        val usb = manager ?: return@withLock unavailableClockDiagnostics(token.generation,
                            resolution, UsbClockQueryStatus.TRANSPORT_ERROR, "USB API unavailable.", withClaimExperiment)
                        val device = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == identity }
                            ?: return@withLock unavailableClockDiagnostics(token.generation, resolution,
                                UsbClockQueryStatus.DEVICE_DISCONNECTED, "Device disconnected before open.", withClaimExperiment)
                        val opened = usb.openDevice(device) ?: return@withLock unavailableClockDiagnostics(
                            token.generation, resolution, accessFailure() ?: UsbClockQueryStatus.TRANSPORT_ERROR,
                            "USB connection could not be opened for read-only clock diagnostics.", withClaimExperiment)
                        var experimentOwnsClose = false
                        try {
                            val accepted = synchronized(connectionLock) {
                                if (closed || inspectionToken != token) false else {
                                    connection = opened
                                    if (withClaimExperiment) workerOwnedConnection = opened
                                    true
                                }
                            }
                            if (!accepted) return@withLock unavailableClockDiagnostics(token.generation,
                                resolution, UsbClockQueryStatus.STALE_GENERATION, "Generation changed before control reads.", withClaimExperiment)
                            if (withClaimExperiment) {
                                val transport = UsbAudioClaimConnection(opened, device)
                                experimentOwnsClose = true // run owns release followed by close in its outer finally.
                                UsbAudioClaimExperiment.diagnostics(UsbAudioClaimExperiment.run(
                                    resolution, token.generation, transport, ::accessFailure))
                            } else UsbAudioClockControl.query(resolution, token.generation,
                                UsbAudioClockConnection(opened), ::accessFailure)
                        } finally {
                            synchronized(connectionLock) {
                                if (connection === opened) connection = null
                                if (workerOwnedConnection === opened) workerOwnedConnection = null
                            }
                            if (!experimentOwnsClose) runCatching { opened.close() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) {
                unavailableClockDiagnostics(token.generation, resolution, UsbClockQueryStatus.PERMISSION_LOST,
                    "USB permission denied during query setup.", withClaimExperiment)
            } catch (_: Exception) {
                unavailableClockDiagnostics(token.generation, resolution, UsbClockQueryStatus.TRANSPORT_ERROR,
                    "Clock-query setup failed; refresh and retry.", withClaimExperiment)
            }
            if (!closed && gate.accepts(token)) {
                val publicationFailure = usbAccessFailure(token)
                when (publicationFailure) {
                    UsbClockQueryStatus.DEVICE_DISCONNECTED -> { invalidate(); refreshOnMain(excluded = identity) }
                    UsbClockQueryStatus.PERMISSION_LOST -> publish(state.copy(status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
                        descriptors = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, devices = state.devices.map { if (it.identity == identity) it.copy(hasPermission = false) else it },
                        clockDiagnostics = invalidateClockDiagnostics(result, UsbClockQueryStatus.PERMISSION_LOST,
                            "Permission lost; returned values are not authoritative.")))
                    UsbClockQueryStatus.TRANSPORT_ERROR -> publish(state.copy(
                        clockDiagnostics = invalidateClockDiagnostics(result, UsbClockQueryStatus.TRANSPORT_ERROR,
                            "Could not revalidate device access; returned values are not authoritative.")))
                    null -> publish(state.copy(clockDiagnostics = result))
                    else -> Unit // Stale result never publishes into current state.
                }
            }
        }
    }

    fun inspectKernelOwnership(identity: UsbAudioDeviceIdentity) = onMain {
        val token = gate.current ?: return@onMain
        val model = state.descriptors ?: return@onMain
        val selected = state.selectedDevice ?: return@onMain
        if (token.identity != identity || selected.identity != identity || pending != null || state.diagnosticOperationRunning) return@onMain
        val plan = UsbFsProbePlan.create(model, selected.interfaces)
        // Keep the current generation and prior clock/claim evidence. Busy gating and the
        // shared mutex prevent overlapping operations; refresh/detach still replace tokens.
        readJob?.cancel()
        inspectionToken = token
        closeConnection()
        publish(state.copy(usbFsDiagnostics = UsbFsOwnershipDiagnostics(token.generation, plan, UsbFsProbeStatus.QUERYING)))
        readJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    ioMutex.withLock {
                        val coroutine = currentCoroutineContext()
                        fun accessFailure(): UsbClockQueryStatus? { coroutine.ensureActive(); return usbAccessFailure(token) }
                        val audio = UsbAndroidAudioInspector.inspect(appContext)
                        accessFailure()?.let { return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                            UsbFsEvidence.accessStatus(it), detail = "USB access check failed; no native ioctl issued.") }
                        val usb = manager ?: return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                            UsbFsProbeStatus.TRANSPORT_ERROR, androidAudio = audio, detail = "USB host API unavailable.")
                        val live = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == identity }
                            ?: return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                                UsbFsProbeStatus.DEVICE_DISCONNECTED, detail = "Device disconnected before native probe.")
                        val opened = usb.openDevice(live) ?: return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                            UsbFsEvidence.accessStatus(accessFailure() ?: UsbClockQueryStatus.TRANSPORT_ERROR),
                            androidAudio = audio, detail = "Android could not open the USB diagnostic connection.")
                        try {
                            val accepted = synchronized(connectionLock) {
                                if (closed || inspectionToken != token) false else {
                                    connection = opened
                                    workerOwnedConnection = opened
                                    true
                                }
                            }
                            if (!accepted) return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                                UsbFsProbeStatus.STALE_GENERATION, detail = "Generation changed before borrowing the fd.")
                            accessFailure()?.let { return@withLock UsbFsOwnershipDiagnostics(token.generation, plan,
                                UsbFsEvidence.accessStatus(it), detail = "Access changed before native ioctls.") }
                            val evidence = UsbFsDiagnostics.inspect(opened.fileDescriptor, plan, token.generation).copy(androidAudio = audio)
                            accessFailure()?.let { evidence.invalidate(it, "Access changed during native probe; ownership results omitted.") } ?: evidence
                        } finally {
                            // Native has returned and its immediate raw-claim release has run.
                            synchronized(connectionLock) {
                                if (connection === opened) connection = null
                                if (workerOwnedConnection === opened) workerOwnedConnection = null
                            }
                            runCatching { opened.close() } // Kotlin owns the borrowed fd throughout.
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { UsbFsOwnershipDiagnostics(token.generation, plan, UsbFsProbeStatus.PERMISSION_LOST,
                detail = "USB permission denied during native probe setup.") }
            catch (_: Exception) { UsbFsOwnershipDiagnostics(token.generation, plan, UsbFsProbeStatus.EXCEPTION,
                detail = "Native ownership diagnostic operation failed; private exception details omitted.") }
            if (!closed && gate.accepts(token)) {
                if (result.status == UsbFsProbeStatus.DEVICE_DISCONNECTED) {
                    // Kernel ENODEV is authoritative even before Android's detach broadcast.
                    invalidate()
                    refreshOnMain(excluded = identity)
                    return@launch
                }
                when (val failure = usbAccessFailure(token)) {
                    UsbClockQueryStatus.DEVICE_DISCONNECTED -> { invalidate(); refreshOnMain(excluded = identity) }
                    UsbClockQueryStatus.PERMISSION_LOST -> publish(state.copy(status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
                        descriptors = null, clockDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null,
                        devices = state.devices.map { if (it.identity == identity) it.copy(hasPermission = false) else it },
                        usbFsDiagnostics = result.invalidate(failure, "Permission lost before publication; native evidence omitted.")))
                    null -> publish(state.copy(usbFsDiagnostics = result))
                    UsbClockQueryStatus.STALE_GENERATION -> Unit
                    else -> publish(state.copy(usbFsDiagnostics = result.invalidate(failure, "Could not revalidate USB access; native evidence omitted.")))
                }
            }
        }
    }

    fun forceOwnAudioControl(identity: UsbAudioDeviceIdentity) = onMain {
        val token = gate.current ?: return@onMain
        val model = state.descriptors ?: return@onMain
        val selected = state.selectedDevice ?: return@onMain
        if (token.identity != identity || selected.identity != identity || pending != null || state.diagnosticOperationRunning) return@onMain
        val plan = UsbFsProbePlan.create(model, selected.interfaces)
        val prior = state.usbFsDiagnostics
        // Require a current Session 4 observation as well as fresh Session 5 baseline.
        // No claim is used to discover a target or to recover an ambiguous topology.
        if (plan.audioControl == null || prior == null || prior.generation != token.generation ||
            prior.status != UsbFsProbeStatus.RECORDED || prior.plan.resolution.target != plan.resolution.target) {
            publish(state.copy(forcedExperiment = UsbAudioForcedExperimentResult(token.generation, plan,
                detail = "Unique AC/direct Clock Source and current Session 4 evidence required. Inspect kernel ownership first; no force claim attempted.")))
            return@onMain
        }
        readJob?.cancel()
        inspectionToken = token
        closeConnection()
        publish(state.copy(forcedExperiment = UsbAudioForcedExperimentResult(token.generation, plan, UsbForcedPhase.QUERYING)))
        readJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    ioMutex.withLock {
                        val coroutine = currentCoroutineContext()
                        fun accessFailure(): UsbClockQueryStatus? { coroutine.ensureActive(); return usbAccessFailure(token) }
                        fun unavailable(failure: UsbClockQueryStatus, detail: String) =
                            UsbAudioForcedExperimentResult(token.generation, plan).invalidate(failure, detail)
                        accessFailure()?.let { return@withLock unavailable(it, "Access check failed before opening forced diagnostic connection.") }
                        val usb = manager ?: return@withLock unavailable(UsbClockQueryStatus.TRANSPORT_ERROR, "USB host API unavailable.")
                        val live = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == identity }
                            ?: return@withLock unavailable(UsbClockQueryStatus.DEVICE_DISCONNECTED, "Device disappeared before open.")
                        val opened = usb.openDevice(live) ?: return@withLock unavailable(
                            accessFailure() ?: UsbClockQueryStatus.TRANSPORT_ERROR, "Android could not open the USB connection.")
                        var runnerOwnsClose = false
                        try {
                            val accepted = synchronized(connectionLock) {
                                if (closed || inspectionToken != token) false else {
                                    connection = opened
                                    workerOwnedConnection = opened
                                    true
                                }
                            }
                            if (!accepted) return@withLock unavailable(UsbClockQueryStatus.STALE_GENERATION, "Generation changed before registering connection.")
                            accessFailure()?.let { return@withLock unavailable(it, "Access changed before experiment setup.") }
                            val transport = UsbAudioForcedConnection(opened, live, appContext, plan, token.generation)
                            runnerOwnsClose = true
                            UsbAudioForcedExperiment.run(plan, token.generation, transport, ::accessFailure)
                        } finally {
                            synchronized(connectionLock) {
                                if (connection === opened) connection = null
                                if (workerOwnedConnection === opened) workerOwnedConnection = null
                            }
                            if (!runnerOwnsClose) runCatching { opened.close() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) { throw cancelled }
            catch (_: SecurityException) { UsbAudioForcedExperimentResult(token.generation, plan).invalidate(
                UsbClockQueryStatus.PERMISSION_LOST, "USB access denied during setup.") }
            catch (_: Exception) { UsbAudioForcedExperimentResult(token.generation, plan,
                detail = "Forced diagnostic setup failed; no successful ownership inferred.") }
            if (!closed && gate.accepts(token)) {
                if (result.accessFailure == UsbClockQueryStatus.DEVICE_DISCONNECTED ||
                    result.recoveryStatus == UsbRecoveryStatus.DEVICE_DISCONNECTED) {
                    invalidate()
                    refreshOnMain(excluded = identity)
                    return@launch
                }
                when (val failure = usbAccessFailure(token)) {
                    UsbClockQueryStatus.DEVICE_DISCONNECTED -> { invalidate(); refreshOnMain(excluded = identity) }
                    UsbClockQueryStatus.STALE_GENERATION -> Unit
                    UsbClockQueryStatus.PERMISSION_LOST -> publish(state.copy(status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
                        descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null,
                        devices = state.devices.map { if (it.identity == identity) it.copy(hasPermission = false) else it },
                        forcedExperiment = result.invalidate(failure, "Permission lost before publication; device values omitted.")))
                    null -> publish(state.copy(forcedExperiment = result))
                    else -> publish(state.copy(forcedExperiment = result.invalidate(failure, "USB access could not be revalidated before publication.")))
                }
            }
        }
    }

    fun configurePcm16(identity: UsbAudioDeviceIdentity) = runConfigurationDiagnostic(identity, silentRun = false)
    fun streamSilentPcm16(identity: UsbAudioDeviceIdentity) {
        if (BuildConfig.DEBUG) runConfigurationDiagnostic(identity, silentRun = true)
    }
    fun playReferencePcm16(identity: UsbAudioDeviceIdentity) {
        if (BuildConfig.DEBUG) runConfigurationDiagnostic(identity, silentRun = true, mode = UsbPcmDiagnosticMode.REFERENCE)
    }
    fun playFlacPrefix(identity: UsbAudioDeviceIdentity, generation: Long, uri: Uri) = onMain {
        if (BuildConfig.DEBUG && gate.current?.generation == generation) runConfigurationDiagnostic(identity, silentRun = true, mode = UsbPcmDiagnosticMode.FLAC_PREFIX, flacUri = uri)
    }
    fun playFullFlac(identity: UsbAudioDeviceIdentity,generation: Long,uri: Uri) = onMain {
        if (BuildConfig.DEBUG && gate.current?.generation==generation)
            runConfigurationDiagnostic(identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri)
    }
    /** A second service-owned client of the same full decoder/configuration/transport/cleanup path. */
    override suspend fun runIntegratedFull(uri: Uri,control: UsbFullPlaybackControl,progress: (UsbFullProgress)->Unit): UsbSilentTransportExperimentResult = withContext(Dispatchers.Main.immediate) {
        check(BuildConfig.DEBUG)
        val selected=state.selectedDevice ?: throw UsbIntegratedStartException("DIRECT_USB_DEVICE_UNAVAILABLE")
        if (!selected.hasPermission) throw UsbIntegratedStartException("DIRECT_USB_PERMISSION_FAILED")
        if (state.descriptors==null || state.diagnosticOperationRunning || integratedClient!=null)
            throw UsbIntegratedStartException("DIRECT_USB_CONFIGURATION_FAILED")
        val client=UsbFullPlaybackClient(progress,control); integratedClient=client
        try {
            runConfigurationDiagnostic(selected.identity,true,UsbPcmDiagnosticMode.FLAC_FULL,uri,client)
            client.result?.let { return@withContext it }
            val job=readJob ?: throw UsbIntegratedStartException("DIRECT_USB_CONFIGURATION_FAILED")
            withContext(NonCancellable) { job.join() }
            client.result ?: throw UsbIntegratedStartException("DIRECT_RESOURCE_CLEANUP_UNVERIFIED")
        } finally { control.finish();integratedClient=null }
    }
    override fun stopIntegratedFull() = onMain {
        try { integratedClient?.control?.stop() }
        finally {
            state.integratedFlacUsbExperiment?.let { userStoppedStream=it.streamGeneration }
            synchronized(connectionLock) { fullStop }?.invoke()
        }
    }
    suspend fun awaitIdle() { withContext(NonCancellable) { readJob?.join() } }
    fun stopFullFlac(identity: UsbAudioDeviceIdentity,generation: Long,stream: Long) = onMain {
        val running=state.fullFlacUsbExperiment ?: return@onMain
        if (BuildConfig.DEBUG && gate.current?.identity==identity && gate.current?.generation==generation &&
            running.streamGeneration==stream && running.phase in setOf(UsbSilentPhase.PRIMING_SOURCE,UsbSilentPhase.CONFIGURING,UsbSilentPhase.STARTING_TRANSPORT)) {
            userStoppedStream=stream
            synchronized(connectionLock) { fullStop }?.invoke()
        }
    }
    private fun runConfigurationDiagnostic(identity: UsbAudioDeviceIdentity, silentRun: Boolean, mode: UsbPcmDiagnosticMode = UsbPcmDiagnosticMode.ZERO, flacUri: Uri? = null, client: UsbFullPlaybackClient?=null) = onMain {
        fun currentExperiment() = if (client!=null) state.integratedFlacUsbExperiment else state.endpointExperiment(mode)
        fun withExperiment(base: UsbAudioDiagnosticState,value: UsbSilentTransportExperimentResult?) =
            if (client!=null) base.copy(integratedFlacUsbExperiment=value) else base.withEndpointExperiment(mode,value)
        val token = gate.current ?: return@onMain
        val model = state.descriptors ?: return@onMain
        val selected = state.selectedDevice ?: return@onMain
        if (token.identity != identity || selected.identity != identity || pending != null || state.diagnosticOperationRunning) return@onMain
        var silentResolution = if (silentRun) UsbSilentTransportPlan.resolve(model, selected.interfaces) else null
        val stream = if (silentRun) streamGenerations.incrementAndGet() else 0L
        if (mode==UsbPcmDiagnosticMode.FLAC_FULL) userStoppedStream=0L
        var resolution = if (silentRun) silentResolution?.plan?.configuration ?: UsbAudioConfigurationResolution(issues = silentResolution?.issues.orEmpty())
            else UsbAudioConfigurationPlan.resolve(model, selected.interfaces)
        val initialPlan = resolution.plan
        val nativeIdle = UsbFsDiagnostics.silentTransportIdle()
        if (initialPlan == null || !nativeIdle) {
            val blockedResolution = if (nativeIdle) resolution else resolution.copy(issues = resolution.issues +
                "Native library unavailable or a prior endpoint session is retained; no further configuration/ownership allowed.")
            val blocked = UsbAudioConfigurationResult(token.generation, blockedResolution,
                targetStatus = UsbTargetConfigurationStatus.BLOCKED, failedStep = UsbConfigurationStep.TARGET,
                detail = "Configuration topology is ambiguous/unsupported; no forced ownership or configuration issued.")
            publish(if (silentRun) withExperiment(state, UsbSilentTransportExperimentResult(token.generation, stream,
                requireNotNull(silentResolution), status = UsbSilentStatus.BLOCKED, configuration = blocked, detail = blocked.detail, mode = mode))
                else state.copy(configurationExperiment = blocked))
            if (client!=null) client.result=currentExperiment()
            return@onMain
        }
        readJob?.cancel()
        inspectionToken = token
        closeConnection()
        publish(if (silentRun) withExperiment(state, UsbSilentTransportExperimentResult(token.generation, stream,
            requireNotNull(silentResolution), phase = if (mode.isFlac) UsbSilentPhase.PRIMING_SOURCE else UsbSilentPhase.CONFIGURING, mode = mode))
            else state.copy(configurationExperiment = UsbAudioConfigurationResult(token.generation, resolution, UsbConfigurationPhase.QUERYING)))
        readJob = scope.launch {
            var silentResult: UsbSilentTransportExperimentResult? = null
            var prefixSource: UsbFlacPrefixSource? = null
            var originalFlacFd: ParcelFileDescriptor? = null
            var originalFlacFdClosed = false
            fun finished(base: UsbAudioDiagnosticState, configuration: UsbAudioConfigurationResult): UsbAudioDiagnosticState {
                if (!silentRun) return base.copy(configurationExperiment = configuration)
                val value = (silentResult ?: UsbSilentTransportExperimentResult(token.generation, stream, requireNotNull(silentResolution),
                    status = if (mode.isFlac) UsbSilentStatus.USB_CONFIGURATION_FAILED
                        else if (configuration.targetStatus == UsbTargetConfigurationStatus.BLOCKED) UsbSilentStatus.BLOCKED else UsbSilentStatus.FAILED,
                    detail = configuration.detail, mode = mode)).copy(configuration = configuration)
                val finalValue = if (mode.isFlac) {
                    value.withFlacInputClosed(originalFlacFdClosed, prefixSource?.result)
                } else value
                return withExperiment(base, configuration.accessFailure?.let { finalValue.invalidate(it) } ?: finalValue)
            }
            val result = try {
                withContext(Dispatchers.IO) {
                    ioMutex.withLock {
                        currentCoroutineContext().ensureActive()
                        fun unavailable(failure: UsbClockQueryStatus, detail: String) =
                            UsbAudioConfigurationResult(token.generation, resolution).invalidate(failure, detail)
                        if (!UsbFsDiagnostics.silentTransportIdle()) return@withLock UsbAudioConfigurationResult(token.generation, resolution,
                            targetStatus = UsbTargetConfigurationStatus.BLOCKED, detail = "Prior endpoint buffers are retained/native API unavailable; configuration cannot begin.")
                        usbAccessFailure(token)?.let { return@withLock unavailable(it, "Access check failed before Session 6 open.") }
                        val usb = manager ?: return@withLock unavailable(UsbClockQueryStatus.TRANSPORT_ERROR, "USB host API unavailable.")
                        val live = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == identity }
                            ?: return@withLock unavailable(UsbClockQueryStatus.DEVICE_DISCONNECTED, "Device disappeared before configuration open.")
                        if (mode.isFlac) {
                            // SAF input and prebuffer must succeed before opening/claiming the USB configuration connection.
                            fun sourceBlocked(status: UsbSilentStatus, detail: String): UsbAudioConfigurationResult {
                                val blocked = UsbAudioConfigurationResult(token.generation, resolution,
                                    targetStatus = UsbTargetConfigurationStatus.BLOCKED, detail = detail)
                                silentResult = UsbSilentTransportExperimentResult(token.generation, stream, requireNotNull(silentResolution),
                                    status = status, configuration = blocked, mode = mode, flacSource = prefixSource?.result, detail = detail)
                                return blocked
                            }
                            try { originalFlacFd = appContext.contentResolver.openFileDescriptor(requireNotNull(flacUri), "r") }
                            catch (_: Exception) { return@withLock sourceBlocked(UsbSilentStatus.FLAC_INPUT_OPEN_FAILED, "Read-only SAF input could not be opened; no USB ownership attempted.") }
                            val input = originalFlacFd ?: return@withLock sourceBlocked(UsbSilentStatus.FLAC_INPUT_OPEN_FAILED, "SAF provider returned no descriptor; no USB ownership attempted.")
                            val source = UsbFlacPrefixSource(input.fd, token.generation, stream,mode==UsbPcmDiagnosticMode.FLAC_FULL)
                            prefixSource = source
                            if(client?.control?.stopped==true) source.requestStop()
                            synchronized(connectionLock) { endpointStop = source::requestStop; if (mode==UsbPcmDiagnosticMode.FLAC_FULL) fullStop=source::requestStop }
                            if (userStoppedStream==stream) source.requestStop()
                            if (!source.prepare { usbAccessFailure(token) } || !source.healthy()) {
                                source.finish()
                                val status = if (mode==UsbPcmDiagnosticMode.FLAC_FULL) UsbFlacFullStatus.sourceFailure(source.result) else when {
                                    source.result.count("sourceIntegrityMask") and 16L != 0L -> UsbSilentStatus.SOURCE_EOF_BEFORE_DURATION
                                    source.result.exceptionType != null || source.result.count("sourceIntegrityMask") and 32L.inv() != 0L -> UsbSilentStatus.FLAC_SOURCE_FAILED
                                    else -> UsbSilentStatus.QUEUE_PRIME_FAILED
                                }
                                return@withLock sourceBlocked(status, "FLAC validation/prebuffer failed; no USB ownership attempted.")
                            }
                            val sourceRateHz = source.result.count("sampleRate")
                            silentResolution = UsbSilentTransportPlan.resolve(model, selected.interfaces, sourceRateHz, source.result.count("bitsPerSample"), source.result.count("channels"))
                            resolution = silentResolution?.plan?.configuration ?: UsbAudioConfigurationResolution(issues = silentResolution?.issues.orEmpty())
                            if (resolution.plan == null) return@withLock sourceBlocked(UsbSilentStatus.USB_CONFIGURATION_FAILED, "Source-selected PCM transport configuration unavailable; no USB ownership attempted.")
                            currentCoroutineContext().ensureActive()
                            usbAccessFailure(token)?.let { return@withLock unavailable(it, "Access changed after FLAC priming; no USB ownership attempted.") }
                            withContext(Dispatchers.Main.immediate) {
                                if (!closed && gate.accepts(token)) {
                                    client?.progress?.invoke(UsbFullProgress(0,source.result.count("totalSamples"),sourceRateHz,UsbFullTransportPhase.STARTING,
                                        mapOf("sourceSeekCapable" to source.result.count("sourceSeekCapable"),"sourcePositionFrame" to 0L)))
                                    publish(withExperiment(state, currentExperiment()?.copy(phase = UsbSilentPhase.CONFIGURING)))
                                }
                            }
                        }
                        val opened = usb.openDevice(live) ?: return@withLock unavailable(
                            usbAccessFailure(token) ?: UsbClockQueryStatus.TRANSPORT_ERROR, "Android could not open the configuration connection.")
                        var runnerOwnsClose = false
                        try {
                            val accepted = synchronized(connectionLock) {
                                if (closed || inspectionToken != token) false else {
                                    connection = opened
                                    workerOwnedConnection = opened
                                    true
                                }
                            }
                            if (!accepted) return@withLock unavailable(UsbClockQueryStatus.STALE_GENERATION, "Generation changed before Session 6 registration.")
                            currentCoroutineContext().ensureActive()
                            usbAccessFailure(token)?.let { return@withLock unavailable(it, "Access changed before configuration setup.") }
                            val transport = UsbAudioConfigurationConnection(opened, live, appContext, requireNotNull(resolution.plan), token.generation)
                            if (silentRun) synchronized(connectionLock) {
                                endpointStop = { prefixSource?.requestStop(); transport.stopSilentTransport() }
                                if (mode==UsbPcmDiagnosticMode.FLAC_FULL) fullStop={ prefixSource?.requestStop(); transport.stopSilentTransport(authority=false) }
                            }
                            runnerOwnsClose = true
                            // Runner checks cancellation on forward steps and uses this cancellation-free
                            // attachment check for best-effort restoration on the original connection.
                            if (silentRun) {
                                val value = UsbSilentTransportExperiment.run(requireNotNull(silentResolution), token.generation, stream, transport,
                                    factory = { target, _ -> transport.silentBackend(target, token.generation, stream, mode, prefixSource) { usbAccessFailure(token) }.also { it.progressListener=client?.progress;it.playbackControl=client?.control } },
                                    onPhase = { phase -> withContext(Dispatchers.Main.immediate) {
                                        if (!closed && gate.accepts(token) && currentExperiment()?.streamGeneration == stream)
                                            publish(withExperiment(state, currentExperiment()?.copy(phase = phase)))
                                    } },
                                    mode = mode, prefixSource = prefixSource, accessFailure = { usbAccessFailure(token) })
                                silentResult = value
                                requireNotNull(value.configuration)
                            } else UsbAudioConfigurationExperiment.run(resolution, token.generation, transport) { usbAccessFailure(token) }
                        } finally {
                            synchronized(connectionLock) {
                                if (connection === opened) connection = null
                                if (workerOwnedConnection === opened) { workerOwnedConnection = null; endpointStop = null; fullStop=null }
                            }
                            if (!runnerOwnsClose) runCatching { opened.close() }
                        }
                    }
                }
            } catch (cancelled: CancellationException) {
                if (client==null) throw cancelled
                UsbAudioConfigurationResult(token.generation,resolution).invalidate(UsbClockQueryStatus.STALE_GENERATION,"Integrated owner/attachment stopped; cleanup completed before result.")
            }
            catch (_: SecurityException) { UsbAudioConfigurationResult(token.generation, resolution).invalidate(
                UsbClockQueryStatus.PERMISSION_LOST, "USB access denied during configuration setup.") }
            catch (_: Exception) { UsbAudioConfigurationResult(token.generation, resolution,
                targetStatus = UsbTargetConfigurationStatus.EXCEPTION, detail = "Configuration setup failed; no successful configuration inferred.") }
            finally {
                withContext(NonCancellable + Dispatchers.IO) {
                    try { prefixSource?.finish() }
                    finally {
                        originalFlacFd?.let { input ->
                            originalFlacFdClosed = runCatching { input.close(); true }.getOrDefault(false)
                        }
                        synchronized(connectionLock) { if (inspectionToken == token) { endpointStop = null; fullStop=null } }
                    }
                }
            }
            if (client!=null) {
                val candidate=finished(state,result).integratedFlacUsbExperiment
                val failure=if(closed || !gate.accepts(token)) UsbClockQueryStatus.STALE_GENERATION else usbAccessFailure(token)
                client.result=if(failure!=null) candidate?.invalidate(failure) else candidate
            }
            if (!closed && gate.accepts(token) && (!silentRun || currentExperiment()?.streamGeneration == stream)) {
                if (result.accessFailure == UsbClockQueryStatus.DEVICE_DISCONNECTED) {
                    invalidate(); refreshOnMain(excluded = identity); return@launch
                }
                when (val failure = usbAccessFailure(token)) {
                    UsbClockQueryStatus.DEVICE_DISCONNECTED -> { invalidate(); refreshOnMain(excluded = identity) }
                    UsbClockQueryStatus.STALE_GENERATION -> Unit
                    UsbClockQueryStatus.PERMISSION_LOST -> publish(finished(state.copy(status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED,
                        descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null,
                        devices = state.devices.map { if (it.identity == identity) it.copy(hasPermission = false) else it },
                        configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null), result.invalidate(failure, "Permission lost before configuration publication.")))
                    null -> publish(finished(state, result))
                    else -> publish(finished(state, result.invalidate(failure, "USB access could not be revalidated before publication.")))
                }
            }
        }
    }

    /** Shared live-device validation for clock, claimed-control and native diagnostics. */
    private fun usbAccessFailure(token: UsbAudioInspectionToken): UsbClockQueryStatus? {
        if (closed || inspectionToken != token) return UsbClockQueryStatus.STALE_GENERATION
        val usb = manager ?: return UsbClockQueryStatus.TRANSPORT_ERROR
        return try {
            val live = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == token.identity }
                ?: return UsbClockQueryStatus.DEVICE_DISCONNECTED
            if (!usb.hasPermission(live)) UsbClockQueryStatus.PERMISSION_LOST else null
        } catch (_: SecurityException) { UsbClockQueryStatus.PERMISSION_LOST }
        catch (_: RuntimeException) { UsbClockQueryStatus.TRANSPORT_ERROR }
    }

    private fun unavailableClockDiagnostics(generation: Long, resolution: UsbAudioClockResolution,
        failure: UsbClockQueryStatus, detail: String, withClaimExperiment: Boolean): UsbAudioClockDiagnostics =
        if (withClaimExperiment) UsbAudioClaimExperiment.diagnostics(UsbAudioClaimExperimentResult(generation, resolution,
            claimStatus = UsbAudioClaimExperiment.claimFailure(failure), detail = detail,
            accessFailure = failure.takeUnless { it == UsbClockQueryStatus.AMBIGUOUS_TARGET }))
        else UsbAudioClockControl.unavailable(generation, resolution, failure, detail)

    private fun invalidateClockDiagnostics(result: UsbAudioClockDiagnostics, failure: UsbClockQueryStatus,
        detail: String): UsbAudioClockDiagnostics = result.claimExperiment?.let {
        UsbAudioClaimExperiment.invalidate(it, failure, detail)
    } ?: UsbAudioClockControl.unavailable(result.generation, result.resolution, failure, detail)

    private fun snapshot(device: UsbDevice): UsbAudioDeviceSnapshot = UsbAudioDeviceSnapshot(
        device.diagnosticIdentity(), runCatching { device.manufacturerName }.getOrNull(),
        runCatching { device.productName }.getOrNull(), device.deviceClass,
        (0 until device.interfaceCount).map { i ->
            val iface = device.getInterface(i)
            UsbAndroidInterfaceSnapshot(iface.id, iface.alternateSetting, iface.interfaceClass,
                iface.interfaceSubclass, iface.interfaceProtocol, (0 until iface.endpointCount).map { e ->
                    val ep = iface.getEndpoint(e)
                    UsbAndroidEndpointSnapshot(ep.address, ep.attributes, ep.maxPacketSize, ep.interval)
                })
        }, manager?.hasPermission(device) == true)

    private fun publish(updated: UsbAudioDiagnosticState) {
        state = updated
        UsbAudioDiagnosticBridge.publish(this, updated)
    }

    private fun invalidate() {
        gate.select(null)
        inspectionToken = null
        cancelPermission()
        readJob?.cancel()
        readJob = null
        closeConnection()
        state = state.copy(selectedIdentity = null, descriptors = null, clockDiagnostics = null, usbFsDiagnostics = null, forcedExperiment = null, configurationExperiment = null, silentExperiment = null, referenceExperiment = null, flacUsbExperiment = null, fullFlacUsbExperiment = null, integratedFlacUsbExperiment = null, generation = gate.generation, message = null)
    }

    private fun cancelPermission() {
        pending = null
        permissionIntent?.cancel()
        permissionIntent = null
        permissionTimeout?.let(handler::removeCallbacks)
        permissionTimeout = null
    }

    private fun closeConnection() {
        synchronized(connectionLock) { endpointStop }?.invoke()
        val opened = synchronized(connectionLock) {
            connection?.takeUnless { it === workerOwnedConnection }.also { if (it != null) connection = null }
        }
        runCatching { opened?.close() }
    }

    fun close() {
        check(Looper.myLooper() == handler.looper)
        if (closed) return
        closed = true
        invalidate()
        if (registered) runCatching { appContext.unregisterReceiver(receiver) }
        registered = false
        scope.cancel()
        UsbAudioDiagnosticBridge.detach(this)
    }

    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == handler.looper) { if (!closed) action() }
        else handler.post { if (!closed) action() }
    }

    private companion object {
        // A recreated service also waits for a cancelled predecessor to release AC and
        // close. This is the same narrow serialized diagnostic I/O boundary, not a pool.
        val diagnosticIoMutex = Mutex()
        val streamGenerations = AtomicLong(0)
    }
}
