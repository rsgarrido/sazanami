package io.github.rsgarrido.sazanami.data.backup

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupArchiveTest {
    @Test
    fun packageRoundTripKeepsMetadataAndBinaryVisualAssetEntries() {
        val metadata = BackupVisualAsset(
            ownerType = "ARTIST_IMAGE",
            ownerKey = "artist_abc",
            assetReference = "artist-abc-1.image",
            normalizedArtistName = "artist",
            thumbnailEntry = "visual_assets/artists/artist_abc/artist-abc-1.image/thumbnail.webp",
            displayEntry = "visual_assets/artists/artist_abc/artist-abc-1.image/display.webp"
        )
        val payload = BackupVisualAssetPayload(
            metadata = metadata,
            thumbnailBytes = byteArrayOf(1, 2, 3),
            displayBytes = byteArrayOf(4, 5, 6)
        )
        val backup = emptyBackup().copy(
            visualAssets = listOf(metadata),
            visualAssetPayloads = listOf(payload)
        )

        val output = ByteArrayOutputStream()
        BackupArchive.write(backup, output)
        val restored = BackupArchive.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(listOf(metadata), restored.visualAssets)
        assertEquals(1, restored.visualAssetPayloads.size)
        assertArrayEquals(payload.thumbnailBytes, restored.visualAssetPayloads.single().thumbnailBytes)
        assertArrayEquals(payload.displayBytes, restored.visualAssetPayloads.single().displayBytes)
    }

    @Test
    fun missingOptionalBinaryEntryDoesNotInvalidateDataBackup() {
        val metadata = BackupVisualAsset(
            ownerType = "PLAYLIST_IMAGE",
            ownerKey = "42",
            assetReference = "playlist-42-1.image",
            thumbnailEntry = "visual_assets/playlists/42/playlist-42-1.image/thumbnail.webp",
            displayEntry = "visual_assets/playlists/42/playlist-42-1.image/display.webp"
        )
        val output = ByteArrayOutputStream()
        BackupArchive.write(emptyBackup().copy(visualAssets = listOf(metadata)), output)

        val restored = BackupArchive.read(ByteArrayInputStream(output.toByteArray()))

        assertEquals(listOf(metadata), restored.visualAssets)
        assertTrue(restored.visualAssetPayloads.isEmpty())
    }

    @Test
    fun legacyJsonOnlyBackupRemainsReadable() {
        val backup = emptyBackup()
        val restored = BackupArchive.read(
            ByteArrayInputStream(AppBackupJson.encodeBackup(backup).toByteArray())
        )

        assertEquals(AppBackupJson.CURRENT_SCHEMA_VERSION, restored.schemaVersion)
        assertTrue(restored.visualAssets.isEmpty())
    }

    private fun emptyBackup() = AppBackup(
        createdAt = 1L,
        canonicalListeningHistory = BackupListeningHistoryV2()
    )
}
