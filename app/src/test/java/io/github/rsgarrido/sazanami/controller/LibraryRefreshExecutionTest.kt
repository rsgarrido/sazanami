package io.github.rsgarrido.sazanami.controller

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotEquals
import org.junit.Test

class LibraryRefreshExecutionTest {
    @Test
    fun artworkRepairIsOffMainThread() = runBlocking {
        val callerThread = Thread.currentThread().name

        val refreshThread = runLibraryScanOffMain { Thread.currentThread().name }

        assertNotEquals(callerThread, refreshThread)
    }
}
