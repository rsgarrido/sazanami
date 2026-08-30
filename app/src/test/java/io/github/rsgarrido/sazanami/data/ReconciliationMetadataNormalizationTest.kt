package io.github.rsgarrido.sazanami.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class ReconciliationMetadataNormalizationTest {
    @Test
    fun canonicalNormalizationFoldsSafeQuoteDashEllipsisAndWhitespaceVariants() {
        assertEquals("everything's ruined", candidateCanonicalNormalize("Everything’s Ruined"))
        assertEquals("'single' \"double\"", candidateCanonicalNormalize("‘single’ “double”"))
        assertEquals("part-one", candidateCanonicalNormalize("Part—One"))
        assertEquals("wait... now", candidateCanonicalNormalize("Wait…\u00a0\u00a0Now"))
        assertEquals("hidden space", candidateCanonicalNormalize("Hidden\u200b Space"))
    }

    @Test
    fun canonicalNormalizationDoesNotFoldDiacriticsOrRemoveMeaningfulPunctuation() {
        assertNotEquals(candidateCanonicalNormalize("Cafe"), candidateCanonicalNormalize("Café"))
        assertNotEquals(candidateCanonicalNormalize("S.A.S.S"), candidateCanonicalNormalize("SASS"))
        assertEquals(candidateAccentNormalize("Cafe"), candidateAccentNormalize("Café"))
        assertEquals(candidatePunctuationNormalize("S.A.S.S"), candidatePunctuationNormalize("SASS"))
    }

    @Test
    fun unicodeCompositionAndJapaneseMetadataRemainIntact() {
        assertEquals(candidateCanonicalNormalize("Cafe\u0301"),
            candidateCanonicalNormalize("Café"))
        assertEquals("夢中猫「夜」", candidateCanonicalNormalize("夢中猫「夜」"))
        assertNotEquals(candidateCanonicalNormalize("夢中猫"), candidateCanonicalNormalize("夢中犬"))
    }
}
