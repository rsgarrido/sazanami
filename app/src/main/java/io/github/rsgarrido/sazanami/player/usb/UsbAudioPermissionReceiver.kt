package io.github.rsgarrido.sazanami.player.usb

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.hardware.usb.UsbDevice
import android.hardware.usb.UsbManager
import android.os.Build

/** Explicit, non-exported PendingIntent target. It never opens a device or starts playback. */
class UsbAudioPermissionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != context.packageName + ACTION_SUFFIX) return
        val requestId = intent.getStringExtra(EXTRA_REQUEST_ID) ?: return
        val device = runCatching { intent.usbDevice() }.getOrNull()
        UsbAudioDiagnosticBridge.permissionResult(requestId, device?.diagnosticIdentity(),
            intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false))
    }

    internal companion object {
        const val ACTION_SUFFIX = ".USB_AUDIO_DIAGNOSTIC_PERMISSION"
        const val EXTRA_REQUEST_ID = "usb_diagnostic_request_id"
    }
}

@Suppress("DEPRECATION")
internal fun Intent.usbDevice(): UsbDevice? = if (Build.VERSION.SDK_INT >= 33)
    getParcelableExtra(UsbManager.EXTRA_DEVICE, UsbDevice::class.java)
else getParcelableExtra(UsbManager.EXTRA_DEVICE)

internal fun UsbDevice.diagnosticIdentity(): UsbAudioDeviceIdentity =
    UsbAudioDeviceIdentity(deviceName, deviceId, vendorId, productId)
