package io.github.rsgarrido.sazanami.data.importing

data class ImportOccurrenceKey(
    val fingerprintVersion: Int,
    val fingerprint: String,
    val duplicateOrdinal: Int
) {
    init {
        require(fingerprintVersion > 0)
        require(fingerprint.isNotBlank())
        require(duplicateOrdinal >= 0)
    }
}

enum class ImportDedupeDisposition {
    NEW,
    ALREADY_IMPORTED
}

data class ImportOccurrenceDecision(
    val key: ImportOccurrenceKey,
    val disposition: ImportDedupeDisposition
)

data class ListeningImportSelectionSummary(
    val importableMusicOccurrencesAcrossFiles: Long,
    val selectedMusicOccurrences: Long,
    val overlappingOccurrencesSuppressed: Long,
    val distinctFingerprints: Int
)

data class ListeningImportDedupePlan(
    val totalImportableRecords: Long,
    val overlappingOccurrencesSuppressed: Long,
    val newOccurrences: Long,
    val alreadyImportedOccurrences: Long
)

/** Assigns ordinals in source order and preserves indistinguishable legitimate occurrences. */
class ListeningImportDuplicateOrdinalAssigner {
    private val nextOrdinals = HashMap<ListeningImportFingerprint, Int>()

    fun assign(fingerprint: ListeningImportFingerprint): ImportOccurrenceKey {
        val ordinal = nextOrdinals[fingerprint] ?: 0
        check(ordinal < Int.MAX_VALUE) { "Duplicate ordinal overflow." }
        nextOrdinals[fingerprint] = ordinal + 1
        return ImportOccurrenceKey(
            fingerprint.fingerprintVersion,
            fingerprint.fingerprint,
            ordinal
        )
    }
}

/**
 * Combines selected files as overlapping history views. The desired count for each fingerprint is
 * the maximum count in any one file, never the sum across files.
 *
 * Memory is O(selection distinct fingerprints + current-file distinct fingerprints). Records are
 * consumed incrementally and are not retained.
 */
class ListeningImportSelectionPlanner {
    fun plan(files: Iterable<Sequence<ListeningImportFingerprint>>): ListeningImportSelectionPlan {
        val builder = ListeningImportSelectionBuilder()
        files.forEach { file ->
            builder.beginFile()
            file.forEach(builder::accept)
            builder.endFile()
        }
        return builder.build()
    }
}

/** Incremental entry point for parsers that deliver one normalized record at a time. */
class ListeningImportSelectionBuilder {
    private var maximumCounts = LinkedHashMap<FingerprintKey, Int>()
    private var ownerFileIndices = LinkedHashMap<FingerprintKey, Int>()
    private var currentFileCounts: LinkedHashMap<FingerprintKey, Int>? = null
    private var inputOccurrences = 0L
    private var fileCount = 0
    private var built = false

    fun beginFile() {
        check(!built) { "This selection has already been built." }
        check(currentFileCounts == null) { "The previous source file is still active." }
        currentFileCounts = LinkedHashMap()
    }

    fun accept(fingerprint: ListeningImportFingerprint) {
        val fileCounts = checkNotNull(currentFileCounts) { "No source file is active." }
        inputOccurrences = Math.addExact(inputOccurrences, 1L)
        val key = FingerprintKey(fingerprint.fingerprintVersion, fingerprint.fingerprint)
        fileCounts[key] = Math.addExact(fileCounts[key] ?: 0, 1)
    }

    fun endFile() {
        val fileCounts = checkNotNull(currentFileCounts) { "No source file is active." }
        if (fileCount == 0) {
            maximumCounts = fileCounts
            fileCounts.keys.forEach { ownerFileIndices[it] = fileCount }
        } else {
            fileCounts.forEach { (key, count) ->
                if (count > (maximumCounts[key] ?: 0)) {
                    maximumCounts[key] = count
                    ownerFileIndices[key] = fileCount
                }
            }
        }
        currentFileCounts = null
        fileCount++
    }

    fun build(): ListeningImportSelectionPlan {
        check(!built) { "This selection has already been built." }
        check(currentFileCounts == null) { "A source file is still active." }
        require(fileCount > 0) { "At least one source file is required." }
        val selected = maximumCounts.values.sumOf(Int::toLong)
        built = true
        return ListeningImportSelectionPlan(
            desiredCounts = maximumCounts,
            ownerFileIndices = ownerFileIndices,
            summary = ListeningImportSelectionSummary(
                importableMusicOccurrencesAcrossFiles = inputOccurrences,
                selectedMusicOccurrences = selected,
                overlappingOccurrencesSuppressed = inputOccurrences - selected,
                distinctFingerprints = maximumCounts.size
            )
        )
    }
}

class ListeningImportSelectionPlan internal constructor(
    private val desiredCounts: Map<FingerprintKey, Int>,
    private val ownerFileIndices: Map<FingerprintKey, Int>,
    val summary: ListeningImportSelectionSummary
) {
    /** The first selected file having the maximum multiplicity owns this fingerprint's occurrences. */
    fun isOccurrenceOwner(fileIndex: Int, fingerprint: ListeningImportFingerprint): Boolean =
        ownerFileIndices[FingerprintKey(fingerprint.fingerprintVersion, fingerprint.fingerprint)] == fileIndex

    fun occurrenceKeyChunks(maxKeys: Int): Sequence<List<ImportOccurrenceKey>> {
        require(maxKeys > 0)
        return sequence {
            var chunk = ArrayList<ImportOccurrenceKey>(maxKeys)
            desiredCounts.forEach { (fingerprint, count) ->
                repeat(count) { ordinal ->
                    chunk += ImportOccurrenceKey(
                        fingerprint.version,
                        fingerprint.value,
                        ordinal
                    )
                    if (chunk.size == maxKeys) {
                        yield(chunk)
                        chunk = ArrayList(maxKeys)
                    }
                }
            }
            if (chunk.isNotEmpty()) yield(chunk)
        }
    }
}

/** Performs bounded lookups and optionally streams per-occurrence decisions to Session 3 code. */
class ListeningImportDedupePlanner(
    private val lookupBatchSize: Int = DEFAULT_LOOKUP_BATCH_SIZE
) {
    init {
        require(lookupBatchSize in 1..MAX_LOOKUP_BATCH_SIZE)
    }

    suspend fun plan(
        sourceProfileId: Long,
        selection: ListeningImportSelectionPlan,
        findExisting: suspend (sourceProfileId: Long, keys: List<ImportOccurrenceKey>) -> Set<ImportOccurrenceKey>,
        onDecision: suspend (ImportOccurrenceDecision) -> Unit = {}
    ): ListeningImportDedupePlan {
        require(sourceProfileId > 0)
        var newCount = 0L
        var existingCount = 0L
        selection.occurrenceKeyChunks(lookupBatchSize).forEach { chunk ->
            val requested = chunk.toHashSet()
            val existing = findExisting(sourceProfileId, chunk)
            require(requested.containsAll(existing)) { "Lookup returned an unrequested occurrence key." }
            chunk.forEach { key ->
                val disposition = if (key in existing) {
                    existingCount++
                    ImportDedupeDisposition.ALREADY_IMPORTED
                } else {
                    newCount++
                    ImportDedupeDisposition.NEW
                }
                onDecision(ImportOccurrenceDecision(key, disposition))
            }
        }
        check(newCount + existingCount == selection.summary.selectedMusicOccurrences)
        return ListeningImportDedupePlan(
            totalImportableRecords = selection.summary.selectedMusicOccurrences,
            overlappingOccurrencesSuppressed = selection.summary.overlappingOccurrencesSuppressed,
            newOccurrences = newCount,
            alreadyImportedOccurrences = existingCount
        )
    }

    companion object {
        const val DEFAULT_LOOKUP_BATCH_SIZE = 500
        const val MAX_LOOKUP_BATCH_SIZE = 900
    }
}

internal data class FingerprintKey(val version: Int, val value: String)
