package io.github.rsgarrido.sazanami.data.importing

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class ListeningImportDedupePlannerTest {
    @Test fun duplicateOrdinalsPreserveMultiplicityAndRestartPerFingerprint() {
        val assigner = ListeningImportDuplicateOrdinalAssigner()
        assertEquals(
            listOf(key('a', 0), key('a', 1), key('a', 2), key('b', 0), key('a', 3)),
            listOf(fp('a'), fp('a'), fp('a'), fp('b'), fp('a')).map(assigner::assign)
        )
    }

    @Test fun multiFileSelectionUsesMaximumMultiplicity() {
        val selection = plan(
            listOf('a', 'a', 'b'),
            listOf('a', 'a', 'b', 'c')
        )
        assertEquals(7, selection.summary.importableMusicOccurrencesAcrossFiles)
        assertEquals(4, selection.summary.selectedMusicOccurrences)
        assertEquals(3, selection.summary.overlappingOccurrencesSuppressed)
        assertEquals(
            listOf(key('a', 0), key('a', 1), key('b', 0), key('c', 0)),
            selection.occurrenceKeyChunks(10).flatten().toList()
        )
    }

    @Test fun maximumRuleHandlesEitherFileThreeFilesDisjointAndRepeatedFile() {
        assertEquals(3, plan(listOf('a', 'a', 'a'), listOf('a')).summary.selectedMusicOccurrences)
        assertEquals(3, plan(listOf('a'), listOf('a', 'a', 'a')).summary.selectedMusicOccurrences)
        assertEquals(
            4,
            plan(listOf('a', 'a'), listOf('a'), listOf('a', 'a', 'a', 'a'))
                .summary.selectedMusicOccurrences
        )
        assertEquals(4, plan(listOf('a', 'b'), listOf('c', 'd')).summary.selectedMusicOccurrences)
        val repeated = plan(listOf('a', 'a', 'b'), listOf('a', 'a', 'b'))
        assertEquals(3, repeated.summary.selectedMusicOccurrences)
        assertEquals(3, repeated.summary.overlappingOccurrencesSuppressed)
    }

    @Test fun persistedMultisetComparisonStreamsNewAndAlreadyDecisions() = runBlocking {
        val selection = plan(listOf('a', 'a', 'a', 'b', 'c'))
        val persisted = setOf(key('a', 0), key('a', 1), key('b', 0))
        val decisions = mutableListOf<ImportOccurrenceDecision>()
        val result = ListeningImportDedupePlanner(2).plan(
            sourceProfileId = 1,
            selection = selection,
            findExisting = { _, keys -> keys.filterTo(mutableSetOf()) { it in persisted } },
            onDecision = decisions::add
        )
        assertEquals(5, result.totalImportableRecords)
        assertEquals(3, result.alreadyImportedOccurrences)
        assertEquals(2, result.newOccurrences)
        assertEquals(
            listOf(key('a', 2), key('c', 0)),
            decisions.filter { it.disposition == ImportDedupeDisposition.NEW }.map { it.key }
        )
    }

    @Test fun exactRepeatIsAllExistingAndSourceProfilesRemainIsolated() = runBlocking {
        val selection = plan(listOf('a', 'b'))
        val persistedInProfileA = selection.occurrenceKeyChunks(10).flatten().toSet()
        suspend fun forProfile(profileId: Long): ListeningImportDedupePlan =
            ListeningImportDedupePlanner().plan(profileId, selection, { id, _ ->
                if (id == 1L) persistedInProfileA else emptySet()
            })
        assertEquals(0, forProfile(1).newOccurrences)
        assertEquals(2, forProfile(1).alreadyImportedOccurrences)
        assertEquals(2, forProfile(2).newOccurrences)
        assertEquals(0, forProfile(2).alreadyImportedOccurrences)
    }

    @Test fun lookupIsBoundedRatherThanOneQueryPerOccurrence() = runBlocking {
        val selection = plan((0 until 1_201).map { hexChar(it) })
        var lookups = 0
        val result = ListeningImportDedupePlanner(500).plan(1, selection, { _, keys ->
            lookups++
            require(keys.size <= 500)
            emptySet()
        })
        assertEquals(3, lookups)
        assertEquals(1_201, result.newOccurrences)
    }

    private fun plan(vararg files: List<Char>): ListeningImportSelectionPlan =
        ListeningImportSelectionPlanner().plan(files.map { values -> values.asSequence().map(::fp) })

    private fun fp(value: Char) = ListeningImportFingerprint(1, value.toString().repeat(64))
    private fun key(value: Char, ordinal: Int) = ImportOccurrenceKey(1, value.toString().repeat(64), ordinal)
    private fun hexChar(index: Int): Char = "0123456789abcdef"[index % 16]
}

