package io.github.rsgarrido.sazanami.data

import io.github.rsgarrido.sazanami.data.local.ListeningSource
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryAutomaticReconciliationTest {
    @Test
    fun onlyUniqueExactAndCanonicalExactItemsBecomeAutomaticRequests() {
        val exact = item(1, ReconciliationMatchConfidence.EXACT, target(11))
        val canonical = item(2, ReconciliationMatchConfidence.CANONICAL_EXACT, target(12))
        val fuzzy = item(3, ReconciliationMatchConfidence.FUZZY, target(13))
        val ambiguous = item(
            4,
            ReconciliationMatchConfidence.AMBIGUOUS,
            target(14),
            target(15)
        )
        val unmatched = item(5, ReconciliationMatchConfidence.UNMATCHED)

        val requests = automaticReconciliationRequests(discovery(
            exact, canonical, fuzzy, ambiguous, unmatched
        ))

        assertEquals(listOf(1L, 2L), requests.map { it.sourceIdentityId })
        assertEquals(listOf(11L, 12L), requests.map { it.target.identityId })
    }

    @Test
    fun multipleCanonicalEquivalentCandidatesAreNeverAutomatic() {
        val canonicalDuplicate = item(
            1,
            ReconciliationMatchConfidence.AMBIGUOUS,
            target(11, "Everything's Ruined"),
            target(12, "Everything’s Ruined")
        )

        assertTrue(automaticReconciliationRequests(discovery(canonicalDuplicate)).isEmpty())
    }

    @Test
    fun straightAndMojibakeApostropheMatcherResultIsEligibleWhenUnique() {
        val matcher = ReconciliationCandidateMatcher()
        val result = matcher.discover(
            listOf(source(1, "Everything's Ruined")),
            listOf(target(11, "Everything\u00e2\u20ac\u2122s Ruined"))
        )

        assertEquals(ReconciliationMatchConfidence.CANONICAL_EXACT,
            result.items.single().confidence)
        assertEquals(1L, automaticReconciliationRequests(result).single().sourceIdentityId)
    }

    private fun discovery(vararg items: HistoricalReconciliationItem) =
        ReconciliationCandidateDiscovery(
            items.toList(),
            ReconciliationCandidateSummary(
                totalReviewableIdentities = items.size,
                withSuggestions = items.count {
                    it.disposition == ReconciliationCandidateDisposition.SUGGESTED
                },
                ambiguous = items.count {
                    it.disposition == ReconciliationCandidateDisposition.AMBIGUOUS
                },
                noCandidate = items.count {
                    it.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE
                }
            )
        )

    private fun item(
        id: Long,
        confidence: ReconciliationMatchConfidence,
        vararg targets: LocalReconciliationTarget
    ): HistoricalReconciliationItem {
        val disposition = when (confidence) {
            ReconciliationMatchConfidence.AMBIGUOUS -> ReconciliationCandidateDisposition.AMBIGUOUS
            ReconciliationMatchConfidence.UNMATCHED -> ReconciliationCandidateDisposition.NO_CANDIDATE
            else -> ReconciliationCandidateDisposition.SUGGESTED
        }
        return HistoricalReconciliationItem(
            source = source(id, "Track $id"),
            candidates = targets.map { target ->
                ListeningIdentityReconciliationCandidate(
                    target,
                    ReconciliationCandidateEvidence(
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationMetadataRelation.EXACT,
                        ReconciliationVersionRelation.NONE,
                        emptySet(),
                        ReconciliationCandidateCategory.STRONG_METADATA
                    )
                )
            },
            disposition = disposition,
            hasMoreCandidates = false,
            confidence = confidence,
            candidateCount = targets.size
        )
    }

    private fun source(id: Long, title: String) = HistoricalReconciliationSource(
        id,
        title,
        "Faith No More",
        "Angel Dust",
        null,
        setOf(ListeningSource.SPOTIFY_IMPORT),
        true,
        HistoricalReconciliationMetrics(5, 5, 500_000, 1, 1, 2)
    )

    private fun target(id: Long, title: String = "Track") = LocalReconciliationTarget(
        id,
        id + 100,
        "ref-$id",
        title,
        "Faith No More",
        "Angel Dust",
        null,
        180_000,
        "$title.flac",
        "flac",
        "Music/Faith No More"
    )
}
