package io.github.rsgarrido.sazanami.player

import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import io.github.rsgarrido.sazanami.data.ListeningNativeTrackResolver
import io.github.rsgarrido.sazanami.data.PlaybackQueueRepository
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.PersistedQueueRepeatMode
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.membershipKey

internal class RoomPlaybackQueueTrackAccess(
    private val database: AppDatabase,
    private val catalogSongs: suspend () -> List<Song>,
    private val nativeTrackResolver: ListeningNativeTrackResolver =
        ListeningNativeTrackResolver(database)
) : PlaybackQueueTrackAccess {
    override suspend fun identify(
        items: List<LivePlaybackQueueItem>
    ): List<IdentifiedLivePlaybackQueueItem> {
        val identified = mutableListOf<IdentifiedLivePlaybackQueueItem>()
        for (item in items) {
            val resolved = nativeTrackResolver.resolveOrCreate(
                referenceKey = item.evidence.referenceKey,
                reference = item.evidence.reference
            )
            identified += IdentifiedLivePlaybackQueueItem(
                liveItem = item,
                trackIdentityId = resolved.trackIdentityId,
                localTrackBindingId = resolved.localTrackBindingId
            )
        }
        return identified
    }

    override suspend fun resolve(
        entries: List<PlaybackQueueEntryEntity>
    ): List<ResolvedPlaybackQueueItem> {
        if (entries.isEmpty()) return emptyList()
        val songsByReferenceKey = catalogSongs()
            .associateBy(Song::membershipKey)
        if (songsByReferenceKey.isEmpty()) return emptyList()

        return entries.mapNotNull { entry ->
            val bindings = bindingsFor(entry)
            val song = bindings.firstNotNullOfOrNull { binding ->
                songsByReferenceKey[binding.referenceKey]
            } ?: return@mapNotNull null
            ResolvedPlaybackQueueItem(entry, song)
        }
    }

    private suspend fun bindingsFor(
        entry: PlaybackQueueEntryEntity
    ): List<LocalTrackBindingEntity> {
        val bindings = mutableListOf<LocalTrackBindingEntity>()
        entry.localTrackBindingId?.let { bindingId ->
            database.localTrackBindingDao().getById(bindingId)?.let(bindings::add)
        }
        bindings += database.localTrackBindingDao().getForTrackIdentity(entry.trackIdentityId)

        var sourceIdentityId = entry.trackIdentityId
        val visited = mutableSetOf<Long>()
        while (visited.add(sourceIdentityId)) {
            val reconciliation = database.listeningIdentityReconciliationDao()
                .findBySource(sourceIdentityId) ?: break
            sourceIdentityId = reconciliation.targetIdentityId
            bindings += database.localTrackBindingDao().getForTrackIdentity(sourceIdentityId)
        }
        return bindings.distinctBy { binding -> binding.id }
    }
}

internal class Media3PlaybackQueueRuntime(
    private val player: Player,
    private val isLogicalShuffleEnabled: () -> Boolean,
    private val logicalBaseSongs: () -> List<Song>,
    private val beforeTimelineReplacement: () -> Unit,
    private val onBaseSongsRestored: (List<Song>, Boolean) -> Unit,
    private val mediaItemFactory: (ResolvedPlaybackQueueItem) -> MediaItem = { item ->
        item.song.toPlayableMediaItem(
            itemInstanceId = item.persistedEntry.entryId
        )
    }
) : PlaybackQueueRuntime {
    override fun captureSnapshot(): LivePlaybackQueueSnapshot? {
        if (player.mediaItemCount == 0) return null
        val entries = (0 until player.mediaItemCount).mapNotNull { index ->
            val evidence = player.getMediaItemAt(index).listeningEvidence()
                ?: return@mapNotNull null
            LivePlaybackQueueItem(
                entryId = evidence.itemInstanceId,
                evidence = evidence
            )
        }
        if (entries.isEmpty()) return null
        val unmatchedEntries = entries.toMutableList()
        val baseEntryIds = buildList {
            logicalBaseSongs().forEach { song ->
                val referenceKey = song.membershipKey()
                val matchIndex = unmatchedEntries.indexOfFirst { item ->
                    item.evidence.referenceKey == referenceKey
                }
                if (matchIndex >= 0) add(unmatchedEntries.removeAt(matchIndex).entryId)
            }
            addAll(unmatchedEntries.map(LivePlaybackQueueItem::entryId))
        }
        val currentEntryId = player.currentMediaItem
            ?.listeningEvidence()
            ?.itemInstanceId
        return LivePlaybackQueueSnapshot(
            entries = entries,
            baseEntryIds = baseEntryIds,
            currentEntryId = currentEntryId,
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            shouldPlay = player.playWhenReady,
            shuffleEnabled = isLogicalShuffleEnabled(),
            repeatMode = player.repeatMode.toPersistedQueueRepeatMode()
        )
    }

    override fun replaceTimeline(restoration: PlaybackQueueRestoration) {
        beforeTimelineReplacement()
        val playbackOrdered = restoration.entries.sortedBy { item ->
            item.persistedEntry.playbackOrder
        }
        val currentIndex = playbackOrdered.indexOfFirst { item ->
            item.persistedEntry.entryId == restoration.currentEntryId
        }.coerceAtLeast(0)
        val baseSongs = restoration.entries
            .sortedBy { item -> item.persistedEntry.baseOrder }
            .map(ResolvedPlaybackQueueItem::song)

        onBaseSongsRestored(baseSongs, restoration.shuffleEnabled)
        if (player.playWhenReady) player.pause()
        player.shuffleModeEnabled = false
        player.repeatMode = restoration.repeatMode.toPlayerRepeatMode()
        player.setMediaItems(
            playbackOrdered.map(mediaItemFactory),
            currentIndex,
            restoration.currentPositionMs
        )
        player.prepare()
        if (restoration.shouldPlay) player.play()
    }
}

internal object PlaybackQueueRuntimeBridge {
    private var coordinator: PlaybackQueueCoordinator? = null
    @Volatile
    private var activeQueueId: String? = null

    fun register(coordinator: PlaybackQueueCoordinator) {
        this.coordinator = coordinator
        updateActiveQueueId(coordinator.getActiveQueueId())
    }

    fun unregister(coordinator: PlaybackQueueCoordinator) {
        if (this.coordinator === coordinator) {
            this.coordinator = null
            updateActiveQueueId(null)
        }
    }

    suspend fun saveActiveQueue(): String? = coordinator?.persistActiveQueueSnapshot()

    suspend fun createQueueFromCurrent(): String? =
        coordinator?.createQueueFromCurrent()?.queue?.queueId

    suspend fun switchActiveQueue(queueId: String): Boolean =
        coordinator?.switchToQueue(queueId) == true

    fun getActiveQueueId(): String? = activeQueueId

    fun updateActiveQueueId(queueId: String?) {
        activeQueueId = queueId
    }
}

internal fun PlaybackQueueRepository.asPlaybackQueuePersistence(): PlaybackQueuePersistence =
    RepositoryPlaybackQueuePersistence(this)

private fun Int.toPersistedQueueRepeatMode(): PersistedQueueRepeatMode = when (this) {
    Player.REPEAT_MODE_ALL -> PersistedQueueRepeatMode.ALL
    Player.REPEAT_MODE_ONE -> PersistedQueueRepeatMode.ONE
    else -> PersistedQueueRepeatMode.OFF
}

private fun PersistedQueueRepeatMode.toPlayerRepeatMode(): Int = when (this) {
    PersistedQueueRepeatMode.OFF -> Player.REPEAT_MODE_OFF
    PersistedQueueRepeatMode.ALL -> Player.REPEAT_MODE_ALL
    PersistedQueueRepeatMode.ONE -> Player.REPEAT_MODE_ONE
}
