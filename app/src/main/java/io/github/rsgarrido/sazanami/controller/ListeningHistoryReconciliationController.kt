package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidateService
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationFailure
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationLinkResult
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatingState
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatings
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationBindingService
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRepository
import io.github.rsgarrido.sazanami.data.LocalReconciliationBatchResult
import io.github.rsgarrido.sazanami.data.LocalReconciliationBindingRequest
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.membershipKey
import io.github.rsgarrido.sazanami.data.toDomain
import io.github.rsgarrido.sazanami.data.toReconciliationTarget
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

enum class ReconciliationReviewTab { REVIEW, UNMATCHED, LINKED }

data class LinkedHistoricalReconciliation(
    val source: HistoricalReconciliationSource,
    val target: LocalReconciliationTarget,
    val reconciledAt: Long
)

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

    data class Batch(val selections: List<ReconciliationBatchSelection>) : ReconciliationConfirmation
}

data class ReconciliationBatchSelection(
    val source: HistoricalReconciliationSource,
    val target: LocalReconciliationTarget
)

data class ReconciliationSearchState(
    val sourceIds: List<Long>,
    val query: String = "",
    val results: List<LocalReconciliationTarget> = emptyList()
)

data class ReconciliationReviewContent(
    val reviewItems: List<HistoricalReconciliationItem>,
    val linkedItems: List<LinkedHistoricalReconciliation>,
    val activeTab: ReconciliationReviewTab = ReconciliationReviewTab.REVIEW,
    val browseMode: ReconciliationBrowseMode = ReconciliationBrowseMode.TRACKS,
    val browseQuery: String = "",
    val sortOption: ReconciliationSortOption = ReconciliationSortOption.HISTORICAL_PLAYS,
    val reviewFilter: ReconciliationReviewFilter = ReconciliationReviewFilter.ALL,
    val skippedSourceIds: Set<Long> = emptySet(),
    val selectedSourceIds: Set<Long> = emptySet(),
    val expandedSourceId: Long? = null,
    val expandedAlbumKey: ReconciliationAlbumKey? = null,
    val expandedArtistKey: String? = null,
    val confirmation: ReconciliationConfirmation? = null,
    val search: ReconciliationSearchState? = null,
    val isWorking: Boolean = false,
    val message: String? = null,
    val preparedDataset: ReconciliationPreparedDataset = prepareReconciliationDataset(
        reviewItems,
        linkedItems
    )
) {
    val visibleReviewItems: List<HistoricalReconciliationItem>
        get() = reviewItems.filter {
            it.disposition != ReconciliationCandidateDisposition.NO_CANDIDATE &&
                it.source.identityId !in skippedSourceIds
        }
    val unmatchedItems: List<HistoricalReconciliationItem>
        get() = reviewItems.filter {
            it.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE
        }
    val reviewCount: Int get() = visibleReviewItems.size
    val unmatchedCount: Int get() = unmatchedItems.size
    val linkedCount: Int get() = linkedItems.size
    val visibleTracks: List<ReconciliationTrackPresentation> by lazy(LazyThreadSafetyMode.NONE) {
        filterAndSortReconciliationTracks(
            dataset = preparedDataset,
            status = when (activeTab) {
                ReconciliationReviewTab.REVIEW -> ReconciliationTrackStatus.REVIEW
                ReconciliationReviewTab.UNMATCHED -> ReconciliationTrackStatus.UNMATCHED
                ReconciliationReviewTab.LINKED -> ReconciliationTrackStatus.LINKED
            },
            query = browseQuery,
            sort = sortOption,
            reviewFilter = reviewFilter,
            skippedSourceIds = skippedSourceIds
        )
    }

    val visibleAlbums: List<ReconciliationAlbumPresentation> by lazy(LazyThreadSafetyMode.NONE) {
        groupReconciliationAlbums(preparedDataset.tracks, visibleTracks, sortOption)
    }

    val visibleArtists: List<ReconciliationArtistPresentation> by lazy(LazyThreadSafetyMode.NONE) {
        groupReconciliationArtists(preparedDataset.tracks, visibleTracks, sortOption)
    }

    val selectableVisibleSourceIds: Set<Long> by lazy(LazyThreadSafetyMode.NONE) {
        visibleTracks.filter(ReconciliationTrackPresentation::isSelectable)
            .mapTo(linkedSetOf(), ReconciliationTrackPresentation::sourceId)
    }
}

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
    suspend fun linkBatch(
        requests: List<LocalReconciliationBindingRequest>
    ): LocalReconciliationBatchResult
    suspend fun unlink(sourceIdentityId: Long): Boolean
}

class DefaultListeningHistoryReconciliationOperations(
    private val database: AppDatabase,
    private val currentSongs: () -> List<Song> = { emptyList() },
    private val candidateService: ListeningIdentityReconciliationCandidateService =
        ListeningIdentityReconciliationCandidateService(database),
    private val repository: ListeningIdentityReconciliationRepository =
        ListeningIdentityReconciliationRepository(database),
    private val bindingService: ListeningIdentityReconciliationBindingService =
        ListeningIdentityReconciliationBindingService(database, repository)
) : ListeningHistoryReconciliationOperations {
    override suspend fun load(): ReconciliationReviewSnapshot {
        val dao = database.listeningIdentityReconciliationCandidateDao()
        val currentSongList = currentSongs()
            .distinctBy(Song::membershipKey)
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
    ): ListeningIdentityReconciliationLinkResult = bindingService.linkManyAtomically(
        sourceIdentityIds,
        target,
        currentSongs()
    )

    override suspend fun linkBatch(
        requests: List<LocalReconciliationBindingRequest>
    ): LocalReconciliationBatchResult = bindingService.linkBatch(requests, currentSongs())

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

    /** Refreshes an already-open review once after an external automatic batch commit. */
    fun onExternalReconciliationMutation() {
        if (contentOrNull() == null || operationJob?.isActive == true) return
        refresh(showLoading = false)
    }

    fun selectTab(tab: ReconciliationReviewTab) = updateContent {
        copy(
            activeTab = tab,
            selectedSourceIds = emptySet(),
            expandedSourceId = null,
            expandedAlbumKey = null,
            expandedArtistKey = null,
            message = null
        )
    }

    fun selectBrowseMode(mode: ReconciliationBrowseMode) = updateContent {
        copy(
            browseMode = mode,
            expandedSourceId = null,
            expandedAlbumKey = null,
            expandedArtistKey = null
        )
    }

    fun updateBrowseQuery(query: String) = updateContent { copy(browseQuery = query) }

    fun selectSort(option: ReconciliationSortOption) = updateContent { copy(sortOption = option) }

    fun selectReviewFilter(filter: ReconciliationReviewFilter) = updateContent {
        copy(reviewFilter = filter)
    }

    fun toggleExpanded(sourceId: Long) = updateContent {
        copy(expandedSourceId = if (expandedSourceId == sourceId) null else sourceId)
    }

    fun skip(sourceId: Long) = updateContent {
        copy(
            skippedSourceIds = skippedSourceIds + sourceId,
            selectedSourceIds = selectedSourceIds - sourceId,
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
            is ReconciliationConfirmation.Batch -> performBatchLink(content, confirmation)
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
                    val message = if (confirmation.sources.size == 1) {
                        "History linked. Statistics will now combine it with the local track."
                    } else {
                        "${confirmation.sources.size} histories linked. Statistics will now combine them with the local track."
                    }
                    if (!applySuccessfulLink(content, confirmation, result, message)) {
                        reloadAfterMutation(message)
                    }
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

    private fun performBatchLink(
        content: ReconciliationReviewContent,
        confirmation: ReconciliationConfirmation.Batch
    ) {
        _state.value = ListeningHistoryReconciliationUiState.Content(content.copy(isWorking = true))
        operationJob = scope.launch {
            val result = try {
                withContext(workDispatcher) {
                    operations.linkBatch(confirmation.selections.map { selection ->
                        LocalReconciliationBindingRequest(
                            selection.source.identityId,
                            selection.target
                        )
                    })
                }
            } catch (_: CancellationException) {
                updateContent { copy(isWorking = false) }
                return@launch
            } catch (_: Throwable) {
                updateContent {
                    copy(
                        isWorking = false,
                        confirmation = null,
                        message = GENERIC_REFRESH_MESSAGE
                    )
                }
                return@launch
            }

            val message = batchResultMessage(result)
            if (result.conflicts.isNotEmpty() || result.failures.isNotEmpty() ||
                result.alreadyLinked > 0 ||
                !applySuccessfulBatch(content, confirmation, result, message)
            ) {
                reloadAfterMutation(message)
            }
        }
    }

    fun toggleAlbum(key: ReconciliationAlbumKey) = updateContent {
        copy(expandedAlbumKey = if (expandedAlbumKey == key) null else key)
    }

    fun toggleArtist(key: String) = updateContent {
        copy(expandedArtistKey = if (expandedArtistKey == key) null else key)
    }

    fun toggleSelected(sourceId: Long) = updateContent {
        if (sourceId !in selectableVisibleSourceIds && sourceId !in selectedSourceIds) {
            return@updateContent this
        }
        copy(
            selectedSourceIds = if (sourceId in selectedSourceIds) {
                selectedSourceIds - sourceId
            } else {
                selectedSourceIds + sourceId
            },
            message = null
        )
    }

    fun selectReviewItems(sourceIds: List<Long>) = updateContent {
        val requested = sourceIds.toSet()
        val eligible = preparedDataset.tracks.asSequence()
            .filter(ReconciliationTrackPresentation::isSelectable)
            .filter { it.sourceId !in skippedSourceIds }
            .map(ReconciliationTrackPresentation::sourceId)
            .filter(requested::contains)
            .toSet()
        copy(selectedSourceIds = selectedSourceIds + eligible, message = null)
    }

    fun clearSelection() = updateContent { copy(selectedSourceIds = emptySet()) }

    fun requestLinkSelected() {
        val content = contentOrNull() ?: return
        if (content.isWorking) return
        val selected = content.preparedDataset.tracks.asSequence()
            .filter {
                it.sourceId in content.selectedSourceIds &&
                    it.sourceId !in content.skippedSourceIds &&
                    it.isSelectable
            }
            .map { track ->
                ReconciliationBatchSelection(
                    track.source,
                    requireNotNull(track.proposedCandidate).target
                )
            }
            .toList()
        if (selected.isEmpty()) return
        _state.value = ListeningHistoryReconciliationUiState.Content(
            content.copy(confirmation = ReconciliationConfirmation.Batch(selected), message = null)
        )
    }

    /**
     * A successful link already contains everything needed to update the review. Avoid rebuilding
     * aggregates and every candidate from all historical events after each manual confirmation.
     */
    private fun applySuccessfulLink(
        content: ReconciliationReviewContent,
        confirmation: ReconciliationConfirmation.Link,
        result: ListeningIdentityReconciliationLinkResult.Linked,
        message: String
    ): Boolean {
        val linksBySource = result.links.associateBy { it.sourceIdentityId }
        if (linksBySource.keys != confirmation.sources.mapTo(mutableSetOf()) { it.identityId }) {
            return false
        }
        val targetIdentityIds = result.links.mapTo(mutableSetOf()) { it.targetIdentityId }
        if (targetIdentityIds.size != 1) return false
        val resolvedTarget = confirmation.target.copy(identityId = targetIdentityIds.single())
        val sourceIds = linksBySource.keys
        localTargets = localTargets.map { target ->
            if (target.referenceKey == resolvedTarget.referenceKey) resolvedTarget else target
        }
        val newlyLinked = confirmation.sources.map { source ->
            LinkedHistoricalReconciliation(
                source = source,
                target = resolvedTarget,
                reconciledAt = requireNotNull(linksBySource[source.identityId]).reconciledAt
            )
        }
        val reviewItems = content.reviewItems.filterNot { it.source.identityId in sourceIds }
        val linkedItems = (content.linkedItems + newlyLinked)
            .distinctBy { it.source.identityId }
        _state.value = ListeningHistoryReconciliationUiState.Content(
            content.copy(
                reviewItems = reviewItems,
                linkedItems = linkedItems,
                selectedSourceIds = content.selectedSourceIds - sourceIds,
                expandedSourceId = null,
                confirmation = null,
                search = null,
                isWorking = false,
                message = message,
                preparedDataset = prepareReconciliationDataset(reviewItems, linkedItems)
            )
        )
        return true
    }

    private fun applySuccessfulBatch(
        content: ReconciliationReviewContent,
        confirmation: ReconciliationConfirmation.Batch,
        result: LocalReconciliationBatchResult,
        message: String
    ): Boolean {
        if (result.requested != confirmation.selections.size ||
            result.newlyLinked != confirmation.selections.size ||
            result.links.size != confirmation.selections.size
        ) return false
        val selectionsBySource = confirmation.selections.associateBy { it.source.identityId }
        val linksBySource = result.links.associateBy { it.sourceIdentityId }
        if (linksBySource.keys != selectionsBySource.keys) return false

        val resolvedTargetsByReference = mutableMapOf<String, LocalReconciliationTarget>()
        val newlyLinked = linksBySource.map { (sourceId, link) ->
            val selection = requireNotNull(selectionsBySource[sourceId])
            val resolvedTarget = selection.target.copy(identityId = link.targetIdentityId)
            resolvedTargetsByReference[resolvedTarget.referenceKey] = resolvedTarget
            LinkedHistoricalReconciliation(selection.source, resolvedTarget, link.reconciledAt)
        }
        localTargets = localTargets.map { target ->
            resolvedTargetsByReference[target.referenceKey] ?: target
        }
        val sourceIds = linksBySource.keys
        val reviewItems = content.reviewItems.filterNot { it.source.identityId in sourceIds }
        val linkedItems = (content.linkedItems + newlyLinked).distinctBy { it.source.identityId }
        _state.value = ListeningHistoryReconciliationUiState.Content(
            content.copy(
                reviewItems = reviewItems,
                linkedItems = linkedItems,
                selectedSourceIds = emptySet(),
                expandedSourceId = null,
                confirmation = null,
                isWorking = false,
                message = message,
                preparedDataset = prepareReconciliationDataset(reviewItems, linkedItems)
            )
        )
        return true
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
                    activeTab = previous?.activeTab ?: ReconciliationReviewTab.REVIEW,
                    browseMode = previous?.browseMode ?: ReconciliationBrowseMode.TRACKS,
                    browseQuery = previous?.browseQuery.orEmpty(),
                    sortOption = previous?.sortOption
                        ?: ReconciliationSortOption.HISTORICAL_PLAYS,
                    reviewFilter = previous?.reviewFilter ?: ReconciliationReviewFilter.ALL,
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

fun batchResultMessage(result: LocalReconciliationBatchResult): String {
    val parts = buildList {
        if (result.newlyLinked > 0) add("${result.newlyLinked} linked")
        if (result.alreadyLinked > 0) add("${result.alreadyLinked} already linked")
        if (result.conflicts.isNotEmpty()) {
            val count = result.conflicts.size
            add("$count ${if (count == 1) "conflict" else "conflicts"}")
        }
        if (result.failures.isNotEmpty()) add("${result.failures.size} failed")
    }
    return if (parts.isEmpty()) "No selected histories were changed."
    else parts.joinToString(separator = " · ", postfix = ".")
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
