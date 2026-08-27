package com.example.cdplaya.ui.tageditor

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TagEditorAdvancedMetadataTest {
    @Test
    fun `BPM accepts blank or conservative positive whole numbers`() {
        assertTrue(isValidEditableBpm(""))
        assertTrue(isValidEditableBpm(" 120 "))
        assertTrue(isValidEditableBpm("999"))
    }

    @Test
    fun `BPM rejects zero negative decimal text and excessive values`() {
        assertFalse(isValidEditableBpm("0"))
        assertFalse(isValidEditableBpm("-90"))
        assertFalse(isValidEditableBpm("120.5"))
        assertFalse(isValidEditableBpm("fast"))
        assertFalse(isValidEditableBpm("1000"))
    }

    @Test
    fun `untouched legacy BPM does not block an unrelated edit`() {
        assertFalse(hasInvalidChangedBpm(originalBpm = "fast", editedBpm = "fast"))
        assertTrue(hasInvalidChangedBpm(originalBpm = "fast", editedBpm = "0"))
        assertFalse(hasInvalidChangedBpm(originalBpm = "fast", editedBpm = "120"))
    }
}
