package com.example.cdplaya.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SongRatingBackupValidatorTest {
    @Test
    fun acceptsEveryRatingValueAndValidTimestamps() {
        val ratings = BackupSongRatings(entries = (1..5).map { value ->
            BackupSongRating(value.toLong(), value, 10L, 20L)
        })

        assertEquals(ratings, SongRatingBackupValidator.validate(ratings, history(5)))
    }

    @Test
    fun rejectsInvalidValuesDuplicatesReferencesTimestampsAndFormat() {
        val valid = BackupSongRating(1L, 3, 10L, 20L)
        listOf(
            BackupSongRatings(formatVersion = 2, entries = listOf(valid)),
            BackupSongRatings(entries = listOf(valid, valid.copy(rating = 4))),
            BackupSongRatings(entries = listOf(valid.copy(trackIdentityBackupId = 2L))),
            BackupSongRatings(entries = listOf(valid.copy(rating = 0))),
            BackupSongRatings(entries = listOf(valid.copy(rating = 6))),
            BackupSongRatings(entries = listOf(valid.copy(ratedAt = -1L))),
            BackupSongRatings(entries = listOf(valid.copy(updatedAt = 9L)))
        ).forEach { ratings ->
            assertThrows(IllegalArgumentException::class.java) {
                SongRatingBackupValidator.validate(ratings, history(1))
            }
        }
    }

    @Test
    fun versionSevenDecodesAsVersionEightWithNoRatings() {
        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(
                    schemaVersion = 7,
                    createdAt = 1L,
                    canonicalListeningHistory = BackupListeningHistoryV2()
                )
            ).replace("\"schemaVersion\":10", "\"schemaVersion\":7")
        )

        assertEquals(10, decoded.schemaVersion)
        assertEquals(emptyList<BackupSongRating>(), decoded.songRatings.entries)
    }

    @Test
    fun versionEightJsonRoundTripPreservesRatingReferencesAndTimestamps() {
        val canonical = history(1)
        val ratings = BackupSongRatings(
            entries = listOf(BackupSongRating(1L, 5, 100L, 200L))
        )
        val decoded = AppBackupJson.decodeBackup(
            AppBackupJson.encodeBackup(
                AppBackup(
                    createdAt = 1L,
                    canonicalListeningHistory = canonical,
                    songRatings = ratings
                )
            )
        )

        assertEquals(10, decoded.schemaVersion)
        assertEquals(ratings, decoded.songRatings)
    }

    @Test
    fun versionSixAggregateHistoryMigratesWithoutRatings() {
        val decoded = AppBackupJson.decodeBackup(
            """
            {
              "schemaVersion": 6,
              "createdAt": 50,
              "favorites": [{"songKey":"favorite","title":"Song","artist":"Artist","album":"Album","duration":1000,"createdAt":1}],
              "listeningHistory": [{"songKey":"played","title":"Song","artist":"Artist","album":"Album","duration":1000,"playCount":5,"firstPlayedAt":1,"lastPlayedAt":2}]
            }
            """.trimIndent()
        )

        assertEquals(10, decoded.schemaVersion)
        assertEquals(1, decoded.canonicalListeningHistory?.baselines?.size)
        assertEquals(emptyList<BackupSongRating>(), decoded.songRatings.entries)
    }

    private fun history(identityCount: Int): BackupListeningHistoryV2 {
        val history = BackupListeningHistoryV2(
            identities = (1..identityCount).map { id ->
                BackupListeningTrackIdentity(
                    backupIdentityId = id.toLong(),
                    titleSnapshot = "Song $id",
                    artistSnapshot = "Artist",
                    albumSnapshot = "Album",
                    albumArtistSnapshot = null,
                    durationMsSnapshot = 1L,
                    normalizedTitle = "song $id",
                    normalizedArtist = "artist",
                    normalizedAlbum = "album",
                    metadataKey = null,
                    metadataKeyVersion = 1,
                    createdAt = 0L,
                    updatedAt = 0L
                )
            }
        )
        return history.copy(summary = history.recordsSummary())
    }
}
