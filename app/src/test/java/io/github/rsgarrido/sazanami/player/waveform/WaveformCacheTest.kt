package io.github.rsgarrido.sazanami.player.waveform

import java.nio.file.Files
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WaveformCacheTest {
    @Test
    fun writeThenRead_roundTripsBinaryCache() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val key = waveformCacheKey(
            WaveformSource(1L, "/music/song.mp3", 100L, 200L)
        )
        val expected = WaveformData(listOf(0f, 0.25f, 1f), key)

        WaveformCache(directory).write(expected)
        val actual = WaveformCache(directory).read(key)

        assertEquals(expected, actual)
        directory.deleteRecursively()
    }

    @Test
    fun missingOrInvalidCache_returnsNull() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val key = waveformCacheKey(
            WaveformSource(2L, "/music/song.flac", 300L, 400L)
        )
        directory.mkdirs()
        java.io.File(directory, "$key.bin").writeText("not a waveform")

        assertNull(WaveformCache(directory).read(key))
        assertNull(WaveformCache(directory).read("unsafe-key"))
        directory.deleteRecursively()
    }

    @Test
    fun stats_reportsFileCountAndTotalSize() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val cache = WaveformCache(directory)
        cache.write(waveformData(1L, List(10) { 0.5f }))
        cache.write(waveformData(2L, List(20) { 0.25f }))

        val stats = cache.getStats()

        assertEquals(2, stats.fileCount)
        assertTrue(stats.totalBytes > 0L)
        directory.deleteRecursively()
    }

    @Test
    fun clear_removesDiskAndMemoryEntries() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val cache = WaveformCache(directory)
        val data = waveformData(3L, listOf(0.5f))
        cache.write(data)

        val stats = cache.clear()

        assertEquals(WaveformCacheStats(0, 0L), stats)
        assertNull(cache.read(data.sourceKey))
        directory.deleteRecursively()
    }

    @Test
    fun maintain_removesOldestFilesUntilBelowTarget() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val cache = WaveformCache(
            directory = directory,
            maximumDiskBytes = 300L,
            maintenanceTargetBytes = 150L
        )
        val oldest = waveformData(4L, List(10) { 0.1f })
        val middle = waveformData(5L, List(10) { 0.2f })
        val newest = waveformData(6L, List(10) { 0.3f })
        listOf(oldest, middle, newest).forEachIndexed { index, data ->
            cache.write(data)
            java.io.File(directory, "${data.sourceKey}.bin").setLastModified(index + 1L)
        }

        val stats = cache.maintain()

        assertTrue(stats.totalBytes <= 150L)
        assertNull(WaveformCache(directory).read(oldest.sourceKey))
        assertEquals(newest, WaveformCache(directory).read(newest.sourceKey))
        directory.deleteRecursively()
    }

    @Test
    fun maintain_ignoresCorruptFilesAndRemovesTemporaryFiles() {
        val directory = Files.createTempDirectory("waveform-cache-test").toFile()
        val key = waveformData(7L, listOf(1f)).sourceKey
        directory.resolve("$key.bin").writeText("corrupt")
        directory.resolve("$key.tmp").writeText("partial")

        val stats = WaveformCache(directory).maintain()

        assertEquals(1, stats.fileCount)
        assertTrue(!directory.resolve("$key.tmp").exists())
        directory.deleteRecursively()
    }

    private fun waveformData(id: Long, amplitudes: List<Float>): WaveformData {
        val key = waveformCacheKey(WaveformSource(id, "/music/$id.flac", id, id * 2L))
        return WaveformData(amplitudes, key)
    }
}
