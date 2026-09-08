package io.github.rsgarrido.sazanami.widget

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkContract
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkReference
import io.github.rsgarrido.sazanami.data.EmbeddedArtworkSource
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetProvider
import io.github.rsgarrido.sazanami.data.visual.VisualAssetStore
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException
import java.security.MessageDigest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingWidgetArtworkInstrumentedTest {
    @Test
    fun embeddedArtworkProviderIsHostSelectableReadableAndReadOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encoded = encodedArtwork(Color.CYAN)
        val artworkHash = MessageDigest.getInstance("SHA-256")
            .digest(encoded)
            .joinToString("") { byte -> "%02x".format(byte.toInt() and 0xff) }
        val reference = EmbeddedArtworkReference(
            source = EmbeddedArtworkSource(
                uri = Uri.parse("content://media/external/audio/media/42"),
                displayName = "widget-test.flac",
                dateModifiedEpochSeconds = 42L,
                fileSizeBytes = 1_024L
            ),
            artworkHash = artworkHash,
            extension = "png"
        )
        val embeddedFile = File(context.cacheDir, "embedded_album_art/${reference.fileName}")
        embeddedFile.parentFile?.mkdirs()
        embeddedFile.writeBytes(encoded)
        val sessionUri = EmbeddedArtworkContract.buildUri(context.packageName, reference)

        try {
            assertProviderExported(context, checkNotNull(sessionUri.authority))
            assertEquals(
                sessionUri,
                widgetHostArtworkUri(context.packageName, sessionUri.toString())
            )
            val decoded = context.contentResolver.openInputStream(sessionUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            assertNotNull(decoded)
            decoded?.recycle()
            assertReadOnly(context, sessionUri)
        } finally {
            embeddedFile.delete()
        }
    }

    @Test
    fun visualAssetProviderIsHostSelectableReadableAndReadOnly() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val encoded = encodedArtwork(Color.MAGENTA)
        val ownerKey = "widget-runtime-${System.nanoTime()}"
        val store = VisualAssetStore(context)
        val asset = store.restoreVariants(
            ownerType = VisualAssetOwnerType.ARTIST_IMAGE,
            ownerKey = ownerKey,
            thumbnailBytes = encoded,
            displayBytes = encoded
        )
        val sessionUri = VisualAssetProvider.uriFor(context.packageName, asset.identity)

        try {
            assertProviderExported(context, checkNotNull(sessionUri.authority))
            assertEquals(
                sessionUri,
                widgetHostArtworkUri(context.packageName, sessionUri.toString())
            )
            val decoded = context.contentResolver.openInputStream(sessionUri)?.use { stream ->
                BitmapFactory.decodeStream(stream)
            }
            assertNotNull(decoded)
            decoded?.recycle()
            assertReadOnly(context, sessionUri)
        } finally {
            store.delete(VisualAssetOwnerType.ARTIST_IMAGE, ownerKey, asset.reference)
        }
    }

    @Test
    fun missingOrNonNormalizedArtworkUsesPackagedPlaceholder() {
        val context = ApplicationProvider.getApplicationContext<Context>()

        assertNull(widgetHostArtworkUri(context.packageName, null))
        assertNull(widgetHostArtworkUri(context.packageName, "file:///private/artwork.webp"))
        assertNull(widgetHostArtworkUri(context.packageName, "content://other.provider/art.webp"))
        assertEquals(
            WidgetArtworkPresentation.PLACEHOLDER,
            widgetArtworkPresentation(artworkUriAvailable = false)
        )
    }

    private fun encodedArtwork(color: Int): ByteArray {
        val bitmap = Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
            eraseColor(color)
        }
        return try {
            ByteArrayOutputStream().use { output ->
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output))
                output.toByteArray()
            }
        } finally {
            bitmap.recycle()
        }
    }

    private fun assertReadOnly(context: Context, uri: Uri) {
        try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            fail("Artwork provider unexpectedly accepted write access")
        } catch (_: FileNotFoundException) {
            // Expected: both artwork providers accept only mode "r".
        } catch (_: SecurityException) {
            // Also acceptable for a provider rejecting write access at the framework boundary.
        }
    }

    @Suppress("DEPRECATION")
    private fun assertProviderExported(context: Context, authority: String) {
        val provider = context.packageManager.resolveContentProvider(authority, 0)
        assertNotNull(provider)
        assertTrue(provider?.exported == true)
    }
}
