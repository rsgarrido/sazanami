package io.github.rsgarrido.sazanami.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ListeningMediaItemMetadataTest {
    @Test
    fun validIdentityAndNullableBindingEvidenceRoundTripsThroughPureValues() {
        val values = validValues()
        val parsed = requireNotNull(ListeningMediaItemMetadata.fromValues(values))

        assertEquals("attempt-1", parsed.itemInstanceId)
        assertEquals("local:v1:first", parsed.referenceKey)
        assertEquals(42L, parsed.reference.mediaStoreId)
        assertEquals("Title", parsed.reference.title)
    }

    @Test
    fun missingOrMalformedStableIdsAreRejectedSafely() {
        assertNull(ListeningMediaItemMetadata.fromValues(validValues() - ListeningMediaItemMetadata.ITEM_INSTANCE_ID))
        assertNull(ListeningMediaItemMetadata.fromValues(validValues() + (ListeningMediaItemMetadata.REFERENCE_KEY to 99L)))
        assertNull(
            requireNotNull(
                ListeningMediaItemMetadata.fromValues(
                    validValues() + (ListeningMediaItemMetadata.MEDIA_STORE_ID to "42")
                )
            ).reference.mediaStoreId
        )
        assertNull(ListeningMediaItemMetadata.fromValues(validValues() + (ListeningMediaItemMetadata.DURATION_MS to -1L)))
        assertNull(ListeningMediaItemMetadata.fromValues(validValues() + (ListeningMediaItemMetadata.PORTABLE_KEY_VERSION to "1")))
    }

    @Test
    fun duplicateLookingTracksRemainDistinctByExactLocalReference() {
        val first = requireNotNull(ListeningMediaItemMetadata.fromValues(validValues()))
        val second = requireNotNull(
            ListeningMediaItemMetadata.fromValues(
                validValues() + mapOf(
                    ListeningMediaItemMetadata.ITEM_INSTANCE_ID to "attempt-2",
                    ListeningMediaItemMetadata.REFERENCE_KEY to "local:v1:second",
                    ListeningMediaItemMetadata.MEDIA_STORE_ID to 43L
                )
            )
        )

        assertEquals(first.reference.title, second.reference.title)
        assertEquals(first.reference.artist, second.reference.artist)
        assertNotEquals(first.referenceKey, second.referenceKey)
        assertNotEquals(first.reference.mediaStoreId, second.reference.mediaStoreId)
    }

    private fun validValues(): Map<String, Any?> = mapOf(
        ListeningMediaItemMetadata.ITEM_INSTANCE_ID to "attempt-1",
        ListeningMediaItemMetadata.REFERENCE_KEY to "local:v1:first",
        ListeningMediaItemMetadata.MEDIA_STORE_ID to 42L,
        ListeningMediaItemMetadata.VOLUME_NAME to "external",
        ListeningMediaItemMetadata.CONTENT_URI to "content://media/42",
        ListeningMediaItemMetadata.RELATIVE_PATH to "Music/",
        ListeningMediaItemMetadata.DISPLAY_NAME to "song.flac",
        ListeningMediaItemMetadata.FILE_SIZE_BYTES to 1_000L,
        ListeningMediaItemMetadata.DATE_MODIFIED_SECONDS to 2_000L,
        ListeningMediaItemMetadata.DURATION_MS to 60_000L,
        ListeningMediaItemMetadata.TITLE to "Title",
        ListeningMediaItemMetadata.ARTIST to "Artist",
        ListeningMediaItemMetadata.ALBUM to "Album",
        ListeningMediaItemMetadata.ALBUM_ARTIST to "Album Artist",
        ListeningMediaItemMetadata.LEGACY_STABLE_KEY to "legacy",
        ListeningMediaItemMetadata.PORTABLE_KEY to "portable:v1:key",
        ListeningMediaItemMetadata.PORTABLE_KEY_VERSION to 1
    )
}
