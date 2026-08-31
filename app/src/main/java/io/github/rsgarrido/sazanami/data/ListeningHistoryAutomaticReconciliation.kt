package io.github.rsgarrido.sazanami.data

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningIdentityReconciliationEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

data class LocalReconciliationBindingRequest(
    val sourceIdentityId: Long,
    val target: LocalReconciliationTarget
)

data class LocalReconciliationBatchFailure(
    val sourceIdentityId: Long,
    val targetReferenceKey: String,
    val reason: ListeningIdentityReconciliationFailure
)

data class LocalReconciliationBatchResult(
    val requested: Int,
    val newlyLinked: Int,
    val alreadyLinked: Int,
    val conflicts: List<ListeningIdentityReconciliationBatchConflict>,
    val failures: List<LocalReconciliationBatchFailure>,
    val links: List<ListeningIdentityReconciliationEntity>
)

data class AutomaticReconciliationResult(
    val reviewed: Int,
    val requested: Int,
    val newlyLinked: Int,
    val alreadyLinked: Int,
    val skippedNonDeterministic: Int,
    val conflicts: Int,
    val failures: Int
)

/**
 * Shared local-binding authority for manual and automatic reconciliation. Local identities and
 * reconciliation rows are committed in one outer transaction; listening events are never touched.
 */
class ListeningIdentityReconciliationBindingService(
    private val database: AppDatabase,
    private val repository: ListeningIdentityReconciliationRepository =
        ListeningIdentityReconciliationRepository(database),
    private val nativeTrackResolver: ListeningNativeTrackResolver =
        ListeningNativeTrackResolver(database)
) {
    suspend fun linkManyAtomically(
        sourceIdentityIds: List<Long>,
        target: LocalReconciliationTarget,
        currentSongs: List<Song>
    ): ListeningIdentityReconciliationLinkResult {
        if (sourceIdentityIds.isEmpty()) {
            return ListeningIdentityReconciliationLinkResult.Rejected(
                ListeningIdentityReconciliationFailure.NO_SOURCES
            )
        }
        val song = currentSongs.distinctBy(Song::membershipKey)
            .associateBy(Song::membershipKey)[target.referenceKey]
            ?: return ListeningIdentityReconciliationLinkResult.Rejected(
                ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND
            )
        return try {
            database.withTransaction {
                val existingBinding = database.localTrackBindingDao()
                    .getByReferenceKey(target.referenceKey)
                val resolved = resolveTarget(
                    target,
                    song,
                    existingBinding,
                    allowMissingReactivation = false
                )
                    ?: throw AtomicLinkRollback(
                        ListeningIdentityReconciliationLinkResult.Rejected(
                            ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING
                        )
                    )
                when (val result = repository.linkMany(
                    sourceIdentityIds,
                    resolved.trackIdentityId
                )) {
                    is ListeningIdentityReconciliationLinkResult.Linked -> result
                    is ListeningIdentityReconciliationLinkResult.Rejected ->
                        throw AtomicLinkRollback(result)
                }
            }
        } catch (rollback: AtomicLinkRollback) {
            rollback.result
        }
    }

    suspend fun linkBatch(
        requests: List<LocalReconciliationBindingRequest>,
        currentSongs: List<Song>
    ): LocalReconciliationBatchResult = database.withTransaction {
        if (requests.isEmpty()) return@withTransaction LocalReconciliationBatchResult(
            0, 0, 0, emptyList(), emptyList(), emptyList()
        )
        val songsByReference = currentSongs.distinctBy(Song::membershipKey)
            .associateBy(Song::membershipKey)
        val bindingsByReference = database.localTrackBindingDao().getAllForBackup()
            .associateBy(LocalTrackBindingEntity::referenceKey)
            .toMutableMap()
        val resolvedByReference = mutableMapOf<String, NativeListeningTrack>()
        val localFailures = mutableListOf<LocalReconciliationBatchFailure>()
        val repositoryRequests = mutableListOf<ListeningIdentityReconciliationBindingRequest>()

        requests.forEach { request ->
            val referenceKey = request.target.referenceKey
            val song = songsByReference[referenceKey]
            if (song == null) {
                localFailures += LocalReconciliationBatchFailure(
                    request.sourceIdentityId,
                    referenceKey,
                    ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND
                )
                return@forEach
            }
            val resolved = resolvedByReference[referenceKey] ?: resolveTarget(
                request.target,
                song,
                bindingsByReference[referenceKey],
                allowMissingReactivation = true
            )?.also { resolvedByReference[referenceKey] = it }
            if (resolved == null) {
                localFailures += LocalReconciliationBatchFailure(
                    request.sourceIdentityId,
                    referenceKey,
                    ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING
                )
                return@forEach
            }
            repositoryRequests += ListeningIdentityReconciliationBindingRequest(
                request.sourceIdentityId,
                resolved.trackIdentityId
            )
        }

        val repositoryResult = repository.linkBatch(repositoryRequests)
        LocalReconciliationBatchResult(
            requested = requests.size,
            newlyLinked = repositoryResult.newlyLinked,
            alreadyLinked = repositoryResult.alreadyLinked,
            conflicts = repositoryResult.conflicts,
            failures = localFailures + repositoryResult.failures.map { failure ->
                val localRequest = requests.first { request ->
                    request.sourceIdentityId == failure.request.sourceIdentityId
                }
                LocalReconciliationBatchFailure(
                    failure.request.sourceIdentityId,
                    localRequest.target.referenceKey,
                    failure.reason
                )
            },
            links = repositoryResult.links
        )
    }

    private suspend fun resolveTarget(
        target: LocalReconciliationTarget,
        song: Song,
        existing: LocalTrackBindingEntity?,
        allowMissingReactivation: Boolean
    ): NativeListeningTrack? {
        if (target.identityId > 0L && existing?.trackIdentityId != target.identityId) return null
        if (existing != null && existing.missingSince == null) {
            return NativeListeningTrack(existing.trackIdentityId, existing.id)
        }
        if (existing != null && !allowMissingReactivation) return null
        return nativeTrackResolver.resolveOrCreate(
            target.referenceKey,
            song.toSongReference(),
            refreshExistingBinding = existing != null
        )
    }
}

class ListeningHistoryAutomaticReconciler(
    private val database: AppDatabase,
    private val candidateService: ListeningIdentityReconciliationCandidateService =
        ListeningIdentityReconciliationCandidateService(database),
    private val bindingService: ListeningIdentityReconciliationBindingService =
        ListeningIdentityReconciliationBindingService(database)
) {
    private val mutex = Mutex()

    suspend fun reconcile(currentSongs: List<Song>): AutomaticReconciliationResult = mutex.withLock {
        val bindingsByReference = database.localTrackBindingDao().getAllForBackup()
            .associateBy(LocalTrackBindingEntity::referenceKey)
        val targets = currentSongs.distinctBy(Song::membershipKey)
            .sortedWith(localReconciliationSongComparator)
            .mapIndexed { index, song ->
                song.toReconciliationTarget(
                    binding = bindingsByReference[song.membershipKey()],
                    transientId = -(index + 1L)
                )
            }
        val discovery = candidateService.discoverCandidates(targets)
        val requests = automaticReconciliationRequests(discovery)
        val batch = bindingService.linkBatch(requests, currentSongs)
        AutomaticReconciliationResult(
            reviewed = discovery.items.size,
            requested = requests.size,
            newlyLinked = batch.newlyLinked,
            alreadyLinked = batch.alreadyLinked,
            skippedNonDeterministic = discovery.items.size - requests.size,
            conflicts = batch.conflicts.size,
            failures = batch.failures.size
        )
    }
}

internal fun automaticReconciliationRequests(
    discovery: ReconciliationCandidateDiscovery
): List<LocalReconciliationBindingRequest> = discovery.items.mapNotNull { item ->
    if (!item.isDeterministic ||
        (item.confidence != ReconciliationMatchConfidence.EXACT &&
            item.confidence != ReconciliationMatchConfidence.CANONICAL_EXACT)
    ) return@mapNotNull null
    val candidate = item.candidates.singleOrNull() ?: return@mapNotNull null
    LocalReconciliationBindingRequest(item.source.identityId, candidate.target)
}

internal fun Song.toReconciliationTarget(
    binding: LocalTrackBindingEntity?,
    transientId: Long
): LocalReconciliationTarget {
    require(transientId < 0L)
    val referenceKey = membershipKey()
    return LocalReconciliationTarget(
        identityId = binding?.trackIdentityId ?: transientId,
        localBindingId = binding?.id ?: transientId,
        referenceKey = referenceKey,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist.takeIf(String::isNotBlank),
        durationMs = duration.takeIf { it > 0L },
        displayName = displayName.takeIf(String::isNotBlank),
        fileExtension = displayName.substringAfterLast('.', "").takeIf(String::isNotBlank),
        relativeFolder = relativePath.replace('\\', '/').trim('/').takeIf(String::isNotBlank)
    )
}

private val localReconciliationSongComparator = compareBy<Song>(
    { candidateConservativeNormalize(it.title) },
    { candidateConservativeNormalize(it.artist) },
    { candidateConservativeNormalize(it.album) },
    Song::membershipKey
)

private class AtomicLinkRollback(
    val result: ListeningIdentityReconciliationLinkResult.Rejected
) : RuntimeException()
