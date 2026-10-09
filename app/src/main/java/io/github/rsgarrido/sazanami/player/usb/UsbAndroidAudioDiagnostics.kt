package io.github.rsgarrido.sazanami.player.usb

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build

/** Secondary framework evidence only. Does not select routes, change attributes or match by product name. */
internal object UsbAndroidAudioInspector {
    fun inspect(context: Context): UsbAndroidAudioDiagnostics {
        return try {
            val manager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
                ?: return UsbAndroidAudioDiagnostics(detail = "AudioManager unavailable.")
            val outputs = manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS).filter {
                it.type in setOf(AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET, AudioDeviceInfo.TYPE_USB_ACCESSORY)
            }.map {
                val address = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) UsbFsEvidence.safeAudioAddress(it.address) else null
                UsbAndroidAudioOutput(it.productName?.toString(), it.type, it.id, address,
                    it.sampleRates.toList(), it.encodings.toList(), it.channelCounts.toList(), it.channelMasks.toList(), it.channelIndexMasks.toList())
            }
            UsbAndroidAudioDiagnostics(outputs, "USB output candidates only; no unique match to the selected UsbDevice is asserted.")
        } catch (_: SecurityException) { UsbAndroidAudioDiagnostics(detail = "Android denied the AudioDeviceInfo query.") }
        catch (_: RuntimeException) { UsbAndroidAudioDiagnostics(detail = "Android USB audio capability query unavailable.") }
    }
}
