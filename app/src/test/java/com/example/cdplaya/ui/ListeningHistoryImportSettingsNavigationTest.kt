package com.example.cdplaya.ui

import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ListeningHistoryImportSettingsNavigationTest {
    @Test
    fun importUsesDedicatedDestinationAndKeepsSettingsDestinationAvailable() {
        assertNotEquals(
            MusicPrimaryDestination.SETTINGS,
            MusicPrimaryDestination.LISTENING_HISTORY_IMPORT
        )
        assertTrue(MusicPrimaryDestination.entries.contains(MusicPrimaryDestination.SETTINGS))
        assertTrue(
            MusicPrimaryDestination.entries.contains(
                MusicPrimaryDestination.LISTENING_HISTORY_IMPORT
            )
        )
    }

    @Test
    fun matchingUsesDedicatedDestinationWithoutReplacingImportOrSettings() {
        assertNotEquals(
            MusicPrimaryDestination.SETTINGS,
            MusicPrimaryDestination.LISTENING_HISTORY_RECONCILIATION
        )
        assertNotEquals(
            MusicPrimaryDestination.LISTENING_HISTORY_IMPORT,
            MusicPrimaryDestination.LISTENING_HISTORY_RECONCILIATION
        )
        assertTrue(
            MusicPrimaryDestination.entries.contains(
                MusicPrimaryDestination.LISTENING_HISTORY_RECONCILIATION
            )
        )
    }
}
