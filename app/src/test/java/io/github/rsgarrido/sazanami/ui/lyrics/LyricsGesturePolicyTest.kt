package io.github.rsgarrido.sazanami.ui.lyrics

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LyricsGesturePolicyTest {
    @Test
    fun deliberateHeaderDragClosesLyrics() {
        assertTrue(shouldCloseLyricsFromHeader(180f, 0f))
    }

    @Test
    fun fastDownwardHeaderFlingClosesLyrics() {
        assertTrue(shouldCloseLyricsFromHeader(30f, 1_200f))
    }

    @Test
    fun shortHeaderMovementDoesNotCloseLyrics() {
        assertFalse(shouldCloseLyricsFromHeader(80f, 400f))
    }

    @Test
    fun upwardHeaderMovementDoesNotCloseLyrics() {
        assertFalse(shouldCloseLyricsFromHeader(0f, -2_000f))
    }
}
