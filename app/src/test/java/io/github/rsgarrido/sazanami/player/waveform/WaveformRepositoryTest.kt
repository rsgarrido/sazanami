package io.github.rsgarrido.sazanami.player.waveform

import android.net.Uri
import io.github.rsgarrido.sazanami.data.Song
import java.nio.file.Files
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.mockito.Mockito.mock

class WaveformRepositoryTest {
    @Test
    fun defaultAnalysisResolution_capturesShortQuietSections() {
        assertEquals(512, WaveformRepository.DEFAULT_ANALYZED_BAR_COUNT)
    }

    @Test
    fun load_analyzesOnceThenUsesCache() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        var analysisCount = 0
        val source = WaveformSource(7L, "/music/example.flac", 10L, 20L)
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver { source },
            analyzer = WaveformAnalyzer { _, sourceKey, barCount ->
                analysisCount++
                WaveformData(List(barCount) { 0.5f }, sourceKey)
            }
        )

        val first = repository.load(song())
        val second = repository.load(song())

        assertEquals(1, analysisCount)
        assertEquals(first, second)
        assertEquals(WaveformRepository.DEFAULT_ANALYZED_BAR_COUNT, first?.amplitudes?.size)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun load_fileMetadataChangeInvalidatesCache() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        var analysisCount = 0
        var lastModified = 10L
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver {
                WaveformSource(7L, "/music/example.flac", lastModified, 20L)
            },
            analyzer = WaveformAnalyzer { _, sourceKey, _ ->
                analysisCount++
                WaveformData(listOf(1f), sourceKey)
            }
        )

        repository.load(song())
        lastModified++
        repository.load(song())

        assertEquals(2, analysisCount)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun load_analysisFailureIsNotCachedAndReturnsNull() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        var analysisCount = 0
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver {
                WaveformSource(7L, "/music/unsupported.xyz", 10L, 20L)
            },
            analyzer = WaveformAnalyzer { _, _, _ ->
                analysisCount++
                null
            }
        )

        assertNull(repository.load(song()))
        assertNull(repository.load(song()))

        assertEquals(2, analysisCount)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun prefetch_cachedSongSkipsAnalysis() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        val source = WaveformSource(7L, "/music/example-7.flac", 10L, 20L)
        val sourceKey = waveformCacheKey(source)
        WaveformCache(directory).write(WaveformData(listOf(0.25f, 1f), sourceKey))
        var analysisCount = 0
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver { source },
            analyzer = WaveformAnalyzer { _, _, _ ->
                analysisCount++
                null
            }
        )

        repository.prefetch(listOf(song()))

        assertEquals(0, analysisCount)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun prefetch_concurrentDuplicateUsesOneAnalysis() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        val analysisStarted = CompletableDeferred<Unit>()
        val finishAnalysis = CompletableDeferred<Unit>()
        var analysisCount = 0
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver {
                WaveformSource(7L, "/music/example-7.flac", 10L, 20L)
            },
            analyzer = WaveformAnalyzer { _, sourceKey, _ ->
                analysisCount++
                analysisStarted.complete(Unit)
                finishAnalysis.await()
                WaveformData(listOf(1f), sourceKey)
            }
        )

        val first = async { repository.prefetch(listOf(song())) }
        analysisStarted.await()
        val second = async { repository.prefetch(listOf(song())) }
        yield()
        finishAnalysis.complete(Unit)
        awaitAll(first, second)

        assertEquals(1, analysisCount)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun prefetch_limitsWorkToTwoUniqueSongs() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        val analyzedSongIds = mutableListOf<Long>()
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver { song ->
                WaveformSource(
                    songId = song.id,
                    filePath = song.filePath,
                    lastModified = 10L,
                    fileLength = 20L
                )
            },
            analyzer = WaveformAnalyzer { song, sourceKey, _ ->
                analyzedSongIds += song.id
                WaveformData(listOf(1f), sourceKey)
            }
        )

        repository.prefetch(listOf(song(1L), song(2L), song(3L), song(2L)))

        assertEquals(listOf(1L, 2L), analyzedSongIds)
        directory.deleteRecursively()
        Unit
    }

    @Test
    fun prefetch_failureIsSilentAndCurrentLoadCanRetry() = runBlocking {
        val directory = Files.createTempDirectory("waveform-repository-test").toFile()
        var analysisCount = 0
        val repository = repository(
            directory = directory,
            sourceResolver = WaveformSourceResolver {
                WaveformSource(7L, "/music/example-7.xyz", 10L, 20L)
            },
            analyzer = WaveformAnalyzer { _, _, _ ->
                analysisCount++
                null
            }
        )

        repository.prefetch(listOf(song()))
        assertNull(repository.load(song()))

        assertEquals(2, analysisCount)
        directory.deleteRecursively()
        Unit
    }

    private fun repository(
        directory: java.io.File,
        sourceResolver: WaveformSourceResolver,
        analyzer: WaveformAnalyzer
    ) = WaveformRepository(
        cache = WaveformCache(directory),
        analyzer = analyzer,
        sourceResolver = sourceResolver,
        ioDispatcher = Dispatchers.Unconfined
    )

    private fun song(id: Long = 7L) = Song(
        id = id,
        title = "Example",
        artist = "Artist",
        album = "Album",
        trackNumber = 1,
        duration = 180_000L,
        uri = mock(Uri::class.java),
        filePath = "/music/example-$id.flac",
        folderPath = "/music",
        albumArtUri = null
    )
}
