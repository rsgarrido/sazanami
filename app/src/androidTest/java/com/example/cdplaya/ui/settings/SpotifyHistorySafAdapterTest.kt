package com.example.cdplaya.ui.settings

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.example.cdplaya.data.importing.spotify.SpotifyExtendedStreamingParser
import com.example.cdplaya.data.importing.spotify.SpotifyFileParseResult
import com.example.cdplaya.data.importing.spotify.SpotifyParseControl
import java.io.ByteArrayInputStream
import java.io.FilterInputStream
import java.io.IOException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SpotifyHistorySafAdapterTest {
    private val contract = OpenSpotifyHistoryDocuments()

    @Test
    fun pickerUsesOpenDocumentOpenableMultiSelectAndReadGrant() {
        val intent = contract.createIntent(ApplicationProvider.getApplicationContext(), Unit)
        assertEquals(Intent.ACTION_OPEN_DOCUMENT, intent.action)
        assertTrue(intent.categories?.contains(Intent.CATEGORY_OPENABLE) == true)
        assertTrue(intent.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
        assertTrue(intent.flags and Intent.FLAG_GRANT_READ_URI_PERMISSION != 0)
        assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("application/json") == true)
        assertTrue(intent.getStringArrayExtra(Intent.EXTRA_MIME_TYPES)?.contains("text/plain") == true)
    }

    @Test
    fun oneAndMultipleSelectedDocumentsAreReturnedWithoutDuplicates() {
        val one = Uri.parse("content://test/one")
        val two = Uri.parse("content://test/two")
        assertEquals(
            listOf(one),
            contract.parseResult(Activity.RESULT_OK, Intent().setData(one))
        )

        val clip = ClipData.newRawUri("files", one).apply {
            addItem(ClipData.Item(two))
            addItem(ClipData.Item(one))
        }
        assertEquals(
            listOf(one, two),
            contract.parseResult(Activity.RESULT_OK, Intent().apply { clipData = clip })
        )
    }

    @Test
    fun sourceCanBeReopenedAndParserClosesEveryStream() {
        val bytes = """[{"ts":"2020-01-01T00:00:00Z","ms_played":1000,"master_metadata_track_name":"Song","master_metadata_album_artist_name":"Artist","master_metadata_album_album_name":"Album","spotify_track_uri":"spotify:track:abc"}]"""
            .toByteArray()
        var opened = 0
        var closed = 0
        val file = SafListeningHistoryImportFile(
            uri = Uri.parse("content://test/history"),
            displayName = "history.json"
        ) {
            opened++
            object : FilterInputStream(ByteArrayInputStream(bytes)) {
                override fun close() {
                    closed++
                    super.close()
                }
            }
        }
        val parser = SpotifyExtendedStreamingParser()
        repeat(2) {
            val result = parser.parse(file::openStream) { SpotifyParseControl.CONTINUE }
            assertTrue(result is SpotifyFileParseResult.Completed)
        }
        assertEquals(2, opened)
        assertEquals(2, closed)
    }

    @Test(expected = IOException::class)
    fun nullStreamBecomesSafeAccessFailure() {
        SafListeningHistoryImportFile(
            uri = Uri.parse("content://test/missing"),
            displayName = "missing.json",
            streamOpener = { null }
        ).openStream()
    }

    @Test
    fun displayNameFallbackDoesNotExposeUriOrPath() {
        assertEquals("Selected JSON file", safeDisplayName(null))
        assertEquals("Selected JSON file", safeDisplayName("  "))
        assertEquals("history.json", safeDisplayName(" history.json "))
    }
}
