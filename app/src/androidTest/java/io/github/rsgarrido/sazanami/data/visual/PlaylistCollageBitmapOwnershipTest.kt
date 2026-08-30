package io.github.rsgarrido.sazanami.data.visual

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import androidx.core.graphics.drawable.toBitmap
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class PlaylistCollageBitmapOwnershipTest {
    @Test
    fun bitmapDrawableExtractionMayAliasSharedBitmapAndRendererLeavesItValid() {
        val resources = InstrumentationRegistry.getInstrumentation().targetContext.resources
        val shared = Bitmap.createBitmap(24, 24, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.MAGENTA)
        }
        val extracted = BitmapDrawable(resources, shared).toBitmap(
            width = shared.width,
            height = shared.height,
            config = shared.config
        )

        // This is the ownership shape returned by Coil for a cached BitmapDrawable.
        assertSame(shared, extracted)
        val rendered = PlaylistCollageRenderer.render(listOf(extracted), size = 48)

        assertFalse(shared.isRecycled)
        assertFalse(rendered.isRecycled)
        assertTrue(shared.getPixel(0, 0) == Color.MAGENTA)
    }

    @Test
    fun multipleCollageConsumersCanReuseOneSharedSource() {
        val shared = Bitmap.createBitmap(32, 16, Bitmap.Config.ARGB_8888).apply {
            eraseColor(Color.CYAN)
        }

        val first = PlaylistCollageRenderer.render(listOf(shared), size = 64)
        val second = PlaylistCollageRenderer.render(listOf(shared, shared), size = 64)

        assertFalse(shared.isRecycled)
        assertFalse(first.isRecycled)
        assertFalse(second.isRecycled)
        assertTrue(shared.getPixel(0, 0) == Color.CYAN)
    }

    @Test
    fun cancellationAfterRenderingDoesNotInvalidateSharedOrRenderedBitmaps() = runBlocking {
        val shared = Bitmap.createBitmap(20, 20, Bitmap.Config.ARGB_8888)
        lateinit var rendered: Bitmap
        val generation = launch(start = CoroutineStart.UNDISPATCHED) {
            rendered = PlaylistCollageRenderer.render(listOf(shared), size = 40)
            awaitCancellation()
        }

        generation.cancelAndJoin()

        assertFalse(shared.isRecycled)
        assertFalse(rendered.isRecycled)
    }
}
