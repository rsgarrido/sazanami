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
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

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
    },
    private val evidenceReader: (MediaItem) -> ListeningMediaItemEvidence? =
        MediaItem::listeningEvidence,
    private val queuedReferenceKeys: () -> List<String> = { emptyList() }
) : PlaybackQueueRuntime {
    private val directOutput get()=(player as? ExperimentalUsbPlaybackPlayer)?.takeIf { it.isDirectSelected }
    private val explicitQueuedIds=linkedSetOf<String>()
    private var queuedOutput: ExperimentalUsbPlaybackPlayer?=null
    private var queuedActivation: Long?=null
    override fun invalidateAutomaticAdvance() { directOutput?.invalidateAutomaticAdvance() }
    // The route remains the same owner even if output changes while Room resolution suspends.
    override fun beginContextPreparation() { (player as? ExperimentalUsbPlaybackPlayer)?.beginDirectContextPreparation() }
    override fun endContextPreparation() { (player as? ExperimentalUsbPlaybackPlayer)?.endDirectContextPreparation() }
    override fun canAdvanceGeneration(generation: Long)=directOutput?.canAdvanceGeneration(generation)==true
    override fun canPrepareDirectCurrent(generation: Long)=directOutput?.canPrepareDirectCurrent(generation)==true
    override suspend fun prepareDirectCurrentAndAwait(generation: Long): Boolean {
        val output=directOutput ?: return false
        val request=output.prepareDirectCurrent(generation)
        val result=try { request.await() } catch(cancelled: CancellationException) { request.cancel();throw cancelled }
        return result.status in setOf(DirectSourceTransitionStatus.READY,DirectSourceTransitionStatus.COMPLETED) &&
            directOutput===output && result.nativeGeneration==output.directState.generation && output.directQueueCheckpointAllowed
    }
    override suspend fun navigateAndAwait(command: Int,generation: Long?): Boolean {
        val output=directOutput ?: return false
        val request=output.navigateDirect(command,generation)
        val result=try { request.await() } catch(cancelled: CancellationException) { request.cancel();throw cancelled }
        return result.status in setOf(DirectSourceTransitionStatus.READY,DirectSourceTransitionStatus.COMPLETED,DirectSourceTransitionStatus.UNCHANGED) &&
            directOutput===output && output.directQueueCheckpointAllowed
    }
    override fun setQueueRepeat(repeat: Int)=directOutput?.setDirectRepeat(repeat)==true
    override fun setQueueOrder(entryIds: List<String>,shuffle: Boolean)=directOutput?.setDirectOrder(entryIds,shuffle)==true
    override fun queuedEntryIds(snapshot: LivePlaybackQueueSnapshot): List<String> {
        val output=directOutput
        if(output==null || queuedOutput!==output || queuedActivation!=output.directState.activation?.serial) {
            explicitQueuedIds.clear()
            explicitQueuedIds.addAll(matchQueuedEntryIdsForShuffle(snapshot.entries.map { it.entryId to it.evidence.referenceKey },
                snapshot.currentEntryId ?: return emptyList(),queuedReferenceKeys()))
            queuedOutput=output
            queuedActivation=output?.directState?.activation?.serial
        }
        val current=snapshot.entries.indexOfFirst { it.entryId==snapshot.currentEntryId }
        val upcoming=snapshot.entries.drop(current+1).map { it.entryId }.toSet()
        explicitQueuedIds.retainAll(upcoming)
        return snapshot.entries.map { it.entryId }.filter { it in explicitQueuedIds }
    }
    override fun enqueueSongs(songs: List<Song>,playNext: Boolean): Boolean {
        val output=directOutput ?: return false
        val snapshot=captureSnapshot() ?: return false
        val queued=queuedEntryIds(snapshot)
        val queue=output.queuePlayer
        val items=(0 until queue.mediaItemCount).map(queue::getMediaItemAt)
        val current=queue.currentMediaItemIndex
        val added=songs.map { it.toPlayableMediaItem() }
        val (explicit,ordinary)=items.drop(current+1).partition { evidenceReader(it)?.itemInstanceId in queued }
        val queuedItems=explicit.toMutableList()
        insertExplicitQueueItems(queuedItems,added,playNext)
        if(!output.editDirectQueue(items.take(current+1)+queuedItems+ordinary)) return false
        explicitQueuedIds.addAll(added.map { checkNotNull(evidenceReader(it)?.itemInstanceId) })
        onBaseSongsRestored(logicalBaseSongs()+songs,queue.shuffleModeEnabled)
        return true
    }
    override fun canReplaceTimeline(): Boolean =
        directOutput?.canReplaceDirectTimeline() ?: player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)

    override fun canCheckpoint(): Boolean = directOutput?.directQueueCheckpointAllowed ?: true
    override fun usesDirectOutput(): Boolean = directOutput!=null
    override fun requestedPlaying(): Boolean = directOutput?.directRequestedPlaying ?: player.playWhenReady
    override fun canRestoreBackup(): Boolean = directOutput==null
    override fun canActivate(restoration: PlaybackQueueRestoration): Boolean {
        val output=directOutput ?: return canReplaceTimeline()
        val ordered=restoration.entries.sortedBy { it.persistedEntry.playbackOrder }
        val index=ordered.indexOfFirst { it.persistedEntry.entryId==restoration.currentEntryId }
        return output.canAdmitDirectTimeline(ordered.map(mediaItemFactory),index,restoration.currentPositionMs,restoration.repeatMode.toPlayerRepeatMode())
    }
    override fun canSelectEntry(entryId: String): Boolean {
        val output=directOutput ?: return player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)
        val queue=output.queuePlayer
        val items=(0 until queue.mediaItemCount).map(queue::getMediaItemAt)
        val index=items.indexOfFirst { evidenceReader(it)?.itemInstanceId==entryId }
        return output.canAdmitDirectTimeline(items,index,0,queue.repeatMode)
    }
    override fun captureBaseSongs(): List<Song> = logicalBaseSongs().toList()
    override fun restoreBaseSongs(songs: List<Song>,shuffle: Boolean) {
        beforeTimelineReplacement()
        onBaseSongsRestored(songs,shuffle)
    }

    override fun captureForBackupRestore(): BackupRestorePlaybackSnapshot? {
        if (player.mediaItemCount == 0) return null
        return BackupRestorePlaybackSnapshot(
            mediaItems = (0 until player.mediaItemCount).map(player::getMediaItemAt),
            currentIndex = player.currentMediaItemIndex.coerceAtLeast(0),
            currentPositionMs = player.currentPosition.coerceAtLeast(0L),
            shouldPlay = player.playWhenReady,
            shuffleEnabled = player.shuffleModeEnabled,
            repeatMode = player.repeatMode
        )
    }

    override fun clearForBackupRestore() {
        player.pause()
        player.stop()
        player.clearMediaItems()
    }

    override fun restoreAfterFailedBackupRestore(snapshot: BackupRestorePlaybackSnapshot) {
        player.shuffleModeEnabled = snapshot.shuffleEnabled
        player.repeatMode = snapshot.repeatMode
        player.setMediaItems(
            snapshot.mediaItems,
            snapshot.currentIndex,
            snapshot.currentPositionMs
        )
        player.prepare()
        if (snapshot.shouldPlay) player.play()
    }

    override fun completeBackupRestore() {
        onBaseSongsRestored(emptyList(), false)
        player.shuffleModeEnabled = false
        player.repeatMode = Player.REPEAT_MODE_OFF
    }

    override fun captureSnapshot(): LivePlaybackQueueSnapshot? {
        if(!canCheckpoint()) return null
        return captureSelectionSnapshot(useIntent=false)
    }
    override fun captureOutputExitSnapshot(): LivePlaybackQueueSnapshot? {
        val output=directOutput ?: return captureSnapshot()
        if(!output.canCaptureDirectExitSnapshot) return null
        return captureSelectionSnapshot(useIntent=false)
    }
    override fun captureSelectionSnapshot(): LivePlaybackQueueSnapshot? {
        return captureSelectionSnapshot(useIntent=true)
    }
    private fun captureSelectionSnapshot(useIntent: Boolean): LivePlaybackQueueSnapshot? {
        val output=directOutput
        val player=directOutput?.queuePlayer ?: this.player
        if (player.mediaItemCount == 0) return null
        val mediaItems=if(useIntent && output!=null) output.directIntentItems else (0 until player.mediaItemCount).map(player::getMediaItemAt)
        val selectedIndex=if(useIntent && output!=null) output.directIntentIndex else player.currentMediaItemIndex
        val entries = mediaItems.mapNotNull { item ->
            val evidence = evidenceReader(item)
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
        val currentEntryId = mediaItems.getOrNull(selectedIndex)
            ?.let(evidenceReader)
            ?.itemInstanceId
        return LivePlaybackQueueSnapshot(
            entries = entries,
            baseEntryIds = baseEntryIds,
            currentEntryId = currentEntryId,
            currentPositionMs = (if(useIntent && output!=null) output.directIntentPositionMs else player.currentPosition).coerceAtLeast(0L),
            shouldPlay = player.playWhenReady,
            shuffleEnabled = if(directOutput!=null) player.shuffleModeEnabled else isLogicalShuffleEnabled(),
            repeatMode = player.repeatMode.toPersistedQueueRepeatMode()
        )
    }

    override fun replaceTimeline(restoration: PlaybackQueueRestoration) {
        check(canReplaceTimeline()) { "Playback queue replacement is unavailable" }
        check(directOutput==null) { "Direct activation requires native acknowledgment" }
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

    override suspend fun replaceTimelineAndAwait(restoration: PlaybackQueueRestoration): Boolean {
        val output=directOutput ?: return super<PlaybackQueueRuntime>.replaceTimelineAndAwait(restoration)
        if(!canReplaceTimeline()) return false
        val ordered=restoration.entries.sortedBy { it.persistedEntry.playbackOrder }
        val index=ordered.indexOfFirst { it.persistedEntry.entryId==restoration.currentEntryId }
        val request=output.replaceDirectTimeline(ordered.map(mediaItemFactory),index,restoration.shouldPlay,
            restoration.currentPositionMs,restoration.shuffleEnabled,restoration.repeatMode.toPlayerRepeatMode())
        val result=try { request.await() } catch(cancelled: CancellationException) { request.cancel();throw cancelled }
        if(result.status !in setOf(DirectSourceTransitionStatus.READY,DirectSourceTransitionStatus.COMPLETED) ||
            directOutput!==output || !output.directQueueCheckpointAllowed) return false
        beforeTimelineReplacement()
        explicitQueuedIds.clear();queuedOutput=output;queuedActivation=output.directState.activation?.serial
        onBaseSongsRestored(restoration.entries.sortedBy { it.persistedEntry.baseOrder }.map { it.song },output.queuePlayer.shuffleModeEnabled)
        return true
    }
    override suspend fun restoreColdTimeline(restoration: PlaybackQueueRestoration): Boolean {
        val output=directOutput ?: return super<PlaybackQueueRuntime>.restoreColdTimeline(restoration)
        if(!canReplaceTimeline() || output.queuePlayer.mediaItemCount!=0) return false
        val ordered=restoration.entries.sortedBy { it.persistedEntry.playbackOrder }
        val index=ordered.indexOfFirst { it.persistedEntry.entryId==restoration.currentEntryId }
        output.restoreDirectIdleTimeline(ordered.map(mediaItemFactory),index,restoration.currentPositionMs,
            restoration.shuffleEnabled,restoration.repeatMode.toPlayerRepeatMode())
        restoreBaseSongs(restoration.entries.sortedBy { it.persistedEntry.baseOrder }.map { it.song },restoration.shuffleEnabled)
        return true
    }

    override suspend fun seekToEntryAndAwait(entryId: String): Boolean {
        val output=directOutput ?: return super<PlaybackQueueRuntime>.seekToEntryAndAwait(entryId)
        if(!output.canReplaceDirectTimeline()) return false
        val queue=output.queuePlayer
        val index=(0 until queue.mediaItemCount).firstOrNull { evidenceReader(queue.getMediaItemAt(it))?.itemInstanceId==entryId } ?: return false
        val request=output.selectDirectQueueOccurrence(index)
        val result=try { request.await() } catch(cancelled: CancellationException) { request.cancel();throw cancelled }
        return result.status in setOf(DirectSourceTransitionStatus.READY,DirectSourceTransitionStatus.COMPLETED,DirectSourceTransitionStatus.UNCHANGED) &&
            directOutput===output && output.directQueueCheckpointAllowed &&
            evidenceReader(queue.currentMediaItem ?: return false)?.itemInstanceId==entryId
    }

    override fun removeEntry(entryId: String): Boolean {
        directOutput?.let { output ->
            val queue=output.queuePlayer
            val items=(0 until queue.mediaItemCount).map(queue::getMediaItemAt).toMutableList()
            val index=items.indexOfFirst { evidenceReader(it)?.itemInstanceId==entryId }
            // Current removal, clearing and edits during preparation retain their safety guard.
            if(index<0 || index==queue.currentMediaItemIndex) return false
            val removedKey=evidenceReader(items.removeAt(index))?.referenceKey
            if(!output.editDirectQueue(items)) return false
            val base=logicalBaseSongs().toMutableList()
            val baseIndex=base.indexOfFirst { it.membershipKey()==removedKey }
            if(baseIndex>=0) base.removeAt(baseIndex)
            onBaseSongsRestored(base,queue.shuffleModeEnabled)
            return true
        }
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false
        val index = (0 until player.mediaItemCount).firstOrNull { candidate ->
            evidenceReader(player.getMediaItemAt(candidate))?.itemInstanceId == entryId
        } ?: return false
        player.removeMediaItem(index)
        return true
    }

    override fun moveEntry(entryId: String, toPlaybackOrder: Int): Boolean {
        directOutput?.let { output ->
            val queue=output.queuePlayer
            val items=(0 until queue.mediaItemCount).map(queue::getMediaItemAt).toMutableList()
            val from=items.indexOfFirst { evidenceReader(it)?.itemInstanceId==entryId }
            if(from !in items.indices || toPlaybackOrder !in items.indices ||
                from<=queue.currentMediaItemIndex || toPlaybackOrder<=queue.currentMediaItemIndex) return false
            items.add(toPlaybackOrder,items.removeAt(from))
            if(!output.editDirectQueue(items)) return false
            if(!queue.shuffleModeEnabled) {
                val songs=logicalBaseSongs().associateBy { it.id.toString() }
                val ordered=items.mapNotNull { songs[it.mediaId] }
                if(ordered.size==items.size) onBaseSongsRestored(ordered,false)
            }
            return true
        }
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false
        if (toPlaybackOrder !in 0 until player.mediaItemCount) return false
        val fromIndex = (0 until player.mediaItemCount).firstOrNull { candidate ->
            evidenceReader(player.getMediaItemAt(candidate))?.itemInstanceId == entryId
        } ?: return false
        val currentIndex = player.currentMediaItemIndex
        if (currentIndex >= 0 && (fromIndex <= currentIndex || toPlaybackOrder <= currentIndex)) {
            return false
        }
        if (fromIndex == toPlaybackOrder) return true
        player.moveMediaItem(fromIndex, toPlaybackOrder)
        return true
    }

    override fun seekToEntry(entryId: String): Boolean {
        if(directOutput!=null) return false // Synchronous acknowledgment is insufficient for native selection.
        if (!player.isCommandAvailable(Player.COMMAND_SEEK_TO_MEDIA_ITEM)) return false
        val index = (0 until player.mediaItemCount).firstOrNull { candidate ->
            evidenceReader(player.getMediaItemAt(candidate))?.itemInstanceId == entryId
        } ?: return false
        if (index == player.currentMediaItemIndex) return true
        player.seekToDefaultPosition(index)
        return true
    }

    override fun insertEntry(
        item: ResolvedPlaybackQueueItem,
        playbackOrder: Int
    ): Boolean {
        directOutput?.let { output ->
            val queue=output.queuePlayer
            val items=(0 until queue.mediaItemCount).map(queue::getMediaItemAt).toMutableList()
            if(items.any { evidenceReader(it)?.itemInstanceId==item.persistedEntry.entryId }) return false
            items.add(playbackOrder.coerceIn(0,items.size),mediaItemFactory(item))
            if(!output.editDirectQueue(items)) return false
            onBaseSongsRestored(logicalBaseSongs().toMutableList().apply {
                add(item.persistedEntry.baseOrder.coerceIn(0,size),item.song)
            },queue.shuffleModeEnabled)
            return true
        }
        if (!player.isCommandAvailable(Player.COMMAND_CHANGE_MEDIA_ITEMS)) return false
        if ((0 until player.mediaItemCount).any { candidate ->
                evidenceReader(player.getMediaItemAt(candidate))?.itemInstanceId ==
                    item.persistedEntry.entryId
            }) return false
        player.addMediaItem(playbackOrder.coerceIn(0, player.mediaItemCount), mediaItemFactory(item))
        val restoredBaseSongs = logicalBaseSongs().toMutableList().apply {
            add(item.persistedEntry.baseOrder.coerceIn(0, size), item.song)
        }
        onBaseSongsRestored(restoredBaseSongs, isLogicalShuffleEnabled())
        return true
    }
}

internal object PlaybackQueueRuntimeBridge {
    private var coordinator: PlaybackQueueCoordinator? = null
    private var contextRequest: ((List<Song>,List<MediaItem>,Int,Long,Boolean,List<String>,Boolean,PersistedQueueRepeatMode)->Unit)?=null
    @Volatile
    private var activeQueueId: String? = null
    private val publication=MutableStateFlow(0L)
    val revisions=publication.asStateFlow()

    fun register(coordinator: PlaybackQueueCoordinator) {
        this.coordinator = coordinator
        updateActiveQueueId(coordinator.getActiveQueueId())
    }

    fun unregister(coordinator: PlaybackQueueCoordinator) {
        if (this.coordinator === coordinator) {
            this.coordinator = null
            contextRequest=null
            updateActiveQueueId(null)
        }
    }
    fun registerContextRequest(owner: PlaybackQueueCoordinator,
        request: (List<Song>,List<MediaItem>,Int,Long,Boolean,List<String>,Boolean,PersistedQueueRepeatMode)->Unit) {
        check(coordinator===owner)
        contextRequest=request
    }
    fun requestPlaybackContext(songs: List<Song>,items: List<MediaItem>,index: Int,positionMs: Long,
        playing: Boolean,baseEntryIds: List<String>,shuffle: Boolean,repeat: PersistedQueueRepeatMode): Boolean {
        val request=contextRequest ?: return false
        request(songs,items,index,positionMs,playing,baseEntryIds,shuffle,repeat)
        return true
    }

    suspend fun saveActiveQueue(): String? = coordinator?.persistActiveQueueSnapshot()

    suspend fun beginBackupRestore() {
        coordinator?.beginBackupRestore()
    }

    suspend fun finishBackupRestore(databaseCommitted: Boolean) {
        coordinator?.finishBackupRestore(databaseCommitted)
    }

    fun prepareNewPlaybackContext(baseEntryIds: List<String>) {
        coordinator?.prepareNewPlaybackContext(baseEntryIds)
    }

    suspend fun createQueueFromCurrent(): String? =
        coordinator?.createQueueFromCurrent()?.queue?.queueId

    suspend fun switchActiveQueue(queueId: String): Boolean =
        coordinator?.switchToQueue(queueId) == true

    suspend fun createAndActivateQueue(displayName: String, songs: List<Song>): String? =
        coordinator?.createAndActivateQueue(displayName, songs)?.queue?.queueId

    suspend fun createInactiveQueue(songs: List<Song>): String? =
        coordinator?.createInactiveQueue(songs)?.queue?.queueId

    suspend fun appendToInactiveQueue(queueId: String, songs: List<Song>): Boolean =
        coordinator?.appendToInactiveQueue(queueId, songs) != null
    suspend fun enqueueSongs(songs: List<Song>,playNext: Boolean): Boolean =
        coordinator?.enqueueSongs(songs,playNext)==true

    suspend fun removeQueueEntry(queueId: String, entryId: String): Boolean =
        coordinator?.removeEntry(queueId, entryId) == true

    suspend fun removeQueueEntryForUndo(
        queueId: String,
        entryId: String
    ): PlaybackQueueEntryRemoval? = coordinator?.removeEntryForUndo(queueId, entryId)

    suspend fun undoRemoveQueueEntry(removal: PlaybackQueueEntryRemoval): Boolean =
        coordinator?.undoRemoveEntry(removal) == true

    suspend fun playQueueEntry(queueId: String, entryId: String): Boolean =
        coordinator?.playEntry(queueId, entryId) == true

    suspend fun reorderQueueEntry(
        queueId: String,
        entryId: String,
        toPlaybackOrder: Int
    ): Boolean = coordinator?.reorderEntry(queueId, entryId, toPlaybackOrder) == true

    fun getActiveQueueId(): String? = activeQueueId

    fun getActiveQueueSnapshot(): LivePlaybackQueueSnapshot? =
        coordinator?.captureActiveQueueSnapshot()

    fun updateActiveQueueId(queueId: String?) {
        activeQueueId = queueId
        publication.update { it+1L }
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
