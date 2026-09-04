package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Looper
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.Timeline
import androidx.media3.common.util.UnstableApi
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Exercises the actual logical Player exposed to MediaLibrarySession, not a mocked controller. */
@androidx.annotation.OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
class AndroidAutoLogicalPlayerTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private fun onMain(block: () -> Unit) {
        instrumentation.runOnMainSync(block)
        instrumentation.waitForIdleSync()
    }

    @Test
    fun middleIndexDuplicatesAndPositionUpdatesKeepTimelineStable() {
        lateinit var physical: StatePlayer
        lateinit var logical: SmoothPlaybackPlayer
        var timelines = 0
        var initial: Timeline = Timeline.EMPTY
        onMain {
            physical = StatePlayer("active", 2)
            logical = SmoothPlaybackPlayer(physical)
            logical.addListener(object : Player.Listener {
                override fun onTimelineChanged(timeline: Timeline, reason: Int) { timelines++ }
            })
            initial = logical.currentTimeline
            assertEquals(3, initial.windowCount)
            assertEquals(2, logical.currentMediaItemIndex)
            assertEquals("duplicate", logical.currentMediaItem!!.mediaId)
        }
        try {
            repeat(10) { tick -> onMain { physical.position((tick + 1) * 1000L) } }
            onMain {
                assertEquals(initial, logical.currentTimeline)
                assertEquals(2, logical.currentMediaItemIndex)
                assertEquals(0, timelines)
                logical.updateArtwork(logical.currentMediaItem!!, Uri.parse("content://test.visualassets/art"))
            }
            onMain {
                assertEquals("content://test.visualassets/art", logical.mediaMetadata.artworkUri.toString())
                assertEquals(initial, logical.currentTimeline)
                assertEquals(0, timelines)
            }
            for (repeatMode in listOf(Player.REPEAT_MODE_OFF, Player.REPEAT_MODE_ALL, Player.REPEAT_MODE_ONE)) {
                onMain { physical.policy(repeatMode, true) }
                onMain {
                    assertEquals(repeatMode, logical.repeatMode)
                    assertEquals(2, logical.currentMediaItemIndex)
                    assertEquals(initial, logical.currentTimeline)
                }
            }
        } finally { onMain { logical.release() } }
    }

    @Test
    fun intentionalCrossfadeExposesOneTransitionAndFullIncomingQueue() {
        lateinit var outgoing: StatePlayer
        lateinit var incoming: StatePlayer
        lateinit var logical: SmoothPlaybackPlayer
        var transitions = 0
        onMain {
            outgoing = StatePlayer("outgoing", 0)
            incoming = StatePlayer("incoming", 1)
            logical = SmoothPlaybackPlayer(outgoing)
            logical.addListener(object : Player.Listener {
                override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) { transitions++ }
            })
            logical.rebindPhysicalPlayerForCrossfade(incoming, 1f, true)
        }
        try {
            onMain {
                assertEquals(3, logical.currentTimeline.windowCount)
                assertEquals(1, logical.currentMediaItemIndex)
                assertEquals("middle", logical.currentMediaItem!!.mediaId)
                assertEquals(listOf("duplicate", "middle", "duplicate"),
                    (0 until logical.mediaItemCount).map { logical.getMediaItemAt(it).mediaId })
                assertEquals(1, transitions)
            }
        } finally { onMain { logical.release(); outgoing.release() } }
    }

    private class StatePlayer(role: String, index: Int) : SimpleBasePlayer(Looper.getMainLooper()) {
        private var state = State.Builder()
            .setAvailableCommands(Player.Commands.Builder().addAllCommands().build())
            .setPlaylist(listOf("duplicate", "middle", "duplicate").mapIndexed { i, id ->
                MediaItemData.Builder("$role-entry-$i")
                    .setMediaItem(MediaItem.Builder().setMediaId(id).setUri("content://media/$id").build())
                    .setDurationUs(60_000_000).build()
            })
            .setCurrentMediaItemIndex(index)
            .setPlaybackState(Player.STATE_READY)
            .setPlayWhenReady(true, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()

        override fun getState(): State = state
        fun position(ms: Long) {
            state = state.buildUpon().setContentPositionMs(ms).build()
            invalidateState()
        }
        fun policy(repeatMode: Int, shuffle: Boolean) {
            state = state.buildUpon().setRepeatMode(repeatMode).setShuffleModeEnabled(shuffle).build()
            invalidateState()
        }
        override fun handleSetVolume(volume: Float): ListenableFuture<*> = Futures.immediateVoidFuture()
        override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
    }
}
