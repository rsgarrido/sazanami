package io.github.rsgarrido.sazanami.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test

class AndroidAutoControllerCommandPolicyTest {
    @Test
    fun `media notification controller cannot populate the platform queue`() {
        val unavailable = AndroidAutoControllerCommandPolicy.unavailablePlayerCommands(
            isMediaNotificationController = true
        )

        assertEquals(setOf(Player.COMMAND_GET_TIMELINE), unavailable)
        assertTransportCommandsRemain(unavailable)
    }

    @Test
    fun `android auto companion is not denied unrelated player commands`() {
        val unavailable = AndroidAutoControllerCommandPolicy.unavailablePlayerCommands(
            isMediaNotificationController = false
        )

        assertEquals(emptySet<Int>(), unavailable)
    }

    @Test
    fun `sazanami controller retains the complete command set`() {
        val unavailable = AndroidAutoControllerCommandPolicy.unavailablePlayerCommands(
            isMediaNotificationController = false
        )

        assertEquals(emptySet<Int>(), unavailable)
    }

    private fun assertTransportCommandsRemain(unavailable: Set<Int>) {
        assertFalse(unavailable.contains(Player.COMMAND_PLAY_PAUSE))
        assertFalse(unavailable.contains(Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM))
        assertFalse(unavailable.contains(Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
        assertFalse(unavailable.contains(Player.COMMAND_SET_SHUFFLE_MODE))
        assertFalse(unavailable.contains(Player.COMMAND_SET_REPEAT_MODE))
    }
}
