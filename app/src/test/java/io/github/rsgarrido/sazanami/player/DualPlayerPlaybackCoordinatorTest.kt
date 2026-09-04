package io.github.rsgarrido.sazanami.player

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionParameters
import androidx.media3.exoplayer.ExoPlayer
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerAudioProcessor
import io.github.rsgarrido.sazanami.player.equalizer.EqualizerRuntimeBridge
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentCaptor
import org.mockito.Mockito.atLeastOnce
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`

class DualPlayerPlaybackCoordinatorTest {
    @Before
    fun setUp() {
        LogicalPlaylistMutationTransactions.clearForTest()
        LogicalNavigationPolicyTransactions.clearForTest()
        EqualizerRuntimeBridge.release()
    }

    @After
    fun tearDown() {
        CrossfadeTrace.sinkForTest = null
        LogicalPlaylistMutationTransactions.clearForTest()
        LogicalNavigationPolicyTransactions.clearForTest()
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
    fun handoffTransactionRetainsProvenanceAcrossRemoveAndDelayedReplace() {
        val classifier = CrossfadeHandoffPlaylistMutationClassifier()
        classifier.begin(currentMediaId = "second")
        val token = LogicalPlaylistMutationTransactions.begin("second")
        LogicalPlaylistMutationTransactions.expectRemovePrefix(token, 0, 1)
        LogicalPlaylistMutationTransactions.expectReplaceUpcoming(
            token = token,
            fromIndex = 1,
            toIndex = 3,
            mediaIds = listOf("third", "fourth")
        )
        LogicalPlaylistMutationTransactions.seal(token)

        val remove = requireNotNull(
            LogicalPlaylistMutationTransactions.claimRemovePrefix(
                currentMediaId = "second",
                fromIndex = 0,
                toIndex = 1
            )
        )
        assertTrue(
            classifier.accept(currentMediaId = "second", claim = remove)
        )
        classifier.markCrossfadeCompleted()
        assertFalse(classifier.shouldCloseAtCrossfadeCompletion())

        val delayedReplace = requireNotNull(
            LogicalPlaylistMutationTransactions.claimReplaceUpcoming(
                currentMediaId = "second",
                fromIndex = 1,
                toIndex = 3,
                mediaIds = listOf("third", "fourth")
            )
        )
        assertTrue(
            classifier.accept(
                currentMediaId = "second",
                claim = delayedReplace
            )
        )
        assertTrue(classifier.shouldCloseAfterClaim())
        assertEquals(token.id, classifier.transactionId())
        assertEquals(
            token.id,
            classifier.end(
                abortSourceTransaction = false,
                reason = "effects_complete"
            )
        )
        assertNull(classifier.transactionId())
    }

    @Test
    fun malformedInternalOperationFailsClosedAndCannotPoisonLaterExternalEdit() {
        val classifier = CrossfadeHandoffPlaylistMutationClassifier()
        classifier.begin("second")
        val token = LogicalPlaylistMutationTransactions.begin("second")
        LogicalPlaylistMutationTransactions.expectReplaceUpcoming(
            token = token,
            fromIndex = 1,
            toIndex = 2,
            mediaIds = listOf("third")
        )
        LogicalPlaylistMutationTransactions.seal(token)

        assertNull(
            LogicalPlaylistMutationTransactions.claimReplaceUpcoming(
                currentMediaId = "second",
                fromIndex = 1,
                toIndex = 2,
                mediaIds = listOf("user-selected")
            )
        )
        assertFalse(LogicalPlaylistMutationTransactions.isActive(token.id))
        classifier.end(
            abortSourceTransaction = true,
            reason = "external_edit"
        )
        assertNull(classifier.transactionId())
    }

    @Test
    fun abortedHandoffClosesPendingSourceTransactionAndRejectsDelayedCommand() {
        val classifier = CrossfadeHandoffPlaylistMutationClassifier()
        classifier.begin("second")
        val token = LogicalPlaylistMutationTransactions.begin("second")
        LogicalPlaylistMutationTransactions.expectRemovePrefix(token, 0, 1)
        LogicalPlaylistMutationTransactions.expectReplaceUpcoming(
            token = token,
            fromIndex = 1,
            toIndex = 2,
            mediaIds = listOf("third")
        )
        LogicalPlaylistMutationTransactions.seal(token)
        val remove = requireNotNull(
            LogicalPlaylistMutationTransactions.claimRemovePrefix(
                currentMediaId = "second",
                fromIndex = 0,
                toIndex = 1
            )
        )
        assertTrue(classifier.accept("second", remove))

        classifier.end(
            abortSourceTransaction = true,
            reason = "incoming_player_error"
        )

        assertFalse(LogicalPlaylistMutationTransactions.isActive(token.id))
        assertNull(
            LogicalPlaylistMutationTransactions.claimReplaceUpcoming(
                currentMediaId = "second",
                fromIndex = 1,
                toIndex = 2,
                mediaIds = listOf("third")
            )
        )
    }

    @Test
    fun newSourceTransactionInvalidatesAnUnclaimedStaleTransaction() {
        val stale = LogicalPlaylistMutationTransactions.begin("second")
        LogicalPlaylistMutationTransactions.expectReplaceUpcoming(
            token = stale,
            fromIndex = 1,
            toIndex = 2,
            mediaIds = listOf("third")
        )
        LogicalPlaylistMutationTransactions.seal(stale)

        val current = LogicalPlaylistMutationTransactions.begin("third")

        assertFalse(LogicalPlaylistMutationTransactions.isActive(stale.id))
        assertTrue(LogicalPlaylistMutationTransactions.isActive(current.id))
        LogicalPlaylistMutationTransactions.seal(current)
        assertFalse(LogicalPlaylistMutationTransactions.isActive(current.id))
    }

    @Test
    fun navigationTransactionKeepsShuffleAndDelayedRepeatInternalAfterCompletion() {
        val classifier = CrossfadeHandoffNavigationPolicyClassifier()
        classifier.begin("second")
        val token = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(token, false)
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            token,
            Player.REPEAT_MODE_ALL
        )
        LogicalNavigationPolicyTransactions.seal(token)

        val shuffle = requireNotNull(
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                currentMediaId = "second",
                enabled = false
            )
        )
        assertEquals(
            LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
            shuffle.operation
        )
        assertTrue(classifier.accept("second", shuffle))
        classifier.markCrossfadeCompleted()
        assertFalse(classifier.shouldCloseAtCrossfadeCompletion())

        val delayedRepeat = requireNotNull(
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                currentMediaId = "second",
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )
        assertEquals(
            LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
            delayedRepeat.operation
        )
        assertTrue(classifier.accept("second", delayedRepeat))
        assertTrue(classifier.shouldCloseAfterClaim())
        assertEquals(
            token.id,
            classifier.end(
                abortSourceTransaction = false,
                reason = "effects_complete"
            )
        )
    }

    @Test
    fun navigationMismatchAndAbortCannotClassifyLaterUserCommandAsInternal() {
        val classifier = CrossfadeHandoffNavigationPolicyClassifier()
        classifier.begin("second")
        val token = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(token, false)
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            token,
            Player.REPEAT_MODE_OFF
        )
        LogicalNavigationPolicyTransactions.seal(token)

        assertNull(
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                currentMediaId = "second",
                repeatMode = Player.REPEAT_MODE_ONE
            )
        )
        assertFalse(LogicalNavigationPolicyTransactions.isActive(token.id))
        classifier.end(
            abortSourceTransaction = true,
            reason = "external_repeat_change"
        )
        assertNull(
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                currentMediaId = "second",
                enabled = false
            )
        )
    }

    @Test
    fun staleNavigationTransactionCannotPoisonExactCommandForNewMediaIdentity() {
        val stale = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(stale, false)
        LogicalNavigationPolicyTransactions.seal(stale)
        val current = LogicalNavigationPolicyTransactions.begin("third")
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            current,
            Player.REPEAT_MODE_ALL
        )
        LogicalNavigationPolicyTransactions.seal(current)

        val claim = requireNotNull(
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                currentMediaId = "third",
                repeatMode = Player.REPEAT_MODE_ALL
            )
        )

        assertEquals(current.id, claim.transactionId)
        assertFalse(LogicalNavigationPolicyTransactions.isActive(stale.id))
        assertFalse(LogicalNavigationPolicyTransactions.isActive(current.id))
        assertNull(
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                currentMediaId = "second",
                enabled = false
            )
        )
    }

    @Test
    fun navigationTransactionClosesOnHandoffCancellationOrRelease() {
        val classifier = CrossfadeHandoffNavigationPolicyClassifier()
        classifier.begin("second")
        val token = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(token, false)
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            token,
            Player.REPEAT_MODE_OFF
        )
        LogicalNavigationPolicyTransactions.seal(token)
        val shuffle = requireNotNull(
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                currentMediaId = "second",
                enabled = false
            )
        )
        assertTrue(classifier.accept("second", shuffle))

        classifier.end(
            abortSourceTransaction = true,
            reason = "handoff_cancelled"
        )

        assertFalse(LogicalNavigationPolicyTransactions.isActive(token.id))
        assertNull(
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                currentMediaId = "second",
                repeatMode = Player.REPEAT_MODE_OFF
            )
        )
    }

    @Test
    fun repeatedMediaTransitionNavigationReconciliationsRemainSourceOwned() {
        val first = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(first, false)
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            first,
            Player.REPEAT_MODE_OFF
        )
        LogicalNavigationPolicyTransactions.seal(first)
        val duplicate = LogicalNavigationPolicyTransactions.begin("second")
        LogicalNavigationPolicyTransactions.expectShuffleMode(duplicate, false)
        LogicalNavigationPolicyTransactions.expectRepeatMode(
            duplicate,
            Player.REPEAT_MODE_OFF
        )
        LogicalNavigationPolicyTransactions.seal(duplicate)

        assertEquals(
            first.id,
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                "second",
                false
            )?.transactionId
        )
        assertEquals(
            first.id,
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                "second",
                Player.REPEAT_MODE_OFF
            )?.transactionId
        )
        assertEquals(
            duplicate.id,
            LogicalNavigationPolicyTransactions.claimShuffleMode(
                "second",
                false
            )?.transactionId
        )
        assertEquals(
            duplicate.id,
            LogicalNavigationPolicyTransactions.claimRepeatMode(
                "second",
                Player.REPEAT_MODE_OFF
            )?.transactionId
        )
        assertFalse(LogicalNavigationPolicyTransactions.isActive(first.id))
        assertFalse(LogicalNavigationPolicyTransactions.isActive(duplicate.id))
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
    fun crossfadeDisabledUsesNativePlaylistWithoutEosPauseOrRoleSwap() {
        assertNativeTransition(CrossfadeRuntimeConfiguration.DISABLED)
    }

    @Test
    fun preservedAlbumUsesNativePlaylistEvenWithReadyStandby() {
        assertNativeTransition(
            CrossfadeRuntimeConfiguration.TEST_ENABLED,
            items = listOf(albumItem("intro", 1), albumItem("six_feet_deep", 2))
        )
    }

    @Test
    fun repeatOneAndNativeShuffleDoNotGuardOrHandoffAtItemEnd() {
        assertNativeTransition(
            CrossfadeRuntimeConfiguration.TEST_ENABLED,
            repeatMode = Player.REPEAT_MODE_ONE,
            nextIndex = 0
        )
        assertNativeTransition(
            CrossfadeRuntimeConfiguration.TEST_ENABLED,
            items = listOf(localItem("first"), localItem("second"), localItem("third")),
            shuffle = true,
            nextIndex = 2
        )
    }

    @Test
    fun repeatAllWrapAndDuplicateQueueEntriesStayOnNativeTimeline() {
        assertNativeTransition(
            CrossfadeRuntimeConfiguration.DISABLED,
            repeatMode = Player.REPEAT_MODE_ALL,
            currentIndex = 1,
            nextIndex = 0
        )
        // Same media ID is legal for distinct queue occurrences; indices stay authoritative.
        assertNativeTransition(
            CrossfadeRuntimeConfiguration.TEST_ENABLED,
            items = listOf(localItem("duplicate"), localItem("duplicate"))
        )
    }

    @Test
    fun unrelatedAlbumKeepsConfiguredCrossfadeAndPromotionRestoresNativeProgression() {
        val fixture = TransitionFixture(
            items = listOf(albumItem("first", 1), albumItem("second", 2, album = "Other"))
        )
        fixture.startOverlap()
        assertEquals(CrossfadeTransitionState.CROSSFADING, fixture.coordinator.crossfadeState)
        verify(fixture.playerA, atLeastOnce()).pauseAtEndOfMediaItems = true
        assertTrue(fixture.integration.transitions.isEmpty())
        fixture.finishAtEos()

        assertSame(fixture.playerB, fixture.coordinator.logicalPhysicalPlayer)
        assertEquals(listOf(fixture.items[1]), fixture.integration.logicalHandoffs)
        assertEquals(listOf(fixture.items[0]), fixture.integration.completedOutgoing)
        assertEquals(0.4f, fixture.pipelineB.baselineVolume, 0f)
        assertEquals(listOf(0.4f), fixture.logical.reboundBaselines)
        assertFalse(fixture.playerB.pauseAtEndOfMediaItems)
        assertFalse(fixture.playerA.pauseAtEndOfMediaItems)
        assertEquals(0, fixture.logical.activationCount)
        fixture.coordinator.release()
    }

    @Test
    fun disablingAlbumPreservationStillAllowsIntentionalAlbumCrossfade() {
        val fixture = TransitionFixture(
            items = listOf(albumItem("intro", 1), albumItem("six_feet_deep", 2)),
            configuration = CrossfadeRuntimeConfiguration.TEST_ENABLED.copy(
                preserveAlbumTransitions = false, durationMillis = 9_000L
            )
        )
        fixture.startOverlap()
        assertEquals(CrossfadeTransitionState.CROSSFADING, fixture.coordinator.crossfadeState)
        fixture.finishAtEos()
        assertSame(fixture.playerB, fixture.coordinator.logicalPhysicalPlayer)
        fixture.coordinator.release()
    }

    @Test
    fun crossfadeIntoAlbumLeavesItsFollowingNativeBoundaryUnguarded() {
        val fixture = TransitionFixture(items = listOf(
            localItem("mixed"), albumItem("intro", 1), albumItem("six_feet_deep", 2)
        ))
        fixture.startOverlap()
        fixture.finishAtEos()
        assertSame(fixture.playerB, fixture.coordinator.logicalPhysicalPlayer)
        assertFalse(fixture.playerB.pauseAtEndOfMediaItems)
        val listener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(fixture.playerB, atLeastOnce()).addListener(listener.capture())
        `when`(fixture.playerB.currentMediaItemIndex).thenReturn(2)
        `when`(fixture.playerB.currentMediaItem).thenReturn(fixture.items[2])
        listener.allValues.last().onMediaItemTransition(
            fixture.items[2], Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )
        assertSame(fixture.playerB, fixture.coordinator.logicalPhysicalPlayer)
        assertEquals(1, fixture.integration.logicalHandoffs.size)
        assertEquals(1, fixture.integration.transitions.size)
        fixture.coordinator.release()
    }

    @Test
    fun failedStandbyAtGuardedEosContinuesExistingPlaylistWithoutPromotion() {
        val fixture = TransitionFixture()
        fixture.coordinator.markStandbyFailedForTest()
        fixture.finishAtEos()
        assertSame(fixture.playerA, fixture.coordinator.logicalPhysicalPlayer)
        assertTrue(fixture.integration.transitions.isEmpty())
        assertTrue(fixture.logical.reboundPlayers.isEmpty())
        verify(fixture.playerA).playWhenReady = true
        verify(fixture.playerB, never()).playWhenReady = true
        assertFalse(fixture.playerA.pauseAtEndOfMediaItems)
        fixture.coordinator.release()
    }

    @Test
    fun crossfadeCancellationRestoresNativeBoundaryBeforeAndAfterMidpoint() {
        for (pastMidpoint in listOf(false, true)) {
            val fixture = TransitionFixture()
            fixture.startOverlap()
            if (pastMidpoint) {
                fixture.position = 7_500L
                fixture.scheduler.runNext()
            }
            fixture.coordinator.updateCrossfadeConfiguration(CrossfadeRuntimeConfiguration.DISABLED)
            val survivor = if (pastMidpoint) fixture.playerB else fixture.playerA
            assertSame(survivor, fixture.coordinator.logicalPhysicalPlayer)
            assertFalse(survivor.pauseAtEndOfMediaItems)
            assertFalse(fixture.playerA.pauseAtEndOfMediaItems)
            assertFalse(fixture.playerB.pauseAtEndOfMediaItems)
            fixture.coordinator.release()
        }
    }

    @Test
    fun staleOutgoingCallbacksCannotPublishAnotherTransitionAfterCrossfade() {
        val fixture = TransitionFixture()
        fixture.startOverlap()
        fixture.finishAtEos()
        val binds = fixture.integration.bound.size
        fixture.activeListener.onMediaItemTransition(fixture.items[1], Player.MEDIA_ITEM_TRANSITION_REASON_AUTO)
        fixture.finishAtEos()
        assertEquals(binds, fixture.integration.bound.size)
        assertEquals(1, fixture.integration.logicalHandoffs.size)
        assertFalse(fixture.coordinator.isActive(fixture.playerA))
        assertTrue(fixture.coordinator.isActive(fixture.playerB))
        assertSame(fixture.pipelineB.equalizerRuntime, EqualizerRuntimeBridge.selectedTelemetryRuntime())
        fixture.coordinator.release()
        verify(fixture.playerA, times(1)).release()
        verify(fixture.playerB, times(1)).release()
        assertEquals(0, EqualizerRuntimeBridge.registeredRuntimeCountForTest())
    }

    @Test
    fun failedCrossfadePromotionReturnsToOneNativeActivePipeline() {
        val fixture = TransitionFixture(integrationOverride = FailingOnceIntegration())
        fixture.startOverlap()
        fixture.finishAtEos()
        assertSame(fixture.playerA, fixture.coordinator.logicalPhysicalPlayer)
        assertEquals(PhysicalPlayerRole.ACTIVE, fixture.pipelineA.role)
        assertEquals(PhysicalPlayerRole.STANDBY, fixture.pipelineB.role)
        assertFalse(fixture.playerA.pauseAtEndOfMediaItems)
        fixture.coordinator.release()
    }

    private fun assertNativeTransition(
        configuration: CrossfadeRuntimeConfiguration,
        items: List<MediaItem> = listOf(localItem("first"), localItem("second")),
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        shuffle: Boolean = false,
        currentIndex: Int = 0,
        nextIndex: Int = 1
    ) {
        val fixture = TransitionFixture(
            items, configuration, repeatMode, shuffle, currentIndex,
            initialPauseAtEnd = true
        )
        fixture.coordinator.markStandbyReadyForTest()
        fixture.coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.IDLE, fixture.coordinator.crossfadeState)
        assertFalse(fixture.playerA.pauseAtEndOfMediaItems)
        verify(fixture.playerA, never()).pauseAtEndOfMediaItems = true
        verify(fixture.playerB, never()).playWhenReady = true
        `when`(fixture.playerA.currentMediaItemIndex).thenReturn(nextIndex)
        `when`(fixture.playerA.currentMediaItem).thenReturn(items[nextIndex])
        fixture.activeListener.onMediaItemTransition(
            items[nextIndex],
            if (repeatMode == Player.REPEAT_MODE_ONE) Player.MEDIA_ITEM_TRANSITION_REASON_REPEAT
            else Player.MEDIA_ITEM_TRANSITION_REASON_AUTO
        )
        assertSame(fixture.playerA, fixture.coordinator.logicalPhysicalPlayer)
        assertSame(items[nextIndex], fixture.coordinator.logicalPhysicalPlayer.currentMediaItem)
        assertTrue(fixture.logical.reboundPlayers.isEmpty())
        assertTrue(fixture.integration.transitions.isEmpty())
        assertTrue(fixture.integration.logicalHandoffs.isEmpty())
        assertEquals(items, List(fixture.playerA.mediaItemCount, fixture.playerA::getMediaItemAt))
        val destructiveCalls = org.mockito.Mockito.mockingDetails(fixture.playerA).invocations
            .filter { it.method.name in setOf(
                "setMediaItems", "setMediaItem", "replaceMediaItems", "clearMediaItems",
                "removeMediaItems", "stop", "prepare", "pause", "seekTo", "setPlayWhenReady"
            ) }
        assertTrue(destructiveCalls.toString(), destructiveCalls.isEmpty())
        assertEquals(0, fixture.scheduler.runUntilEmpty())
        fixture.coordinator.release()
    }

    private inner class TransitionFixture(
        val items: List<MediaItem> = listOf(localItem("first"), localItem("second")),
        configuration: CrossfadeRuntimeConfiguration = CrossfadeRuntimeConfiguration.TEST_ENABLED,
        repeatMode: Int = Player.REPEAT_MODE_OFF,
        shuffle: Boolean = false,
        currentIndex: Int = 0,
        integrationOverride: ActivePlayerIntegration? = null,
        initialPauseAtEnd: Boolean = false
    ) {
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        var position = 0L
        val pipelineA: PhysicalPlayerPipeline
        val pipelineB: PhysicalPlayerPipeline
        val coordinator: DualPlayerPlaybackCoordinator
        val logical = RecordingLogicalPlayer(playerA)
        val integration = RecordingIntegration()
        val activeListener: Player.Listener

        init {
            stubPlaylist(playerA, items, currentIndex)
            stubPlaylist(playerB, items, 1)
            `when`(playerA.currentMediaItem).thenReturn(items[currentIndex])
            `when`(playerB.currentMediaItem).thenReturn(items[1])
            `when`(playerA.repeatMode).thenReturn(repeatMode)
            `when`(playerA.shuffleModeEnabled).thenReturn(shuffle)
            `when`(playerA.duration).thenReturn(10_000L)
            `when`(playerA.currentPosition).thenAnswer { position }
            for (player in listOf(playerA, playerB)) {
                `when`(player.playbackState).thenReturn(Player.STATE_READY)
                `when`(player.isPlaying).thenReturn(true)
                var guarded = initialPauseAtEnd
                `when`(player.pauseAtEndOfMediaItems).thenAnswer { guarded }
                org.mockito.Mockito.doAnswer { invocation ->
                    guarded = invocation.getArgument(0)
                    null
                }.`when`(player).pauseAtEndOfMediaItems = org.mockito.ArgumentMatchers.anyBoolean()
            }
            pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
            pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
            val firstKey = checkNotNull(StandbyTargetResolver.key(items[currentIndex]))
            pipelineA.prepareBaseline(firstKey)
            pipelineA.updateBaseline(firstKey, 0.6f)
            coordinator = DualPlayerPlaybackCoordinator(
                pipelineA, pipelineB,
                standbyBaselinePreparer = StandbyBaselinePreparer { _, ready -> ready(0.4f); true },
                crossfadeClock = clock,
                crossfadeScheduler = scheduler,
                initialCrossfadeConfiguration = configuration
            )
            coordinator.attachLogicalPlayer(logical, integrationOverride ?: integration)
            val captor = ArgumentCaptor.forClass(Player.Listener::class.java)
            verify(playerA).addListener(captor.capture())
            activeListener = captor.value
        }

        fun startOverlap() {
            position = 5_000L
            coordinator.markStandbyReadyForTest()
            coordinator.synchronizeStandby()
        }

        fun finishAtEos() = activeListener.onPlayWhenReadyChanged(
            false, Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM
        )
    }

    private fun albumItem(id: String, track: Int, album: String = "Live"): MediaItem {
        val extras = mock(Bundle::class.java)
        val values = mapOf(
            ListeningMediaItemMetadata.ITEM_INSTANCE_ID to id,
            ListeningMediaItemMetadata.REFERENCE_KEY to id,
            ListeningMediaItemMetadata.FILE_SIZE_BYTES to 1L,
            ListeningMediaItemMetadata.DATE_MODIFIED_SECONDS to 1L,
            ListeningMediaItemMetadata.DURATION_MS to 10_000L,
            ListeningMediaItemMetadata.PORTABLE_KEY_VERSION to 1,
            ListeningMediaItemMetadata.ALBUM to album,
            ListeningMediaItemMetadata.ALBUM_ARTIST to "The Warning"
        )
        for ((key, value) in values) `when`(extras.get(key)).thenReturn(value)
        `when`(extras.containsKey(AlbumTransitionMetadata.RAW_TRACK_NUMBER)).thenReturn(true)
        `when`(extras.getInt(AlbumTransitionMetadata.RAW_TRACK_NUMBER)).thenReturn(track)
        `when`(extras.getString(AlbumTransitionMetadata.FOLDER_PATH)).thenReturn("Music/Live")
        return localItem(id).buildUpon()
            .setMediaMetadata(MediaMetadata.Builder().setExtras(extras).build()).build()
    }

    @Test
    fun consecutiveCrossfadesRemainEligibleAcrossInternalHandoffSynchronization() {
        val first = localItem("first")
        val second = localItem("second")
        val third = localItem("third")
        val fourth = localItem("fourth")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        var outgoingPosition = 5_000L
        var incomingPosition = 0L
        var currentItemA = first
        var currentIndexA = 0
        var currentItemB = second
        var currentIndexB = 1
        val playlist = listOf(first, second, third, fourth)
        stubPlaylist(playerA, playlist, 0)
        stubPlaylist(playerB, playlist, 1)
        `when`(playerA.currentMediaItem).thenAnswer { currentItemA }
        `when`(playerA.currentMediaItemIndex).thenAnswer { currentIndexA }
        `when`(playerB.currentMediaItem).thenAnswer { currentItemB }
        `when`(playerB.currentMediaItemIndex).thenAnswer { currentIndexB }
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
        val trace = mutableListOf<String>()
        CrossfadeTrace.sinkForTest = trace::add
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
        assertTrue(integration.audibleStarts.isEmpty())

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

        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 1L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REMOVE_PREFIX
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 101L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
                navigationValue = "false"
            )
        )
        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            coordinator.crossfadeState
        )

        outgoingPosition = 10_000L
        scheduler.runNext()

        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)
        assertSame(pipelineB, coordinator.active)
        assertSame(pipelineA, coordinator.standby)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineB.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineA.role)
        verify(playerA).setMediaItems(playlist, 2, 0L)
        assertTrue(
            trace.any { entry ->
                entry.startsWith(
                    "NEW_ACTIVE mediaId=second futureCrossfadeSuppressed=false"
                )
            }
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 1L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REPLACE_UPCOMING
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 101L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
                navigationValue = Player.REPEAT_MODE_OFF.toString()
            )
        )
        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)

        currentItemA = third
        currentIndexA = 2
        incomingPosition = 5_000L
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        incomingPosition = 7_500L
        scheduler.runNext()
        assertSame(playerA, coordinator.logicalPhysicalPlayer)
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 2L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REMOVE_PREFIX
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 102L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
                navigationValue = "false"
            )
        )
        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            coordinator.crossfadeState
        )
        incomingPosition = 10_000L
        scheduler.runNext()

        assertSame(pipelineA, coordinator.active)
        assertSame(pipelineB, coordinator.standby)
        assertEquals(listOf(second, third), integration.transitions.map {
            it?.incomingMediaItem
        })
        assertEquals(listOf(first, second), integration.completedOutgoing)
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 2L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REPLACE_UPCOMING
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 102L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
                navigationValue = Player.REPEAT_MODE_OFF.toString()
            )
        )
        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)

        currentItemB = fourth
        currentIndexB = 3
        outgoingPosition = 5_000L
        incomingPosition = 0L
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        outgoingPosition = 7_500L
        scheduler.runNext()
        assertSame(playerB, coordinator.logicalPhysicalPlayer)
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 3L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REMOVE_PREFIX
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 103L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
                navigationValue = "false"
            )
        )
        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            coordinator.crossfadeState
        )
        outgoingPosition = 10_000L
        scheduler.runNext()

        assertSame(pipelineB, coordinator.active)
        assertSame(pipelineA, coordinator.standby)
        assertEquals(listOf(second, third, fourth), integration.transitions.map {
            it?.incomingMediaItem
        })
        assertEquals(listOf(first, second, third), integration.completedOutgoing)
        assertTrue(
            trace.any { entry ->
                entry.startsWith(
                    "NEW_ACTIVE mediaId=fourth futureCrossfadeSuppressed=false"
                )
            }
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                preservesCurrentMediaItem = true,
                transactionId = 3L,
                playlistOperation =
                    LogicalPlaylistMutationOperation.REPLACE_UPCOMING
            )
        )
        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin =
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
                transactionId = 103L,
                navigationOperation =
                    LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
                navigationValue = Player.REPEAT_MODE_OFF.toString()
            )
        )
        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)

        coordinator.release()
    }

    @Test
    fun runtimeEnableAffectsFutureOverlapAndDisableCollapsesActiveCrossfade() {
        val first = localItem("first")
        val second = localItem("second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(10_000L)
        `when`(playerA.currentPosition).thenReturn(5_000L)
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        `when`(playerB.isPlaying).thenReturn(true)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        pipelineA.prepareBaseline(firstKey)
        pipelineA.updateBaseline(firstKey, 1f)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            standbyBaselinePreparer = StandbyBaselinePreparer { _, result ->
                result(1f)
                true
            },
            crossfadeClock = clock,
            crossfadeScheduler = scheduler,
            initialCrossfadeConfiguration = CrossfadeRuntimeConfiguration.DISABLED
        )
        val integration = RecordingIntegration()
        coordinator.attachLogicalPlayer(RecordingLogicalPlayer(playerA), integration)
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)

        coordinator.updateCrossfadeConfiguration(CrossfadeRuntimeConfiguration.TEST_ENABLED)
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        coordinator.updateCrossfadeConfiguration(
            CrossfadeRuntimeConfiguration.TEST_ENABLED.copy(
                durationMillis = 12_000L,
                preserveAlbumTransitions = false
            )
        )
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        coordinator.updateCrossfadeConfiguration(CrossfadeRuntimeConfiguration.DISABLED)
        assertEquals(CrossfadeTransitionState.IDLE, coordinator.crossfadeState)
        assertSame(pipelineA, coordinator.active)
        assertSame(pipelineB, coordinator.standby)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineA.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineB.role)
        assertEquals(listOf(second), integration.cancelledIncoming)
        assertEquals(1f, lastSetVolume(playerA), 0.0001f)
        assertEquals(0f, lastSetVolume(playerB), 0.0001f)
        assertEquals(0, scheduler.runUntilEmpty())

        coordinator.markStandbyReadyForTest()
        coordinator.updateCrossfadeConfiguration(CrossfadeRuntimeConfiguration.TEST_ENABLED)
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        coordinator.release()
    }

    @Test
    fun externalPlaylistMutationStillCancelsAnActiveOverlap() {
        val first = localItem("first")
        val second = localItem("second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(10_000L)
        `when`(playerA.currentPosition).thenReturn(5_000L)
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        `when`(playerB.isPlaying).thenReturn(true)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        pipelineA.prepareBaseline(firstKey)
        pipelineA.updateBaseline(firstKey, 1f)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            standbyBaselinePreparer = StandbyBaselinePreparer { _, result ->
                result(1f)
                true
            },
            crossfadeClock = clock,
            crossfadeScheduler = scheduler
        )
        val integration = RecordingIntegration()
        coordinator.attachLogicalPlayer(RecordingLogicalPlayer(playerA), integration)
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.PLAYLIST_MUTATION,
                origin = LogicalPlaybackCommandOrigin.EXTERNAL,
                preservesCurrentMediaItem = true
            )
        )

        assertEquals(CrossfadeTransitionState.CANCELLED, coordinator.crossfadeState)
        assertSame(pipelineA, coordinator.active)
        assertEquals(listOf(second), integration.cancelledIncoming)
        assertEquals(1f, lastSetVolume(playerA), 0.0001f)
        assertEquals(0f, lastSetVolume(playerB), 0.0001f)
        assertEquals(0, scheduler.runUntilEmpty())
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CANCELLED, coordinator.crossfadeState)
        coordinator.release()
    }

    @Test
    fun cancellationAfterMidpointKeepsIncomingAuthorityAndExactReplayGainBaseline() {
        val first = localItem("after-midpoint-first")
        val second = localItem("after-midpoint-second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        var outgoingPosition = 5_000L
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(10_000L)
        `when`(playerA.currentPosition).thenAnswer { outgoingPosition }
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        `when`(playerB.isPlaying).thenReturn(true)
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
        coordinator.attachLogicalPlayer(logical, RecordingIntegration())
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()

        outgoingPosition = 7_500L
        scheduler.runNext()
        assertEquals(
            CrossfadeTransitionState.LOGICALLY_HANDED_OFF,
            coordinator.crossfadeState
        )

        coordinator.onLogicalCommand(LogicalPlaybackCommand.SEEK)

        assertEquals(CrossfadeTransitionState.CANCELLED, coordinator.crossfadeState)
        assertSame(pipelineB, coordinator.active)
        assertSame(playerB, coordinator.logicalPhysicalPlayer)
        assertEquals(PhysicalPlayerRole.ACTIVE, pipelineB.role)
        assertEquals(PhysicalPlayerRole.STANDBY, pipelineA.role)
        assertEquals(0.4f, lastSetVolume(playerB), 0.0001f)
        assertEquals(0f, lastSetVolume(playerA), 0.0001f)
        assertEquals(0, scheduler.runUntilEmpty())
        coordinator.release()
    }

    @Test
    fun internalRepeatSynchronizationDoesNotCancelActiveOverlap() {
        assertNavigationPolicyDuringOverlap(
            origin = LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
            operation = LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
            value = navigationRepeatModeTraceValue(Player.REPEAT_MODE_ALL),
            expectsCancellation = false
        )
    }

    @Test
    fun internalShuffleSynchronizationDoesNotCancelActiveOverlap() {
        assertNavigationPolicyDuringOverlap(
            origin = LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL,
            operation = LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
            value = "false",
            expectsCancellation = false
        )
    }

    @Test
    fun externalRepeatChangeStillCancelsActiveOverlapSafely() {
        assertNavigationPolicyDuringOverlap(
            origin = LogicalPlaybackCommandOrigin.EXTERNAL,
            operation = LogicalNavigationPolicyOperation.SET_REPEAT_MODE,
            value = navigationRepeatModeTraceValue(Player.REPEAT_MODE_ALL),
            expectsCancellation = true
        )
    }

    @Test
    fun externalShuffleChangeStillCancelsActiveOverlapSafely() {
        assertNavigationPolicyDuringOverlap(
            origin = LogicalPlaybackCommandOrigin.EXTERNAL,
            operation = LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE,
            value = "true",
            expectsCancellation = true
        )
    }

    @Test
    fun routineStartupCommandsDoNotCancelNaturalFiftyEightSecondNineSecondCrossfade() {
        val first = localItem("first")
        val second = localItem("second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        var outgoingPosition = 0L
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(58_000L)
        `when`(playerA.currentPosition).thenAnswer { outgoingPosition }
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        // A prepared standby is intentionally silent until beginCrossfade requests playback.
        `when`(playerB.isPlaying).thenReturn(false)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        pipelineA.prepareBaseline(firstKey)
        pipelineA.updateBaseline(firstKey, 1f)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        val trace = mutableListOf<String>()
        CrossfadeTrace.sinkForTest = trace::add
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            standbyBaselinePreparer = StandbyBaselinePreparer { _, result ->
                result(1f)
                true
            },
            crossfadeClock = clock,
            crossfadeScheduler = scheduler,
            initialCrossfadeConfiguration = CrossfadeRuntimeConfiguration(
                enabled = true,
                durationMillis = 9_000L,
                preserveAlbumTransitions = false
            )
        )
        coordinator.attachLogicalPlayer(RecordingLogicalPlayer(playerA), RecordingIntegration())
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.SCHEDULED, coordinator.crossfadeState)

        coordinator.onLogicalCommand(LogicalPlaybackCommand.PLAY_PAUSE)
        coordinator.onLogicalCommand(LogicalPlaybackCommand.NAVIGATION_POLICY)
        coordinator.onLogicalCommand(LogicalPlaybackCommand.PLAYBACK_PARAMETERS)
        outgoingPosition = 49_000L
        coordinator.synchronizeStandby()

        assertEquals(
            trace.joinToString(separator = "\n"),
            CrossfadeTransitionState.CROSSFADING,
            coordinator.crossfadeState
        )
        verify(playerB).playWhenReady = true
        assertTrue(
            trace.any {
                it == "COMMAND PLAY_PAUSE origin=EXTERNAL operation=NONE " +
                    "value=none action=keep_future_crossfade"
            }
        )
        assertTrue(
            trace.any {
                it == "COMMAND NAVIGATION_POLICY origin=EXTERNAL operation=NONE " +
                    "value=none action=keep_future_crossfade"
            }
        )
        assertTrue(trace.any { it.startsWith("START_REQUESTED durationMs=9000") })
        assertFalse(trace.any { it.contains("reason=cancelled_by_interaction") })
        coordinator.release()
    }

    @Test
    fun activeReplayGainResultIsRekeyedWhenPhysicalPipelineGetsSelectedItem() {
        val oldItem = localItem("old")
        val selectedItem = localItem("selected")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        `when`(playerA.currentMediaItem).thenReturn(selectedItem)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val oldKey = checkNotNull(StandbyTargetResolver.key(oldItem))
        val selectedKey = checkNotNull(StandbyTargetResolver.key(selectedItem))
        pipelineA.prepareBaseline(oldKey)
        pipelineA.updateBaseline(oldKey, 0.5f)
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            initialCrossfadeConfiguration = CrossfadeRuntimeConfiguration.DISABLED
        )

        coordinator.updateActiveBaseline(0.75f)

        assertTrue(pipelineA.hasExactBaselineFor(selectedKey))
        assertEquals(0.75f, pipelineA.baselineVolume, 0.0001f)
        assertFalse(pipelineA.hasExactBaselineFor(oldKey))
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

    private fun assertNavigationPolicyDuringOverlap(
        origin: LogicalPlaybackCommandOrigin,
        operation: LogicalNavigationPolicyOperation,
        value: String,
        expectsCancellation: Boolean
    ) {
        val first = localItem("navigation-first")
        val second = localItem("navigation-second")
        val playerA = mock(ExoPlayer::class.java)
        val playerB = mock(ExoPlayer::class.java)
        stubPlaylist(playerA, listOf(first, second), 0)
        stubPlaylist(playerB, listOf(first, second), 1)
        `when`(playerA.currentMediaItem).thenReturn(first)
        `when`(playerB.currentMediaItem).thenReturn(second)
        `when`(playerA.duration).thenReturn(10_000L)
        `when`(playerA.currentPosition).thenReturn(5_000L)
        `when`(playerA.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerB.playbackState).thenReturn(Player.STATE_READY)
        `when`(playerA.isPlaying).thenReturn(true)
        `when`(playerB.isPlaying).thenReturn(true)
        val pipelineA = pipeline(PhysicalPlayerRole.ACTIVE, playerA)
        val firstKey = checkNotNull(StandbyTargetResolver.key(first))
        pipelineA.prepareBaseline(firstKey)
        pipelineA.updateBaseline(firstKey, 1f)
        val pipelineB = pipeline(PhysicalPlayerRole.STANDBY, playerB)
        val clock = ManualCrossfadeClock()
        val scheduler = ManualCrossfadeScheduler(clock)
        val coordinator = DualPlayerPlaybackCoordinator(
            initialActive = pipelineA,
            initialStandby = pipelineB,
            standbyBaselinePreparer = StandbyBaselinePreparer { _, result ->
                result(1f)
                true
            },
            crossfadeClock = clock,
            crossfadeScheduler = scheduler
        )
        val integration = RecordingIntegration()
        coordinator.attachLogicalPlayer(RecordingLogicalPlayer(playerA), integration)
        coordinator.markStandbyReadyForTest()
        coordinator.synchronizeStandby()
        assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)

        coordinator.onLogicalCommand(
            LogicalPlaybackCommandEvent(
                command = LogicalPlaybackCommand.NAVIGATION_POLICY,
                origin = origin,
                transactionId = if (
                    origin ==
                    LogicalPlaybackCommandOrigin.CROSSFADE_HANDOFF_INTERNAL
                ) {
                    99L
                } else {
                    null
                },
                navigationOperation = operation,
                navigationValue = value
            )
        )

        val activeListener = ArgumentCaptor.forClass(Player.Listener::class.java)
        verify(playerA, atLeastOnce()).addListener(activeListener.capture())
        when (operation) {
            LogicalNavigationPolicyOperation.SET_REPEAT_MODE ->
                activeListener.allValues.last().onRepeatModeChanged(
                    when (value) {
                        navigationRepeatModeTraceValue(Player.REPEAT_MODE_ALL),
                        Player.REPEAT_MODE_ALL.toString() -> Player.REPEAT_MODE_ALL
                        navigationRepeatModeTraceValue(Player.REPEAT_MODE_ONE),
                        Player.REPEAT_MODE_ONE.toString() -> Player.REPEAT_MODE_ONE
                        else -> Player.REPEAT_MODE_OFF
                    }
                )
            LogicalNavigationPolicyOperation.SET_SHUFFLE_MODE ->
                activeListener.allValues.last().onShuffleModeEnabledChanged(
                    value.toBoolean()
                )
        }

        if (expectsCancellation) {
            assertEquals(CrossfadeTransitionState.CANCELLED, coordinator.crossfadeState)
            assertSame(pipelineA, coordinator.active)
            assertEquals(listOf(second), integration.cancelledIncoming)
            assertEquals(0, scheduler.runUntilEmpty())
        } else {
            assertEquals(CrossfadeTransitionState.CROSSFADING, coordinator.crossfadeState)
            assertTrue(integration.cancelledIncoming.isEmpty())
        }
        coordinator.release()
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
        val audibleStarts = mutableListOf<MediaItem>()
        val logicalHandoffs = mutableListOf<MediaItem>()
        val completedOutgoing = mutableListOf<MediaItem>()
        val cancelledIncoming = mutableListOf<MediaItem>()

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

        override fun onCrossfadeIncomingAudible(incomingMediaItem: MediaItem) {
            audibleStarts += incomingMediaItem
        }

        override fun onCrossfadeLogicalHandoff(incomingMediaItem: MediaItem) {
            logicalHandoffs += incomingMediaItem
        }

        override fun onCrossfadeCompleted(outgoingMediaItem: MediaItem?) {
            outgoingMediaItem?.let(completedOutgoing::add)
        }

        override fun onCrossfadeCancelled(
            outgoingMediaItem: MediaItem?,
            incomingMediaItem: MediaItem,
            survivingMediaItem: MediaItem?
        ) {
            cancelledIncoming += incomingMediaItem
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

        fun runUntilEmpty(): Int {
            var executed = 0
            while (scheduled.isNotEmpty()) {
                val task = scheduled.removeFirst()
                if (task.cancelled) continue
                clock.nowMillis += task.delayMillis
                task.action()
                executed += 1
            }
            return executed
        }
    }

    private companion object {
        val AUDIO_ATTRIBUTES: AudioAttributes = AudioAttributes.Builder()
            .setUsage(C.USAGE_MEDIA)
            .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
            .build()
    }
}
