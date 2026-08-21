package com.example.cdplaya.controller

import com.example.cdplaya.data.HistoricalReconciliationItem
import com.example.cdplaya.data.HistoricalReconciliationSource
import com.example.cdplaya.data.ListeningIdentityReconciliationCandidateService
import com.example.cdplaya.data.ListeningIdentityReconciliationFailure
import com.example.cdplaya.data.ListeningIdentityReconciliationLinkResult
import com.example.cdplaya.data.ListeningIdentityReconciliationRatingState
import com.example.cdplaya.data.ListeningIdentityReconciliationRatings
import com.example.cdplaya.data.ListeningIdentityReconciliationRepository
import com.example.cdplaya.data.ListeningNativeTrackResolver
import com.example.cdplaya.data.LocalReconciliationTarget
import com.example.cdplaya.data.ReconciliationCandidateDisposition
import com.example.cdplaya.data.Song
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.LocalTrackBindingEntity
import com.example.cdplaya.data.membershipKey
import com.example.cdplaya.data.toDomain
import com.example.cdplaya.data.toSongReference
import java.util.Locale
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

enum class ReconciliationReviewTab { SUGGESTED, UNMATCHED, LINKED }

data class LinkedHistoricalReconciliation(
    val source: HistoricalReconciliationSource,
    val target: LocalReconciliationTarget,
    val reconciledAt: Long
)

data class LinkedReconciliationGroup(
    val target: LocalReconciliationTarget,
    val items: List<LinkedHistoricalReconciliation>
) {
    val historicalIdentityCount: Int get() = items.size
    val historicalPlayCount: Long get() = items.sumOf { it.source.metrics.qualifiedPlayCount }
}

data class ReconciliationReviewSnapshot(
    val reviewItems: List<HistoricalReconciliationItem>,
    val linkedItems: List<LinkedHistoricalReconciliation>,
    val localTargets: List<LocalReconciliationTarget>
)

sealed interface ReconciliationConfirmation {
    data class Link(
        val sources: List<HistoricalReconciliationSource>,
        val target: LocalReconciliationTarget,
        val ratings: List<ListeningIdentityReconciliationRatings>
    ) : ReconciliationConfirmation

    data class Unlink(val item: LinkedHistoricalReconciliation) : ReconciliationConfirmation
}

data class ReconciliationSearchState(
    val sourceIds: List<Long>,
    val query: String = "",
    val results: List<LocalReconciliationTarget> = emptyList()
)

data class ReconciliationReviewContent(
    val reviewItems: List<HistoricalReconciliationItem>,
    val linkedItems: List<LinkedHistoricalReconciliation>,
    val activeTab: ReconciliationReviewTab = ReconciliationReviewTab.SUGGESTED,
    val skippedSourceIds: Set<Long> = emptySet(),
    val expandedSourceId: Long? = null,
    val expandedLinkedTargetId: Long? = null,
    val confirmation: ReconciliationConfirmation? = null,
    val search: ReconciliationSearchState? = null,
    val isWorking: Boolean = false,
    val message: String? = null
) {
    val suggestedItems: List<HistoricalReconciliationItem>
        get() = reviewItems.filter {
            it.disposition != ReconciliationCandidateDisposition.NO_CANDIDATE &&
                it.source.identityId !in skippedSourceIds
        }
    val unmatchedItems: List<HistoricalReconciliationItem>
        get() = reviewItems.filter {
            it.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE
        }
    val suggestedCount: Int get() = suggestedItems.size
    val unmatchedCount: Int get() = unmatchedItems.size
    val linkedCount: Int get() = linkedItems.size
    val linkedGroups: List<LinkedReconciliationGroup>
        get() = groupLinkedReconciliations(linkedItems)
}

fun groupLinkedReconciliations(
    items: List<LinkedHistoricalReconciliation>
): List<LinkedReconciliationGroup> = items
    .groupBy { it.target.identityId }
    .map { (_, groupedItems) ->
        LinkedReconciliationGroup(
            target = groupedItems.first().target,
            items = groupedItems.sortedWith(compareBy(
                { it.source.title.lowercase(Locale.ROOT) },
                { it.source.artist.lowercase(Locale.ROOT) },
                { it.source.album.lowercase(Locale.ROOT) },
                { it.source.identityId }
            ))
        )
    }
    .sortedWith(compareBy(
        { it.target.title.lowercase(Locale.ROOT) },
        { it.target.artist.lowercase(Locale.ROOT) },
        { it.target.album.lowercase(Locale.ROOT) },
        { it.target.identityId }
    ))

sealed interface ListeningHistoryReconciliationUiState {
    data object Loading : ListeningHistoryReconciliationUiState
    data class Content(val value: ReconciliationReviewContent) : ListeningHistoryReconciliationUiState
    data class Error(val message: String) : ListeningHistoryReconciliationUiState
}

interface ListeningHistoryReconciliationOperations {
    suspend fun load(): ReconciliationReviewSnapshot
    suspend fun inspectRatings(
        sourceIdentityId: Long,
        target: LocalReconciliationTarget
    ): ListeningIdentityReconciliationRatings
    suspend fun linkMany(
        sourceIdentityIds: List<Long>,
        target: LocalReconciliationTarget
    ): ListeningIdentityReconciliationLinkResult
    suspend fun unlink(sourceIdentityId: Long): Boolean
}

class DefaultListeningHistoryReconciliationOperations(
    private val database: AppDatabase,
    private val currentSongs: () -> List<Song> = { emptyList() },
    private val candidateService: ListeningIdentityReconciliationCandidateService =
        ListeningIdentityReconciliationCandidateService(database),
    private val repository: ListeningIdentityReconciliationRepository =
        ListeningIdentityReconciliationRepository(database),
    private val nativeTrackResolver: ListeningNativeTrackResolver =
        ListeningNativeTrackResolver(database)
) : ListeningHistoryReconciliationOperations {
    private var songsByReferenceKey: Map<String, Song> = emptyMap()

    override suspend fun load(): ReconciliationReviewSnapshot {
        val dao = database.listeningIdentityReconciliationCandidateDao()
        val currentSongList = currentSongs()
            .distinctBy(Song::membershipKey)
        songsByReferenceKey = currentSongList.associateBy(Song::membershipKey)
        val bindingsByReferenceKey = database.localTrackBindingDao().getAllForBackup()
            .associateBy(LocalTrackBindingEntity::referenceKey)
        val targets = currentSongList
            .sortedWith(currentSongComparator)
            .mapIndexed { index, song ->
                song.toReconciliationTarget(
                    binding = bindingsByReferenceKey[song.membershipKey()],
                    transientId = -(index + 1L)
                )
            }
        val discovery = candidateService.discoverCandidates(targets)
        val sources = dao.getAllHistoricalSources().map { it.toDomain() }
            .associateBy(HistoricalReconciliationSource::identityId)
        val targetsById = dao.getAllLocalTargets().map { it.toDomain() }
            .associateBy(LocalReconciliationTarget::identityId)
            .toMutableMap()
            .apply {
                targets.filter { it.identityId > 0L }.forEach { put(it.identityId, it) }
            }
        val linked = repository.listLinks().mapNotNull { link ->
            val source = sources[link.sourceIdentityId] ?: return@mapNotNull null
            val target = targetsById[link.targetIdentityId] ?: return@mapNotNull null
            LinkedHistoricalReconciliation(source, target, link.reconciledAt)
        }
        return ReconciliationReviewSnapshot(discovery.items, linked, targets)
    }

    override suspend fun inspectRatings(
        sourceIdentityId: Long,
        target: LocalReconciliationTarget
    ) = repository.inspectRatings(sourceIdentityId, target.identityId)

    override suspend fun linkMany(
        sourceIdentityIds: List<Long>,
        target: LocalReconciliationTarget
    ): ListeningIdentityReconciliationLinkResult {
        val song = songsByReferenceKey[target.referenceKey]
            ?: return ListeningIdentityReconciliationLinkResult.Rejected(
                ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND
            )
        val resolved = nativeTrackResolver.resolveOrCreate(
            target.referenceKey,
            song.toSongReference(),
            refreshExistingBinding = true
        )
        return repository.linkMany(sourceIdentityIds, resolved.trackIdentityId)
    }

    override suspend fun unlink(sourceIdentityId: Long) = repository.unlink(sourceIdentityId)
}

class ListeningHistoryReconciliationController(
    private val operations: ListeningHistoryReconciliationOperations,
    private val scope: CoroutineScope,
    private val workDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val maxSearchResults: Int = 100
) {
    private val _state = MutableStateFlow<ListeningHistoryReconciliationUiState>(
        ListeningHistoryReconciliationUiState.Loading
    )
    val state: StateFlow<ListeningHistoryReconciliationUiState> = _state.asStateFlow()
    private var localTargets: List<LocalReconciliationTarget> = emptyList()
    private var operationJob: Job? = null

    fun enter() {
        if (operationJob?.isActive == true) return
        val existing = contentOrNull()
        if (existing != null) {
            _state.value = ListeningHistoryReconciliationUiState.Content(
                existing.copy(skippedSourceIds = emptySet(), message = null)
            )
        }
        refresh(showLoading = existing == null)
    }

    fun retry() = refresh(showLoading = true)

    fun selectTab(tab: ReconciliationReviewTab) = updateContent { copy(activeTab = tab, message = null) }

    fun toggleExpanded(sourceId: Long) = updateContent {
        copy(expandedSourceId = if (expandedSourceId == sourceId) null else sourceId)
    }

    fun toggleLinkedGroup(targetIdentityId: Long) = updateContent {
        copy(
            expandedLinkedTargetId = if (expandedLinkedTargetId == targetIdentityId) {
                null
            } else {
                targetIdentityId
            }
        )
    }

    fun skip(sourceId: Long) = updateContent {
        copy(
            skippedSourceIds = skippedSourceIds + sourceId,
            expandedSourceId = if (expandedSourceId == sourceId) null else expandedSourceId,
            message = "Skipped for now. It will return the next time you open this screen."
        )
    }

    fun openSearch(sourceIds: List<Long>) = updateContent {
        val eligible = localTargets.sortedWith(currentTargetComparator).take(maxSearchResults)
        copy(search = ReconciliationSearchState(sourceIds.distinct(), results = eligible), message = null)
    }

    fun updateSearchQuery(query: String) = updateContent {
        val normalized = query.trim().lowercase(Locale.ROOT)
        val matches = (if (normalized.isBlank()) localTargets else localTargets.filter { target ->
            target.title.lowercase(Locale.ROOT).contains(normalized) ||
                target.artist.lowercase(Locale.ROOT).contains(normalized) ||
                target.album.lowercase(Locale.ROOT).contains(normalized)
        }).sortedWith(currentTargetComparator)
        copy(search = search?.copy(query = query, results = matches.take(maxSearchResults)))
    }

    fun closeSearch() = updateContent { copy(search = null) }

    fun chooseTarget(sourceIds: List<Long>, target: LocalReconciliationTarget) {
        val content = contentOrNull() ?: return
        if (content.isWorking) return
        val distinctIds = sourceIds.distinct()
        val sourcesById = content.reviewItems.associate { it.source.identityId to it.source }
        val sources = distinctIds.mapNotNull(sourcesById::get)
        if (sources.size != distinctIds.size || sources.isEmpty()) return
        _state.value = ListeningHistoryReconciliationUiState.Content(
            content.copy(isWorking = true, search = null, message = null)
        )
        operationJob = scope.launch {
            try {
                val ratings = withContext(workDispatcher) {
                    distinctIds.map { operations.inspectRatings(it, target) }
                }
                updateContent {
                    copy(
                        isWorking = false,
                        confirmation = ReconciliationConfirmation.Link(sources, target, ratings)
                    )
                }
            } catch (_: CancellationException) {
                updateContent { copy(isWorking = false) }
            } catch (_: Throwable) {
                updateContent { copy(isWorking = false, message = GENERIC_REFRESH_MESSAGE) }
                refresh(showLoading = false)
            }
        }
    }

    fun requestUnlink(item: LinkedHistoricalReconciliation) = updateContent {
        copy(confirmation = ReconciliationConfirmation.Unlink(item), message = null)
    }

    fun cancelConfirmation() = updateContent { copy(confirmation = null) }

    fun confirm() {
        val content = contentOrNull() ?: return
        if (content.isWorking) return
        when (val confirmation = content.confirmation) {
            is ReconciliationConfirmation.Link -> performLink(content, confirmation)
            is ReconciliationConfirmation.Unlink -> performUnlink(content, confirmation.item)
            null -> Unit
        }
    }

    fun clearMessage() = updateContent { copy(message = null) }

    private fun performLink(content: ReconciliationReviewContent, confirmation: ReconciliationConfirmation.Link) {
        _state.value = ListeningHistoryReconciliationUiState.Content(content.copy(isWorking = true))
        operationJob = scope.launch {
            val result = try {
                withContext(workDispatcher) {
                    operations.linkMany(
                        confirmation.sources.map(HistoricalReconciliationSource::identityId),
                        confirmation.target
                    )
                }
            } catch (_: CancellationException) {
                updateContent { copy(isWorking = false) }
                return@launch
            } catch (_: Throwable) {
                updateContent { copy(isWorking = false, confirmation = null, message = GENERIC_REFRESH_MESSAGE) }
                refresh(showLoading = false)
                return@launch
            }
            when (result) {
                is ListeningIdentityReconciliationLinkResult.Linked -> {
                    reloadAfterMutation(
                        if (confirmation.sources.size == 1) {
                            "History linked. Statistics will now combine it with the local track."
                        } else {
                            "${confirmation.sources.size} histories linked. Statistics will now combine them with the local track."
                        }
                    )
                }
                is ListeningIdentityReconciliationLinkResult.Rejected -> {
                    val message = reconciliationFailureMessage(
                        result.reason,
                        confirmation.sources.size > 1
                    )
                    reloadAfterMutation(message)
                }
            }
        }
    }

    private fun performUnlink(content: ReconciliationReviewContent, item: LinkedHistoricalReconciliation) {
        _state.value = ListeningHistoryReconciliationUiState.Content(content.copy(isWorking = true))
        operationJob = scope.launch {
            val unlinked = try {
                withContext(workDispatcher) { operations.unlink(item.source.identityId) }
            } catch (_: CancellationException) {
                updateContent { copy(isWorking = false) }
                return@launch
            } catch (_: Throwable) {
                false
            }
            reloadAfterMutation(
                if (unlinked) "History unlinked. Statistics now treats it separately."
                else "This imported history was already unlinked. The list has been refreshed."
            )
        }
    }

    private fun refresh(showLoading: Boolean) {
        if (showLoading) _state.value = ListeningHistoryReconciliationUiState.Loading
        operationJob?.cancel()
        operationJob = scope.launch {
            try {
                val snapshot = withContext(workDispatcher) { operations.load() }
                localTargets = snapshot.localTargets
                _state.value = ListeningHistoryReconciliationUiState.Content(
                    ReconciliationReviewContent(snapshot.reviewItems, snapshot.linkedItems)
                )
            } catch (_: CancellationException) {
                Unit
            } catch (_: Throwable) {
                _state.value = ListeningHistoryReconciliationUiState.Error(
                    "Imported tracks couldn't be loaded. Try again."
                )
            }
        }
    }

    private suspend fun reloadAfterMutation(message: String) {
        try {
            val previous = contentOrNull()
            val snapshot = withContext(workDispatcher) { operations.load() }
            localTargets = snapshot.localTargets
            _state.value = ListeningHistoryReconciliationUiState.Content(
                ReconciliationReviewContent(
                    reviewItems = snapshot.reviewItems,
                    linkedItems = snapshot.linkedItems,
                    activeTab = previous?.activeTab ?: ReconciliationReviewTab.SUGGESTED,
                    skippedSourceIds = previous?.skippedSourceIds.orEmpty(),
                    message = message
                )
            )
        } catch (_: Throwable) {
            _state.value = ListeningHistoryReconciliationUiState.Error(
                "The change was saved, but the list couldn't be refreshed. Try again."
            )
        }
    }

    private fun contentOrNull() =
        (_state.value as? ListeningHistoryReconciliationUiState.Content)?.value

    private inline fun updateContent(transform: ReconciliationReviewContent.() -> ReconciliationReviewContent) {
        val current = contentOrNull() ?: return
        _state.value = ListeningHistoryReconciliationUiState.Content(current.transform())
    }

    companion object {
        const val GENERIC_REFRESH_MESSAGE = "The tracks changed before they could be linked. Review the matches again."
    }
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
        fileExtension = displayName.substringAfterLast('.', "")
            .takeIf(String::isNotBlank),
        relativeFolder = relativePath.replace('\\', '/').trim('/').takeIf(String::isNotBlank)
    )
}

private val currentSongComparator = compareBy<Song>(
    { it.title.lowercase(Locale.ROOT) },
    { it.artist.lowercase(Locale.ROOT) },
    { it.album.lowercase(Locale.ROOT) },
    Song::membershipKey
)

private val currentTargetComparator = compareBy<LocalReconciliationTarget>(
    { it.title.lowercase(Locale.ROOT) },
    { it.artist.lowercase(Locale.ROOT) },
    { it.album.lowercase(Locale.ROOT) },
    LocalReconciliationTarget::referenceKey
)

fun reconciliationFailureMessage(
    failure: ListeningIdentityReconciliationFailure,
    isMany: Boolean = false
): String = when (failure) {
    ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED ->
        if (isMany) "Some imported history has already been linked. Review the matches again."
        else "This imported track has already been linked."
    ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING,
    ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND ->
        "That song is no longer available in your library. Choose another track."
    ListeningIdentityReconciliationFailure.SOURCE_NOT_FOUND,
    ListeningIdentityReconciliationFailure.SOURCE_HAS_NO_IMPORTED_HISTORY ->
        if (isMany) "Some imported history changed before it could be linked. Review the matches again."
        else "This imported history is no longer available."
    else -> ListeningHistoryReconciliationController.GENERIC_REFRESH_MESSAGE
}

fun ratingWarning(ratings: List<ListeningIdentityReconciliationRatings>): String? {
    val conflict = ratings.firstOrNull {
        it.state == ListeningIdentityReconciliationRatingState.CONFLICTING_RATINGS
    }
    if (conflict != null) {
        return "These tracks have different ratings. After linking, the local song's ${conflict.targetRating}-star rating will be used. The imported rating will remain saved."
    }
    if (ratings.any { it.state == ListeningIdentityReconciliationRatingState.SOURCE_ONLY }) {
        return "This imported track has a rating, but the local song is unrated. After linking, the local song's rating will be used in Statistics. The historical rating remains saved if you unlink later."
    }
    return null
}
