package io.github.rsgarrido.sazanami.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class ListeningHistoryBackupValidatorTest {
    @Test
    fun completeFixture_roundTripsEnumsOrderingAndSummary() {
        val fixture = validHistory()
        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(createdAt = 1L, canonicalListeningHistory = fixture)
            )
        ).canonicalListeningHistory!!

        assertEquals(fixture, decoded)
        assertEquals(3L, decoded.summary.identityCount)
        assertEquals(2L, decoded.summary.bindingCount)
        assertEquals(1L, decoded.summary.baselineCount)
        assertEquals(4L, decoded.summary.eventCount)
        assertEquals(2L, decoded.summary.qualifiedEventCount)
        assertEquals(2L, decoded.summary.nonQualifiedEventCount)
        assertEquals(100L, decoded.summary.earliestDetailedEventAt)
        assertEquals(400L, decoded.summary.latestDetailedEventAt)
        assertEquals(
            listOf("cdplaya", "spotify_import", "lastfm_import", "cdplaya"),
            decoded.events.map { it.source }
        )
    }

    @Test
    fun v6AggregateHistory_becomesSeparateBaselinesWithoutSyntheticEvents() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 6,
              "createdAt": 999,
              "listeningHistory": [
                {"songKey":"one","title":"Same","artist":"Artist","album":"Album","duration":1000,"playCount":3,"firstPlayedAt":10,"lastPlayedAt":30},
                {"songKey":"two","title":"Same","artist":"Artist","album":"Album","duration":1000,"playCount":4,"firstPlayedAt":20,"lastPlayedAt":40}
              ]
            }
            """.trimIndent()
        )
        val history = decoded.canonicalListeningHistory!!

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(2, history.identities.size)
        assertEquals(2, history.bindings.size)
        assertEquals(listOf(3, 4), history.baselines.map { it.historicalPlayCount })
        assertEquals(listOf(10L, 20L), history.baselines.map { it.firstKnownPlayedAt })
        assertEquals(listOf(30L, 40L), history.baselines.map { it.lastKnownPlayedAt })
        assertTrue(history.events.isEmpty())
        assertTrue(history.identities[0].backupIdentityId != history.identities[1].backupIdentityId)
        assertTrue(history.bindings[0].referenceKey != history.bindings[1].referenceKey)
    }

    @Test
    fun version7CanonicalSection_isNotRebuiltFromLegacyCompatibilityRows() {
        val canonical = validHistory()
        val legacy = BackupListeningHistoryEntry(
            songKey = "stale",
            title = "Stale",
            artist = "Artist",
            album = "Album",
            duration = 1_000,
            playCount = 999,
            firstPlayedAt = 1,
            lastPlayedAt = 2
        )
        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(
                    createdAt = 1,
                    listeningHistory = listOf(legacy),
                    canonicalListeningHistory = canonical
                )
            )
        )

        assertEquals(canonical, decoded.canonicalListeningHistory)
        assertEquals(listOf(legacy), decoded.listeningHistory)
    }

    @Test
    fun version8NativeHistoryAndRatingMigrateDirectlyWithoutImportProvenance() {
        val full = validHistory()
        val nativeEvent = full.events.first()
        val history = full.copy(
            identities = full.identities.filter { it.backupIdentityId == nativeEvent.trackIdentityBackupId },
            bindings = full.bindings.filter { it.backupBindingId == nativeEvent.localTrackBindingBackupId },
            baselines = emptyList(),
            events = listOf(nativeEvent),
            importSources = emptyList(), importBatches = emptyList(), externalTrackIds = emptyList(),
            importedEventEvidence = emptyList(), batchEventObservations = emptyList()
        ).let { it.copy(summary = it.recordsSummary()) }
        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(
                    schemaVersion = 8,
                    createdAt = 999,
                    canonicalListeningHistory = history,
                    songRatings = BackupSongRatings(
                        entries = listOf(BackupSongRating(nativeEvent.trackIdentityBackupId, 4, 123, 456))
                    )
                )
            )
        )
        val migrated = requireNotNull(decoded.canonicalListeningHistory)

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, decoded.schemaVersion)
        assertEquals(nativeEvent, migrated.events.single())
        assertEquals(4, decoded.songRatings.entries.single().rating)
        assertEquals(123L, decoded.songRatings.entries.single().ratedAt)
        assertEquals(456L, decoded.songRatings.entries.single().updatedAt)
        assertTrue(migrated.importSources.isEmpty())
        assertTrue(migrated.importBatches.isEmpty())
        assertTrue(migrated.externalTrackIds.isEmpty())
        assertTrue(migrated.importedEventEvidence.isEmpty())
        assertTrue(migrated.batchEventObservations.isEmpty())
    }

    @Test
    fun validator_rejectsEveryRequiredStructuralFailure() {
        val valid = validHistory()
        val mutations = listOf<Pair<String, (BackupListeningHistoryV2) -> BackupListeningHistoryV2>>(
            "format" to { it.copy(formatVersion = 1) },
            "identity" to { it.copy(identities = it.identities + it.identities.first()) },
            "binding id" to { it.copy(bindings = it.bindings + it.bindings.first()) },
            "binding identity" to { it.replaceBinding(0) { b -> b.copy(trackIdentityBackupId = 99) } },
            "baseline identity" to { it.replaceBaseline { b -> b.copy(trackIdentityBackupId = 99) } },
            "event identity" to { it.replaceEvent(0) { e -> e.copy(trackIdentityBackupId = 99) } },
            "event binding" to { it.replaceEvent(0) { e -> e.copy(localTrackBindingBackupId = 99) } },
            "binding ownership" to {
                it.replaceEvent(0) { e -> e.copy(trackIdentityBackupId = 3) }
            },
            "event UUID" to { it.replaceEvent(1) { e -> e.copy(eventUuid = it.events[0].eventUuid) } },
            "session" to {
                it.replaceEvent(1) { e -> e.copy(playbackSessionId = it.events[0].playbackSessionId) }
            },
            "source key" to {
                it.replaceEvent(3) { e ->
                    e.copy(source = it.events[0].source, sourceEventKey = it.events[0].sourceEventKey)
                }
            },
            "listened" to { it.replaceEvent(0) { e -> e.copy(listenedMs = -1) } },
            "timestamps" to { it.replaceEvent(0) { e -> e.copy(endedAt = requireNotNull(e.startedAt) - 1) } },
            "source enum" to { it.replaceEvent(0) { e -> e.copy(source = "future") } },
            "qualification enum" to {
                it.replaceEvent(0) { e -> e.copy(qualificationReason = "future") }
            },
            "timestamp evidence" to { it.replaceEvent(0) { e -> e.copy(timestampEvidence = "future") } },
            "qualification policy" to { it.replaceEvent(0) { e -> e.copy(qualificationPolicy = "future") } },
            "completion classification" to { it.replaceEvent(0) { e -> e.copy(completionClassification = "future") } },
            "pending publication" to { it.replaceEvent(1) { e -> e.copy(publicationState = "import_pending") } },
            "end enum" to { it.replaceEvent(0) { e -> e.copy(endReason = "future") } },
            "rule" to { it.replaceEvent(0) { e -> e.copy(qualificationRuleVersion = 0) } },
            "play count" to { it.replaceBaseline { b -> b.copy(historicalPlayCount = 0) } },
            "summary" to { it.copy(summary = it.summary.copy(eventCount = 99)) }
        )

        mutations.forEach { (label, mutate) ->
            expectInvalid(label) { ListeningHistoryBackupValidator.validate(mutate(valid)) }
        }
    }

    @Test
    fun importLedgerValidation_rejectsDuplicateAndMissingDurableReferences() {
        val base = validHistory()
        val source = BackupListeningImportSource(1, "profile", "spotify_import", "Profile", "digest", 1, 2)
        val batch = BackupListeningImportBatch(
            1, "batch", 1, "published", 1, "spotify", 1, 1, 2, 200, 200,
            1, 1, 0, 0, 0, 1, 0, 0, 0, null, "test"
        )
        val valid = base.copy(
            importSources = listOf(source), importBatches = listOf(batch),
            externalTrackIds = listOf(BackupListeningTrackExternalId(2, "spotify_import", "catalog", 1, 2)),
            importedEventEvidence = listOf(BackupImportedListeningEventEvidence(
                "uuid-2", 1, 1, "fingerprint", 0, null, null, "false", "exact")),
            batchEventObservations = listOf(BackupListeningImportBatchEvent(1, "uuid-2"))
        )
        ListeningHistoryBackupValidator.validate(valid)

        expectInvalid("duplicate profile") {
            ListeningHistoryBackupValidator.validate(valid.copy(importSources = listOf(source, source.copy(backupSourceProfileId = 2))))
        }
        expectInvalid("missing batch profile") {
            ListeningHistoryBackupValidator.validate(valid.copy(importBatches = listOf(batch.copy(sourceProfileBackupId = 99))))
        }
        expectInvalid("duplicate external") {
            ListeningHistoryBackupValidator.validate(valid.copy(externalTrackIds = valid.externalTrackIds + valid.externalTrackIds.single()))
        }
        expectInvalid("missing evidence event") {
            ListeningHistoryBackupValidator.validate(valid.copy(importedEventEvidence = listOf(valid.importedEventEvidence.single().copy(eventUuid = "missing"))))
        }
        expectInvalid("duplicate link") {
            ListeningHistoryBackupValidator.validate(valid.copy(batchEventObservations = valid.batchEventObservations + valid.batchEventObservations.single()))
        }
        expectInvalid("native import profile") {
            ListeningHistoryBackupValidator.validate(valid.copy(importSources = listOf(source.copy(sourceType = "cdplaya"))))
        }
        expectInvalid("native external ID") {
            ListeningHistoryBackupValidator.validate(valid.copy(externalTrackIds = listOf(valid.externalTrackIds.single().copy(sourceType = "cdplaya"))))
        }
        expectInvalid("batch policy ownership") {
            ListeningHistoryBackupValidator.validate(valid.copy(importBatches = listOf(batch.copy(qualificationPolicy = "lastfm"))))
        }
        expectInvalid("batch event source ownership") {
            ListeningHistoryBackupValidator.validate(valid.copy(batchEventObservations = listOf(
                BackupListeningImportBatchEvent(1, "uuid-3")
            )))
        }
        expectInvalid("batch evidence profile ownership") {
            ListeningHistoryBackupValidator.validate(valid.copy(
                importSources = listOf(
                    source,
                    source.copy(backupSourceProfileId = 2, stableUuid = "other-profile", accountIdentityDigest = null)
                ),
                importedEventEvidence = listOf(valid.importedEventEvidence.single().copy(sourceProfileBackupId = 2))
            ))
        }
        expectInvalid("event observed by multiple profiles") {
            val otherSource = source.copy(
                backupSourceProfileId = 2, stableUuid = "other-profile", accountIdentityDigest = null
            )
            val otherBatch = batch.copy(
                backupBatchId = 2, stableUuid = "other-batch", sourceProfileBackupId = 2
            )
            ListeningHistoryBackupValidator.validate(valid.copy(
                importSources = listOf(source, otherSource),
                importBatches = listOf(batch, otherBatch),
                batchEventObservations = listOf(
                    BackupListeningImportBatchEvent(1, "uuid-2"),
                    BackupListeningImportBatchEvent(2, "uuid-2")
                )
            ))
        }
    }

    @Test
    fun backup9SemanticValidationRejectsNativeAndImportedOwnershipMismatches() {
        val valid = validHistory()
        val mutations = listOf<Pair<String, (BackupListeningHistoryV2) -> BackupListeningHistoryV2>>(
            "native publication" to { it.replaceEvent(0) { event -> event.copy(publicationState = "import_published") } },
            "native timestamp evidence" to { it.replaceEvent(0) { event -> event.copy(timestampEvidence = "source_end_only") } },
            "native attribution" to { it.replaceEvent(0) { event -> event.copy(attributionAt = event.endedAt!!) } },
            "native policy" to { it.replaceEvent(0) { event -> event.copy(qualificationPolicy = "spotify") } },
            "native source completion" to { it.replaceEvent(0) { event -> event.copy(completionClassification = "source_documented_natural") } },
            "native missing completion" to { it.replaceEvent(0) { event -> event.copy(completionClassification = "none") } },
            "import native publication" to { it.replaceEvent(1) { event -> event.copy(publicationState = "native") } },
            "import policy" to { it.replaceEvent(1) { event -> event.copy(qualificationPolicy = "lastfm") } },
            "import native completion" to { it.replaceEvent(1) { event -> event.copy(completionClassification = "native_natural") } }
        )

        mutations.forEach { (label, mutate) ->
            expectInvalid(label) { ListeningHistoryBackupValidator.validate(mutate(valid)) }
        }
    }

    private fun validHistory(): BackupListeningHistoryV2 {
        val identities = listOf(
            identity(1, "Baseline only"),
            identity(2, "Detailed only"),
            identity(3, "Unicode — “same” 🎵")
        )
        val bindings = listOf(
            binding(10, 2, "reference:one", missingSince = null),
            binding(11, 2, "reference:missing", missingSince = 90)
        )
        val baselines = listOf(
            BackupLegacyListeningBaseline(1, 7, 1, 90, "legacy:one", 99)
        )
        val events = listOf(
            event("uuid-1", 2, 100, "cdplaya", 10, true, "natural_end", "natural_end", "session-1", "source-1"),
            event("uuid-2", 2, 200, "spotify_import", 11, false, "none", "transition", null, "source-1"),
            event("uuid-3", 3, 300, "lastfm_import", null, true, "time_threshold", "stopped", "session-3", null),
            event("uuid-4", 3, 400, "cdplaya", null, false, "none", "error", null, "source-4", listenedMs = 2_000, duration = 1_000)
        )
        return BackupListeningHistoryV2(
            identities = identities,
            bindings = bindings,
            baselines = baselines,
            events = events
        ).let { it.copy(summary = it.recordsSummary()) }
    }

    private fun identity(id: Long, title: String) = BackupListeningTrackIdentity(
        id, title, "Artist", "Album", null, 1_000, title.lowercase(), "artist", "album",
        "metadata:$id", 1, 1, 2
    )

    private fun binding(id: Long, identityId: Long, key: String, missingSince: Long?) =
        BackupLocalTrackBinding(
            id, identityId, key, id, "external", "content://$id", "Music/", "song-$id.flac",
            "/private/song-$id.flac", 42, 7, 1_000, "legacy-$id", "portable-$id", 1,
            1, 2, missingSince
        )

    private fun event(
        uuid: String,
        identityId: Long,
        startedAt: Long,
        source: String,
        bindingId: Long?,
        qualified: Boolean,
        qualification: String,
        endReason: String,
        session: String?,
        sourceKey: String?,
        listenedMs: Long = 500,
        duration: Long? = 1_000
    ) = BackupListeningEvent(
        uuid, source, identityId, bindingId, session, startedAt, startedAt + 10, listenedMs,
        duration, qualified, qualification, 1, endReason, sourceKey, null, startedAt + 20
    )

    private fun BackupListeningHistoryV2.replaceEvent(
        index: Int,
        transform: (BackupListeningEvent) -> BackupListeningEvent
    ) = copy(events = events.mapIndexed { eventIndex, event ->
        if (eventIndex == index) transform(event) else event
    })

    private fun BackupListeningHistoryV2.replaceBinding(
        index: Int,
        transform: (BackupLocalTrackBinding) -> BackupLocalTrackBinding
    ) = copy(bindings = bindings.mapIndexed { bindingIndex, binding ->
        if (bindingIndex == index) transform(binding) else binding
    })

    private fun BackupListeningHistoryV2.replaceBaseline(
        transform: (BackupLegacyListeningBaseline) -> BackupLegacyListeningBaseline
    ) = copy(baselines = baselines.map(transform))

    private fun expectInvalid(label: String, block: () -> Unit) {
        try {
            block()
            fail("Expected invalid backup for $label")
        } catch (_: IllegalArgumentException) {
            // Expected.
        }
    }
}
