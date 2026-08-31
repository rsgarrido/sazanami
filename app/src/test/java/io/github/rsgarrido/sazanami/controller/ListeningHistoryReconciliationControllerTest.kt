package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationMetrics
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidate
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationBatchConflict
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationBindingRequest
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationFailure
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationLinkResult
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatingState
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationRatings
import io.github.rsgarrido.sazanami.data.LocalReconciliationTarget
import io.github.rsgarrido.sazanami.data.LocalReconciliationBatchFailure
import io.github.rsgarrido.sazanami.data.LocalReconciliationBatchResult
import io.github.rsgarrido.sazanami.data.LocalReconciliationBindingRequest
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateEvidence
import io.github.rsgarrido.sazanami.data.ReconciliationMetadataRelation
import io.github.rsgarrido.sazanami.data.ReconciliationVersionRelation
import io.github.rsgarrido.sazanami.data.local.ListeningIdentityReconciliationEntity
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancel
import kotlin.system.measureTimeMillis
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryReconciliationControllerTest {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)
    private val operations = FakeOperations()
    private val controller = ListeningHistoryReconciliationController(
        operations,
        scope,
        Dispatchers.Unconfined,
        maxSearchResults = 2
    )

    @After fun tearDown() = scope.cancel()

    @Test fun initialLoadingAndSuccessfulLoadPopulateAllCounts() {
        operations.snapshot = snapshot()
        assertTrue(controller.state.value is ListeningHistoryReconciliationUiState.Loading)
        controller.enter()
        val content = content()
        assertEquals(1, content.reviewCount)
        assertEquals(1, content.unmatchedCount)
        assertEquals(1, content.linkedCount)
    }

    @Test fun loadingRemainsVisibleWhileDiscoveryIsRunning() {
        operations.suspendLoad = true
        controller.enter()
        assertTrue(controller.state.value is ListeningHistoryReconciliationUiState.Loading)
    }

    @Test fun discoveryFailureUsesSafeRetryableError() {
        operations.loadFailure = IllegalStateException("private sql")
        controller.enter()
        val state = controller.state.value as ListeningHistoryReconciliationUiState.Error
        assertEquals("Imported tracks couldn't be loaded. Try again.", state.message)
    }

    @Test fun linkedSourceAppearsOnlyInLinked() {
        operations.snapshot = snapshot()
        controller.enter()
        val linkedId = content().linkedItems.single().source.identityId
        assertFalse(content().reviewItems.any { it.source.identityId == linkedId })
    }

    @Test fun skipIsSessionOnlyAndRecreationRestoresPersistedLinks() {
        operations.snapshot = snapshot()
        controller.enter()
        controller.toggleSelected(1)
        controller.skip(1)
        assertTrue(content().visibleReviewItems.isEmpty())
        assertTrue(content().selectedSourceIds.isEmpty())

        val recreated = ListeningHistoryReconciliationController(
            operations,
            scope,
            Dispatchers.Unconfined
        )
        recreated.enter()
        val recreatedContent =
            (recreated.state.value as ListeningHistoryReconciliationUiState.Content).value
        assertEquals(1, recreatedContent.reviewCount)
        assertEquals(1, recreatedContent.linkedCount)
        assertTrue(recreatedContent.skippedSourceIds.isEmpty())
    }

    @Test fun reopeningSameControllerStartsANewSkipSession() {
        operations.snapshot = snapshot()
        controller.enter()
        controller.skip(1)
        assertTrue(content().visibleReviewItems.isEmpty())

        controller.enter()

        assertEquals(1, content().reviewCount)
        assertTrue(content().skippedSourceIds.isEmpty())
    }

    @Test fun manualSearchMatchesTitleArtistAndAlbumAndIsBounded() {
        operations.snapshot = snapshot().copy(localTargets = listOf(
            target(10, "Needle", "One", "Red"),
            target(11, "Other", "Needle Artist", "Blue"),
            target(12, "Third", "Two", "Needle Album")
        ))
        controller.enter()
        controller.openSearch(listOf(1))
        controller.updateSearchQuery("needle")
        val search = content().search!!
        assertEquals(2, search.results.size)
        assertTrue(search.results.all { it.identityId != 99L })
    }

    @Test fun manualSearchFiltersFullLibraryBeforeApplyingResultCap() {
        operations.snapshot = snapshot().copy(localTargets = listOf(
            target(10, "Alpha", "Artist", "Album"),
            target(11, "Beta", "Artist", "Album"),
            target(12, "Gamma", "Artist", "Album"),
            target(13, "Needle After Browse Cap", "Artist", "Album")
        ))
        controller.enter()
        controller.openSearch(listOf(1))
        assertEquals(2, content().search!!.results.size)

        controller.updateSearchQuery("needle")

        assertEquals(listOf(13L), content().search!!.results.map { it.identityId })
    }

    @Test fun tenThousandTargetSearchFiltersBeforeTheHundredResultCapDeterministically() {
        val largeOperations = FakeOperations()
        largeOperations.snapshot = snapshot().copy(
            localTargets = (0 until 10_000).map { index ->
                target(
                    id = 10_000L + index,
                    title = if (index >= 9_850) {
                        "Needle ${index.toString().padStart(5, '0')}"
                    } else {
                        "Library ${index.toString().padStart(5, '0')}"
                    }
                )
            }
        )
        val largeController = ListeningHistoryReconciliationController(
            largeOperations,
            scope,
            Dispatchers.Unconfined,
            maxSearchResults = 100
        )

        val elapsed = measureTimeMillis {
            largeController.enter()
            largeController.openSearch(listOf(1))
            largeController.updateSearchQuery("needle")
        }
        val results = (largeController.state.value as ListeningHistoryReconciliationUiState.Content)
            .value.search!!.results

        assertEquals(100, results.size)
        assertEquals(100, results.map { it.identityId }.distinct().size)
        assertEquals("Needle 09850", results.first().title)
        assertEquals("Needle 09949", results.last().title)
        println("reconciliation current-library search localTargets=10000 matches=150 cap=100 ms=$elapsed")
    }

    @Test fun fifteenHundredLinkedIdentitiesUsePreparedTrackAlbumAndArtistViews() {
        val linked = (0 until 500).flatMap { targetIndex ->
            val canonical = target(
                id = 10_000L + targetIndex,
                title = "Target ${targetIndex.toString().padStart(4, '0')}"
            )
            (0 until 3).map { aliasIndex ->
                LinkedHistoricalReconciliation(
                    source(
                        id = 100_000L + targetIndex * 3L + aliasIndex,
                        title = "Alias $targetIndex-$aliasIndex"
                    ),
                    canonical,
                    targetIndex.toLong()
                )
            }
        }
        val content = ReconciliationReviewContent(
            reviewItems = emptyList(),
            linkedItems = linked,
            activeTab = ReconciliationReviewTab.LINKED
        )
        val elapsed = measureTimeMillis {
            assertEquals(1_500, content.visibleTracks.size)
            assertEquals(1, content.visibleAlbums.size)
            assertEquals(1, content.visibleArtists.size)
        }

        assertEquals(1_500, content.linkedCount)
        assertEquals(1_500, content.visibleTracks.map { it.sourceId }.distinct().size)
        println("reconciliation prepared linked identities=1500 ms=$elapsed")
    }

    @Test fun selectingTargetRequiresConfirmationAndCancelDoesNotLink() {
        operations.snapshot = snapshot()
        controller.enter()
        controller.chooseTarget(listOf(1), target(10))
        assertTrue(content().confirmation is ReconciliationConfirmation.Link)
        assertEquals(0, operations.linkCalls)
        controller.cancelConfirmation()
        assertNull(content().confirmation)
        assertEquals(0, operations.linkCalls)
    }

    @Test fun confirmedLinkRefreshesListsAndUsesExactlyOneExplicitWrite() {
        val before = snapshot()
        val source = before.reviewItems.first().source
        val chosen = before.localTargets.first()
        operations.snapshot = before
        controller.enter()
        controller.chooseTarget(listOf(source.identityId), chosen)
        operations.snapshot = before.copy(
            reviewItems = before.reviewItems.drop(1),
            linkedItems = before.linkedItems + LinkedHistoricalReconciliation(source, chosen, 9)
        )
        controller.confirm()
        assertEquals(1, operations.linkCalls)
        assertEquals(1, operations.loadCalls)
        assertEquals(listOf(source.identityId), operations.lastLinkedSources)
        assertEquals(2, content().linkedCount)
        assertTrue(content().message!!.startsWith("History linked"))
    }

    @Test fun oneExternalAutomaticBatchCommitTriggersOneOpenScreenRefresh() {
        operations.snapshot = snapshot()
        controller.enter()
        assertEquals(1, operations.loadCalls)

        controller.onExternalReconciliationMutation()

        assertEquals(2, operations.loadCalls)
    }

    @Test fun linkManyIsOneAtomicOperationAndFailureRefreshesWithoutPartialSuccess() {
        val first = source(1, "Fragment")
        val second = source(2, "Fragment")
        val candidate = target(10)
        operations.snapshot = ReconciliationReviewSnapshot(
            listOf(item(first, candidate), item(second, candidate)), emptyList(), listOf(candidate)
        )
        controller.enter()
        controller.chooseTarget(listOf(1, 2), candidate)
        operations.linkResult = ListeningIdentityReconciliationLinkResult.Rejected(
            ListeningIdentityReconciliationFailure.SOURCE_ALREADY_RECONCILED,
            2
        )
        controller.confirm()
        assertEquals(1, operations.linkCalls)
        assertEquals(listOf(1L, 2L), operations.lastLinkedSources)
        assertEquals(0, content().linkedCount)
        assertTrue(content().message!!.contains("Some imported history"))
    }

    @Test fun staleUnavailableTargetMapsToHumanReadableMessageAndRefreshes() {
        operations.snapshot = snapshot()
        controller.enter()
        controller.chooseTarget(listOf(1), target(10))
        operations.linkResult = ListeningIdentityReconciliationLinkResult.Rejected(
            ListeningIdentityReconciliationFailure.TARGET_HAS_NO_LOCAL_BINDING
        )
        controller.confirm()
        assertEquals(
            "That song is no longer available in your library. Choose another track.",
            content().message
        )
    }

    @Test fun unlinkRequiresConfirmationCancelKeepsLinkAndConfirmRefreshes() {
        val before = snapshot()
        operations.snapshot = before
        controller.enter()
        val linked = content().linkedItems.single()
        controller.requestUnlink(linked)
        assertEquals(0, operations.unlinkCalls)
        controller.cancelConfirmation()
        assertEquals(0, operations.unlinkCalls)
        controller.requestUnlink(linked)
        operations.snapshot = before.copy(linkedItems = emptyList())
        controller.confirm()
        assertEquals(1, operations.unlinkCalls)
        assertEquals(0, content().linkedCount)
    }

    @Test fun selectedEligibleReviewItemsUseOneBatchAndUpdatePreparedState() {
        val first = source(1, "First")
        val second = source(2, "Second")
        val firstTarget = target(10, "First local")
        val secondTarget = target(11, "Second local")
        operations.snapshot = ReconciliationReviewSnapshot(
            listOf(item(first, firstTarget), item(second, secondTarget)),
            emptyList(),
            listOf(firstTarget, secondTarget)
        )
        controller.enter()

        controller.toggleSelected(first.identityId)
        controller.toggleSelected(second.identityId)
        controller.requestLinkSelected()

        assertTrue(content().confirmation is ReconciliationConfirmation.Batch)
        controller.confirm()

        assertEquals(1, operations.batchCalls)
        assertEquals(0, operations.linkCalls)
        assertEquals(setOf(1L, 2L), operations.lastBatchRequests
            .map { it.sourceIdentityId }.toSet())
        assertEquals(2, content().linkedCount)
        assertTrue(content().reviewItems.isEmpty())
        assertTrue(content().selectedSourceIds.isEmpty())
        assertEquals(1, operations.loadCalls)
    }

    @Test fun ambiguousRowsCannotBeSelectedAndAlreadyLinkedBatchRefreshesOnce() {
        val eligible = source(1, "Eligible")
        val eligibleTarget = target(10)
        val ambiguousSource = source(2, "Ambiguous")
        val ambiguous = HistoricalReconciliationItem(
            ambiguousSource,
            listOf(
                ListeningIdentityReconciliationCandidate(eligibleTarget,
                    candidateEvidence(ReconciliationCandidateCategory.AMBIGUOUS)),
                ListeningIdentityReconciliationCandidate(target(11),
                    candidateEvidence(ReconciliationCandidateCategory.AMBIGUOUS))
            ),
            ReconciliationCandidateDisposition.AMBIGUOUS,
            false
        )
        operations.snapshot = ReconciliationReviewSnapshot(
            listOf(item(eligible, eligibleTarget), ambiguous),
            emptyList(),
            listOf(eligibleTarget)
        )
        controller.enter()
        controller.toggleSelected(ambiguousSource.identityId)
        assertTrue(content().selectedSourceIds.isEmpty())

        controller.toggleSelected(eligible.identityId)
        controller.requestLinkSelected()
        operations.batchResult = LocalReconciliationBatchResult(
            requested = 1,
            newlyLinked = 0,
            alreadyLinked = 1,
            conflicts = emptyList(),
            failures = emptyList(),
            links = emptyList()
        )
        controller.confirm()

        assertEquals(1, operations.batchCalls)
        assertEquals(2, operations.loadCalls)
        assertTrue(content().selectedSourceIds.isEmpty())
        assertTrue(content().message!!.contains("already linked"))
    }

    @Test fun batchConflictDoesNotMoveOrDropTheReviewIdentity() {
        val source = source(1, "Conflict")
        val proposed = target(10)
        operations.snapshot = ReconciliationReviewSnapshot(
            listOf(item(source, proposed)),
            emptyList(),
            listOf(proposed)
        )
        controller.enter()
        controller.toggleSelected(source.identityId)
        controller.requestLinkSelected()
        operations.batchResult = LocalReconciliationBatchResult(
            requested = 1,
            newlyLinked = 0,
            alreadyLinked = 0,
            conflicts = listOf(ListeningIdentityReconciliationBatchConflict(
                ListeningIdentityReconciliationBindingRequest(source.identityId, proposed.identityId),
                existingTargetIdentityId = 99
            )),
            failures = emptyList(),
            links = emptyList()
        )

        controller.confirm()

        assertEquals(1, operations.batchCalls)
        assertEquals(listOf(source.identityId), content().reviewItems.map { it.source.identityId })
        assertEquals(0, content().linkedCount)
        assertTrue(content().selectedSourceIds.isEmpty())
        assertTrue(content().message!!.contains("conflict"))
    }

    @Test fun mixedBatchResultReloadsAuthoritativeCountsAndClearsSelectionOnce() {
        val sources = (1L..4L).map { source(it, "Source $it") }
        val targets = (1L..4L).map { target(it + 10, "Target $it") }
        operations.snapshot = ReconciliationReviewSnapshot(
            sources.mapIndexed { index, source -> item(source, targets[index]) },
            emptyList(),
            targets
        )
        controller.enter()
        sources.forEach { controller.toggleSelected(it.identityId) }
        controller.requestLinkSelected()

        operations.batchResult = LocalReconciliationBatchResult(
            requested = 4,
            newlyLinked = 1,
            alreadyLinked = 1,
            conflicts = listOf(ListeningIdentityReconciliationBatchConflict(
                ListeningIdentityReconciliationBindingRequest(
                    sources[2].identityId,
                    targets[2].identityId
                ),
                existingTargetIdentityId = 99
            )),
            failures = listOf(LocalReconciliationBatchFailure(
                sourceIdentityId = sources[3].identityId,
                targetReferenceKey = targets[3].referenceKey,
                reason = ListeningIdentityReconciliationFailure.TARGET_NOT_FOUND
            )),
            links = listOf(ListeningIdentityReconciliationEntity(
                sources[0].identityId,
                targets[0].identityId,
                9
            ))
        )
        operations.snapshot = ReconciliationReviewSnapshot(
            reviewItems = listOf(item(sources[2], targets[2]), item(sources[3], targets[3])),
            linkedItems = listOf(
                LinkedHistoricalReconciliation(sources[0], targets[0], 9),
                LinkedHistoricalReconciliation(sources[1], targets[1], 8)
            ),
            localTargets = targets
        )

        controller.confirm()

        assertEquals(1, operations.batchCalls)
        assertEquals(2, operations.loadCalls)
        assertEquals(2, content().reviewCount)
        assertEquals(0, content().unmatchedCount)
        assertEquals(2, content().linkedCount)
        assertTrue(content().selectedSourceIds.isEmpty())
        assertTrue(content().message!!.contains("1 linked"))
        assertTrue(content().message!!.contains("already linked"))
        assertTrue(content().message!!.contains("conflict"))
        assertTrue(content().message!!.contains("failed"))
    }

    @Test fun ratingWarningsCoverNoRatingTargetOnlySourceOnlyAndConflict() {
        assertNull(ratingWarning(listOf(ratings(null, null))))
        assertNull(ratingWarning(listOf(ratings(null, 5))))
        assertNull(ratingWarning(listOf(ratings(4, 4))))
        assertTrue(ratingWarning(listOf(ratings(3, null)))!!.contains("local song is unrated"))
        assertTrue(ratingWarning(listOf(ratings(2, 5)))!!.contains("5-star rating"))
    }

    private fun content() =
        (controller.state.value as ListeningHistoryReconciliationUiState.Content).value

    private class FakeOperations : ListeningHistoryReconciliationOperations {
        var snapshot = ReconciliationReviewSnapshot(emptyList(), emptyList(), emptyList())
        var suspendLoad = false
        var loadFailure: Throwable? = null
        var loadCalls = 0
        var linkCalls = 0
        var batchCalls = 0
        var unlinkCalls = 0
        var lastLinkedSources = emptyList<Long>()
        var lastBatchRequests = emptyList<LocalReconciliationBindingRequest>()
        var linkResult: ListeningIdentityReconciliationLinkResult? = null
        var batchResult: LocalReconciliationBatchResult? = null

        override suspend fun load(): ReconciliationReviewSnapshot {
            loadCalls++
            loadFailure?.let { throw it }
            if (suspendLoad) awaitCancellation()
            return snapshot
        }

        override suspend fun inspectRatings(
            sourceIdentityId: Long,
            target: LocalReconciliationTarget
        ) =
            ratings(null, null)

        override suspend fun linkMany(
            sourceIdentityIds: List<Long>,
            target: LocalReconciliationTarget
        ): ListeningIdentityReconciliationLinkResult {
            linkCalls++
            lastLinkedSources = sourceIdentityIds
            return linkResult ?: ListeningIdentityReconciliationLinkResult.Linked(
                sourceIdentityIds.map { sourceIdentityId ->
                    ListeningIdentityReconciliationEntity(sourceIdentityId, target.identityId, 9)
                }
            )
        }

        override suspend fun linkBatch(
            requests: List<LocalReconciliationBindingRequest>
        ): LocalReconciliationBatchResult {
            batchCalls++
            lastBatchRequests = requests
            return batchResult ?: LocalReconciliationBatchResult(
                requested = requests.size,
                newlyLinked = requests.size,
                alreadyLinked = 0,
                conflicts = emptyList(),
                failures = emptyList(),
                links = requests.map { request ->
                    ListeningIdentityReconciliationEntity(
                        request.sourceIdentityId,
                        request.target.identityId,
                        9
                    )
                }
            )
        }

        override suspend fun unlink(sourceIdentityId: Long): Boolean {
            unlinkCalls++
            return true
        }
    }

    companion object {
        private fun snapshot(): ReconciliationReviewSnapshot {
            val candidate = target(10)
            val linkedSource = source(3, "Already linked")
            return ReconciliationReviewSnapshot(
                reviewItems = listOf(
                    item(source(1, "Suggested"), candidate),
                    HistoricalReconciliationItem(
                        source(2, "Unmatched"), emptyList(),
                        ReconciliationCandidateDisposition.NO_CANDIDATE, false
                    )
                ),
                linkedItems = listOf(LinkedHistoricalReconciliation(linkedSource, candidate, 8)),
                localTargets = listOf(candidate)
            )
        }

        private fun item(source: HistoricalReconciliationSource, target: LocalReconciliationTarget) =
            HistoricalReconciliationItem(
                source,
                listOf(ListeningIdentityReconciliationCandidate(
                    target,
                    ReconciliationCandidateEvidence(
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationVersionRelation.NONE,
                        emptySet(),
                        ReconciliationCandidateCategory.STRONG_METADATA
                    )
                )),
                ReconciliationCandidateDisposition.SUGGESTED,
                false
            )

        private fun candidateEvidence(category: ReconciliationCandidateCategory) =
            ReconciliationCandidateEvidence(
                ReconciliationMetadataRelation.FUZZY,
                ReconciliationMetadataRelation.EXACT,
                ReconciliationMetadataRelation.EXACT,
                ReconciliationVersionRelation.NONE,
                emptySet(),
                category
            )

        private fun source(id: Long, title: String) = HistoricalReconciliationSource(
            id, title, "Fictional Artist", "Fictional Album", null,
            setOf(ListeningSource.SPOTIFY_IMPORT), true,
            HistoricalReconciliationMetrics(3, 2, 180_000, 2, 1_000, 2_000)
        )

        private fun target(
            id: Long,
            title: String = "Local Song",
            artist: String = "Fictional Artist",
            album: String = "Fictional Album"
        ) = LocalReconciliationTarget(
            id, id + 100, "ref-$id", title, artist, album, null,
            179_000, "$title.flac", "flac", "Music/$artist"
        )

        private fun ratings(source: Int?, target: Int?) = ListeningIdentityReconciliationRatings(
            source,
            target,
            when {
                source == null && target == null -> ListeningIdentityReconciliationRatingState.NO_RATINGS
                source == null -> ListeningIdentityReconciliationRatingState.TARGET_ONLY
                target == null -> ListeningIdentityReconciliationRatingState.SOURCE_ONLY
                source == target -> ListeningIdentityReconciliationRatingState.SAME_RATING
                else -> ListeningIdentityReconciliationRatingState.CONFLICTING_RATINGS
            }
        )
    }
}
