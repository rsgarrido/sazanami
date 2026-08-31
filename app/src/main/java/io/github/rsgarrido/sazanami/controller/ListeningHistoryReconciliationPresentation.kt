package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.data.HistoricalReconciliationItem
import io.github.rsgarrido.sazanami.data.HistoricalReconciliationSource
import io.github.rsgarrido.sazanami.data.ListeningIdentityReconciliationCandidate
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateCategory
import io.github.rsgarrido.sazanami.data.ReconciliationCandidateDisposition
import io.github.rsgarrido.sazanami.data.ReconciliationMetadataRelation
import io.github.rsgarrido.sazanami.data.ReconciliationVersionRelation
import io.github.rsgarrido.sazanami.data.candidateAccentNormalize
import io.github.rsgarrido.sazanami.data.candidateCanonicalNormalize

enum class ReconciliationBrowseMode { TRACKS, ALBUMS, ARTISTS }

enum class ReconciliationSortOption { HISTORICAL_PLAYS, TRACK_TITLE, ARTIST, ALBUM }

enum class ReconciliationReviewFilter {
    ALL,
    TITLE_FORMATTING,
    ACCENT_DIACRITIC,
    SIMILAR_TITLE,
    AMBIGUOUS
}

enum class ReconciliationTrackStatus { REVIEW, UNMATCHED, LINKED }

enum class ReconciliationMatchReason(val label: String) {
    METADATA_MATCH("Title, artist, and album match"),
    TITLE_FORMATTING("Title formatting difference"),
    PUNCTUATION("Punctuation difference"),
    ACCENT_DIACRITIC("Accent/diacritic difference"),
    SIMILAR_TITLE("Similar title"),
    VERSION_FORMATTING("Title/version formatting"),
    INCOMPLETE_METADATA("Some imported metadata is missing"),
    MULTIPLE_MATCHES("Multiple possible matches"),
    NO_LIKELY_MATCH("No likely library match"),
    LINKED("Linked to library")
}

data class ReconciliationAlbumKey(
    val normalizedArtist: String,
    val normalizedAlbum: String
) {
    val stableKey: String get() = "$normalizedArtist\u0000$normalizedAlbum"
}

data class ReconciliationTrackPresentation(
    val source: HistoricalReconciliationSource,
    val status: ReconciliationTrackStatus,
    val reviewItem: HistoricalReconciliationItem? = null,
    val linkedItem: LinkedHistoricalReconciliation? = null,
    val proposedCandidate: ListeningIdentityReconciliationCandidate? = null,
    val reason: ReconciliationMatchReason,
    val searchText: String,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbum: String,
    val albumKey: ReconciliationAlbumKey
) {
    val sourceId: Long get() = source.identityId
    val isSelectable: Boolean
        get() = status == ReconciliationTrackStatus.REVIEW && proposedCandidate != null
}

data class ReconciliationAlbumPresentation(
    val key: ReconciliationAlbumKey,
    val title: String,
    val artist: String,
    val tracks: List<ReconciliationTrackPresentation>,
    val importedCount: Int,
    val linkedCount: Int,
    val reviewCount: Int,
    val unmatchedCount: Int,
    val historicalPlayCount: Long
)

data class ReconciliationArtistPresentation(
    val key: String,
    val artist: String,
    val albums: List<ReconciliationAlbumPresentation>,
    val tracks: List<ReconciliationTrackPresentation>,
    val importedCount: Int,
    val linkedCount: Int,
    val reviewCount: Int,
    val unmatchedCount: Int,
    val historicalPlayCount: Long
)

data class ReconciliationPreparedDataset(
    val tracks: List<ReconciliationTrackPresentation>
)

fun prepareReconciliationDataset(
    reviewItems: List<HistoricalReconciliationItem>,
    linkedItems: List<LinkedHistoricalReconciliation>
): ReconciliationPreparedDataset {
    val reviewTracks = reviewItems.map { item ->
        val status = if (item.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE) {
            ReconciliationTrackStatus.UNMATCHED
        } else {
            ReconciliationTrackStatus.REVIEW
        }
        val proposed = item.candidates.singleOrNull()
            ?.takeIf { item.disposition == ReconciliationCandidateDisposition.SUGGESTED && !item.hasMoreCandidates }
        presentation(
            source = item.source,
            status = status,
            reviewItem = item,
            linkedItem = null,
            proposedCandidate = proposed,
            reason = reconciliationMatchReason(item)
        )
    }
    val linkedTracks = linkedItems.map { linked ->
        presentation(
            source = linked.source,
            status = ReconciliationTrackStatus.LINKED,
            reviewItem = null,
            linkedItem = linked,
            proposedCandidate = null,
            reason = ReconciliationMatchReason.LINKED
        )
    }
    return ReconciliationPreparedDataset(
        (reviewTracks + linkedTracks).distinctBy(ReconciliationTrackPresentation::sourceId)
    )
}

fun filterAndSortReconciliationTracks(
    dataset: ReconciliationPreparedDataset,
    status: ReconciliationTrackStatus,
    query: String,
    sort: ReconciliationSortOption,
    reviewFilter: ReconciliationReviewFilter = ReconciliationReviewFilter.ALL,
    skippedSourceIds: Set<Long> = emptySet()
): List<ReconciliationTrackPresentation> {
    val search = reconciliationSearchKey(query)
    return dataset.tracks.asSequence()
        .filter { it.status == status }
        .filter { status != ReconciliationTrackStatus.REVIEW || it.sourceId !in skippedSourceIds }
        .filter { search.isBlank() || it.searchText.contains(search) }
        .filter { status != ReconciliationTrackStatus.REVIEW || it.matches(reviewFilter) }
        .sortedWith(reconciliationTrackComparator(sort))
        .toList()
}

fun groupReconciliationAlbums(
    allTracks: List<ReconciliationTrackPresentation>,
    visibleTracks: List<ReconciliationTrackPresentation>,
    sort: ReconciliationSortOption
): List<ReconciliationAlbumPresentation> {
    val allByKey = allTracks.groupBy(ReconciliationTrackPresentation::albumKey)
    return visibleTracks.groupBy(ReconciliationTrackPresentation::albumKey).map { (key, visible) ->
        val all = allByKey.getValue(key)
        val representative = all.minWith(reconciliationTrackComparator(ReconciliationSortOption.TRACK_TITLE))
        ReconciliationAlbumPresentation(
            key = key,
            title = representative.source.album.ifBlank { "Unknown album" },
            artist = representative.source.artist.ifBlank { "Unknown artist" },
            tracks = visible.sortedWith(reconciliationTrackComparator(sort)),
            importedCount = all.size,
            linkedCount = all.count { it.status == ReconciliationTrackStatus.LINKED },
            reviewCount = all.count { it.status == ReconciliationTrackStatus.REVIEW },
            unmatchedCount = all.count { it.status == ReconciliationTrackStatus.UNMATCHED },
            historicalPlayCount = all.sumOf { it.source.metrics.qualifiedPlayCount }
        )
    }.sortedWith(reconciliationAlbumComparator(sort))
}

fun groupReconciliationArtists(
    allTracks: List<ReconciliationTrackPresentation>,
    visibleTracks: List<ReconciliationTrackPresentation>,
    sort: ReconciliationSortOption
): List<ReconciliationArtistPresentation> {
    val allByArtist = allTracks.groupBy(ReconciliationTrackPresentation::normalizedArtist)
    return visibleTracks.groupBy(ReconciliationTrackPresentation::normalizedArtist).map { (key, visible) ->
        val all = allByArtist.getValue(key)
        val representative = all.minWith(reconciliationTrackComparator(ReconciliationSortOption.TRACK_TITLE))
        ReconciliationArtistPresentation(
            key = key,
            artist = representative.source.artist.ifBlank { "Unknown artist" },
            albums = groupReconciliationAlbums(all, visible, sort),
            tracks = visible.sortedWith(reconciliationTrackComparator(sort)),
            importedCount = all.size,
            linkedCount = all.count { it.status == ReconciliationTrackStatus.LINKED },
            reviewCount = all.count { it.status == ReconciliationTrackStatus.REVIEW },
            unmatchedCount = all.count { it.status == ReconciliationTrackStatus.UNMATCHED },
            historicalPlayCount = all.sumOf { it.source.metrics.qualifiedPlayCount }
        )
    }.sortedWith(reconciliationArtistComparator(sort))
}

fun reconciliationMatchReason(item: HistoricalReconciliationItem): ReconciliationMatchReason {
    if (item.disposition == ReconciliationCandidateDisposition.NO_CANDIDATE) {
        return ReconciliationMatchReason.NO_LIKELY_MATCH
    }
    if (item.disposition == ReconciliationCandidateDisposition.AMBIGUOUS ||
        item.candidates.size != 1 || item.hasMoreCandidates
    ) {
        return ReconciliationMatchReason.MULTIPLE_MATCHES
    }
    val candidate = item.candidates.single()
    val evidence = candidate.evidence
    if (evidence.category == ReconciliationCandidateCategory.VERSION_SENSITIVE ||
        evidence.versionRelation == ReconciliationVersionRelation.DIFFERENT ||
        (evidence.versionRelation == ReconciliationVersionRelation.SAME &&
            evidence.titleRelation == ReconciliationMetadataRelation.FUZZY) ||
        evidence.titleRelation == ReconciliationMetadataRelation.VERSION_VARIANT
    ) {
        return ReconciliationMatchReason.VERSION_FORMATTING
    }
    if (evidence.category == ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE) {
        return ReconciliationMatchReason.INCOMPLETE_METADATA
    }
    if (evidence.category == ReconciliationCandidateCategory.CANONICAL_METADATA) {
        return ReconciliationMatchReason.TITLE_FORMATTING
    }
    if (evidence.titleRelation == ReconciliationMetadataRelation.FUZZY ||
        evidence.category == ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT
    ) {
        val importedTitle = item.source.title
        val localTitle = candidate.target.title
        if (reconciliationPunctuationKey(importedTitle) == reconciliationPunctuationKey(localTitle)) {
            return ReconciliationMatchReason.PUNCTUATION
        }
        if (candidateAccentNormalize(importedTitle) == candidateAccentNormalize(localTitle)) {
            return ReconciliationMatchReason.ACCENT_DIACRITIC
        }
        return ReconciliationMatchReason.SIMILAR_TITLE
    }
    return when (evidence.category) {
        ReconciliationCandidateCategory.AMBIGUOUS -> ReconciliationMatchReason.MULTIPLE_MATCHES
        ReconciliationCandidateCategory.STRONG_METADATA -> ReconciliationMatchReason.METADATA_MATCH
        ReconciliationCandidateCategory.CANONICAL_METADATA -> ReconciliationMatchReason.TITLE_FORMATTING
        ReconciliationCandidateCategory.TYPOGRAPHY_VARIANT -> ReconciliationMatchReason.SIMILAR_TITLE
        ReconciliationCandidateCategory.INCOMPLETE_EVIDENCE -> ReconciliationMatchReason.INCOMPLETE_METADATA
        ReconciliationCandidateCategory.VERSION_SENSITIVE -> ReconciliationMatchReason.VERSION_FORMATTING
    }
}

fun reconciliationSearchKey(value: String): String = candidateAccentNormalize(value)
    .replace(Regex("[\\p{Z}\\s]+"), " ")
    .trim()

private fun presentation(
    source: HistoricalReconciliationSource,
    status: ReconciliationTrackStatus,
    reviewItem: HistoricalReconciliationItem?,
    linkedItem: LinkedHistoricalReconciliation?,
    proposedCandidate: ListeningIdentityReconciliationCandidate?,
    reason: ReconciliationMatchReason
): ReconciliationTrackPresentation {
    val artist = reconciliationSearchKey(source.artist)
    val album = reconciliationSearchKey(source.album)
    return ReconciliationTrackPresentation(
        source = source,
        status = status,
        reviewItem = reviewItem,
        linkedItem = linkedItem,
        proposedCandidate = proposedCandidate,
        reason = reason,
        searchText = listOf(source.title, source.artist, source.album)
            .joinToString("\u0000", transform = ::reconciliationSearchKey),
        normalizedTitle = reconciliationSearchKey(source.title),
        normalizedArtist = artist,
        normalizedAlbum = album,
        albumKey = ReconciliationAlbumKey(artist, album)
    )
}

private fun ReconciliationTrackPresentation.matches(filter: ReconciliationReviewFilter): Boolean = when (filter) {
    ReconciliationReviewFilter.ALL -> true
    ReconciliationReviewFilter.TITLE_FORMATTING -> reason == ReconciliationMatchReason.PUNCTUATION ||
        reason == ReconciliationMatchReason.TITLE_FORMATTING ||
        reason == ReconciliationMatchReason.VERSION_FORMATTING
    ReconciliationReviewFilter.ACCENT_DIACRITIC -> reason == ReconciliationMatchReason.ACCENT_DIACRITIC
    ReconciliationReviewFilter.SIMILAR_TITLE -> reason == ReconciliationMatchReason.SIMILAR_TITLE
    ReconciliationReviewFilter.AMBIGUOUS -> reason == ReconciliationMatchReason.MULTIPLE_MATCHES
}

private fun reconciliationPunctuationKey(value: String): String = candidateCanonicalNormalize(value)
    .replace(Regex("[\\p{P}\\p{S}]+"), " ")
    .replace(Regex("[\\p{Z}\\s]+"), " ")
    .trim()

private fun reconciliationTrackComparator(sort: ReconciliationSortOption) = when (sort) {
    ReconciliationSortOption.HISTORICAL_PLAYS -> compareByDescending<ReconciliationTrackPresentation> {
        it.source.metrics.qualifiedPlayCount
    }.thenBy { it.normalizedTitle }.thenBy { it.normalizedArtist }
        .thenBy { it.normalizedAlbum }.thenBy { it.sourceId }
    ReconciliationSortOption.TRACK_TITLE -> compareBy<ReconciliationTrackPresentation> {
        it.normalizedTitle
    }.thenBy { it.normalizedArtist }.thenBy { it.normalizedAlbum }.thenBy { it.sourceId }
    ReconciliationSortOption.ARTIST -> compareBy<ReconciliationTrackPresentation> {
        it.normalizedArtist
    }.thenBy { it.normalizedAlbum }.thenBy { it.normalizedTitle }.thenBy { it.sourceId }
    ReconciliationSortOption.ALBUM -> compareBy<ReconciliationTrackPresentation> {
        it.normalizedAlbum
    }.thenBy { it.normalizedArtist }.thenBy { it.normalizedTitle }.thenBy { it.sourceId }
}

private fun reconciliationAlbumComparator(sort: ReconciliationSortOption) = when (sort) {
    ReconciliationSortOption.HISTORICAL_PLAYS -> compareByDescending<ReconciliationAlbumPresentation> {
        it.historicalPlayCount
    }.thenBy { it.key.normalizedAlbum }.thenBy { it.key.normalizedArtist }
    ReconciliationSortOption.ARTIST -> compareBy<ReconciliationAlbumPresentation> {
        it.key.normalizedArtist
    }.thenBy { it.key.normalizedAlbum }
    ReconciliationSortOption.TRACK_TITLE,
    ReconciliationSortOption.ALBUM -> compareBy<ReconciliationAlbumPresentation> {
        it.key.normalizedAlbum
    }.thenBy { it.key.normalizedArtist }
}

private fun reconciliationArtistComparator(sort: ReconciliationSortOption) = when (sort) {
    ReconciliationSortOption.HISTORICAL_PLAYS -> compareByDescending<ReconciliationArtistPresentation> {
        it.historicalPlayCount
    }.thenBy(ReconciliationArtistPresentation::key)
    ReconciliationSortOption.TRACK_TITLE,
    ReconciliationSortOption.ARTIST,
    ReconciliationSortOption.ALBUM -> compareBy(ReconciliationArtistPresentation::key)
}
