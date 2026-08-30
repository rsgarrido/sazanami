package io.github.rsgarrido.sazanami.data

import org.junit.Assert.assertEquals
import org.junit.Test

class EmbeddedArtworkFormatTest {
    @Test
    fun detectsPngSignature() {
        val bytes = byteArrayOf(
            0x89.toByte(), 0x50, 0x4e, 0x47, 0x0d, 0x0a, 0x1a, 0x0a
        )

        assertEquals("png", embeddedArtworkExtension(bytes))
    }

    @Test
    fun detectsWebpSignature() {
        val bytes = "RIFF0000WEBP".toByteArray()

        assertEquals("webp", embeddedArtworkExtension(bytes))
    }

    @Test
    fun defaultsToJpegForJpegAndUnknownFrameworkPayloads() {
        assertEquals(
            "jpg",
            embeddedArtworkExtension(
                byteArrayOf(0xff.toByte(), 0xd8.toByte(), 0xff.toByte())
            )
        )
        assertEquals("jpg", embeddedArtworkExtension(byteArrayOf(1, 2, 3)))
    }
}
