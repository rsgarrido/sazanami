package io.github.rsgarrido.sazanami.lyrics

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsRootStoreTest {
    @Test
    fun addsIgnoresDuplicateRemovesAndPersistsMultipleRoots() = runBlocking {
        val persistence = MemoryRootPersistence()
        val store = PersistedLyricsRootStore(persistence)
        val first = root("content://provider/tree/first", "First")
        val second = root("content://provider/tree/second", "Second")

        store.addRoot(first)
        store.addRoot(first.copy(displayName = "Duplicate"))
        store.addRoot(second)

        assertEquals(listOf(first, second), store.roots.value)
        assertEquals(listOf(first, second), PersistedLyricsRootStore(persistence).roots.value)

        store.removeRoot(first.uri)
        assertEquals(listOf(second), store.roots.value)
    }

    @Test
    fun malformedStoredJsonAndUrisAreIgnoredSafely() {
        assertTrue(decodeRoots("{bad").isEmpty())
        val encoded = """
            [
              {"uri":"not a uri","displayName":"Bad","volumeId":null},
              {"uri":"content://provider/tree/good","displayName":"Good","volumeId":null}
            ]
        """.trimIndent()

        assertEquals(
            listOf("content://provider/tree/good"),
            decodeRoots(encoded).map(LyricsRoot::uri)
        )
    }

    @Test
    fun pickerCancellationMakesNoStoreChanges() = runBlocking {
        val store = PersistedLyricsRootStore(MemoryRootPersistence())
        val selectedRoot: LyricsRoot? = null

        selectedRoot?.let { store.addRoot(it) }

        assertTrue(store.roots.value.isEmpty())
    }

    private fun root(uri: String, name: String) = LyricsRoot(uri, name)

    private class MemoryRootPersistence(
        private var value: String? = null
    ) : LyricsRootPersistence {
        override fun read(): String? = value
        override fun write(value: String) {
            this.value = value
        }
    }
}
