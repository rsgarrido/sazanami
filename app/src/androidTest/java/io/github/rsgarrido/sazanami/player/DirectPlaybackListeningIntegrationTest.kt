package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.*
import io.github.rsgarrido.sazanami.data.local.*
import io.github.rsgarrido.sazanami.player.usb.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Actual Direct producer -> service binding -> shared adapter/recorder -> in-memory Room. No JNI. */
@RunWith(AndroidJUnit4::class)
@androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)
class DirectPlaybackListeningIntegrationTest {
    @Test fun verifiedEofBeforeFirstProgressCompletesInternalRequestWithoutAutomaticAdvancement() = runBlocking {
        withContext(Dispatchers.Main) {
            val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
            val engine = Engine()
            val player = DirectUsbMediaPlayer(scope, engine, changed = {}, librarySong = { song })
            try {
                val result = player.replaceSource(song.toPlayableMediaItem("short-source"), true)
                engine.terminal.complete(naturalResult())
                assertEquals(DirectSourceTransitionStatus.COMPLETED, withTimeout(5000) { result.await() }.status)
                assertEquals(IntegratedDirectPhase.ENDED, player.evidence.phase)
                assertFalse(player.isPlaying)
                assertEquals(1, engine.starts)
                assertFalse(player.availableCommands.contains(androidx.media3.common.Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM))
            } finally {
                engine.terminal.complete(naturalResult())
                player.stopAndJoin();scope.cancel()
            }
        }
    }

    @Test fun acceptedEofPersistsOriginalEvidenceAndUpdatesExistingConsumers() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
        val engine = Engine()
        lateinit var adapter: PlaybackServiceListeningAdapter
        lateinit var binding: DirectPlaybackListeningBinding
        lateinit var player: DirectUsbMediaPlayer
        var adapterReady = false
        var bindingReady = false
        var playerReady = false
        val observedItems = mutableListOf<String?>()
        val original = song.toPlayableMediaItem("original-item")
        try {
            withContext(Dispatchers.IO) {
                SmartPlaylistDatabaseTriggers.install(database.openHelper.writableDatabase)
            }
            LibraryCacheRepository(database.cachedSongDao()).replaceCachedSongs(listOf(song))
            val playlists = SmartPlaylistRepository(database)
            val heavy = SmartPlaylistTemplate.HEAVY_ROTATION
            val snapshot = requireNotNull(playlists.createGeneratedPlaylist(
                name = "Heavy Rotation", templateKey = heavy.key, draft = heavy.draft,
                membershipMode = heavy.membershipMode, refreshPolicy = heavy.refreshPolicy,
                refreshIntervalMillis = heavy.refreshIntervalMillis))
            playlists.refreshGeneratedSnapshot(snapshot.playlistId)
            val never = requireNotNull(playlists.createSmartPlaylist("Never Played", SmartPlaylistTemplate.NEVER_PLAYED.draft))
            assertEquals(listOf(song.id), playlists.resolveFinalMembership(never.playlistId).songs.map { it.id })

            withContext(Dispatchers.Main) {
                adapter = PlaybackServiceListeningAdapter(
                    ListeningNativeTrackResolver(database), ListeningEventRepository(database.listeningEventDao()))
                adapterReady = true
                binding = DirectPlaybackListeningBinding(adapter)
                bindingReady = true
                binding.onActivated()
                player = DirectUsbMediaPlayer(scope, engine, changed = {}, librarySong = { song },
                    listeningChanged = { item, state, terminal ->
                        observedItems += item?.listeningEvidence()?.itemInstanceId
                        binding.onObservation(item, state, terminal)
                    })
                playerReady = true
                player.setMediaItem(original)
                player.prepare()
                yield()
                assertEquals(IntegratedDirectPhase.PREPARING, player.evidence.phase)
            }
            assertTrue(database.listeningTrackIdentityDao().getAll().isEmpty())
            assertEquals(0L, database.listeningEventDao().count())

            withContext(Dispatchers.Main) {
                engine.progress(UsbFullProgress(100, 10001, 44100, UsbFullTransportPhase.PLAYING))
                engine.progress(UsbFullProgress(10001, 10001, 44100, UsbFullTransportPhase.ENDED))
                assertEquals(IntegratedDirectPhase.STOPPING, player.evidence.phase)
                assertEquals(androidx.media3.common.Player.STATE_IDLE, player.playbackState)
            }
            assertEquals(0L, database.listeningEventDao().count())
            val statistics = ListeningStatsRepository(database)
            val publication = async(start = CoroutineStart.UNDISPATCHED) {
                withTimeout(5_000) {
                    statistics.observeProductionHistory().first { it.mostPlayed.isNotEmpty() }
                }
            }
            withContext(Dispatchers.Main) { engine.terminal.complete(naturalResult()) }
            val published = publication.await()
            val stored = database.listeningEventDao().getBackupPage(10, 0).single()
            val localBinding = requireNotNull(database.localTrackBindingDao().getByReferenceKey(song.membershipKey()))
            assertEquals(ListeningSource.NATIVE, stored.source)
            assertEquals(ListeningQualificationPolicy.NATIVE, stored.qualificationPolicy)
            assertEquals(ListeningEventPublicationState.NATIVE, stored.publicationState)
            assertEquals(ListeningTimestampEvidence.NATIVE_EXACT, stored.timestampEvidence)
            assertEquals(localBinding.trackIdentityId, stored.trackIdentityId)
            assertEquals(localBinding.id, stored.localTrackBindingId)
            assertFalse(stored.playbackSessionId.isNullOrBlank())
            assertEquals(song.duration, stored.trackDurationMs)
            assertEquals(stored.startedAt, stored.attributionAt)
            assertTrue(stored.listenedMs >= 0)
            assertEquals(ListeningEndReason.NATURAL_END, stored.endReason)
            assertEquals(ListeningQualificationReason.NATURAL_END, stored.qualificationReason)
            assertEquals(ListeningCompletionClassification.NATIVE_NATURAL, stored.completionClassification)
            assertTrue(stored.qualifiedAsPlay)
            assertEquals(1L, published.mostPlayed.single().track.playCounts.totalPlayCount)
            val overview = statistics.getAllTimeOverview()
            assertEquals(1L, overview.playCounts.totalPlayCount)
            assertEquals(stored.listenedMs, overview.listeningTime.confirmedDetailedListeningMs)
            assertEquals(1L, overview.naturalCompletionCount)
            assertTrue(playlists.resolveFinalMembership(never.playlistId).songs.isEmpty())
            assertEquals(listOf(song.id), playlists.previewMatchingSongs(heavy.draft).songs.map { it.id })
            // A new play dirties live dependencies, but does not bypass generated snapshot policy.
            assertTrue(playlists.resolveFinalMembership(snapshot.playlistId).songs.isEmpty())
            assertEquals(listOf(song.id), playlists.refreshGeneratedSnapshot(snapshot.playlistId).songs.map { it.id })

            withContext(Dispatchers.Main) {
                assertEquals(IntegratedDirectPhase.ENDED, player.evidence.phase)
                assertTrue(observedItems.isNotEmpty())
                assertTrue(observedItems.all { it == "original-item" })
                val duplicateTerminal = player.evidence
                binding.onObservation(original, duplicateTerminal, true)
                player.setMediaItem(song.copy(id = 13, title = "Later selection").toPlayableMediaItem("later-item"))
                // A late old terminal cannot become a global stop/error for a newer selection.
                binding.onObservation(original, duplicateTerminal.copy(phase = IntegratedDirectPhase.ERROR), true)
                binding.onDeactivated()
                binding.onObservation(original, duplicateTerminal.copy(phase = IntegratedDirectPhase.STOPPED), true)
            }
            assertEquals(1L, database.listeningEventDao().count())
            assertEquals(stored, database.listeningEventDao().getByPlaybackSessionId(requireNotNull(stored.playbackSessionId)))
            assertEquals(1L, database.listeningTrackIdentityDao().getAll().size.toLong())
            // Exercise the existing conflict-ignore path with the persisted session key.
            assertEquals(-1L, database.listeningEventDao().insertIgnoringConflict(stored.copy(id = 0, eventUuid = "duplicate")))
        } finally {
            withContext(Dispatchers.Main) {
                engine.terminal.complete(naturalResult())
                if (playerReady) player.stopAndJoin()
                if (bindingReady) binding.onDeactivated()
                if (adapterReady) adapter.closeGracefully()
                yield()
                scope.cancel()
            }
            database.close()
        }
    }

    private class Engine : FullUsbPlaybackEngineClient {
        var starts = 0
        lateinit var progress: (UsbFullProgress) -> Unit
        val terminal = CompletableDeferred<UsbSilentTransportExperimentResult>()
        override suspend fun runIntegratedFull(uri: Uri, control: UsbFullPlaybackControl, progress: (UsbFullProgress) -> Unit): UsbSilentTransportExperimentResult {
            starts++
            this.progress = progress
            control.bind({ true }, {})
            return terminal.await()
        }
        override fun stopIntegratedFull() = Unit
    }

    private val song = Song(12, "Original", "Artist", "Album", 1, 60_000,
        Uri.parse("content://media/external/audio/media/12"), "Music/Original.flac", "Music", null)

    /** Same full-EOF diagnostic facts used by IntegratedDirectPlaybackTest; no history thresholds. */
    private fun naturalResult(): UsbSilentTransportExperimentResult {
        val values = UsbIsoNativeStats.fields.associateWith { 0L }.toMutableMap().apply { putAll(mapOf(
            "formatChannels" to 2L, "formatBitsPerSample" to 16L, "formatBytesPerSample" to 2L,
            "formatBytesPerFrame" to 4L, "formatSubslotBytes" to 2L, "formatValidBits" to 16L,
            "formatAlternate" to 9L, "schedulerNominalRateHz" to 44100L, "playbackTransportState" to 7L,
            "sourceMode" to 3L, "stopReason" to 14L, "started" to 1L, "workerStopped" to 1L, "state" to 7L,
            "streamInfoTotalSamples" to 10001L, "sourceFramesGenerated" to 10001L, "framesScheduled" to 10001L,
            "framesSubmitted" to 10001L, "framesCompleted" to 10001L, "prefixSourceFramesConsumed" to 10001L,
            "feedbackPacketsValid" to 1L, "feedbackScheduledPackets" to 1L, "trueEofReached" to 1L,
            "producerFinishedNaturally" to 1L, "md5VerificationPass" to 1L, "prefixProducerStopped" to 1L,
            "prefixProducerJoined" to 1L, "prefixInitialPrimedFrames" to 8192L, "finalSubmittedSourceFrame" to 10000L,
            "finalCompletedSourceFrame" to 10000L, "outUrbsSubmitted" to 30L, "outUrbsCompleted" to 30L,
            "outPacketsSubmitted" to 235L, "outPacketsCompleted" to 235L, "outBytesSubmitted" to 40004L,
            "kernelOutActualBytes" to 40004L, "terminalTailOutUrbs" to 1L, "terminalTailPackets" to 3L,
            "terminalTailFrames" to 101L, "remainingFramesAtTerminalTransition" to 101L,
            "terminalTailPacketCompletions" to 3L, "terminalTailFrameCompletions" to 101L,
            "wholeDecodedPcmHash" to 123L, "allEnqueuedPcmHash" to 123L, "sourcePcmHash" to 123L, "submittedPcmHash" to 123L)) }
        return UsbSilentTransportExperimentResult(1, 2, UsbSilentTransportResolution(),
            status = UsbSilentStatus.FULL_FLAC_USB_TRANSPORT_VERIFIED,
            configuration = UsbAudioConfigurationResult(1, UsbAudioConfigurationResolution(),
                targetStatus = UsbTargetConfigurationStatus.TARGET_CONFIGURATION_VERIFIED,
                acClaim = UsbConfigurationAndroidOwnership(true, true), asClaim = UsbConfigurationAndroidOwnership(true, true),
                restorationStatus = UsbConfigurationRestorationStatus.RESTORED,
                asRelease = UsbConfigurationAndroidOwnership(true, true), acRelease = UsbConfigurationAndroidOwnership(true, true),
                closeStatus = UsbAudioCleanupStatus.SUCCEEDED),
            transport = UsbIsoTransportResult(1, 2, values), originalFlacFdClosed = true,
            flacSource = UsbFlacSourceResult(mapOf("sampleRate" to 44100L, "totalSamples" to 10001L, "producerJoined" to 1L)))
    }
}
