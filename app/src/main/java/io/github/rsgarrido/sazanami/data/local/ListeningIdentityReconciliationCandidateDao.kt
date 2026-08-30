package io.github.rsgarrido.sazanami.data.local

import androidx.room.Dao
import androidx.room.Query

data class HistoricalReconciliationSourceRow(
    val identityId: Long,
    val titleSnapshot: String,
    val artistSnapshot: String,
    val albumSnapshot: String,
    val albumArtistSnapshot: String?,
    val providerStorageValues: String,
    val importedEventCount: Long,
    val qualifiedPlayCount: Long,
    val recordedListeningMs: Long,
    val completedCount: Long,
    val firstListenedAt: Long,
    val lastListenedAt: Long,
    val externalIdCount: Long
)

data class LocalReconciliationTargetRow(
    val identityId: Long,
    val localBindingId: Long,
    val referenceKey: String,
    val title: String,
    val artist: String,
    val album: String,
    val albumArtist: String?,
    val durationMs: Long?,
    val displayName: String?,
    val relativePath: String?
)

/** Read-only, batched inputs for transient imported-history candidate discovery. */
@Dao
interface ListeningIdentityReconciliationCandidateDao {
    @Query(
        """
        SELECT identity.id AS identityId,
               identity.titleSnapshot AS titleSnapshot,
               identity.artistSnapshot AS artistSnapshot,
               identity.albumSnapshot AS albumSnapshot,
               identity.albumArtistSnapshot AS albumArtistSnapshot,
               GROUP_CONCAT(DISTINCT event.source) AS providerStorageValues,
               COUNT(event.id) AS importedEventCount,
               SUM(CASE WHEN event.qualifiedAsPlay = 1 THEN 1 ELSE 0 END) AS qualifiedPlayCount,
               COALESCE(SUM(event.listenedMs), 0) AS recordedListeningMs,
               SUM(CASE WHEN event.completionClassification != 'none' THEN 1 ELSE 0 END) AS completedCount,
               MIN(event.attributionAt) AS firstListenedAt,
               MAX(event.attributionAt) AS lastListenedAt,
               (SELECT COUNT(*) FROM listening_track_external_ids externalId
                WHERE externalId.trackIdentityId = identity.id) AS externalIdCount
        FROM listening_track_identities identity
        JOIN listening_events event ON event.trackIdentityId = identity.id
            AND event.source != 'cdplaya'
            AND event.publicationState = 'import_published'
        WHERE NOT EXISTS (
                  SELECT 1 FROM local_track_bindings binding
                  WHERE binding.trackIdentityId = identity.id
              )
        GROUP BY identity.id
        ORDER BY identity.id
        """
    )
    suspend fun getAllHistoricalSources(): List<HistoricalReconciliationSourceRow>

    @Query(
        """
        SELECT identity.id AS identityId,
               identity.titleSnapshot AS titleSnapshot,
               identity.artistSnapshot AS artistSnapshot,
               identity.albumSnapshot AS albumSnapshot,
               identity.albumArtistSnapshot AS albumArtistSnapshot,
               GROUP_CONCAT(DISTINCT event.source) AS providerStorageValues,
               COUNT(event.id) AS importedEventCount,
               SUM(CASE WHEN event.qualifiedAsPlay = 1 THEN 1 ELSE 0 END) AS qualifiedPlayCount,
               COALESCE(SUM(event.listenedMs), 0) AS recordedListeningMs,
               SUM(CASE WHEN event.completionClassification != 'none' THEN 1 ELSE 0 END) AS completedCount,
               MIN(event.attributionAt) AS firstListenedAt,
               MAX(event.attributionAt) AS lastListenedAt,
               (SELECT COUNT(*) FROM listening_track_external_ids externalId
                WHERE externalId.trackIdentityId = identity.id) AS externalIdCount
        FROM listening_track_identities identity
        JOIN listening_events event ON event.trackIdentityId = identity.id
            AND event.source != 'cdplaya'
            AND event.publicationState = 'import_published'
        WHERE NOT EXISTS (
                  SELECT 1 FROM local_track_bindings binding
                  WHERE binding.trackIdentityId = identity.id
              )
          AND NOT EXISTS (
                  SELECT 1 FROM listening_identity_reconciliations reconciliation
                  WHERE reconciliation.sourceIdentityId = identity.id
              )
        GROUP BY identity.id
        ORDER BY identity.id
        """
    )
    suspend fun getReviewableHistoricalSources(): List<HistoricalReconciliationSourceRow>

    @Query(
        """
        SELECT identity.id AS identityId,
               binding.id AS localBindingId,
               binding.referenceKey AS referenceKey,
               identity.titleSnapshot AS title,
               identity.artistSnapshot AS artist,
               identity.albumSnapshot AS album,
               identity.albumArtistSnapshot AS albumArtist,
               COALESCE(binding.durationMsSnapshot, identity.durationMsSnapshot) AS durationMs,
               binding.displayName AS displayName,
               binding.relativePath AS relativePath
        FROM listening_track_identities identity
        JOIN local_track_bindings binding ON binding.trackIdentityId = identity.id
        WHERE binding.missingSince IS NULL
          AND binding.id = (
                  SELECT MIN(activeBinding.id) FROM local_track_bindings activeBinding
                  WHERE activeBinding.trackIdentityId = identity.id
                    AND activeBinding.missingSince IS NULL
              )
          AND NOT EXISTS (
                  SELECT 1 FROM listening_identity_reconciliations reconciliation
                  WHERE reconciliation.sourceIdentityId = identity.id
              )
        ORDER BY identity.id
        """
    )
    suspend fun getEligibleLocalTargets(): List<LocalReconciliationTargetRow>

    @Query(
        """
        SELECT identity.id AS identityId,
               binding.id AS localBindingId,
               binding.referenceKey AS referenceKey,
               identity.titleSnapshot AS title,
               identity.artistSnapshot AS artist,
               identity.albumSnapshot AS album,
               identity.albumArtistSnapshot AS albumArtist,
               COALESCE(binding.durationMsSnapshot, identity.durationMsSnapshot) AS durationMs,
               binding.displayName AS displayName,
               binding.relativePath AS relativePath
        FROM listening_track_identities identity
        JOIN local_track_bindings binding ON binding.trackIdentityId = identity.id
        WHERE binding.id = (
                  SELECT MIN(anyBinding.id) FROM local_track_bindings anyBinding
                  WHERE anyBinding.trackIdentityId = identity.id
              )
        ORDER BY identity.id
        """
    )
    suspend fun getAllLocalTargets(): List<LocalReconciliationTargetRow>
}
