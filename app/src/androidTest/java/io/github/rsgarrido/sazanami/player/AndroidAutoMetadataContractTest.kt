package io.github.rsgarrido.sazanami.player

import android.app.SearchManager
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.media3.common.MediaItem
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.data.visual.AndroidAutoArtworkCache
import io.github.rsgarrido.sazanami.data.visual.VisualAssetOwnerType
import io.github.rsgarrido.sazanami.data.visual.VisualAssetProvider
import io.github.rsgarrido.sazanami.data.visual.VisualAssetStore
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.ByteArrayOutputStream

@RunWith(AndroidJUnit4::class)
class AndroidAutoMetadataContractTest {
    @Test
    fun activityAndSessionRequestsPreserveSameFocusedVoiceIntent() {
        val extras = Bundle().apply {
            putString(SearchManager.QUERY, "The Warning")
            putString(MediaStore.EXTRA_MEDIA_FOCUS, "vnd.android.cursor.item/artist")
            putString(MediaStore.EXTRA_MEDIA_ARTIST, "The Warning")
            putString(MediaStore.EXTRA_MEDIA_TITLE, "Unrelated title")
        }
        val activity = Intent(MediaStore.INTENT_ACTION_MEDIA_PLAY_FROM_SEARCH).putExtras(extras)
            .androidAutoVoiceItem()!!.toAndroidAutoSearchRequest()
        val session = MediaItem.Builder().setRequestMetadata(
            MediaItem.RequestMetadata.Builder().setExtras(extras).build()
        ).build().toAndroidAutoSearchRequest()
        assertEquals(activity, session)
        assertEquals("The Warning", session.artist)
        assertNull(session.title)
        assertNull(Intent(Intent.ACTION_MAIN).androidAutoVoiceItem())
    }

    @Test
    fun coldProviderCanOpenArtworkPublishedInPlayableMetadata() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val bitmap = Bitmap.createBitmap(4, 4, Bitmap.Config.ARGB_8888)
        val bytes = ByteArrayOutputStream().use { output ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, output)
            output.toByteArray()
        }
        bitmap.recycle()
        val store = VisualAssetStore(context)
        val asset = store.restoreVariants(VisualAssetOwnerType.ARTIST_IMAGE,
            "auto-contract-${System.nanoTime()}", bytes, bytes)
        try {
            val uri = VisualAssetProvider.uriFor(context.packageName, asset.identity)
            assertEquals(uri, AndroidAutoArtworkCache(context).externallyReadableUri(uri))
            val song = Song(id = 1, title = "Song", artist = "Artist", album = "Album",
                trackNumber = 1, duration = 1000, uri = Uri.parse("content://media/external/audio/media/1"),
                filePath = "/music/song.flac", folderPath = "/music", albumArtUri = uri)
            val item = song.toPlayableMediaItem()
            assertEquals(uri, item.mediaMetadata.artworkUri)
            context.contentResolver.openFileDescriptor(uri, "r")!!.use { assertTrue(it.statSize > 0) }
            assertNull(AndroidAutoArtworkCache(context).externallyReadableUri(null))
        } finally {
            asset.thumbnailFile.delete()
            asset.displayFile.delete()
            asset.thumbnailFile.parentFile?.delete()
        }
    }
}
