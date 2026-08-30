package io.github.rsgarrido.sazanami.player.audio

import android.media.AudioDeviceInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class AudioRouteMapperTest {
    @Test
    fun usbBluetoothClassicAndBluetoothLeAreDistinct() {
        assertEquals(
            AudioRouteCategory.USB,
            mapAudioRoute(AudioDeviceInfo.TYPE_USB_HEADSET, true).category
        )
        assertEquals(
            AudioRouteCategory.BLUETOOTH_CLASSIC,
            mapAudioRoute(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true).category
        )
        assertEquals(
            AudioRouteCategory.BLUETOOTH_LE,
            mapAudioRoute(AudioDeviceInfo.TYPE_BLE_HEADSET, true).category
        )
    }

    @Test
    fun remotePlaybackDoesNotPretendToBeALocalDevice() {
        val route = mapAudioRoute(AudioDeviceInfo.TYPE_USB_DEVICE, false)

        assertEquals(AudioRouteCategory.REMOTE_CAST, route.category)
        assertFalse(route.isLocalPlayback)
    }

    @Test
    fun mapperCannotPublishHardwareAddressOrProductName() {
        val route = mapAudioRoute(AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, true)

        assertNull(route.productName)
    }
}
