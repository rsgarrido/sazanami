package io.github.rsgarrido.sazanami.player

import androidx.annotation.OptIn
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi

/** Keeps the platform/Android Auto queue presentation separate from the real player timeline. */
@OptIn(UnstableApi::class)
internal object AndroidAutoControllerCommandPolicy {
    fun playerCommands(
        availableCommands: Player.Commands,
        isMediaNotificationController: Boolean
    ): Player.Commands {
        val unavailableCommands = unavailablePlayerCommands(isMediaNotificationController)
        if (unavailableCommands.isEmpty()) return availableCommands
        val builder = availableCommands.buildUpon()
        unavailableCommands.forEach(builder::remove)
        return builder.build()
    }

    fun unavailablePlayerCommands(
        isMediaNotificationController: Boolean
    ): Set<Int> = if (isMediaNotificationController) {
        setOf(Player.COMMAND_GET_TIMELINE)
    } else {
        emptySet()
    }
}
