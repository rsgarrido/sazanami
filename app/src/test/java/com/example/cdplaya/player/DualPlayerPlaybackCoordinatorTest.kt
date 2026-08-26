package com.example.cdplaya.player

import android.net.Uri
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import com.example.cdplaya.player.equalizer.EqualizerAudioProcessor
import com.example.cdplaya.player.equalizer.EqualizerRuntimeBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.inOrder
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DualPlayerPlaybackCoordinatorTest {
    @Before
    fun setUp() {
        EqualizerRuntimeBridge.release()
    }

    @After
    fun tearDown() {
        EqualizerRuntimeBridge.release()
    }

    @Test
    fun targetResolverPreservesConservativePoliciesAndQueueContinuation() {
        val first = localItem("first")
        val second = localItem("second")
        val third = localItem("third")

        val plan = StandbyTargetResolver.resolvePlan(
            snapshot(listOf(first, second, third), currentIndex = 0)
        )

        assertSame(second, plan?.target)
        assertEquals(listOf(first, second, third), plan?.playlist)
        assertEquals(1, plan?.startIndex)
        assertNull(
            StandbyTargetResolver.resolve(
                snapshot(
                    listOf(first, second),
                    currentIndex = 0,
                    repeatMode = Player.REPEAT_MODE_ONE
                )
            )
        )
        assertNull(
            StandbyTargetResolver.resolve(
                snapshot(
                    listOf(first, second),
                    currentIndex = 0,
                    shuffleModeEnabled = true
                )
            )
        )
        val repeatAll = StandbyTargetResolver.resolvePlan(
            snapshot(
                listOf(first, second, third),
                currentIndex = 2,
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )
        assertSame(first, repeatAll?.target)
        assertEquals(listOf(first, second, third), repeatAll?.playlist)
        assertEquals(0, repeatAll?.startIndex)
    }

    @Test
    fun changedFailedAndRemovedTargetsInvalidateSilentPreparation() {
        val output = RecordingStandbyOutput()
        val controller = StandbyPreparationController(output)
        val current = localItem("current")
        val original = localItem("original")
        val replacement = localItem("replacement")

        controller.synchronize(snapshot(listOf(current, original), 0))
        assertSame(original, output.preparedItem)
        assertTrue(output.silentWhenPrepared)

        controller.onError()
        controller.synchronize(snapshot(listOf(current, original), 0))
        assertEquals(1, output.prepareCount)
        assertEquals(StandbyPreparationStatus.EMPTY, controller.state.status)

        controller.synchronize(snapshot(listOf(current, replacement), 0))
        assertSame(replacement, output.preparedItem)
        controller.synchronize(snapshot(listOf(current), 0))
        assertNull(output.preparedItem)
        assertEquals(StandbyPreparationStatus.EMPTY, controller.state.status)
    }

    @Test
    fun naturalHandoffsAlternateAtoBtoAWithoutLeakingStandbyAuthority() {
        val first = localItem("first")
        val second = localItem("second")
        val third = localItem("third")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        var currentItemA = first
        var currentIndexA = 0
        stubPlaylist(playerA, listOf(first, second, third), 0)
        stubPlaylist(playerB, listOf(first, second, third), 1)
        `when`(playerA.currentMediaItemIndex).thenAnswer { currentIndexA }
        `when`(playerA.currentMediaItem).thenAnswer { currentItemA }
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.currentPosition).thenReturn(111L)
        `when`(playerB.currentPosition).thenReturn(222L)
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val coordinator = DualPlayerPlaybackCoordinator(pipelineA, pipelineB)
        val logical = RecordingLogicalPlayer(playerA)
        val integration = RecordingIntegration()
        coordinator.selectActiveTelemetry()
        coordinator.attachLogicalPlayer(logical, integration)

        assertSame(playerA, coordinator.logicalPhysicalPlayer)
        assertSame(first, coordinator.logicalPhysicalPlayer.currentMediaItem)
        assertEquals(111L, coordinator.logicalPhysicalPlayer.currentPosition)
        assertTrue(logical.reboundPlayers.isEmpty())
        assertTrue(integration.transitions.isEmpty())

        coordinator.markStandbyReadyForTest()
        assertTrue(coordinator.attemptNaturalHandoffForTest())

        assertSame(pipelineB, coordinator.active)
        assertSame(pipelineA, coordinator.standby)
        assertSame(playerB, coordinator.logicalPhysicalPlayer)
        assertSame(
            pipelineB.equalizerRuntime,
            EqualizerRuntimeBridge.selectedTelemetryRuntime()
        )
        assertSame(second, coordinator.logicalPhysicalPlayer.currentMediaItem)
        assertEquals(222L, coordinator.logicalPhysicalPlayer.currentPosition)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineB.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineA.role)
        assertEquals(1, listOf(pipelineA, pipelineB).count {
            it.role == PhysicalPlayerRole.ACTIVE
        })
        assertEquals(listOf(playerB), logical.reboundPlayers)
        assertEquals(listOf(second), integration.transitions.map {
            it?.incomingMediaItem
        })
        val firstPromotionOrder = inOrder(playerA, playerB)
        firstPromotionOrder.verify(playerA).volume = 0f
        firstPromotionOrder.verify(playerA).playWhenReady = false
        firstPromotionOrder.verify(playerB).volume = 1f
        firstPromotionOrder.verify(playerB).playWhenReady = true
        verify(playerA).setMediaItems(listOf(first, second, third), 2, 0L)

        currentItemA = third
        currentIndexA = 2
        coordinator.markStandbyReadyForTest()
        assertTrue(coordinator.attemptNaturalHandoffForTest())

        assertSame(pipelineA, coordinator.active)
        assertSame(pipelineB, coordinator.standby)
        assertSame(playerA, coordinator.logicalPhysicalPlayer)
        assertEquals(listOf(playerB, playerA), logical.reboundPlayers)
        assertEquals(listOf(second, third), integration.transitions.map {
            it?.incomingMediaItem
        })
        assertEquals(2, logical.activationCount)
        assertEquals(listOf(pipelineA, pipelineB), integration.unbound)
        assertEquals(1, listOf(pipelineA, pipelineB).count {
            it.role == PhysicalPlayerRole.ACTIVE
        })

        coordinator.release()
    }

    @Test
    fun staleQueueTargetAndFailedStandbyFallBackToCurrentPlayer() {
        val first = localItem("first")
        val prepared = localItem("prepared")
        val replacement = localItem("replacement")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        `when`(playerA.mediaItemCount).thenReturn(2)
        `when`(playerA.currentMediaItemIndex).thenReturn(0)
        `when`(playerA.getMediaItemAt(0)).thenReturn(first)
        `when`(playerA.getMediaItemAt(1)).thenReturn(prepared, replacement)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerA.repeatMode).thenReturn(Player.REPEAT_MODE_OFF)
        `when`(playerA.shuffleModeEnabled).thenReturn(false)
        `when`(playerB.currentMediaItem).thenReturn(prepared)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val coordinator = DualPlayerPlaybackCoordinator(pipelineA, pipelineB)
        val integration = RecordingIntegration()
        coordinator.attachLogicalPlayer(RecordingLogicalPlayer(playerA), integration)
        coordinator.markStandbyReadyForTest()

        assertFalse(coordinator.attemptNaturalHandoffForTest())
        assertSame(pipelineA, coordinator.active)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineB.role)
        assertTrue(integration.transitions.isEmpty())
        verify(playerA).playWhenReady = true

        coordinator.markStandbyFailedForTest()
        assertFalse(coordinator.attemptNaturalHandoffForTest())
        assertSame(pipelineA, coordinator.active)

        coordinator.release()
    }

    @Test
    fun replayGainBaselinesRemainRoleAndMediaKeyScoped() {
        val first = localItem("first")
        val second = localItem("second")
        val third = localItem("third")
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        val secondKey = checkNotNull(StandbyTargetResolver.key(second))
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second, third), 0)
        stubPlaylist(playerB, listOf(first, second, third), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        pipelineA.prepareBaseline(firstKey)
        assertTrue(pipelineA.updateBaseline(firstKey, 0.62f))
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val coordinator = DualPlayerPlaybackCoordinator(pipelineA, pipelineB)
        val logical = RecordingLogicalPlayer(playerA)
        coordinator.attachLogicalPlayer(logical, RecordingIntegration())

        assertTrue(coordinator.updateStandbyBaseline(secondKey, 0.35f))
        assertEquals(0.62f, pipelineA.baselineVolume, 0f)
        assertEquals(0.35f, pipelineB.baselineVolume, 0f)
        assertFalse(coordinator.updateStandbyBaseline(firstKey, 0.9f))
        assertEquals(0.35f, pipelineB.baselineVolume, 0f)
        verify(playerB, never()).volume = 0.35f

        coordinator.markStandbyReadyForTest()
        assertTrue(coordinator.attemptNaturalHandoffForTest())

        assertEquals(listOf(0.35f), logical.reboundBaselines)
        assertEquals(0.35f, pipelineB.baselineVolume, 0f)
        assertFalse(coordinator.updateStandbyBaseline(secondKey, 0.8f))
        assertEquals(1f, pipelineA.baselineVolume, 0f)

        coordinator.release()
    }

    @Test
    fun promotionFailureRollsBackToExactlyOneActiveRole() {
        val first = localItem("first")
        val second = localItem("second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val coordinator = DualPlayerPlaybackCoordinator(pipelineA, pipelineB)
        val logical = RecordingLogicalPlayer(playerA)
        coordinator.attachLogicalPlayer(logical, FailingOnceIntegration())
        coordinator.markStandbyReadyForTest()

        assertFalse(coordinator.attemptNaturalHandoffForTest())

        assertSame(pipelineA, coordinator.active)
        assertSame(pipelineB, coordinator.standby)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineA.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineB.role)
        assertEquals(1, listOf(pipelineA, pipelineB).count {
            it.role == PhysicalPlayerRole.ACTIVE
        })
        assertEquals(listOf(playerB, playerA), logical.reboundPlayers)
        verify(playerB, atLeastOnce()).playWhenReady = false
        verify(playerA).playWhenReady = true

        coordinator.release()
    }

    @Test
    fun telemetryListenersAndLifecycleFollowOnlyThePromotedRole() {
        val first = localItem("first")
        val second = localItem("second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val coordinator = DualPlayerPlaybackCoordinator(pipelineA, pipelineB)
        val logical = RecordingLogicalPlayer(playerA)
        val integration = RecordingIntegration()
        coordinator.attachLogicalPlayer(logical, integration)
        coordinator.selectActiveTelemetry()
        assertSame(
            pipelineA.equalizerRuntime,
            EqualizerRuntimeBridge.selectedTelemetryRuntime()
        )
        val oldListener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(playerA).addListener(oldListener.capture())

        coordinator.markStandbyReadyForTest()
        assertTrue(coordinator.attemptNaturalHandoffForTest())
        assertSame(
            pipelineB.equalizerRuntime,
            EqualizerRuntimeBridge.selectedTelemetryRuntime()
        )
        val bindsBeforeStaleCallback = integration.bound.size
        oldListener.value.onPlayWhenReadyChanged(
            false,
            Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
        )
        assertEquals(bindsBeforeStaleCallback, integration.bound.size)

        coordinator.release()

        verify(playerA, times(1)).release()
        verify(playerB, times(1)).release()
        assertEquals(0, EqualizerRuntimeBridge.registeredRuntimeCountForTest())
        assertNull(EqualizerRuntimeBridge.selectedTelemetryRuntime())
    }

    @Test
    fun audibleCrossfadeHandsOffAtMidpointAndPromotesOnlyAtCompletion() {
        val first = localItem("first")
        val second = localItem("second")
        val third = localItem("third")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        var outgoingPosition = 5_000L
        var incomingPosition = 0L
        var currentItemA = first
        var currentIndexA = 0
        stubPlaylist(playerA, listOf(first, second, third), 0)
        stubPlaylist(playerB, listOf(first, second, third), 1)
        `when`(playerA.currentMediaItem).thenAnswer { currentItemA }
        `when`(playerA.currentMediaItemIndex).thenAnswer { currentIndexA }
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(10_000L)
        `when`(playerB.duration).thenReturn(10_000L)
        `when`(playerA.currentPosition).thenAnswer { outgoingPosition }
        `when`(playerB.currentPosition).thenAnswer { incomingPosition }
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        `when`(playerB.isPlaying).thenReturn(true)
        `when`(playerA.playWhenReady).thenReturn(true)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        pipelineA.prepareBaseline(firstKey)
        pipelineA.updateBaseline(firstKey, 0.6f)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            standbyBaselinePreparer = StandbyBaselinePreparer { _, result ->
                result(0.4f)
                true
            },
            crossfadeClock = clock,
            crossfadeScheduler = scheduler
        )
        val logical = RecordingLogicalPlayer(playerA)
        val integration = RecordingIntegration()
        coordinator.selectActiveTelemetry()
        coordinator.attachLogicalPlayer(logical, integration)
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()

        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)
        assertVolumeWasZeroBeforePlaybackStart(playerB)
        verify(playerB).playWhenReady = true
        assertSame(pipelineA, coordinator.active)
        assertTrue(integration.transitions.isEmpty())

        outgoingPosition = 7_500L
        scheduler.runNext()

        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            coordinator.crossfadeState
        )
        assertSame(playerB, coordinator.logicalPhysicalPlayer)
        assertSame(
            pipelineB.equalizerRuntime,
            EqualizerRuntimeBridge.selectedTelemetryRuntime()
        )
        assertSame(pipelineA, coordinator.active)
        assertEquals(listOf(second), integration.transitions.map {
            it?.incomingMediaItem
        })
        assertEquals(0.3f, lastSetVolume(playerA), 0.0001f)
        assertEquals(0.2f, lastSetVolume(playerB), 0.0001f)
        verify(playerA, never()).playWhenReady = false

        outgoingPosition = 10_000L
        scheduler.runNext()

        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)
        assertSame(pipelineB, coordinator.active)
        assertSame(pipelineA, coordinator.standby)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineB.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineA.role)
        verify(playerA).setMediaItems(listOf(first, second, third), 2, 0L)

        currentItemA = third
        currentIndexA = 2
        incomingPosition = 5_000L
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        incomingPosition = 7_500L
        scheduler.runNext()
        assertSame(playerA, coordinator.logicalPhysicalPlayer)
        incomingPosition = 10_000L
        scheduler.runNext()

        assertSame(pipelineA, coordinator.active)
        assertSame(pipelineB, coordinator.standby)
        assertEquals(listOf(second, third), integration.transitions.map {
            it?.incomingMediaItem
        })

        coordinator.release()
    }

    private fun pipeline(
        role: PhysicalPlayerRole,
        player: ExoPlayer
    ): PhysicalPlayerPipeline {
        val runtime = EqualizerRuntimeBridge.createRuntime()
        return PhysicalPlayerPipeline(
            initialRole = role,
            player = player,
            equalizerRuntime = runtime,
            equalizerAudioProcessor = EqualizerAudioProcessor(runtime),
            audioAttributes = AUDIO_ATTRIBUTES
        )
    }

    private fun stubPlaylist(
        player: ExoPlayer,
        items: List<MediaItem>,
        currentIndex: Int
    ) {
        `when`(player.mediaItemCount).thenReturn(items.size)
        `when`(player.currentMediaItemIndex).thenReturn(currentIndex)
        items.forEachIndexed { index, item ->
            `when`(player.getMediaItemAt(index)).thenReturn(item)
        }
        `when`(player.repeatMode).thenReturn(Player.REPEAT_MODE_OFF)
        `when`(player.shuffleModeEnabled).thenReturn(false)
        `when`(player.playbackParameters).thenReturn(PlaybackParameters.DEFAULT)
        `when`(player.trackSelectionParameters).thenReturn(
            TrackSelectionParameters.DEFAULT
        )
    }

    private fun snapshot(
        items: List<MediaItem>,
        currentIndex: Int,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        shuffleModeEnabled: Boolean = false
    ) = ActivePlaylistSnapshot(
        mediaItems = items,
        currentMediaItemIndex = currentIndex,
        repeatMode = repeatMode,
        shuffleModeEnabled = shuffleModeEnabled
    )

    private fun localItem(id: String): MediaItem {
        val uri = mock(Uri::class.java)
        `when`(uri.scheme).thenReturn("content")
        `when`(uri.toString()).thenReturn("content://media/$id")
        return MediaItem.Builder()
            .setMediaId(id)
            .setUri(uri)
            .build()
    }

    private fun lastSetVolume(player: ExoPlayer): Float =
        org.mockito.Mockito.mockingDetails(player).invocations
            .filter { invocation -> invocation.method.name == "setVolume" }
            .last().arguments.single() as Float

    private fun assertVolumeWasZeroBeforePlaybackStart(player: ExoPlayer) {
        val invocations = org.mockito.Mockito.mockingDetails(player).invocations
        val playbackStartSequence = invocations
            .first { invocation ->
                invocation.method.name == "setPlayWhenReady" &&
                    invocation.arguments.single() == true
            }
            .sequenceNumber
        assertTrue(
            invocations.any { invocation ->
                invocation.sequenceNumber < playbackStartSequence &&
                    invocation.method.name == "setVolume" &&
                    invocation.arguments.single() == 0f
            }
        )
    }

    private class RecordingLogicalPlayer(
        var physicalPlayer: Player,
        override var logicalPlayWhenReady: Boolean = true
    ) : LogicalPlayerRoleBinding {
        val reboundPlayers = mutableListOf<Player>()
        val reboundBaselines = mutableListOf<Float>()
        var activationCount = 0

        override fun rebindPhysicalPlayer(
            newPhysicalPlayer: Player,
            baselineVolume: Float,
            logicalPlayWhenReady: Boolean
        ) {
            physicalPlayer = newPhysicalPlayer
            this.logicalPlayWhenReady = logicalPlayWhenReady
            reboundPlayers += newPhysicalPlayer
            reboundBaselines += baselineVolume
            newPhysicalPlayer.volume = baselineVolume
        }

        override fun rebindPhysicalPlayerForCrossfade(
            newPhysicalPlayer: Player,
            baselineVolume: Float,
            logicalPlayWhenReady: Boolean
        ) {
            physicalPlayer = newPhysicalPlayer
            this.logicalPlayWhenReady = logicalPlayWhenReady
            reboundPlayers += newPhysicalPlayer
            reboundBaselines += baselineVolume
        }

        override fun setCrossfadeVolumeControlActive(active: Boolean) = Unit

        override fun finishCrossfadeVolumeControl(baselineVolume: Float) = Unit

        override fun activateReboundPhysicalPlayer() {
            activationCount += 1
            physicalPlayer.playWhenReady = logicalPlayWhenReady
        }
    }

    private class RecordingIntegration : ActivePlayerIntegration {
        val unbound = mutableListOf<PhysicalPlayerPipeline>()
        val bound = mutableListOf<PhysicalPlayerPipeline>()
        val transitions = mutableListOf<AuthoritativeRoleTransition?>()

        override fun unbind(pipeline: PhysicalPlayerPipeline) {
            unbound += pipeline
        }

        override fun bind(
            pipeline: PhysicalPlayerPipeline,
            transition: AuthoritativeRoleTransition?
        ) {
            bound += pipeline
            transitions += transition
        }
    }

    private class FailingOnceIntegration : ActivePlayerIntegration {
        private var shouldFail = true

        override fun unbind(pipeline: PhysicalPlayerPipeline) = Unit

        override fun bind(
            pipeline: PhysicalPlayerPipeline,
            transition: AuthoritativeRoleTransition?
        ) {
            if (shouldFail) {
                shouldFail = false
                throw IllegalStateException("Injected handoff binding failure")
            }
        }
    }

    private class RecordingStandbyOutput : StandbyPreparationOutput {
        var preparedItem: MediaItem? = null
        var prepareCount = 0
        var silenceCount = 0
        var silentWhenPrepared = false

        override fun enforceSilence() {
            silenceCount += 1
        }

        override fun prepare(plan: StandbyPreparationPlan): Boolean {
            prepareCount += 1
            silentWhenPrepared = silenceCount > 0
            preparedItem = plan.target
            return true
        }

        override fun clear() {
            preparedItem = null
        }
    }

    private class ManualCrossfadeClock : CrossfadeClock {
        var nowMillis = 0L
        override fun elapsedRealtimeMillis(): Long = nowMillis
    }

    private class ManualCrossfadeScheduler(
        private val clock: ManualCrossfadeClock
    ) : CrossfadeScheduler {
        private data class Scheduled(
            val delayMillis: Long,
            val action: () -> Unit,
            var cancelled: Boolean = false
        )

        private val scheduled = ArrayDeque<Scheduled>()

        override fun schedule(
            delayMillis: Long,
            action: () -> Unit
        ): CrossfadeCancellation {
            val task = Scheduled(delayMillis, action)
            scheduled.addLast(task)
            return CrossfadeCancellation { task.cancelled = true }
        }

        fun runNext() {
            while (scheduled.isNotEmpty()) {
                val task = scheduled.removeFirst()
                if (task.cancelled) continue
                clock.nowMillis += task.delayMillis
                task.action()
                return
            }
            error("No crossfade frame was scheduled")
        }
    }

    private companion object {
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
