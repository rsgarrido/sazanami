package com.example.cdplaya.data.backup

import kotlinx.serialization.Serializable

@Serializable
data class AppBackup(
    val schemaVersion: Int = AppBackupJson.CURRENT_SCHEMA_VERSION,
    val createdAt: Long,
    val appName: String = "CDPlaya",
    val favorites: List<BackupFavoriteSong> = emptyList(),
    val playlists: List<BackupPlaylist> = emptyList(),
    val listeningHistory: List<BackupListeningHistoryEntry> = emptyList(),
    val canonicalListeningHistory: BackupListeningHistoryV2? = null,
    val songRatings: BackupSongRatings = BackupSongRatings(),
    val preferences: BackupPreferences = BackupPreferences()
)

@Serializable
data class BackupPreferences(
    val folderSelectionMode: String = "",
    val selectedLibraryFolders: List<String> = emptyList(),
    val selectedPlayerThemeId: String = "",
    val replayGainMode: String = "",
    val audioOffloadPreference: String = "",
    val modernArtworkTransitionStyle: String = "slide",
    val modernSeekbarStyle: String = "classic_bar",
    val playerThemeTokenOverrides: Map<String, BackupPlayerThemeTokenOverrides> = emptyMap(),
    val songsViewMode: String = "list",
    val albumsViewMode: String = "list",
    val artistsViewMode: String = "list",
    val songsGridColumnCount: Int = 2,
    val albumsGridColumnCount: Int = 2,
    val artistsGridColumnCount: Int = 2,
    val homePins: List<BackupHomePin> = emptyList(),
    val showRecentlyAddedOnHome: Boolean = true,
    val equalizer: BackupEqualizerPreferences =
        BackupEqualizerPreferences()
)

@Serializable
data class BackupHomePin(
    val id: String,
    val type: String,
    val title: String,
    val subtitle: String = "",
    val anchor: BackupSongReference
)

@Serializable
data class BackupEqualizerPreferences(
    val enabled: Boolean = false,
    val preampDb: Double = 0.0,
    val automaticHeadroomEnabled: Boolean = true,
    val bandGainsDb: List<Double> = List(10) { 0.0 },
    val limiterEnabled: Boolean = false,
    val limiterCeilingDbfs: Double = -1.0,
    val userPresets: List<BackupEqualizerPreset> = emptyList(),
    val mode: String = "GRAPHIC",
    val parametricPreampDb: Double = 0.0,
    val parametricAutomaticHeadroomEnabled: Boolean = true,
    val parametricFilters: List<BackupParametricFilter> = emptyList(),
    val parametricUserPresets:
    List<BackupParametricEqualizerPreset> = emptyList()
)

@Serializable
data class BackupEqualizerPreset(
    val id: String,
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val bandGainsDb: List<Double>
)

@Serializable
data class BackupParametricFilter(
    val id: String,
    val type: String,
    val enabled: Boolean,
    val frequencyHz: Double,
    val gainDb: Double? = null,
    val q: Double? = null,
    val slope: Double? = null
)

@Serializable
data class BackupParametricEqualizerPreset(
    val id: String,
    val name: String,
    val preampDb: Double,
    val automaticHeadroomEnabled: Boolean,
    val filters: List<BackupParametricFilter>
)

@Serializable
data class BackupPlayerThemeTokenOverrides(
    val shellArgb: Long? = null,
    val accentArgb: Long? = null,
    val displayBackgroundArgb: Long? = null,
    val displayTextArgb: Long? = null,
    val secondaryAccentArgb: Long? = null
)

@Serializable
data class BackupFavoriteSong(
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val createdAt: Long,
    val reference: BackupSongReference? = null
)

@Serializable
data class BackupPlaylist(
    val name: String,
    val createdAt: Long,
    val updatedAt: Long,
    val songs: List<BackupPlaylistSong> = emptyList()
)

@Serializable
data class BackupPlaylistSong(
    val songKey: String,
    val position: Int,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val addedAt: Long,
    val reference: BackupSongReference? = null
)

@Serializable
data class BackupListeningHistoryEntry(
    val songKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val duration: Long,
    val playCount: Int,
    val firstPlayedAt: Long,
    val lastPlayedAt: Long,
    val reference: BackupSongReference? = null
)

@Serializable
data class BackupListeningHistoryV2(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val identities: List<BackupListeningTrackIdentity> = emptyList(),
    val bindings: List<BackupLocalTrackBinding> = emptyList(),
    val baselines: List<BackupLegacyListeningBaseline> = emptyList(),
    val events: List<BackupListeningEvent> = emptyList(),
    val importSources: List<BackupListeningImportSource> = emptyList(),
    val importBatches: List<BackupListeningImportBatch> = emptyList(),
    val externalTrackIds: List<BackupListeningTrackExternalId> = emptyList(),
    val importedEventEvidence: List<BackupImportedListeningEventEvidence> = emptyList(),
    val batchEventObservations: List<BackupListeningImportBatchEvent> = emptyList(),
    val reconciliations: List<BackupListeningIdentityReconciliation> = emptyList(),
    val summary: BackupListeningHistorySummary = BackupListeningHistorySummary()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 2
    }
}

@Serializable
data class BackupListeningTrackIdentity(
    val backupIdentityId: Long,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
    val albumArtistSnapshot: String?,
    val durationMsSnapshot: Long?,
    val normalizedTitle: String,
    val normalizedArtist: String,
    val normalizedAlbum: String,
    val metadataKey: String?,
    val metadataKeyVersion: Int,
    val createdAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupLocalTrackBinding(
    val backupBindingId: Long,
    val trackIdentityBackupId: Long,
    val referenceKey: String,
    val mediaStoreId: Long?,
    val volumeName: String?,
    val contentUri: String?,
    val relativePath: String?,
    val displayName: String?,
    val absolutePath: String?,
    val fileSizeBytes: Long?,
    val dateModifiedEpochSeconds: Long?,
    val durationMsSnapshot: Long?,
    val legacyStableKey: String?,
    val portableKey: String?,
    val portableKeyVersion: Int?,
    val firstSeenAt: Long,
    val lastSeenAt: Long,
    val missingSince: Long?
)

@Serializable
data class BackupLegacyListeningBaseline(
    val trackIdentityBackupId: Long,
    val historicalPlayCount: Int,
    val firstKnownPlayedAt: Long,
    val lastKnownPlayedAt: Long,
    val legacyReferenceKey: String,
    val migratedAt: Long
)

@Serializable
data class BackupListeningEvent(
    val eventUuid: String,
    val source: String,
    val trackIdentityBackupId: Long,
    val localTrackBindingBackupId: Long?,
    val playbackSessionId: String?,
    val startedAt: Long?,
    val endedAt: Long?,
    val listenedMs: Long,
    val trackDurationMs: Long?,
    val qualifiedAsPlay: Boolean,
    val qualificationReason: String,
    val qualificationRuleVersion: Int,
    val endReason: String?,
    val sourceEventKey: String?,
    val importBatchId: Long?,
    val createdAt: Long,
    val attributionAt: Long = startedAt ?: endedAt ?: 0L,
    val timestampEvidence: String = "native_exact",
    val qualificationPolicy: String = when (source) {
        "cdplaya" -> "cdplaya"
        "spotify_import" -> "spotify"
        "lastfm_import" -> "lastfm"
        else -> "other_import"
    },
    val completionClassification: String = if (source == "cdplaya" && endReason == "natural_end") "native_natural" else "none",
    val publicationState: String = if (source == "cdplaya") "native" else "import_published"
)

@Serializable data class BackupListeningImportSource(
    val backupSourceProfileId: Long, val stableUuid: String, val sourceType: String,
    val displayLabel: String, val accountIdentityDigest: String?, val createdAt: Long, val updatedAt: Long
)

@Serializable data class BackupListeningImportBatch(
    val backupBatchId: Long, val stableUuid: String, val sourceProfileBackupId: Long,
    val status: String, val parserVersion: Int, val qualificationPolicy: String,
    val qualificationRuleVersion: Int, val startedAt: Long, val completedAt: Long?,
    val sourceRangeStart: Long?, val sourceRangeEnd: Long?, val parsedCount: Long,
    val insertedCount: Long, val duplicateCount: Long, val ignoredCount: Long,
    val invalidCount: Long, val exactMatchCount: Long, val ambiguousMatchCount: Long,
    val unmatchedCount: Long, val qualifiedCount: Long, val failureCategory: String?,
    val createdAppVersion: String
)

@Serializable data class BackupListeningTrackExternalId(
    val trackIdentityBackupId: Long, val sourceType: String, val externalId: String,
    val createdAt: Long, val lastSeenAt: Long
)

@Serializable data class BackupImportedListeningEventEvidence(
    val eventUuid: String, val sourceProfileBackupId: Long, val fingerprintVersion: Int,
    val fingerprint: String, val duplicateOrdinal: Int, val normalizedReasonStart: String?,
    val normalizedReasonEnd: String?, val skippedState: String, val matchDispositionAtImport: String
)

@Serializable data class BackupListeningImportBatchEvent(val batchBackupId: Long, val eventUuid: String)

@Serializable
data class BackupListeningIdentityReconciliation(
    val sourceIdentityBackupId: Long,
    val targetIdentityBackupId: Long,
    val reconciledAt: Long
)

@Serializable
data class BackupListeningHistorySummary(
    val identityCount: Long = 0,
    val bindingCount: Long = 0,
    val baselineCount: Long = 0,
    val eventCount: Long = 0,
    val qualifiedEventCount: Long = 0,
    val nonQualifiedEventCount: Long = 0,
    val earliestDetailedEventAt: Long? = null,
    val latestDetailedEventAt: Long? = null
)

@Serializable
data class BackupSongRatings(
    val formatVersion: Int = CURRENT_FORMAT_VERSION,
    val entries: List<BackupSongRating> = emptyList()
) {
    companion object {
        const val CURRENT_FORMAT_VERSION = 1
    }
}

@Serializable
data class BackupSongRating(
    val trackIdentityBackupId: Long,
    val rating: Int,
    val ratedAt: Long,
    val updatedAt: Long
)

@Serializable
data class BackupSongReference(
    val relativePath: String = "",
    val displayName: String = "",
    val fileSizeBytes: Long = 0L,
    val duration: Long = 0L,
    val title: String = "",
    val artist: String = "",
    val album: String = "",
    val albumArtist: String = "",
    val legacyStableKey: String = "",
    val portableKey: String = "",
    val portableKeyVersion: Int = 1
)
