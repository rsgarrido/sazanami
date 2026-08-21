package com.example.cdplaya.data

import androidx.room.withTransaction
import com.example.cdplaya.data.local.AppDatabase
import com.example.cdplaya.data.local.HistoricalReconciliationSourceRow
import com.example.cdplaya.data.local.ListeningSource
import com.example.cdplaya.data.local.LocalReconciliationTargetRow
import java.text.Normalizer
import java.util.Locale

enum class ReconciliationCandidateCategory {
    STRONG_METADATA,
    TYPOGRAPHY_VARIANT,
    INCOMPLETE_EVIDENCE,
    VERSION_SENSITIVE,
    AMBIGUOUS
}

enum class ReconciliationCandidateDisposition { SUGGESTED, AMBIGUOUS, NO_CANDIDATE }

enum class ReconciliationMetadataRelation {
    EXACT,
    NORMALIZED,
    MISSING,
    DIFFERENT,
    VERSION_VARIANT
}

enum class ReconciliationVersionRelation { NONE, SAME, DIFFERENT }

enum class ReconciliationMissingField { TITLE, ARTIST, ALBUM }

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
    val category: ReconciliationCandidateCategory
)

data class ListeningIdentityReconciliationCandidate(
    val target: LocalReconciliationTarget,
    val evidence: ReconciliationCandidateEvidence
)

data class HistoricalReconciliationItem(
    val source: HistoricalReconciliationSource,
    val candidates: List<ListeningIdentityReconciliationCandidate>,
    val disposition: ReconciliationCandidateDisposition,
    val hasMoreCandidates: Boolean
)

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
            ?: return noCandidate(source)
        val artist = source.artist.meaningful()
        val album = source.album.meaningful()

        val stage = when {
            artist == null -> index.titleOnly(title)
            album == null -> index.withoutAlbum(title, artist)
            else -> index.withAlbum(title, artist, album)
        }
        if (stage.targets.isEmpty()) return noCandidate(source, stage.hasMore)

        val sorted = stage.targets.distinctBy(LocalReconciliationTarget::referenceKey)
            .sortedWith(targetComparator)
        val hasMore = stage.hasMore || sorted.size > maxCandidates
        val bounded = sorted.take(maxCandidates)
        val ambiguous = hasMore || sorted.size > 1
        val category = when {
            ambiguous -> ReconciliationCandidateCategory.AMBIGUOUS
            stage.kind == MatchKind.VERSION -> ReconciliationCandidateCategory.VERSION_SENSITIVE
            artist == null || album == null -> ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE
            stage.kind == MatchKind.CONSERVATIVE -> ReconciliationCandidateCategory.STRONG_METADATA
            else -> ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT
        }
        val candidates = bounded.map { target ->
            ListeningIdentityReconciliationCandidate(
                target = target,
                evidence = evidence(source, target, category)
            )
        }
        return HistoricalReconciliationItem(
            source = source,
            candidates = candidates,
            disposition = if (ambiguous) ReconciliationCandidateDisposition.AMBIGUOUS
            else ReconciliationCandidateDisposition.SUGGESTED,
            hasMoreCandidates = hasMore
        )
    }

    private fun noCandidate(
        source: HistoricalReconciliationSource,
        hasMore: Boolean = false
    ) = HistoricalReconciliationItem(
        source = source,
        candidates = emptyList(),
        disposition = ReconciliationCandidateDisposition.NO_CANDIDATE,
        hasMoreCandidates = hasMore
    )

    private fun evidence(
        source: HistoricalReconciliationSource,
        target: LocalReconciliationTarget,
        category: ReconciliationCandidateCategory
    ): ReconciliationCandidateEvidence {
        val sourceVersions = versionMarkers(source.title, source.album)
        val targetVersions = versionMarkers(target.title, target.album)
        val versionRelation = when {
            sourceVersions.isEmpty() && targetVersions.isEmpty() -> ReconciliationVersionRelation.NONE
            sourceVersions == targetVersions -> ReconciliationVersionRelation.SAME
            else -> ReconciliationVersionRelation.DIFFERENT
        }
        return ReconciliationCandidateEvidence(
            titleRelation = metadataRelation(source.title, target.title, versionRelation),
            artistRelation = metadataRelation(source.artist, target.artist),
            albumRelation = metadataRelation(source.album, target.album, versionRelation),
            versionRelation = versionRelation,
            missingFields = buildSet {
                if (source.title.meaningful() == null) add(ReconciliationMissingField.TITLE)
                if (source.artist.meaningful() == null) add(ReconciliationMissingField.ARTIST)
                if (source.album.meaningful() == null) add(ReconciliationMissingField.ALBUM)
            },
            category = category
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
        if (candidatePunctuationNormalize(source) == candidatePunctuationNormalize(target)) {
            return ReconciliationMetadataRelation.NORMALIZED
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
        private val typographyTriple = orderedTargets.groupBy { it.triple(::candidateTypographyNormalize) }
        private val typographyPair = orderedTargets.groupBy { it.pair(::candidateTypographyNormalize) }
        private val accentTriple = orderedTargets.groupBy { it.triple(::candidateAccentNormalize) }
        private val accentPair = orderedTargets.groupBy { it.pair(::candidateAccentNormalize) }
        private val punctuationTriple = orderedTargets.groupBy { it.triple(::candidatePunctuationNormalize) }
        private val punctuationPair = orderedTargets.groupBy { it.pair(::candidatePunctuationNormalize) }
        private val title = orderedTargets.groupBy { candidatePunctuationNormalize(it.title) }
        private val versionPair = orderedTargets.groupBy {
            pairKey(candidateConservativeNormalize(it.artist), candidateConservativeNormalize(versionBase(it.title)))
        }

        fun withAlbum(title: String, artist: String, album: String): MatchStage {
            val lookups = listOf(
                MatchKind.CONSERVATIVE to conservativeTriple[tripleKey(
                    candidateConservativeNormalize(artist),
                    candidateConservativeNormalize(title),
                    candidateConservativeNormalize(album)
                )],
                MatchKind.TYPOGRAPHY to typographyTriple[tripleKey(
                    candidateTypographyNormalize(artist),
                    candidateTypographyNormalize(title),
                    candidateTypographyNormalize(album)
                )],
                MatchKind.TYPOGRAPHY to accentTriple[tripleKey(
                    candidateAccentNormalize(artist),
                    candidateAccentNormalize(title),
                    candidateAccentNormalize(album)
                )],
                MatchKind.TYPOGRAPHY to punctuationTriple[tripleKey(
                    candidatePunctuationNormalize(artist),
                    candidatePunctuationNormalize(title),
                    candidatePunctuationNormalize(album)
                )]
            )
            lookups.firstOrNull { !it.second.isNullOrEmpty() }?.let {
                return MatchStage(it.second.orEmpty(), it.first)
            }
            val sourceVersions = versionMarkers(title, album)
            val versionTargets = versionPair[pairKey(
                candidateConservativeNormalize(artist),
                candidateConservativeNormalize(versionBase(title))
            )].orEmpty().filter { target ->
                sourceVersions != versionMarkers(target.title, target.album)
            }
            return MatchStage(versionTargets, MatchKind.VERSION)
        }

        fun withoutAlbum(title: String, artist: String): MatchStage {
            val lookups = listOf(
                MatchKind.CONSERVATIVE to conservativePair[pairKey(
                    candidateConservativeNormalize(artist), candidateConservativeNormalize(title)
                )],
                MatchKind.TYPOGRAPHY to typographyPair[pairKey(
                    candidateTypographyNormalize(artist), candidateTypographyNormalize(title)
                )],
                MatchKind.TYPOGRAPHY to accentPair[pairKey(
                    candidateAccentNormalize(artist), candidateAccentNormalize(title)
                )],
                MatchKind.TYPOGRAPHY to punctuationPair[pairKey(
                    candidatePunctuationNormalize(artist), candidatePunctuationNormalize(title)
                )]
            )
            val found = lookups.firstOrNull { !it.second.isNullOrEmpty() }
            return MatchStage(found?.second.orEmpty(), found?.first ?: MatchKind.CONSERVATIVE)
        }

        fun titleOnly(titleValue: String): MatchStage {
            val matches = title[candidatePunctuationNormalize(titleValue)].orEmpty()
            return if (matches.size <= maxCandidates) MatchStage(matches, MatchKind.TYPOGRAPHY)
            else MatchStage(emptyList(), MatchKind.TYPOGRAPHY, hasMore = true)
        }
    }

    private data class MatchStage(
        val targets: List<LocalReconciliationTarget>,
        val kind: MatchKind,
        val hasMore: Boolean = false
    )

    private enum class MatchKind { CONSERVATIVE, TYPOGRAPHY, VERSION }

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
    val normalized = candidateTypographyNormalize(value)
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
    val normalized = candidateTypographyNormalize(value)
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
    var result = candidateTypographyNormalize(value)
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

internal fun candidateConservativeNormalize(value: String): String = Normalizer
    .normalize(value, Normalizer.Form.NFC)
    .lowercase(Locale.ROOT)
    .trim()
    .replace(Regex("\\s+"), " ")

internal fun candidateTypographyNormalize(value: String): String =
    candidateConservativeNormalize(value)
        .replace(Regex("[\\u2018\\u2019\\u201A\\u201B\\u2032]"), "'")
        .replace(Regex("[\\u2010-\\u2015\\u2212]"), "-")

internal fun candidateAccentNormalize(value: String): String = Normalizer
    .normalize(candidateTypographyNormalize(value), Normalizer.Form.NFD)
    .replace(Regex("\\p{M}+"), "")

internal fun candidatePunctuationNormalize(value: String): String = candidateAccentNormalize(value)
    .replace(Regex("(?<=\\p{L})\\.(?=\\p{L})"), "")
    .replace(Regex("\\s*#\\s*"), "#")
    .replace(Regex("\\s+"), " ")
    .trim()

private fun String.meaningful(): String? = trim().takeIf { it.isNotEmpty() }

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
            ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT -> 1
            ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE -> 2
            ReconciliationCandidateCategory.VERSION_SENSITIVE -> 3
            else -> 4
        }
        ReconciliationCandidateDisposition.AMBIGUOUS -> 5
        ReconciliationCandidateDisposition.NO_CANDIDATE -> 6
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
