package com.example.cdplaya

import androidx.room.Room
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.example.cdplaya.data.ListeningImportRepository
import com.example.cdplaya.data.importing.spotify.ListeningImportStreamSource
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyImportSourceProfileService
import com.example.cdplaya.data.importing.spotify.SpotifyListeningHistoryImportExecutor
import com.example.cdplaya.data.local.AppDatabase
import java.io.ByteArrayInputStream
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlin.system.measureTimeMillis
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyListeningHistoryImportExecutorScaleTest {
    private lateinit var database: AppDatabase

    @Before fun setUp() {
        database = Room.inMemoryDatabaseBuilder(
            InstrumentationRegistry.getInstrumentation().targetContext,
            AppDatabase::class.java
        ).build()
    }

    @After fun tearDown() = database.close()

    @Test fun oneThousandEvents_useTwoBoundedChunkTransactions() = verify(1_000)

    @Test fun tenThousandEvents_useTwentyBoundedChunkTransactions() = verify(10_000)

    private fun verify(count: Int) = runBlocking {
        val json = history(count)
        val repository = ListeningImportRepository(database, nowMillis = { 2_000_000_000_000 })
        val executor = SpotifyListeningHistoryImportExecutor(
            repository = repository,
            sourceProfiles = SpotifyImportSourceProfileService(repository) { 2_000_000_000_000 },
            parser = SpotifyExtendedStreamingParser(
                Clock.fixed(Instant.parse("2035-01-01T00:00:00Z"), ZoneOffset.UTC)
            ),
            nowMillis = { 2_000_000_000_000 },
            batchUuid = { "scale-$count" },
            createdAppVersion = "scale-test"
        )
        var chunks = 0
        lateinit var result: com.example.cdplaya.data.importing.ListeningImportExecutionResult
        val elapsed = measureTimeMillis {
            result = executor.execute(listOf(ListeningImportStreamSource {
                ByteArrayInputStream(json)
            })) { progress -> chunks = progress.chunksCompleted }
        }

        assertEquals(count.toLong(), result.newPublished)
        assertEquals(count.toLong(), database.listeningEventDao().count())
        assertEquals(100.coerceAtMost(count), database.listeningTrackIdentityDao().getAll().size)
        assertEquals((count + 499) / 500, chunks)
        assertEquals(0L, database.listeningEventDao().countPendingForBatch(result.batchId))
        println("session3Executor count=$count elapsedMs=$elapsed chunks=$chunks chunkSize=500")
    }

    private fun history(count: Int): ByteArray = buildString(count * 220) {
        append('[')
        repeat(count) { index ->
            if (index > 0) append(',')
            append("{\"ts\":\"")
            append(Instant.ofEpochSecond(1_700_000_000L + index))
            append("\",\"ms_played\":31000")
            append(",\"master_metadata_track_name\":\"Track ").append(index % 100).append('"')
            append(",\"master_metadata_album_artist_name\":\"Scale Artist\"")
            append(",\"master_metadata_album_album_name\":\"Scale Album\"")
            append(",\"spotify_track_uri\":\"spotify:track:scale").append(index % 100).append("\"}")
        }
        append(']')
    }.toByteArray()
}
