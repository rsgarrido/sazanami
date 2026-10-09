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
import android.os.Build
import android.os.Handler
import android.os.Looper
import androidx.core.content.ContextCompat
import java.util.UUID
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Service-owned, non-invasive USB inspection. No claims, controls, endpoint I/O, or native code. */
internal class UsbAudioController(context: Context) {
    private val appContext = context.applicationContext
    private val manager = appContext.getSystemService(Context.USB_SERVICE) as? UsbManager
    private val handler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = UsbAudioGenerationGate()
    private val connectionLock = Any()
    private var connection: UsbDeviceConnection? = null
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
                        publish(state.copy(descriptors = null,
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
                generation = gate.generation,
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
            descriptors = null, message = null, status = UsbAudioDiagnosticStatus.PERMISSION_REQUIRED))
        if (manager?.hasPermission(device) == true) inspect(token)
    }

    fun requestPermission(identity: UsbAudioDeviceIdentity) = onMain {
        val usb = manager ?: return@onMain
        val token = gate.current ?: return@onMain
        if (identity != token.identity || pending != null || state.status == UsbAudioDiagnosticStatus.OPENING) return@onMain
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
            publish(state.copy(status = UsbAudioDiagnosticStatus.REQUESTING_PERMISSION, message = null, descriptors = null))
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
            descriptors = null, message = "USB access was not granted. No device configuration was changed."))
        else if (!granted) publish(state.copy(status = UsbAudioDiagnosticStatus.ERROR, descriptors = null,
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
            descriptors = null, message = null))
        readJob = scope.launch {
            val result = try {
                withContext(Dispatchers.IO) {
                    val usb = manager ?: return@withContext InspectionResult(error = "USB host API unavailable.")
                    val device = usb.deviceList.values.firstOrNull { it.diagnosticIdentity() == token.identity }
                        ?: return@withContext InspectionResult(error = "Device disconnected before inspection.")
                    if (!usb.hasPermission(device)) return@withContext InspectionResult(error = "USB permission no longer available.")
                    val opened = usb.openDevice(device)
                        ?: return@withContext InspectionResult(error = "Android could not open the USB device for inspection.")
                    try {
                        val accepted = synchronized(connectionLock) {
                            if (closed || inspectionToken != token) false else { connection = opened; true }
                        }
                        if (!accepted) return@withContext InspectionResult()
                        // The only device read is Android's raw-descriptor API. No interface is
                        // claimed, selected, reset or detached; no class-specific request is sent.
                        val bytes = opened.rawDescriptors?.copyOf() ?: byteArrayOf()
                        InspectionResult(model = UsbAudioDescriptorParser.parse(bytes))
                    } finally {
                        synchronized(connectionLock) { if (connection === opened) connection = null }
                        runCatching { opened.close() }
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
        state = state.copy(selectedIdentity = null, descriptors = null, generation = gate.generation, message = null)
    }

    private fun cancelPermission() {
        pending = null
        permissionIntent?.cancel()
        permissionIntent = null
        permissionTimeout?.let(handler::removeCallbacks)
        permissionTimeout = null
    }

    private fun closeConnection() {
        val opened = synchronized(connectionLock) { connection.also { connection = null } }
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
}
