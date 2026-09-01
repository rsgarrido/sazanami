package io.github.rsgarrido.sazanami.player

import android.net.Uri
import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.local.AppDatabase
import io.github.rsgarrido.sazanami.data.local.ListeningTrackIdentityEntity
import io.github.rsgarrido.sazanami.data.local.LocalTrackBindingEntity
import io.github.rsgarrido.sazanami.data.local.PlaybackQueueEntryEntity
import io.github.rsgarrido.sazanami.data.membershipKey
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaybackQueueTrackAccessTest {
    private lateinit var database: AppDatabase

    @Before
    fun setUp() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java).build()
    }

    @After
    fun tearDown() {
        database.close()
    }

    @Test
    fun resolutionUsesExactDurableBindingAndNeverFallsBackToMatchingTitle() = runBlocking {
        val exactSong = song(id = 10L, title = "Same title", displayName = "exact.flac")
        val titleOnlySong = song(id = 11L, title = "Same title", displayName = "other.flac")
        val exactIdentity = seedIdentity("Same title")
        val exactBinding = seedBinding(exactIdentity, exactSong.membershipKey())
        val titleOnlyIdentity = seedIdentity("Same title")
        seedBinding(titleOnlyIdentity, "local:v1:not-present-in-library")
        val access = RoomPlaybackQueueTrackAccess(
            database = database,
            catalogSongs = { listOf(exactSong, titleOnlySong) }
        )

        val resolved = access.resolve(
            listOf(
                queueEntry("exact", exactIdentity, exactBinding, 0),
                queueEntry("title-only", titleOnlyIdentity, null, 1)
            )
        )

        assertEquals(listOf("exact"), resolved.map { it.persistedEntry.entryId })
        assertEquals(exactSong.id, resolved.single().song.id)
    }

    private suspend fun seedIdentity(title: String): Long =
        database.listeningTrackIdentityDao().insert(
            ListeningTrackIdentityEntity(
                titleSnapshot = title,
                artistSnapshot = "Artist",
                albumSnapshot = "Album",
                albumArtistSnapshot = null,
                durationMsSnapshot = 180_000L,
                normalizedTitle = title.lowercase(),
                normalizedArtist = "artist",
                normalizedAlbum = "album",
                metadataKey = "same-metadata",
                metadataKeyVersion = 1,
                createdAt = 1L,
                updatedAt = 1L
            )
        )

    private suspend fun seedBinding(identityId: Long, referenceKey: String): Long =
        database.localTrackBindingDao().insert(
            LocalTrackBindingEntity(
                trackIdentityId = identityId,
                referenceKey = referenceKey,
                mediaStoreId = null,
                volumeName = null,
                contentUri = null,
                relativePath = null,
                displayName = null,
                absolutePath = null,
                fileSizeBytes = null,
                dateModifiedEpochSeconds = null,
                durationMsSnapshot = 180_000L,
                legacyStableKey = null,
                portableKey = "same-metadata",
                portableKeyVersion = 1,
                firstSeenAt = 1L,
                lastSeenAt = 1L,
                missingSince = null
            )
        )

    private fun queueEntry(
        entryId: String,
        identityId: Long,
        bindingId: Long?,
        order: Int
    ) = PlaybackQueueEntryEntity(
        entryId = entryId,
        queueId = "queue",
        trackIdentityId = identityId,
        localTrackBindingId = bindingId,
        baseOrder = order,
        playbackOrder = order
    )

    private fun song(id: Long, title: String, displayName: String) = Song(
        id = id,
        title = title,
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 180_000L,
        uri = Uri.parse("content://media/$id"),
        filePath = "/music/$displayName",
        folderPath = "/music",
        albumArtUri = null,
        volumeName = "external",
        displayName = displayName,
        relativePath = "Music/",
        fileSizeBytes = 1_000L,
        dateModifiedEpochSeconds = 1L
    )
}
