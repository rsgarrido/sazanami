package io.github.rsgarrido.sazanami.player

import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.PlaybackQueueWorkingOutput
import io.github.rsgarrido.sazanami.data.workingOutput
import io.github.rsgarrido.sazanami.player.usb.UsbFullTransportPhase
import kotlinx.coroutines.withTimeout
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Real Room queue contexts and routing; the native worker is simulated and explicitly opted in. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
internal class ExperimentalUsbDirectQueueIntegrationTest : DirectQueueTestFixture() {
    @Test fun optingOutRestoresPausedAndroidAndPreservesIndependentDirectAndSavedQueues() = check { f ->
        f.withController { controller ->
            val saved = f.saved()
            val original = checkNotNull(f.repository.loadQueue(f.originalId))
            val first = f.startAt()
            controller.playSelectedSong(b, listOf(b, c, b))
            first.stopped.await();first.terminal.complete(terminal(first))
            val direct = f.engine.next();f.confirm(direct)
            assertTrue(withTimeout(5000) { f.contextResults.receive() })
            val directId = checkNotNull(f.coordinator.getActiveQueueId())
            assertNotEquals(f.originalId, directId)
            assertEquals(PlaybackQueueWorkingOutput.DIRECT, f.repository.loadQueue(directId)?.queue?.workingOutput)
            assertEquals(PlaybackQueueWorkingOutput.ANDROID, original.queue.workingOutput)
            f.coordinator.persistActiveQueueSnapshot()
            val checkpoint = checkNotNull(f.repository.loadQueue(directId))
            assertEquals(0L, checkpoint.queue.currentPositionMs)
            // Revoke at an uncheckpointed position: exit must retain the already accepted context.
            direct.emit(UsbFullTransportPhase.PLAYING, 192000)

            f.experimentalOptIn = false;f.route.usbSelectionChanged()
            direct.stopped.await()
            assertEquals(0, f.recreations);assertNotNull(f.reservation.owner)
            direct.terminal.complete(terminal(direct));f.settled();f.assertRestoredAndroid()
            assertEquals(0, f.engine.active);assertNull(f.reservation.owner)
            assertFalse(f.route.playWhenReady)
            assertEquals(checkpoint.entries, f.repository.loadQueue(directId)?.entries)
            assertEquals(checkpoint.queue.currentEntryId, f.repository.loadQueue(directId)?.queue?.currentEntryId)
            assertEquals(4000L, f.repository.loadQueue(directId)?.queue?.currentPositionMs)
            assertEquals(saved, f.repository.loadQueue(saved.queue.queueId))
            assertEquals(original.entries, f.repository.loadQueue(f.originalId)?.entries)
            assertEquals(3, f.repository.listQueues().size)

            // Reconnection while opted out cannot acquire Direct's owner or overwrite either context.
            f.gate.select(null);f.route.usbSelectionChanged()
            f.token = f.gate.select(f.identity)!!;f.route.usbSelectionChanged()
            f.route.selectDirect(true, f.identity, f.token.generation);f.settled()
            assertFalse(f.route.isDirectSelected);assertEquals(2, f.engine.runs.size)
            assertNull(f.reservation.owner);f.assertRestoredAndroid()
            direct.emit(UsbFullTransportPhase.PLAYING, 240000)
            f.coordinator.persistActiveQueueSnapshot()
            assertEquals(4000L, f.repository.loadQueue(directId)?.queue?.currentPositionMs)

            // A later acknowledgment makes selection possible; acknowledgment itself starts nothing.
            f.experimentalOptIn = true
            assertFalse(f.route.isDirectSelected);assertEquals(2, f.engine.runs.size)
            f.route.selectDirect(true, f.identity, f.token.generation);f.settled()
            assertTrue(f.route.directState.confirmedDirect);assertEquals(2, f.engine.runs.size)
            assertEquals(saved, f.repository.loadQueue(saved.queue.queueId))
            assertEquals(checkpoint.entries, f.repository.loadQueue(directId)?.entries)
        }
    }
}
