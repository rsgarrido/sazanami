package com.example.cdplaya.player

import android.os.SystemClock
import android.util.Log
import androidx.media3.common.MediaItem
import com.example.cdplaya.data.ListeningEventRepository
import com.example.cdplaya.data.ListeningNativeTrackResolver
import com.example.cdplaya.data.listening.FinalizedListeningEventDraft
import com.example.cdplaya.data.local.ListeningEndReason
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.joinAll
import kotlinx.coroutines.launch
import java.util.UUID

/** Main-looper callback ingress with a single coroutine consumer and independent Room writes. */
class PlaybackServiceListeningAdapter(
    trackResolver: ListeningNativeTrackResolver,
    eventRepository: ListeningEventRepository,
    coordinatorFactory: PlaybackListeningCoordinatorFactory = PlaybackListeningCoordinatorFactory()
) {
    private sealed interface Command {
        data class Playing(val evidence: ListeningMediaItemEvidence?, val active: Boolean, val at: PlaybackCallbackTimestamp) : Command
        data class Transition(val evidence: ListeningMediaItemEvidence?, val reason: ListeningMediaTransitionReason, val active: Boolean, val at: PlaybackCallbackTimestamp) : Command
        data class Discontinuity(val evidence: ListeningMediaItemEvidence?, val at: PlaybackCallbackTimestamp) : Command
        data class Ended(val evidence: ListeningMediaItemEvidence?, val at: PlaybackCallbackTimestamp) : Command
        data class AudibleStart(val evidence: ListeningMediaItemEvidence?, val at: PlaybackCallbackTimestamp) : Command
        data class AudibleEnd(val evidence: ListeningMediaItemEvidence?, val reason: ListeningEndReason, val at: PlaybackCallbackTimestamp) : Command
        data class LogicalHandoff(val evidence: ListeningMediaItemEvidence?) : Command
        data class Error(val at: PlaybackCallbackTimestamp) : Command
        data class Stopped(val at: PlaybackCallbackTimestamp) : Command
        data class Destroy(val at: PlaybackCallbackTimestamp) : Command
    }

    private val adapterJob = SupervisorJob()
    private val adapterScope = CoroutineScope(adapterJob + Dispatchers.Main.immediate)
    private val persistenceJob = SupervisorJob()
    private val persistenceScope = CoroutineScope(persistenceJob + Dispatchers.IO)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val coordinator = coordinatorFactory.create(
        resolution = NativeListeningTrackResolution { evidence ->
            trackResolver.resolveOrCreate(evidence.referenceKey, evidence.reference)
        },
        onFinalized = { draft -> persist(eventRepository, draft) },
        onFailure = { error ->
            Log.e(TAG, "Native listening adapter command failed", error)
        }
    )

    init {
        adapterScope.launch {
            for (command in commands) {
                when (command) {
                    is Command.Playing -> coordinator.onIsPlayingChanged(command.evidence, command.active, command.at)
                    is Command.Transition -> coordinator.onMediaItemTransition(command.evidence, command.reason, command.active, command.at)
                    is Command.Discontinuity -> coordinator.onPositionDiscontinuity(command.evidence, command.at)
                    is Command.Ended -> coordinator.onNaturalEnd(command.evidence, command.at)
                    is Command.AudibleStart -> command.evidence?.let { evidence ->
                        coordinator.onAudibleStarted(evidence, command.at)
                    }
                    is Command.AudibleEnd -> coordinator.onAudibleEnded(
                        command.evidence,
                        command.reason,
                        command.at
                    )
                    is Command.LogicalHandoff ->
                        coordinator.onLogicalHandoff(command.evidence)
                    is Command.Error -> coordinator.onError(command.at)
                    is Command.Stopped -> coordinator.onStopped(command.at)
                    is Command.Destroy -> {
                        coordinator.onServiceDestroyed(command.at)
                        commands.close()
                    }
                }
            }
            persistenceJob.children.toList().joinAll()
            persistenceScope.cancel()
            adapterScope.cancel()
        }
    }

    fun onIsPlayingChanged(item: MediaItem?, isPlaying: Boolean) {
        commands.trySend(Command.Playing(item?.listeningEvidence(), isPlaying, now()))
    }

    fun onMediaItemTransition(item: MediaItem?, reason: ListeningMediaTransitionReason, isPlaying: Boolean) {
        commands.trySend(Command.Transition(item?.listeningEvidence(), reason, isPlaying, now()))
    }

    fun onPositionDiscontinuity(item: MediaItem?) {
        commands.trySend(Command.Discontinuity(item?.listeningEvidence(), now()))
    }

    fun onNaturalEnd(item: MediaItem?) {
        commands.trySend(Command.Ended(item?.listeningEvidence(), now()))
    }

    fun onCrossfadeIncomingAudible(item: MediaItem) {
        commands.trySend(Command.AudibleStart(item.listeningEvidence(), now()))
    }

    fun onCrossfadeLogicalHandoff(item: MediaItem) {
        commands.trySend(Command.LogicalHandoff(item.listeningEvidence()))
    }

    fun onCrossfadeCompleted(outgoingItem: MediaItem?) {
        commands.trySend(
            Command.AudibleEnd(
                outgoingItem?.listeningEvidence(),
                ListeningEndReason.NATURAL_END,
                now()
            )
        )
    }

    fun onCrossfadeCancelled(
        outgoingItem: MediaItem?,
        incomingItem: MediaItem,
        survivingItem: MediaItem?
    ) {
        val timestamp = now()
        val survivorId = survivingItem?.listeningEvidence()?.itemInstanceId
        val outgoingEvidence = outgoingItem?.listeningEvidence()
        val incomingEvidence = incomingItem.listeningEvidence()
        listOf(outgoingEvidence, incomingEvidence)
            .filterNotNull()
            .filter { evidence -> evidence.itemInstanceId != survivorId }
            .forEach { evidence ->
                commands.trySend(
                    Command.AudibleEnd(
                        evidence,
                        ListeningEndReason.TRANSITION,
                        timestamp
                    )
                )
            }
        survivingItem?.let { item ->
            commands.trySend(Command.LogicalHandoff(item.listeningEvidence()))
        }
    }

    fun onError() { commands.trySend(Command.Error(now())) }
    fun onStopped() { commands.trySend(Command.Stopped(now())) }

    /** Drains already timestamped callbacks, finalizes once, then waits asynchronously for writes. */
    fun closeGracefully() { commands.trySend(Command.Destroy(now())) }

    private fun persist(repository: ListeningEventRepository, draft: FinalizedListeningEventDraft) {
        persistenceScope.launch {
            runCatching { repository.insertFinalizedDraft(draft) }
                .onFailure { error ->
                    Log.e(TAG, "Unable to persist finalized native listening event", error)
                }
        }
    }

    private fun now() = PlaybackCallbackTimestamp(
        monotonicMs = SystemClock.elapsedRealtime(),
        wallClockMs = System.currentTimeMillis()
    )

    private companion object { const val TAG = "NativeListening" }
}

class PlaybackListeningCoordinatorFactory {
    fun create(
        resolution: NativeListeningTrackResolution,
        onFinalized: (FinalizedListeningEventDraft) -> Unit,
        onFailure: (Throwable) -> Unit
    ): PlaybackListeningCoordinator {
        val clock = PlaybackCallbackClock()
        fun recorder() = com.example.cdplaya.data.listening.ListeningSessionRecorder(
            monotonicClock = clock,
            wallClock = clock,
            eventUuidGenerator = { UUID.randomUUID().toString() }
        )
        return PlaybackListeningCoordinator(
            recorder = recorder(),
            callbackClock = clock,
            trackResolution = resolution,
            sessionIdGenerator = { UUID.randomUUID().toString() },
            onFinalized = onFinalized,
            onFailure = onFailure,
            additionalRecorderFactory = { recorder() }
        )
    }
}
