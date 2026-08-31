package io.github.rsgarrido.sazanami.data

import androidx.room.withTransaction
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.HistoricalReconciliationSourceRow
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.LocalReconciliationTargetRow
import java.util.Locale

enum class ReconciliationCandidateCategory {
    STRONG_METADATA,
    CANONICAL_METADATA,
    TYPOGRAPHY_VARIANT,
    INCOMPLETE_EVIDENCE,
    VERSION_SENSITIVE,
    AMBIGUOUS
}

enum class ReconciliationCandidateDisposition { SUGGESTED, AMBIGUOUS, NO_CANDIDATE }

enum class ReconciliationMatchConfidence {
    EXACT,
    CANONICAL_EXACT,
    FUZZY,
    AMBIGUOUS,
    UNMATCHED
}

enum class ReconciliationNonDeterministicReason {
    MULTIPLE_PLAUSIBLE_CANDIDATES,
    CANDIDATE_LIMIT_EXCEEDED,
    MISSING_REQUIRED_METADATA,
    FUZZY_ONLY,
    VERSION_CONFLICT,
    NO_CREDIBLE_CANDIDATE
}

enum class ReconciliationMetadataRelation {
    EXACT,
    CANONICAL,
    FUZZY,
    MISSING,
    DIFFERENT,
    VERSION_VARIANT
}

enum class ReconciliationVersionRelation { NONE, SAME, DIFFERENT }

enum class ReconciliationMissingField { TITLE, ARTIST, ALBUM }

enum class ReconciliationMetadataField { TITLE, ARTIST, ALBUM }

enum class ReconciliationState { UNMATCHED }

data class HistoricalReconciliationMetrics(
    val importedEventCount: Long,
    val qualifiedPlayCount: Long,
    val recordedListeningMs: Long,
    val completedCount: Long,
    val firstListenedAt: Long,
    val lastListenedAt: Long
)

data class HistoricalReconciliationSource(
    val identityId: Long,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val importedProviders: Set<ListeningSource>,
    val hasStableExternalId: Boolean,
    val metrics: HistoricalReconciliationMetrics,
    val reconciliationState: ReconciliationState = ReconciliationState.UNMATCHED
)

data class LocalReconciliationTarget(
    val identityId: Long,
    val localBindingId: Long,
    val referenceKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val durationMs: Long?,
    val displayName: String?,
    val fileExtension: String?,
    val relativeFolder: String?
)

data class ReconciliationCandidateEvidence(
    val titleRelation: ReconciliationMetadataRelation,
    val artistRelation: ReconciliationMetadataRelation,
    val albumRelation: ReconciliationMetadataRelation,
    val versionRelation: ReconciliationVersionRelation,
    val missingFields: Set<ReconciliationMissingField>,
    val category: ReconciliationCandidateCategory,
    val importedMetadata: ReconciliationComparisonMetadata? = null,
    val localMetadata: ReconciliationComparisonMetadata? = null,
    val candidateCount: Int = 1,
    val confidence: ReconciliationMatchConfidence = ReconciliationMatchConfidence.FUZZY,
    val matchedFields: Set<ReconciliationMetadataField> = emptySet(),
    val nonDeterministicReason: ReconciliationNonDeterministicReason? = null
)

data class ListeningIdentityReconciliationCandidate(
    val target: LocalReconciliationTarget,
    val evidence: ReconciliationCandidateEvidence
)

data class HistoricalReconciliationItem(
    val source: HistoricalReconciliationSource,
    val candidates: List<ListeningIdentityReconciliationCandidate>,
    val disposition: ReconciliationCandidateDisposition,
    val hasMoreCandidates: Boolean,
    val confidence: ReconciliationMatchConfidence = ReconciliationMatchConfidence.UNMATCHED,
    val importedMetadata: ReconciliationComparisonMetadata = reconciliationComparisonMetadata(
        source.title, source.artist, source.album
    ),
    val candidateCount: Int = candidates.size,
    val nonDeterministicReason: ReconciliationNonDeterministicReason? = null
) {
    val isDeterministic: Boolean
        get() = candidateCount == 1 && !hasMoreCandidates &&
            (confidence == ReconciliationMatchConfidence.EXACT ||
                confidence == ReconciliationMatchConfidence.CANONICAL_EXACT)
}

data class ReconciliationCandidateSummary(
    val totalReviewableIdentities: Int,
    val withSuggestions: Int,
    val ambiguous: Int,
    val noCandidate: Int
)

data class ReconciliationCandidateDiscovery(
    val items: List<HistoricalReconciliationItem>,
    val summary: ReconciliationCandidateSummary
)

/**
 * Request-scoped, read-only candidate discovery. It has no dependency on the mutating
 * reconciliation repository and cannot create or remove confirmed links.
 */
class ListeningIdentityReconciliationCandidateService(
    private val database: AppDatabase,
    private val matcher: ReconciliationCandidateMatcher = ReconciliationCandidateMatcher()
) {
    suspend fun discoverCandidates(): ReconciliationCandidateDiscovery {
        val (sources, targets) = database.withTransaction {
            val dao = database.listeningIdentityReconciliationCandidateDao()
            dao.getReviewableHistoricalSources() to dao.getEligibleLocalTargets()
        }
        return matcher.discover(sources.map { it.toDomain() }, targets.map { it.toDomain() })
    }

    suspend fun discoverCandidates(
        currentLocalTargets: List<LocalReconciliationTarget>
    ): ReconciliationCandidateDiscovery {
        val sources = database.withTransaction {
            database.listeningIdentityReconciliationCandidateDao().getReviewableHistoricalSources()
        }
        return matcher.discover(sources.map { it.toDomain() }, currentLocalTargets)
    }
}

class ReconciliationCandidateMatcher(
    val maxCandidates: Int = DEFAULT_MAX_CANDIDATES
) {
    init {
        require(maxCandidates in 1..100) { "Candidate limit must be between 1 and 100." }
    }

    fun discover(
        sources: List<HistoricalReconciliationSource>,
        targets: List<LocalReconciliationTarget>
    ): ReconciliationCandidateDiscovery {
        val index = CandidateIndex(targets)
        val items = sources.map { match(it, index) }.sortedWith(reviewQueueComparator)
        return ReconciliationCandidateDiscovery(
            items = items,
            summary = ReconciliationCandidateSummary(
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
    }

    private fun match(
        source: HistoricalReconciliationSource,
        index: CandidateIndex
    ): HistoricalReconciliationItem {
        val title = source.title.meaningful()
            ?: return noCandidate(
                source,
                reason = ReconciliationNonDeterministicReason.MISSING_REQUIRED_METADATA
            )
        val artist = source.artist.meaningful()
        val album = source.album.meaningful()

        val stage = when {
            artist == null -> index.titleOnly(title)
            album == null -> index.withoutAlbum(title, artist)
            else -> index.withAlbum(title, artist, album)
        }
        if (stage.targets.isEmpty()) return noCandidate(
            source = source,
            hasMore = stage.hasMore,
            candidateCount = stage.totalCandidateCount,
            reason = if (stage.hasMore) {
                ReconciliationNonDeterministicReason.CANDIDATE_LIMIT_EXCEEDED
            } else {
                ReconciliationNonDeterministicReason.NO_CREDIBLE_CANDIDATE
            }
        )

        val sorted = stage.targets.distinctBy(LocalReconciliationTarget::referenceKey)
            .sortedWith(targetComparator)
        val hasMore = stage.hasMore || sorted.size > maxCandidates
        val bounded = sorted.take(maxCandidates)
        val ambiguous = hasMore || sorted.size > 1
        val confidence = when {
            ambiguous -> ReconciliationMatchConfidence.AMBIGUOUS
            artist == null || album == null -> ReconciliationMatchConfidence.FUZZY
            stage.kind == MatchKind.EXACT -> ReconciliationMatchConfidence.EXACT
            stage.kind == MatchKind.CANONICAL -> ReconciliationMatchConfidence.CANONICAL_EXACT
            else -> ReconciliationMatchConfidence.FUZZY
        }
        val nonDeterministicReason = when {
            hasMore -> ReconciliationNonDeterministicReason.CANDIDATE_LIMIT_EXCEEDED
            ambiguous -> ReconciliationNonDeterministicReason.MULTIPLE_PLAUSIBLE_CANDIDATES
            artist == null || album == null ->
                ReconciliationNonDeterministicReason.MISSING_REQUIRED_METADATA
            stage.kind == MatchKind.VERSION ->
                ReconciliationNonDeterministicReason.VERSION_CONFLICT
            stage.kind == MatchKind.ACCENT || stage.kind == MatchKind.PUNCTUATION ||
                stage.kind == MatchKind.FUZZY -> ReconciliationNonDeterministicReason.FUZZY_ONLY
            else -> null
        }
        val category = when {
            ambiguous -> ReconciliationCandidateCategory.AMBIGUOUS
            stage.kind == MatchKind.VERSION -> ReconciliationCandidateCategory.VERSION_SENSITIVE
            artist == null || album == null -> ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE
            stage.kind == MatchKind.EXACT -> ReconciliationCandidateCategory.STRONG_METADATA
            stage.kind == MatchKind.CANONICAL -> ReconciliationCandidateCategory.CANONICAL_METADATA
            else -> ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT
        }
        val candidates = bounded.map { target ->
            ListeningIdentityReconciliationCandidate(
                target = target,
                evidence = evidence(
                    source = source,
                    target = target,
                    category = category,
                    candidateCount = sorted.size,
                    confidence = confidence,
                    nonDeterministicReason = nonDeterministicReason
                )
            )
        }
        return HistoricalReconciliationItem(
            source = source,
            candidates = candidates,
            disposition = if (ambiguous) ReconciliationCandidateDisposition.AMBIGUOUS
            else ReconciliationCandidateDisposition.SUGGESTED,
            hasMoreCandidates = hasMore,
            confidence = confidence,
            candidateCount = sorted.size,
            nonDeterministicReason = nonDeterministicReason
        )
    }

    private fun noCandidate(
        source: HistoricalReconciliationSource,
        hasMore: Boolean = false,
        candidateCount: Int = 0,
        reason: ReconciliationNonDeterministicReason =
            ReconciliationNonDeterministicReason.NO_CREDIBLE_CANDIDATE
    ) = HistoricalReconciliationItem(
        source = source,
        candidates = emptyList(),
        disposition = ReconciliationCandidateDisposition.NO_CANDIDATE,
        hasMoreCandidates = hasMore,
        confidence = ReconciliationMatchConfidence.UNMATCHED,
        candidateCount = candidateCount,
        nonDeterministicReason = reason
    )

    private fun evidence(
        source: HistoricalReconciliationSource,
        target: LocalReconciliationTarget,
        category: ReconciliationCandidateCategory,
        candidateCount: Int,
        confidence: ReconciliationMatchConfidence,
        nonDeterministicReason: ReconciliationNonDeterministicReason?
    ): ReconciliationCandidateEvidence {
        val sourceVersions = versionMarkers(source.title, source.album)
        val targetVersions = versionMarkers(target.title, target.album)
        val versionRelation = when {
            sourceVersions.isEmpty() && targetVersions.isEmpty() -> ReconciliationVersionRelation.NONE
            sourceVersions == targetVersions -> ReconciliationVersionRelation.SAME
            else -> ReconciliationVersionRelation.DIFFERENT
        }
        val titleRelation = metadataRelation(source.title, target.title, versionRelation)
        val artistRelation = metadataRelation(source.artist, target.artist)
        val albumRelation = metadataRelation(source.album, target.album, versionRelation)
        return ReconciliationCandidateEvidence(
            titleRelation = titleRelation,
            artistRelation = artistRelation,
            albumRelation = albumRelation,
            versionRelation = versionRelation,
            missingFields = buildSet {
                if (source.title.meaningful() == null) add(ReconciliationMissingField.TITLE)
                if (source.artist.meaningful() == null) add(ReconciliationMissingField.ARTIST)
                if (source.album.meaningful() == null) add(ReconciliationMissingField.ALBUM)
            },
            category = category,
            importedMetadata = reconciliationComparisonMetadata(
                source.title, source.artist, source.album
            ),
            localMetadata = reconciliationComparisonMetadata(
                target.title, target.artist, target.album
            ),
            candidateCount = candidateCount,
            confidence = confidence,
            matchedFields = buildSet {
                if (titleRelation.isMatch()) add(ReconciliationMetadataField.TITLE)
                if (artistRelation.isMatch()) add(ReconciliationMetadataField.ARTIST)
                if (albumRelation.isMatch()) add(ReconciliationMetadataField.ALBUM)
            },
            nonDeterministicReason = nonDeterministicReason
        )
    }

    private fun metadataRelation(
        source: String,
        target: String,
        versionRelation: ReconciliationVersionRelation = ReconciliationVersionRelation.NONE
    ): ReconciliationMetadataRelation {
        if (source.meaningful() == null) return ReconciliationMetadataRelation.MISSING
        if (candidateConservativeNormalize(source) == candidateConservativeNormalize(target)) {
            return ReconciliationMetadataRelation.EXACT
        }
        if (candidateCanonicalNormalize(source) == candidateCanonicalNormalize(target)) {
            return ReconciliationMetadataRelation.CANONICAL
        }
        if (candidatePunctuationNormalize(source) == candidatePunctuationNormalize(target) ||
            fuzzyMetadataEquivalent(source, target)) {
            return ReconciliationMetadataRelation.FUZZY
        }
        if (versionRelation == ReconciliationVersionRelation.DIFFERENT &&
            candidateConservativeNormalize(versionBase(source)) ==
            candidateConservativeNormalize(versionBase(target))) {
            return ReconciliationMetadataRelation.VERSION_VARIANT
        }
        return ReconciliationMetadataRelation.DIFFERENT
    }

    private inner class CandidateIndex(targets: List<LocalReconciliationTarget>) {
        private val orderedTargets = targets.distinctBy(LocalReconciliationTarget::referenceKey)
            .sortedWith(targetComparator)
        private val conservativeTriple = orderedTargets.groupBy { it.triple(::candidateConservativeNormalize) }
        private val conservativePair = orderedTargets.groupBy { it.pair(::candidateConservativeNormalize) }
        private val canonicalTriple = orderedTargets.groupBy { it.triple(::candidateCanonicalNormalize) }
        private val canonicalPair = orderedTargets.groupBy { it.pair(::candidateCanonicalNormalize) }
        private val accentTriple = orderedTargets.groupBy { it.triple(::candidateAccentNormalize) }
        private val accentPair = orderedTargets.groupBy { it.pair(::candidateAccentNormalize) }
        private val punctuationTriple = orderedTargets.groupBy { it.triple(::candidatePunctuationNormalize) }
        private val punctuationPair = orderedTargets.groupBy { it.pair(::candidatePunctuationNormalize) }
        private val title = orderedTargets.groupBy { candidatePunctuationNormalize(it.title) }
        private val canonicalArtistAlbum = orderedTargets.groupBy {
            pairKey(candidateCanonicalNormalize(it.artist), candidateCanonicalNormalize(it.album))
        }
        private val canonicalArtist = orderedTargets.groupBy {
            candidateCanonicalNormalize(it.artist)
        }
        private val versionPair = orderedTargets.groupBy {
            pairKey(candidateCanonicalNormalize(it.artist), candidateCanonicalNormalize(versionBase(it.title)))
        }

        fun withAlbum(title: String, artist: String, album: String): MatchStage {
            val equivalenceLookups = listOf(
                MatchKind.EXACT to conservativeTriple[tripleKey(
                    candidateConservativeNormalize(artist),
                    candidateConservativeNormalize(title),
                    candidateConservativeNormalize(album)
                )],
                MatchKind.CANONICAL to canonicalTriple[tripleKey(
                    candidateCanonicalNormalize(artist),
                    candidateCanonicalNormalize(title),
                    candidateCanonicalNormalize(album)
                )],
                MatchKind.ACCENT to accentTriple[tripleKey(
                    candidateAccentNormalize(artist),
                    candidateAccentNormalize(title),
                    candidateAccentNormalize(album)
                )],
                MatchKind.PUNCTUATION to punctuationTriple[tripleKey(
                    candidatePunctuationNormalize(artist),
                    candidatePunctuationNormalize(title),
                    candidatePunctuationNormalize(album)
                )]
            )
            if (equivalenceLookups.take(2).any { !it.second.isNullOrEmpty() }) {
                return collectPlausible(equivalenceLookups)!!
            }
            val fuzzyLookups = equivalenceLookups.drop(2) + listOf(
                MatchKind.FUZZY to canonicalArtistAlbum[pairKey(
                    candidateCanonicalNormalize(artist),
                    candidateCanonicalNormalize(album)
                )].orEmpty().filter { fuzzyMetadataEquivalent(title, it.title) }
            )
            collectPlausible(fuzzyLookups)?.let {
                return it
            }
            val sourceVersions = versionMarkers(title, album)
            val versionTargets = versionPair[pairKey(
                candidateCanonicalNormalize(artist),
                candidateCanonicalNormalize(versionBase(title))
            )].orEmpty().filter { target ->
                sourceVersions != versionMarkers(target.title, target.album)
            }
            return MatchStage(versionTargets, MatchKind.VERSION)
        }

        fun withoutAlbum(title: String, artist: String): MatchStage {
            val equivalenceLookups = listOf(
                MatchKind.EXACT to conservativePair[pairKey(
                    candidateConservativeNormalize(artist), candidateConservativeNormalize(title)
                )],
                MatchKind.CANONICAL to canonicalPair[pairKey(
                    candidateCanonicalNormalize(artist), candidateCanonicalNormalize(title)
                )],
                MatchKind.ACCENT to accentPair[pairKey(
                    candidateAccentNormalize(artist), candidateAccentNormalize(title)
                )],
                MatchKind.PUNCTUATION to punctuationPair[pairKey(
                    candidatePunctuationNormalize(artist), candidatePunctuationNormalize(title)
                )]
            )
            if (equivalenceLookups.take(2).any { !it.second.isNullOrEmpty() }) {
                return collectPlausible(equivalenceLookups)!!
            }
            val fuzzyLookups = equivalenceLookups.drop(2) + listOf(
                MatchKind.FUZZY to canonicalArtist[candidateCanonicalNormalize(artist)]
                    .orEmpty().filter { fuzzyMetadataEquivalent(title, it.title) }
            )
            return collectPlausible(fuzzyLookups) ?: MatchStage(emptyList(), MatchKind.EXACT)
        }

        fun titleOnly(titleValue: String): MatchStage {
            val matches = title[candidatePunctuationNormalize(titleValue)].orEmpty()
            return MatchStage(
                targets = matches,
                kind = MatchKind.PUNCTUATION,
                hasMore = matches.size > maxCandidates,
                totalCandidateCount = matches.size
            )
        }

        private fun collectPlausible(
            lookups: List<Pair<MatchKind, List<LocalReconciliationTarget>?>>
        ): MatchStage? {
            val strongest = lookups.firstOrNull { !it.second.isNullOrEmpty() }?.first ?: return null
            val plausible = lookups.flatMap { it.second.orEmpty() }
                .distinctBy(LocalReconciliationTarget::referenceKey)
            return MatchStage(
                targets = plausible,
                kind = strongest,
                totalCandidateCount = plausible.size
            )
        }
    }

    private data class MatchStage(
        val targets: List<LocalReconciliationTarget>,
        val kind: MatchKind,
        val hasMore: Boolean = false,
        val totalCandidateCount: Int = targets.size
    )

    private enum class MatchKind { EXACT, CANONICAL, ACCENT, PUNCTUATION, FUZZY, VERSION }

    companion object {
        const val DEFAULT_MAX_CANDIDATES = 8
    }
}

private enum class VersionMarker {
    LIVE, REMASTER, REMIX, EDIT, VERSION, DEMO, ACOUSTIC, MONO, STEREO,
    ANNIVERSARY, DELUXE, SESSION
}

private val markerPatterns = linkedMapOf(
    VersionMarker.LIVE to Regex("\\blive\\b"),
    VersionMarker.REMASTER to Regex("\\bremaster(?:ed)?\\b"),
    VersionMarker.REMIX to Regex("\\bremix(?:ed)?\\b"),
    VersionMarker.EDIT to Regex("\\b(?:radio\\s+)?edit\\b"),
    VersionMarker.VERSION to Regex("\\bversion\\b"),
    VersionMarker.DEMO to Regex("\\bdemo\\b"),
    VersionMarker.ACOUSTIC to Regex("\\bacoustic\\b"),
    VersionMarker.MONO to Regex("\\bmono\\b"),
    VersionMarker.STEREO to Regex("\\bstereo\\b"),
    VersionMarker.ANNIVERSARY to Regex("\\banniversary\\b"),
    VersionMarker.DELUXE to Regex("\\bdeluxe\\b"),
    VersionMarker.SESSION to Regex("\\bsession\\b")
)

private val bracketContent = Regex("[\\[(]([^\\])}]+)[\\])}]")
private val dashSuffix = Regex("\\s[-\\u2010-\\u2015]\\s*(.+)$")

private fun versionMarkers(title: String, album: String): Set<VersionMarker> = buildSet {
    addAll(markersInTitle(title))
    addAll(markersInAlbum(album))
}

private fun markersInTitle(value: String): Set<VersionMarker> {
    val normalized = candidateCanonicalNormalize(value)
    val contexts = buildList {
        bracketContent.findAll(normalized).forEach { add(it.groupValues[1]) }
        dashSuffix.find(normalized)?.groupValues?.get(1)?.let(::add)
        add(normalized.substringAfterLast(' '))
        if (Regex("(?:radio\\s+edit|remaster(?:ed)?(?:\\s+\\d{2,4})?|song\\s+version|demo|acoustic|mono|stereo|remix)\\s*$")
                .containsMatchIn(normalized)) add(normalized)
    }
    return markerPatterns.filterValues { pattern -> contexts.any(pattern::containsMatchIn) }.keys
}

private fun markersInAlbum(value: String): Set<VersionMarker> {
    val normalized = candidateCanonicalNormalize(value)
    if (normalized.isBlank()) return emptySet()
    val contexts = buildList {
        bracketContent.findAll(normalized).forEach { add(it.groupValues[1]) }
        dashSuffix.find(normalized)?.groupValues?.get(1)?.let(::add)
        if (normalized.startsWith("live ") || normalized == "live") add("live")
        add(normalized.substringAfterLast(' '))
    }
    return markerPatterns.filterValues { pattern -> contexts.any(pattern::containsMatchIn) }.keys
}

private fun versionBase(value: String): String {
    var result = candidateCanonicalNormalize(value)
    result = bracketContent.replace(result) { match ->
        if (markerPatterns.values.any { it.containsMatchIn(match.groupValues[1]) }) "" else match.value
    }
    val suffix = dashSuffix.find(result)
    if (suffix != null && markerPatterns.values.any { it.containsMatchIn(suffix.groupValues[1]) }) {
        result = result.substring(0, suffix.range.first)
    }
    val trailing = Regex("\\s+(?:radio\\s+edit|remaster(?:ed)?(?:\\s+\\d{2,4})?|remix|edit|version|demo|acoustic|mono|stereo|anniversary|deluxe|session)\\s*$")
    return result.replace(trailing, "").trim()
}

private fun String.meaningful(): String? = trim().takeIf { it.isNotEmpty() }

private fun ReconciliationMetadataRelation.isMatch(): Boolean = when (this) {
    ReconciliationMetadataRelation.EXACT,
    ReconciliationMetadataRelation.CANONICAL,
    ReconciliationMetadataRelation.FUZZY -> true
    ReconciliationMetadataRelation.MISSING,
    ReconciliationMetadataRelation.DIFFERENT,
    ReconciliationMetadataRelation.VERSION_VARIANT -> false
}

private fun fuzzyMetadataEquivalent(first: String, second: String): Boolean {
    val left = candidateCanonicalNormalize(first)
    val right = candidateCanonicalNormalize(second)
    if (left == right || left.length < 4 || right.length < 4) return false
    if (left.none(Char::isLetterOrDigit) || right.none(Char::isLetterOrDigit)) return false
    val longest = maxOf(left.length, right.length)
    val allowedDistance = when {
        longest <= 7 -> 1
        longest <= 20 -> 2
        else -> 3
    }
    val distance = boundedLevenshteinDistance(left, right, allowedDistance)
    return distance <= allowedDistance && distance.toDouble() / longest <= 0.20
}

private fun boundedLevenshteinDistance(left: String, right: String, limit: Int): Int {
    if (kotlin.math.abs(left.length - right.length) > limit) return limit + 1
    var previous = IntArray(right.length + 1) { it }
    for (leftIndex in left.indices) {
        val current = IntArray(right.length + 1)
        current[0] = leftIndex + 1
        var rowMinimum = current[0]
        for (rightIndex in right.indices) {
            val substitutionCost = if (left[leftIndex] == right[rightIndex]) 0 else 1
            current[rightIndex + 1] = minOf(
                current[rightIndex] + 1,
                previous[rightIndex + 1] + 1,
                previous[rightIndex] + substitutionCost
            )
            rowMinimum = minOf(rowMinimum, current[rightIndex + 1])
        }
        if (rowMinimum > limit) return limit + 1
        previous = current
    }
    return previous[right.length]
}

private fun tripleKey(artist: String, title: String, album: String) =
    "$artist\u0000$title\u0000$album"

private fun pairKey(artist: String, title: String) = "$artist\u0000$title"

private fun LocalReconciliationTarget.triple(normalize: (String) -> String) =
    tripleKey(normalize(artist), normalize(title), normalize(album))

private fun LocalReconciliationTarget.pair(normalize: (String) -> String) =
    pairKey(normalize(artist), normalize(title))

private val targetComparator = compareBy<LocalReconciliationTarget>(
    { candidateConservativeNormalize(it.title) },
    { candidateConservativeNormalize(it.artist) },
    { candidateConservativeNormalize(it.album) },
    LocalReconciliationTarget::referenceKey
)

private val reviewQueueComparator = compareBy<HistoricalReconciliationItem> {
    when (it.disposition) {
        ReconciliationCandidateDisposition.SUGGESTED -> when (
            it.candidates.singleOrNull()?.evidence?.category
        ) {
            ReconciliationCandidateCategory.STRONG_METADATA -> 0
            ReconciliationCandidateCategory.CANONICAL_METADATA -> 1
            ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT -> 2
            ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE -> 3
            ReconciliationCandidateCategory.VERSION_SENSITIVE -> 4
            else -> 4
        }
        ReconciliationCandidateDisposition.AMBIGUOUS -> 6
        ReconciliationCandidateDisposition.NO_CANDIDATE -> 7
    }
}.thenByDescending { it.source.metrics.importedEventCount }
    .thenByDescending { it.source.metrics.lastListenedAt }
    .thenBy { candidateConservativeNormalize(it.source.title) }
    .thenBy { it.source.identityId }

internal fun HistoricalReconciliationSourceRow.toDomain() = HistoricalReconciliationSource(
    identityId = identityId,
    title = titleSnapshot,
    artist = artistSnapshot,
    album = albumSnapshot,
    albumArtist = albumArtistSnapshot,
    importedProviders = providerStorageValues.split(',')
        .mapNotNull { it.trim().takeIf(String::isNotEmpty) }
        .map(ListeningSource::fromStorageValue)
        .toSet(),
    hasStableExternalId = externalIdCount > 0,
    metrics = HistoricalReconciliationMetrics(
        importedEventCount = importedEventCount,
        qualifiedPlayCount = qualifiedPlayCount,
        recordedListeningMs = recordedListeningMs,
        completedCount = completedCount,
        firstListenedAt = firstListenedAt,
        lastListenedAt = lastListenedAt
    )
)

internal fun LocalReconciliationTargetRow.toDomain(): LocalReconciliationTarget {
    val extension = displayName?.substringAfterLast('.', missingDelimiterValue = "")
        ?.trim()?.lowercase(Locale.ROOT)?.takeIf(String::isNotEmpty)
    return LocalReconciliationTarget(
        identityId = identityId,
        localBindingId = localBindingId,
        referenceKey = referenceKey,
        title = title,
        artist = artist,
        album = album,
        albumArtist = albumArtist,
        durationMs = durationMs,
        displayName = displayName,
        fileExtension = extension,
        relativeFolder = relativePath?.replace('\\', '/')?.trim()?.trim('/')
            ?.takeIf(String::isNotEmpty)
    )
}
