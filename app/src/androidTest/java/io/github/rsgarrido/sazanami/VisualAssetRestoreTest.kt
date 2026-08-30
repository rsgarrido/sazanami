package io.github.rsgarrido.sazanami

import android.util.Base64
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetStore
import io.github.rsgarrido.sazanami.data.visual.VisualAssetVariant
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class VisualAssetRestoreTest {
    @Test
    fun restoredVariantsPublishUnderFreshManagedReference() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = VisualAssetStore(context)
        val bytes = Base64.decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=",
            Base64.DEFAULT
        )
        val first = store.restoreVariants(
            VisualAssetOwnerType.ARTIST_IMAGE,
            "artist_restore_test",
            bytes,
            bytes
        )
        val second = store.restoreVariants(
            VisualAssetOwnerType.ARTIST_IMAGE,
            "artist_restore_test",
            bytes,
            bytes
        )
        try {
            assertNotEquals(first.reference, second.reference)
            assertNotNull(store.file(
                VisualAssetOwnerType.ARTIST_IMAGE,
                "artist_restore_test",
                second.reference,
                VisualAssetVariant.THUMBNAIL
            ))
            assertNotNull(store.file(
                VisualAssetOwnerType.ARTIST_IMAGE,
                "artist_restore_test",
                second.reference,
                VisualAssetVariant.DISPLAY
            ))
        } finally {
            store.delete(VisualAssetOwnerType.ARTIST_IMAGE, "artist_restore_test", first.reference)
            store.delete(VisualAssetOwnerType.ARTIST_IMAGE, "artist_restore_test", second.reference)
        }
    }
}
