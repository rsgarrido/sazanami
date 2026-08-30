package io.github.rsgarrido.sazanami

import android.database.sqlite.SQLiteConstraintException
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.LegacyListeningBaselineEntity
import io.github.rsgarrido.sazanami.data.local.ListeningEndReason
import io.github.rsgarrido.sazanami.data.local.ListeningEventEntity
import io.github.rsgarrido.sazanami.data.local.ListeningQualificationReason
import io.github.rsgarrido.sazanami.data.local.ListeningSource
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ListeningHistoryDaoTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun identitiesAllowIdenticalMetadataAndRoundTrip() = runBlocking {
        val firstId = database.listeningTrackIdentityDao().insert(identity())
        val secondId = database.listeningTrackIdentityDao().insert(identity())

        assertNotEquals(firstId, secondId)
        assertEquals(identity(id = firstId), database.listeningTrackIdentityDao().getById(firstId))
        assertEquals(2, database.listeningTrackIdentityDao().getAll().size)
    }

    @Test
    fun bindingsAndEventsAllowMissingLocalTrackAndReplayBeyondDuration() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val bindingId = database.localTrackBindingDao().insert(binding(identityId))
        val unboundEvent = event(
            eventUuid = "event-unbound",
            trackIdentityId = identityId,
            localTrackBindingId = null,
            listenedMs = 240_000L,
            trackDurationMs = 180_000L
        )

        database.listeningEventDao().insert(unboundEvent)

        assertEquals(binding(identityId, id = bindingId), database.localTrackBindingDao().getById(bindingId))
        assertNull(database.listeningEventDao().getByUuid("event-unbound")?.localTrackBindingId)
        assertEquals(240_000L, database.listeningEventDao().getByUuid("event-unbound")?.listenedMs)
    }

    @Test
    fun eventDeduplicationKeysAreUniqueButNullKeysCanRepeat() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        database.listeningEventDao().insert(
            event(eventUuid = "uuid-1", trackIdentityId = identityId, playbackSessionId = "session-1")
        )

        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.listeningEventDao().insert(
                    event(eventUuid = "uuid-1", trackIdentityId = identityId)
                )
            }
        }
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.listeningEventDao().insert(
                    event(
                        eventUuid = "uuid-2",
                        trackIdentityId = identityId,
                        playbackSessionId = "session-1"
                    )
                )
            }
        }

        database.listeningEventDao().insert(
            event(
                eventUuid = "uuid-3",
                trackIdentityId = identityId,
                source = ListeningSource.SPOTIFY_IMPORT,
                sourceEventKey = "source-key"
            )
        )
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking {
                database.listeningEventDao().insert(
                    event(
                        eventUuid = "uuid-4",
                        trackIdentityId = identityId,
                        source = ListeningSource.SPOTIFY_IMPORT,
                        sourceEventKey = "source-key"
                    )
                )
            }
        }

        database.listeningEventDao().insert(event(eventUuid = "uuid-5", trackIdentityId = identityId))
        assertEquals(3L, database.listeningEventDao().count())
        assertEquals("uuid-1", database.listeningEventDao().getByPlaybackSessionId("session-1")?.eventUuid)
    }

    @Test
    fun finalizedEventConflictIgnoreIsIdempotentByPlaybackSession() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val first = database.listeningEventDao().insertIgnoringConflict(
            event(
                eventUuid = "native-event-1",
                trackIdentityId = identityId,
                playbackSessionId = "native-session-1"
            )
        )
        val duplicate = database.listeningEventDao().insertIgnoringConflict(
            event(
                eventUuid = "native-event-2",
                trackIdentityId = identityId,
                playbackSessionId = "native-session-1"
            )
        )

        assertTrue(first > 0L)
        assertEquals(-1L, duplicate)
        assertEquals(1L, database.listeningEventDao().count())
    }

    @Test
    fun negativeListeningTimeIsRejectedBeforePersistence() {
        assertThrows(IllegalArgumentException::class.java) {
            event(eventUuid = "negative", trackIdentityId = 1L, listenedMs = -1L)
        }
    }

    @Test
    fun baselinesAreOneToOneAndForeignKeysProtectHistory() = runBlocking {
        val identityId = database.listeningTrackIdentityDao().insert(identity())
        val bindingId = database.localTrackBindingDao().insert(binding(identityId))
        database.legacyListeningBaselineDao().insert(baseline(identityId))
        database.listeningEventDao().insert(
            event(
                eventUuid = "bound-event",
                trackIdentityId = identityId,
                localTrackBindingId = bindingId
            )
        )

        assertEquals(baseline(identityId), database.legacyListeningBaselineDao().getByTrackIdentityId(identityId))
        assertThrows(SQLiteConstraintException::class.java) {
            runBlocking { database.legacyListeningBaselineDao().insert(baseline(identityId)) }
        }

        database.localTrackBindingDao().deleteById(bindingId)
        assertNull(database.listeningEventDao().getByUuid("bound-event")?.localTrackBindingId)
        assertThrows(SQLiteConstraintException::class.java) {
            database.openHelper.writableDatabase.execSQL(
                "DELETE FROM listening_track_identities WHERE id = $identityId"
            )
        }
        assertTrue(database.listeningTrackIdentityDao().getById(identityId) != null)
    }

    private fun identity(id: Long = 0L) = ListeningTrackIdentityEntity(
        id = id,
        titleSnapshot = "Same Song",
        artistSnapshot = "Same Artist",
        albumSnapshot = "Same Album",
        albumArtistSnapshot = null,
        durationMsSnapshot = 180_000L,
        normalizedTitle = "same song",
        normalizedArtist = "same artist",
        normalizedAlbum = "same album",
        metadataKey = "portable:v1:key",
        metadataKeyVersion = 1,
        createdAt = 100L,
        updatedAt = 100L
    )

    private fun binding(trackIdentityId: Long, id: Long = 0L) = LocalTrackBindingEntity(
        id = id,
        trackIdentityId = trackIdentityId,
        referenceKey = "local:v1:reference",
        mediaStoreId = 42L,
        volumeName = "external_primary",
        contentUri = "content://media/42",
        relativePath = "Music/",
        displayName = "Same Song.flac",
        absolutePath = "/storage/Music/Same Song.flac",
        fileSizeBytes = 1234L,
        dateModifiedEpochSeconds = 5678L,
        durationMsSnapshot = 180_000L,
        legacyStableKey = "legacy-key",
        portableKey = "portable:v1:key",
        portableKeyVersion = 1,
        firstSeenAt = 100L,
        lastSeenAt = 200L,
        missingSince = null
    )

    private fun event(
        eventUuid: String,
        trackIdentityId: Long,
        localTrackBindingId: Long? = null,
        playbackSessionId: String? = null,
        source: ListeningSource = ListeningSource.CDPLAYA,
        sourceEventKey: String? = null,
        listenedMs: Long = 90_000L,
        trackDurationMs: Long? = 180_000L
    ) = ListeningEventEntity(
        eventUuid = eventUuid,
        source = source,
        trackIdentityId = trackIdentityId,
        localTrackBindingId = localTrackBindingId,
        playbackSessionId = playbackSessionId,
        startedAt = 1_000L,
        endedAt = 91_000L,
        listenedMs = listenedMs,
        trackDurationMs = trackDurationMs,
        qualifiedAsPlay = true,
        qualificationReason = ListeningQualificationReason.TIME_THRESHOLD,
        qualificationRuleVersion = 1,
        endReason = ListeningEndReason.STOPPED,
        sourceEventKey = sourceEventKey,
        importBatchId = null,
        createdAt = 92_000L
    )

    private fun baseline(trackIdentityId: Long) = LegacyListeningBaselineEntity(
        trackIdentityId = trackIdentityId,
        historicalPlayCount = 17,
        firstKnownPlayedAt = 1_000L,
        lastKnownPlayedAt = 2_000L,
        legacyReferenceKey = "legacy:$trackIdentityId",
        migratedAt = 3_000L
    )
}
