package io.github.rsgarrido.sazanami.player

import androidx.media3.common.MediaMetadata
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AndroidAutoRequestMetadataTest {
    @Test
    fun `plain song query preserves raw query for ranking`() {
        val request = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(query = "Play Shattered Heart by The Warning on Sazanami")
        )

        assertEquals(AndroidAutoRequestType.UNKNOWN, request.requestType)
        assertEquals("Play Shattered Heart by The Warning on Sazanami", request.query)
        assertNull(request.title)
    }

    @Test
    fun `song focus parses title and artist from legacy query`() {
        val request = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(
                query = "Shattered Heart by The Warning",
                focus = "vnd.android.cursor.item/audio"
            )
        )

        assertEquals(AndroidAutoRequestType.SONG, request.requestType)
        assertEquals("Shattered Heart", request.title)
        assertEquals("The Warning", request.artist)
    }

    @Test
    fun `artist focus accepts the query when artist extra is absent`() {
        val request = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(
                query = "Play Avril Lavigne on Sazanami",
                focus = "vnd.android.cursor.item/artist"
            )
        )

        assertEquals(AndroidAutoRequestType.ARTIST, request.requestType)
        assertEquals("Avril Lavigne", request.artist)
    }

    @Test
    fun `album focus splits album and artist from query`() {
        val request = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(
                query = "The Best Damn Thing by Avril Lavigne on Sazanami",
                focus = "vnd.android.cursor.item/album"
            )
        )

        assertEquals(AndroidAutoRequestType.ALBUM, request.requestType)
        assertEquals("The Best Damn Thing", request.album)
        assertEquals("Avril Lavigne", request.artist)
    }

    @Test
    fun `media metadata type supplies artist and album focus without extras`() {
        val artist = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(
                title = "Avril Lavigne",
                mediaType = MediaMetadata.MEDIA_TYPE_ARTIST
            )
        )
        val album = parseAndroidAutoRequest(
            AndroidAutoRequestPayload(
                title = "The Best Damn Thing",
                artist = "Avril Lavigne",
                mediaType = MediaMetadata.MEDIA_TYPE_ALBUM
            )
        )

        assertEquals("Avril Lavigne", artist.artist)
        assertEquals("The Best Damn Thing", album.album)
        assertEquals("Avril Lavigne", album.artist)
    }

    @Test
    fun `playlist genre and media id request types are retained`() {
        assertEquals("Road Trip", parseAndroidAutoRequest(AndroidAutoRequestPayload(
            query = "Road Trip", focus = "vnd.android.cursor.item/playlist"
        )).playlist)
        assertEquals("Rock", parseAndroidAutoRequest(AndroidAutoRequestPayload(
            title = "Rock", mediaType = MediaMetadata.MEDIA_TYPE_GENRE
        )).genre)
        assertEquals(AndroidAutoRequestType.MEDIA_ID, parseAndroidAutoRequest(
            AndroidAutoRequestPayload(mediaId = "song:42")
        ).requestType)
    }

    @Test
    fun `generic request remains a library fallback`() {
        assertEquals(AndroidAutoRequestType.GENERIC, parseAndroidAutoRequest(
            AndroidAutoRequestPayload(query = "Play my music on Sazanami")
        ).requestType)
    }
}
