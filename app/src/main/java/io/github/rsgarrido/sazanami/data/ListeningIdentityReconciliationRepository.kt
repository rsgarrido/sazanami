package io.github.rsgarrido.sazanami.data

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningIdentityReconciliationEntity

enum class ListeningIdentityReconciliationFailure {
    NO_SOURCES,
    DUPLICATE_SOURCE_ID,
    SOURCE_NOT_FOUND,
    TARGET_NOT_FOUND,
    SAME_IDENTITY,
    SOURCE_HAS_LOCAL_BINDING,
    SOURCE_HAS_NO_IMPORTED_HISTORY,
    TARGET_HAS_NO_LOCAL_BINDING,
    SOURCE_ALREADY_RECONCILED,
    SOURCE_IS_TARGET,
    TARGET_IS_SOURCE,
    INVALID_RECONCILED_AT
}

sealed interface ListeningIdentityReconciliationLinkResult {
    data class Linked(
        val links: List<ListeningIdentityReconciliationEntity>
    ) : ListeningIdentityReconciliationLinkResult

    data class Rejected(
        val reason: ListeningIdentityReconciliationFailure,
        val sourceIdentityId: Long? = null
    ) : ListeningIdentityReconciliationLinkResult
}

enum class ListeningIdentityReconciliationRatingState {
    NO_RATINGS,
    TARGET_ONLY,
    SOURCE_ONLY,
    SAME_RATING,
    CONFLICTING_RATINGS
}

data class ListeningIdentityReconciliationRatings(
    val sourceRating: Int?,
    val targetRating: Int?,
    val state: ListeningIdentityReconciliationRatingState
)

data class ListeningIdentityReconciliationBindingRequest(
    val sourceIdentityId: Long,
    val targetIdentityId: Long
)

data class ListeningIdentityReconciliationBatchFailure(
    val request: ListeningIdentityReconciliationBindingRequest,
    val reason: ListeningIdentityReconciliationFailure
)

data class ListeningIdentityReconciliationBatchConflict(
    val request: ListeningIdentityReconciliationBindingRequest,
    val existingTargetIdentityId: Long
)

data class ListeningIdentityReconciliationBatchResult(
    val requested: Int,
    val newlyLinked: Int,
    val alreadyLinked: Int,
    val conflicts: List<ListeningIdentityReconciliationBatchConflict>,
    val failures: List<ListeningIdentityReconciliationBatchFailure>,
    val links: List<ListeningIdentityReconciliationEntity>
)

class ListeningIdentityReconciliationRepository(
    private val database: AppDatabase,
    private val clock: () -> Long = System::currentTimeMillis
) {
    suspend fun link(
        sourceIdentityId: Long,
        targetIdentityId: Long,
        reconciledAt: Long = clock()
    ): ListeningIdentityReconciliationLinkResult =
        linkMany(listOf(sourceIdentityId), targetIdentityId, reconciledAt)

    suspend fun linkMany(
        sourceIdentityIds: List<Long>,
        targetIdentityId: Long,
        reconciledAt: Long = clock()
    ): ListeningIdentityReconciliationLinkResult = database.withTransaction {
        if (sourceIdentityIds.isEmpty()) {
            return@withTransaction rejected(ListeningIdentityReconciliationFailure.NO_SOURCES)
        }
        if (sourceIdentityIds.distinct().size != sourceIdentityIds.size) {
            return@withTransaction rejected(ListeningIdentityReconciliationFailure.DUPLICATE_SOURCE_ID)
        }
        if (reconciledAt < 0L) {
            return@withTransaction rejected(ListeningIdentityReconciliationFailure.INVALID_RECONCILED_AT)
        }
        val requests = sourceIdentityIds.map {
            ListeningIdentityReconciliationBindingRequest(it, targetIdentityId)
        }
        val decisions = classify(requests, reconciledAt)
        decisions.firstOrNull { it !is BatchDecision.New }?.let { decision ->
            return@withTransaction when (decision) {
                is BatchDecision.Already,
                is BatchDecision.Conflict -> rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED,
                    decision.request.sourceIdentityId
                )
                is BatchDecision.Failed -> rejected(
                    decision.reason,
                    decision.request.sourceIdentityId
                )
                is BatchDecision.New -> error("Handled above")
            }
        }
        val links = decisions.filterIsInstance<BatchDecision.New>()
            .map(BatchDecision.New::link)
            .sortedBy(ListeningIdentityReconciliationEntity::sourceIdentityId)
        database.listeningIdentityReconciliationDao().insert(links)
        ListeningIdentityReconciliationLinkResult.Linked(links)
    }

    /**
     * Idempotent partial batch application. All validation reads and inserts share one Room
     * transaction, so a multi-link commit produces one reconciliation-table invalidation.
     */
    suspend fun linkBatch(
        requests: List<ListeningIdentityReconciliationBindingRequest>,
        reconciledAt: Long = clock()
    ): ListeningIdentityReconciliationBatchResult = database.withTransaction {
        if (requests.isEmpty()) return@withTransaction ListeningIdentityReconciliationBatchResult(
            requested = 0,
            newlyLinked = 0,
            alreadyLinked = 0,
            conflicts = emptyList(),
            failures = emptyList(),
            links = emptyList()
        )
        if (reconciledAt < 0L) {
            return@withTransaction ListeningIdentityReconciliationBatchResult(
                requested = requests.size,
                newlyLinked = 0,
                alreadyLinked = 0,
                conflicts = emptyList(),
                failures = requests.map {
                    ListeningIdentityReconciliationBatchFailure(
                        it,
                        ListeningIdentityReconciliationFailure.INVALID_RECONCILED_AT
                    )
                },
                links = emptyList()
            )
        }

        val duplicateSourceIds = requests.groupingBy { it.sourceIdentityId }.eachCount()
            .filterValues { it > 1 }.keys
        // A repeated source is ambiguous (especially if its targets differ), so do not apply any
        // request for that source. Report each occurrence and leave its existing state untouched.
        val uniqueRequests = requests.filter { it.sourceIdentityId !in duplicateSourceIds }
        val decisions = classify(uniqueRequests, reconciledAt)
        val links = decisions.filterIsInstance<BatchDecision.New>()
            .map(BatchDecision.New::link)
            .sortedBy(ListeningIdentityReconciliationEntity::sourceIdentityId)
        if (links.isNotEmpty()) database.listeningIdentityReconciliationDao().insert(links)

        val duplicateFailures = requests.filter { request ->
            request.sourceIdentityId in duplicateSourceIds
        }.map { request ->
            ListeningIdentityReconciliationBatchFailure(
                request,
                ListeningIdentityReconciliationFailure.DUPLICATE_SOURCE_ID
            )
        }
        ListeningIdentityReconciliationBatchResult(
            requested = requests.size,
            newlyLinked = links.size,
            alreadyLinked = decisions.count { it is BatchDecision.Already },
            conflicts = decisions.filterIsInstance<BatchDecision.Conflict>().map {
                ListeningIdentityReconciliationBatchConflict(it.request, it.existingTargetIdentityId)
            },
            failures = duplicateFailures + decisions.filterIsInstance<BatchDecision.Failed>().map {
                ListeningIdentityReconciliationBatchFailure(it.request, it.reason)
            },
            links = links
        )
    }

    suspend fun unlink(sourceIdentityId: Long): Boolean = database.withTransaction {
        database.listeningIdentityReconciliationDao().deleteBySource(sourceIdentityId) == 1
    }

    suspend fun findTargetForSource(
        sourceIdentityId: Long
    ): ListeningIdentityReconciliationEntity? =
        database.listeningIdentityReconciliationDao().findBySource(sourceIdentityId)

    suspend fun findSourcesForTarget(
        targetIdentityId: Long
    ): List<ListeningIdentityReconciliationEntity> =
        database.listeningIdentityReconciliationDao().findSourcesForTarget(targetIdentityId)

    suspend fun listLinks(): List<ListeningIdentityReconciliationEntity> =
        database.listeningIdentityReconciliationDao().getAll()

    suspend fun inspectRatings(
        sourceIdentityId: Long,
        targetIdentityId: Long
    ): ListeningIdentityReconciliationRatings = database.withTransaction {
        val source = database.songRatingDao().getByTrackIdentityId(sourceIdentityId)?.rating
        val target = database.songRatingDao().getByTrackIdentityId(targetIdentityId)?.rating
        val state = when {
            source == null && target == null -> ListeningIdentityReconciliationRatingState.NO_RATINGS
            source == null -> ListeningIdentityReconciliationRatingState.TARGET_ONLY
            target == null -> ListeningIdentityReconciliationRatingState.SOURCE_ONLY
            source == target -> ListeningIdentityReconciliationRatingState.SAME_RATING
            else -> ListeningIdentityReconciliationRatingState.CONFLICTING_RATINGS
        }
        ListeningIdentityReconciliationRatings(source, target, state)
    }

    private suspend fun classify(
        requests: List<ListeningIdentityReconciliationBindingRequest>,
        reconciledAt: Long
    ): List<BatchDecision> {
        if (requests.isEmpty()) return emptyList()
        val sourceIds = requests.mapTo(linkedSetOf()) { it.sourceIdentityId }
        val targetIds = requests.mapTo(linkedSetOf()) { it.targetIdentityId }
        val allIds = sourceIds + targetIds
        val existingIdentityIds = allIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.listeningTrackIdentityDao().getExistingIds(it)
        }.toSet()
        val existingLinks = sourceIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.listeningIdentityReconciliationDao().getForSources(it)
        }.associateBy(ListeningIdentityReconciliationEntity::sourceIdentityId)
        val reconciliationSourceIds = allIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.listeningIdentityReconciliationDao().getSourceIds(it)
        }.toSet()
        val reconciliationTargetIds = sourceIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.listeningIdentityReconciliationDao().getTargetIds(it)
        }.toSet()
        val locallyBoundIds = allIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.localTrackBindingDao().getBoundTrackIdentityIds(it)
        }.toSet()
        val importedHistoryIds = sourceIds.chunked(BULK_QUERY_SIZE).flatMap {
            database.listeningEventDao().getTrackIdentityIdsWithPublishedImportedHistory(it)
        }.toSet()

        return requests.map { request ->
            val sourceId = request.sourceIdentityId
            val targetId = request.targetIdentityId
            val existingLink = existingLinks[sourceId]
            when {
                sourceId == targetId -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.SAME_IDENTITY
                )
                targetId !in existingIdentityIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND
                )
                targetId in reconciliationSourceIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.TARGET_IS_SOURCE
                )
                targetId !in locallyBoundIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING
                )
                sourceId !in existingIdentityIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.SOURCE_NOT_FOUND
                )
                existingLink?.targetIdentityId == targetId -> BatchDecision.Already(request)
                existingLink != null -> BatchDecision.Conflict(
                    request,
                    existingLink.targetIdentityId
                )
                sourceId in reconciliationTargetIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.SOURCE_IS_TARGET
                )
                sourceId in locallyBoundIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.SOURCE_HAS_LOCAL_BINDING
                )
                sourceId !in importedHistoryIds -> BatchDecision.Failed(
                    request,
                    ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY
                )
                else -> BatchDecision.New(
                    request,
                    ListeningIdentityReconciliationEntity(sourceId, targetId, reconciledAt)
                )
            }
        }
    }

    private fun rejected(
        reason: ListeningIdentityReconciliationFailure,
        sourceIdentityId: Long? = null
    ) = ListeningIdentityReconciliationLinkResult.Rejected(reason, sourceIdentityId)

    private sealed interface BatchDecision {
        val request: ListeningIdentityReconciliationBindingRequest

        data class New(
            override val request: ListeningIdentityReconciliationBindingRequest,
            val link: ListeningIdentityReconciliationEntity
        ) : BatchDecision

        data class Already(
            override val request: ListeningIdentityReconciliationBindingRequest
        ) : BatchDecision

        data class Conflict(
            override val request: ListeningIdentityReconciliationBindingRequest,
            val existingTargetIdentityId: Long
        ) : BatchDecision

        data class Failed(
            override val request: ListeningIdentityReconciliationBindingRequest,
            val reason: ListeningIdentityReconciliationFailure
        ) : BatchDecision
    }

    private companion object {
        const val BULK_QUERY_SIZE = 900
    }
}
