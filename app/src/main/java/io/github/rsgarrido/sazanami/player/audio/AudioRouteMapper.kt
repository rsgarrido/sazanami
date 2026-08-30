package io.github.rsgarrido.sazanami.player.audio

import android.media.AudioDeviceInfo

internal fun mapAudioRoute(
    deviceType: Int?,
    isLocalPlayback: Boolean
): AudioRouteInfo {
    if (!isLocalPlayback) {
        return AudioRouteInfo(
            category = AudioRouteCategory.REMOTE_CAST,
            isLocalPlayback = false
        )
    }

    val category = when (deviceType) {
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
        AudioDeviceInfo.TYPE_BUILTIN_SPEAKER_SAFE,
        AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> AudioRouteCategory.BUILT_IN_SPEAKER

        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_LINE_ANALOG,
        AudioDeviceInfo.TYPE_AUX_LINE -> AudioRouteCategory.WIRED_HEADPHONES

        AudioDeviceInfo.TYPE_USB_ACCESSORY,
        AudioDeviceInfo.TYPE_USB_DEVICE,
        AudioDeviceInfo.TYPE_USB_HEADSET -> AudioRouteCategory.USB

        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLUETOOTH_SCO -> AudioRouteCategory.BLUETOOTH_CLASSIC

        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
        AudioDeviceInfo.TYPE_BLE_BROADCAST -> AudioRouteCategory.BLUETOOTH_LE

        AudioDeviceInfo.TYPE_HDMI,
        AudioDeviceInfo.TYPE_HDMI_ARC,
        AudioDeviceInfo.TYPE_HDMI_EARC -> AudioRouteCategory.HDMI

        null,
        AudioDeviceInfo.TYPE_UNKNOWN -> AudioRouteCategory.UNKNOWN

        else -> AudioRouteCategory.OTHER
    }

    // Product names and device addresses are intentionally not accepted by this mapper.
    return AudioRouteInfo(
        category = category,
        productName = null,
        isLocalPlayback = true
    )
}
