package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.assertNull
import org.junit.Test

class AndroidLyricsIndexStoreTest {
    @Test
    fun malformedPersistedIndexFailsSafely() {
        assertNull(decodeLyricsIndexSnapshotOrNull("{not-valid-json"))
    }
}
