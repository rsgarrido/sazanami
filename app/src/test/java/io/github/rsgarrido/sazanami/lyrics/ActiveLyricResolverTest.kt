package io.github.rsgarrido.sazanami.lyrics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ActiveLyricResolverTest {
    private val cues = listOf(
        cue(1_000L, "First"),
        cue(2_000L, "Second"),
        LyricCue(3_000L, LyricCueContent.Instrumental),
        cue(4_000L, "Fourth")
    )

    @Test
    fun positionBeforeFirstCueHasNoActiveLyric() {
        assertNull(ActiveLyricResolver.resolve(cues, 999L))
    }

    @Test
    fun exactTimestampActivatesCue() {
        assertEquals(listOf("First"), ActiveLyricResolver.resolve(cues, 1_000L)?.lines)
    }

    @Test
    fun positionBetweenCuesKeepsMostRecentCueActive() {
        assertEquals(listOf("Second"), ActiveLyricResolver.resolve(cues, 2_999L)?.lines)
    }

    @Test
    fun instrumentalGapHasNoVisibleActiveLyric() {
        assertNull(ActiveLyricResolver.resolve(cues, 3_500L))
    }

    @Test
    fun positionAfterFinalCueKeepsFinalCueActive() {
        assertEquals(listOf("Fourth"), ActiveLyricResolver.resolve(cues, 99_000L)?.lines)
    }

    @Test
    fun seekBackwardAndForwardRequireNoMutableScanState() {
        assertEquals(listOf("Fourth"), ActiveLyricResolver.resolve(cues, 4_500L)?.lines)
        assertEquals(listOf("First"), ActiveLyricResolver.resolve(cues, 1_500L)?.lines)
        assertEquals(listOf("Second"), ActiveLyricResolver.resolve(cues, 2_500L)?.lines)
    }

    @Test
    fun duplicateTimestampReturnsStableVisibleGroup() {
        val duplicates = listOf(
            cue(1_000L, "First"),
            LyricCue(2_000L, LyricCueContent.Instrumental),
            cue(2_000L, "Second A"),
            cue(2_000L, "Second B"),
            cue(3_000L, "Third")
        )

        assertEquals(
            ActiveLyricGroup(2_000L, listOf("Second A", "Second B")),
            ActiveLyricResolver.resolve(duplicates, 2_500L)
        )
    }

    private fun cue(timestampMs: Long, text: String) =
        LyricCue(timestampMs, LyricCueContent.Text(text))
}
