package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.ListeningIdentityReconciliationEntity

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
        validate(sourceIdentityIds, targetIdentityId, reconciledAt)?.let {
            return@withTransaction it
        }
        val links = sourceIdentityIds.sorted().map { sourceIdentityId ->
            ListeningIdentityReconciliationEntity(
                sourceIdentityId = sourceIdentityId,
                targetIdentityId = targetIdentityId,
                reconciledAt = reconciledAt
            )
        }
        database.listeningIdentityReconciliationDao().insert(links)
        ListeningIdentityReconciliationLinkResult.Linked(links)
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

    private suspend fun validate(
        sourceIdentityIds: List<Long>,
        targetIdentityId: Long,
        reconciledAt: Long
    ): ListeningIdentityReconciliationLinkResult.Rejected? {
        if (sourceIdentityIds.isEmpty()) {
            return rejected(ListeningIdentityReconciliationFailure.NO_SOURCES)
        }
        if (sourceIdentityIds.distinct().size != sourceIdentityIds.size) {
            return rejected(ListeningIdentityReconciliationFailure.DUPLICATE_SOURCE_ID)
        }
        if (reconciledAt < 0L) {
            return rejected(ListeningIdentityReconciliationFailure.INVALID_RECONCILED_AT)
        }
        if (database.listeningTrackIdentityDao().getById(targetIdentityId) == null) {
            return rejected(ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND)
        }
        if (database.listeningIdentityReconciliationDao().isSource(targetIdentityId)) {
            return rejected(ListeningIdentityReconciliationFailure.TARGET_IS_SOURCE)
        }
        if (!database.localTrackBindingDao().existsForTrackIdentity(targetIdentityId)) {
            return rejected(ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING)
        }

        sourceIdentityIds.forEach { sourceIdentityId ->
            if (sourceIdentityId == targetIdentityId) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SAME_IDENTITY,
                    sourceIdentityId
                )
            }
            if (database.listeningTrackIdentityDao().getById(sourceIdentityId) == null) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_NOT_FOUND,
                    sourceIdentityId
                )
            }
            if (database.listeningIdentityReconciliationDao().findBySource(sourceIdentityId) != null) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED,
                    sourceIdentityId
                )
            }
            if (database.listeningIdentityReconciliationDao().isTarget(sourceIdentityId)) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_IS_TARGET,
                    sourceIdentityId
                )
            }
            if (database.localTrackBindingDao().existsForTrackIdentity(sourceIdentityId)) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_HAS_LOCAL_BINDING,
                    sourceIdentityId
                )
            }
            if (!database.listeningEventDao().hasPublishedImportedHistory(sourceIdentityId)) {
                return rejected(
                    ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY,
                    sourceIdentityId
                )
            }
        }
        return null
    }

    private fun rejected(
        reason: ListeningIdentityReconciliationFailure,
        sourceIdentityId: Long? = null
    ) = ListeningIdentityReconciliationLinkResult.Rejected(reason, sourceIdentityId)
}
