package com.example.cdplaya.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.fail
import org.junit.Test

class ListeningIdentityReconciliationBackupValidatorTest {
    @Test
    fun validManyToOneReconciliation_isAccepted() {
        val history = validHistory()

        assertEquals(history, ListeningHistoryBackupValidator.validate(history))
    }

    @Test
    fun missingSelfDuplicateRoleAndEligibilityViolations_areRejected() {
        val valid = validHistory()
        val link = valid.reconciliations.first()
        val second = valid.reconciliations.last()
        val cases = listOf(
            valid.copy(reconciliations = listOf(link.copy(sourceIdentityBackupId = 99L))),
            valid.copy(reconciliations = listOf(link.copy(targetIdentityBackupId = 99L))),
            valid.copy(reconciliations = listOf(link.copy(targetIdentityBackupId = link.sourceIdentityBackupId))),
            valid.copy(reconciliations = listOf(link, link.copy(targetIdentityBackupId = 4L))),
            valid.copy(reconciliations = listOf(link.copy(targetIdentityBackupId = 2L), second.copy(sourceIdentityBackupId = 2L))),
            valid.copy(reconciliations = listOf(link.copy(targetIdentityBackupId = 2L), second.copy(sourceIdentityBackupId = 2L, targetIdentityBackupId = 1L))),
            valid.copy(bindings = valid.bindings + binding(9L, 1L)),
            valid.copy(events = valid.events.filterNot { it.trackIdentityBackupId == 1L }),
            valid.copy(reconciliations = listOf(link.copy(targetIdentityBackupId = 4L))),
            valid.copy(reconciliations = listOf(link.copy(reconciledAt = -1L)))
        )

        cases.forEachIndexed { index, invalid ->
            expectInvalid("case $index") {
                ListeningHistoryBackupValidator.validate(
                    invalid.copy(summary = invalid.recordsSummary())
                )
            }
        }
    }

    private fun validHistory(): BackupListeningHistoryV2 {
        val identities = (1L..4L).map { id ->
            BackupListeningTrackIdentity(
                backupIdentityId = id,
                titleSnapshot = "Fictional $id",
                artistSnapshot = "Fictional Artist",
                albumSnapshot = "Fictional Album",
                albumArtistSnapshot = null,
                durationMsSnapshot = 180_000L,
                normalizedTitle = "fictional $id",
                normalizedArtist = "fictional artist",
                normalizedAlbum = "fictional album",
                metadataKey = null,
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 2L
            )
        }
        val history = BackupListeningHistoryV2(
            identities = identities,
            bindings = listOf(binding(1L, 3L)),
            events = listOf(importedEvent("event-a", 1L), importedEvent("event-b", 2L)),
            reconciliations = listOf(
                BackupListeningIdentityReconciliation(1L, 3L, 100L),
                BackupListeningIdentityReconciliation(2L, 3L, 101L)
            )
        )
        return history.copy(summary = history.recordsSummary())
    }

    private fun binding(id: Long, identityId: Long) = BackupLocalTrackBinding(
        backupBindingId = id,
        trackIdentityBackupId = identityId,
        referenceKey = "binding-$id",
        mediaStoreId = id,
        volumeName = "external",
        contentUri = "content://fictional/$id",
        relativePath = "Music/Fictional/",
        displayName = "track-$id.flac",
        absolutePath = null,
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L,
        durationMsSnapshot = 180_000L,
        legacyStableKey = null,
        portableKey = "portable-$id",
        portableKeyVersion = 1,
        firstSeenAt = 1L,
        lastSeenAt = 2L,
        missingSince = 3L
    )

    private fun importedEvent(uuid: String, identityId: Long) = BackupListeningEvent(
        eventUuid = uuid,
        source = "spotify_import",
        trackIdentityBackupId = identityId,
        localTrackBindingBackupId = null,
        playbackSessionId = null,
        startedAt = null,
        endedAt = 50L + identityId,
        listenedMs = 30_000L,
        trackDurationMs = null,
        qualifiedAsPlay = true,
        qualificationReason = "time_threshold",
        qualificationRuleVersion = 1,
        endReason = null,
        sourceEventKey = "source-$identityId",
        importBatchId = null,
        createdAt = 60L + identityId,
        attributionAt = 50L + identityId,
        timestampEvidence = "source_end_only",
        qualificationPolicy = "spotify",
        completionClassification = "none",
        publicationState = "import_published"
    )

    private fun expectInvalid(label: String, block: () -> Unit) {
        try {
            block()
            fail("Expected invalid backup for $label")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
