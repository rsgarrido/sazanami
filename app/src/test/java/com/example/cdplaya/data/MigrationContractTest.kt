package com.example.cdplaya.data

import com.example.cdplaya.data.backup.AppBackupJson
import com.example.cdplaya.data.backup.BackupSongRatings
import com.example.cdplaya.data.local.DatabaseProvider
import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationContractTest {
    @Test
    fun databaseAndBackupVersionContractsRemainExplicit() {
        assertEquals(9, DatabaseProvider.MIGRATION_9_10.startVersion)
        assertEquals(10, DatabaseProvider.MIGRATION_9_10.endVersion)
        assertEquals(10, DatabaseProvider.MIGRATION_10_11.startVersion)
        assertEquals(11, DatabaseProvider.MIGRATION_10_11.endVersion)
        assertEquals(11, DatabaseProvider.MIGRATION_11_12.startVersion)
        assertEquals(12, DatabaseProvider.MIGRATION_11_12.endVersion)
        assertEquals(12, DatabaseProvider.MIGRATION_12_13.startVersion)
        assertEquals(13, DatabaseProvider.MIGRATION_12_13.endVersion)
        assertEquals(13, DatabaseProvider.MIGRATION_13_14.startVersion)
        assertEquals(14, DatabaseProvider.MIGRATION_13_14.endVersion)
        assertEquals(14, DatabaseProvider.MIGRATION_14_15.startVersion)
        assertEquals(15, DatabaseProvider.MIGRATION_14_15.endVersion)
        assertEquals(15, DatabaseProvider.MIGRATION_15_16.startVersion)
        assertEquals(16, DatabaseProvider.MIGRATION_15_16.endVersion)
        assertEquals(14, AppBackupJson.CURRENT_SCHEMA_VERSION)
        assertEquals(2, com.example.cdplaya.data.backup.BackupListeningHistoryV2.CURRENT_FORMAT_VERSION)
        assertEquals(1, BackupSongRatings.CURRENT_FORMAT_VERSION)
    }
}
