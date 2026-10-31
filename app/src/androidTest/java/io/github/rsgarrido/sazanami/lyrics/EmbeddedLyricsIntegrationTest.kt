package io.github.rsgarrido.sazanami.lyrics

import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.system.Os
import android.system.OsConstants
import androidx.activity.ComponentActivity
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.test.platform.app.InstrumentationRegistry
import io.github.rsgarrido.sazanami.data.Song
import io.github.rsgarrido.sazanami.ui.lyrics.LyricsScreen
import io.github.rsgarrido.sazanami.ui.player.rememberPlayerLyricsTransitionState
import io.github.rsgarrido.sazanami.ui.state.PlaybackUiState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.io.File
import java.io.RandomAccessFile
import java.util.ArrayDeque
import java.util.UUID

class EmbeddedLyricsIntegrationTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val failures = ArrayDeque<String>()
    private val diagnostics = EmbeddedLyricsDiagnostics { stage, failure ->
        synchronized(failures) {
            if (failures.size == 8) failures.removeFirst()
            failures.addLast("$stage: ${failure.stackTraceToString().take(4096)}")
        }
    }
    private val reader = AndroidEmbeddedLyricsReader(context.contentResolver, Dispatchers.IO, diagnostics = diagnostics)
    private val fixtureDirectory = File(context.cacheDir, "lyrics-integration-${UUID.randomUUID()}")

    @Before fun registerAudioFixtures() {
        context.contentResolver.call(LyricsFixtureProvider.uri(""), LyricsFixtureProvider.CLEAR_AUDIO, null, null)
        assertTrue("Create target-owned audio fixture directory", fixtureDirectory.mkdir())
        val assets = InstrumentationRegistry.getInstrumentation().context.assets
        LyricsFixtureProvider.FIXTURES.filterNot { it.endsWith(".lrc") }.forEach { name ->
            val file = File(fixtureDirectory, name)
            assets.open(name).use { input -> file.outputStream().use { input.copyTo(it) } }
            ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY).use { descriptor ->
                val extras = Bundle().apply { putParcelable(LyricsFixtureProvider.DESCRIPTOR, descriptor) }
                val reply = context.contentResolver.call(LyricsFixtureProvider.uri(name),
                    LyricsFixtureProvider.REGISTER_AUDIO, name, extras)
                assertTrue("Provider registered $name", reply?.getBoolean(LyricsFixtureProvider.REGISTERED) == true)
            }
        }
    }

    @After fun tearDown() {
        scope.cancel()
        try {
            context.contentResolver.call(LyricsFixtureProvider.uri(""), LyricsFixtureProvider.CLEAR_AUDIO, null, null)
        } finally {
            fixtureDirectory.listFiles()?.forEach { it.delete() }
            fixtureDirectory.delete()
        }
    }

    @Test fun contentResolverReadsSupportedMetadataContainersThroughBorrowedReadOnlyDescriptors() = runBlocking {
        listOf("mp3", "flac", "ogg", "m4a", "wav", "aiff").forEachIndexed { index, extension ->
            val song = song(index.toLong() + 1, "synthetic.$extension")
            val direct = FileEmbeddedLyricsReader().read(File(fixtureDirectory, "synthetic.$extension"),
                song.toLyricsIdentity().trackKey, extension, diagnostics)
            assertEquals(details("$extension direct-file control: $direct"), 1, direct.candidates.size)
            assertProviderDescriptor("synthetic.$extension")
            val result = reader.read(song.toLyricsIdentity())
            assertEquals(details("$extension descriptor extraction: $result"), 1, result.candidates.size)
            assertTrue(result.candidates.single().text.contains("Paper"))
        }
    }

    @Test fun permissionLossMissingDocumentsAndNonSeekableProvidersProduceTypedResults() = runBlocking {
        fun identity(name: String) = song(1, "synthetic.mp3").copy(uri = LyricsFixtureProvider.uri(name)).toLyricsIdentity()
        assertIssue(reader.read(identity("denied")), LyricsSourceIssue.Kind.PERMISSION_DENIED)
        assertIssue(reader.read(identity("missing")), LyricsSourceIssue.Kind.READ_FAILURE)
        assertIssue(reader.read(identity("pipe")), LyricsSourceIssue.Kind.UNSUPPORTED_ACCESS)
        assertEquals(LyricsDocumentReadResult.PermissionLost, AndroidLyricsDocumentReader(context.contentResolver).read(LyricsFixtureProvider.uri("denied").toString()))
        assertEquals(LyricsDocumentReadResult.TooLarge, AndroidLyricsDocumentReader(context.contentResolver, maximumBytes = 4)
            .read(LyricsFixtureProvider.uri("sidecar.lrc").toString()))
    }

    @Test fun sharedScreenLoadsEmbeddedPlainAndEnhancedTextWithoutFoldersAndSurvivesTrackChanges() {
        val repository = repository()
        val playback = MutableStateFlow(PlaybackUiState())
        lateinit var controller: LyricsPlaybackController
        compose.runOnUiThread {
            controller = LyricsPlaybackController(repository, playback, LyricsPositionSource { 1500L }, scope)
            controller.setVisible(true)
        }
        compose.setContent {
            val state by controller.uiState.collectAsState()
            MaterialTheme {
                LyricsScreen(state, false, rememberPlayerLyricsTransitionState(true) {}, interactive = true,
                    onBack = {}, onPlayPause = {}, onSeek = {}, onSuspendAutoFollow = controller::suspendAutoFollow,
                    onReturnToCurrentLine = controller::returnToCurrentLine, onRescan = controller::rescan, onOpenSettings = {})
            }
        }
        compose.runOnUiThread { playback.value = PlaybackUiState(currentSong = song(1, "plain.mp3")) }
        compose.waitUntil(5_000) {
            val state = controller.uiState.value
            assertFalse(details("Expected embedded plain lyrics; actual state: $state"), state is LyricsPlaybackUiState.Unavailable)
            state is LyricsPlaybackUiState.Unsynced
        }
        compose.onNodeWithText("Plain original line").assertIsDisplayed()
        compose.runOnUiThread { playback.value = PlaybackUiState(currentSong = song(2, "synthetic.mp3")) }
        compose.waitUntil(5_000) {
            val state = controller.uiState.value
            assertFalse(details("Expected embedded enhanced lyrics; actual state: $state"), state is LyricsPlaybackUiState.Unavailable)
            state is LyricsPlaybackUiState.Synced
        }
        compose.onNodeWithText("Paper lanterns").assertIsDisplayed()
        val state = controller.uiState.value as LyricsPlaybackUiState.Synced
        assertEquals(LyricsTiming.WORD, state.sourcedLyrics?.timing)
        assertEquals(listOf("Paper lanterns"), state.activeGroup?.lines)
        assertTrue(details("Expected embedded screen source; actual: ${state.sourcedLyrics}"), state.sourcedLyrics?.source is LyricsSource.Embedded)
        compose.runOnUiThread { playback.value = PlaybackUiState(currentSong = null) }
        compose.waitUntil(5_000) { controller.uiState.value is LyricsPlaybackUiState.Hidden }
    }

    @Test fun actualExternalStreamsHonorTimedPriorityAndEmbeddedFallback() = runBlocking {
        val timed = repository("sidecar.lrc")
        val external = assertFound("External timed priority", timed.findLyrics(song(1, "synthetic.mp3").toLyricsIdentity()))
        assertTrue(details("Expected external timed source; actual: $external"), external.lyrics.source is LyricsSource.LocalSidecar)
        assertEquals("External original line", (external.lyrics.document as LyricsDocument.Synced).cues.first().let { (it.content as LyricCueContent.Text).text })
        val plain = repository("plain.lrc")
        val embedded = assertFound("Embedded timed fallback", plain.findLyrics(song(1, "synthetic.mp3").toLyricsIdentity()))
        assertTrue(details("Expected embedded timed source over external plain; actual: $embedded"), embedded.lyrics.source is LyricsSource.Embedded)
        assertEquals(LyricsTiming.WORD, embedded.lyrics.timing)
        val externalPlain = assertFound("External plain priority",
            plain.findLyrics(song(2, "plain.mp3").copy(displayName = "synthetic.mp3").toLyricsIdentity()))
        assertTrue(details("Expected external plain source; actual: $externalPlain"), externalPlain.lyrics.source is LyricsSource.LocalSidecar)
        assertEquals(LyricsTiming.PLAIN, externalPlain.lyrics.timing)
        val missing = assertFound("Missing sidecar falls back to embedded",
            repository("missing.lrc").findLyrics(song(3, "synthetic.mp3").toLyricsIdentity()))
        assertTrue(details("Expected embedded fallback for missing sidecar; actual: $missing"), missing.lyrics.source is LyricsSource.Embedded)
        assertEquals(LyricsTiming.WORD, missing.lyrics.timing)
        assertTrue(details("Expected missing sidecar diagnostic; actual: $missing"),
            missing.lyrics.issues.any { it.kind == LyricsSourceIssue.Kind.STALE_FILE })
    }

    private fun details(message: String): String = synchronized(failures) {
        "$message\nEmbedded read failures (latest eight, test fixtures only):\n${failures.joinToString("\n")}"
    }

    private fun <T> boundary(label: String, block: () -> T): T = try {
        block()
    } catch (failure: Exception) {
        throw AssertionError("$label failed", failure)
    }

    private fun assertProviderDescriptor(name: String) {
        val expected = InstrumentationRegistry.getInstrumentation().context.assets.open(name).use { it.readBytes() }
        val descriptor = boundary("$name provider open") {
            requireNotNull(context.contentResolver.openFileDescriptor(LyricsFixtureProvider.uri(name), "r"))
        }
        descriptor.use {
            boundary("$name descriptor mode, ownership and seekability") {
                assertEquals(expected.size.toLong(), it.statSize)
                assertEquals(Os.getuid(), Os.fstat(it.fileDescriptor).st_uid)
                assertEquals(OsConstants.O_RDONLY, Os.fcntlInt(it.fileDescriptor, OsConstants.F_GETFL, 0) and OsConstants.O_ACCMODE)
                assertEquals(0L, Os.lseek(it.fileDescriptor, 0, OsConstants.SEEK_SET))
            }
            val borrowedBytes = boundary("$name borrowed descriptor read") {
                val bytes = ByteArray(expected.size)
                var offset = 0
                while (offset < bytes.size) {
                    val count = Os.pread(it.fileDescriptor, bytes, offset, bytes.size - offset, offset.toLong())
                    check(count > 0) { "Unexpected descriptor EOF at $offset" }
                    offset += count
                }
                bytes
            }
            assertArrayEquals("$name borrowed descriptor bytes", expected, borrowedBytes)
            val reopenedBytes = boundary("$name /proc descriptor-path reopen") {
                RandomAccessFile("/proc/self/fd/${it.fd}", "r").use { input ->
                    assertEquals(expected.size.toLong(), input.length())
                    ByteArray(expected.size).also(input::readFully)
                }
            }
            assertArrayEquals("$name reopened descriptor bytes", expected, reopenedBytes)
        }
    }

    private fun assertIssue(result: EmbeddedLyricsReadResult, expected: LyricsSourceIssue.Kind) {
        assertTrue(details("Expected $expected; actual embedded result: $result"), result.issues.any { it.kind == expected })
    }

    private fun assertFound(label: String, result: LyricsLookupResult): LyricsLookupResult.Found {
        assertTrue(details("$label expected Found; actual lookup result: $result"), result is LyricsLookupResult.Found)
        return result as LyricsLookupResult.Found
    }

    private fun repository(external: String? = null): LocalLyricsRepository {
        val root = LyricsRoot("content://synthetic-root", "Synthetic")
        val roots = object : LyricsRootStore {
            override val roots = MutableStateFlow(if (external == null) emptyList() else listOf(root))
            override suspend fun addRoot(root: LyricsRoot) { roots.value = roots.value + root }
            override suspend fun removeRoot(rootUri: String) { roots.value = roots.value.filterNot { it.uri == rootUri } }
        }
        val files = if (external == null) emptyList() else listOf(IndexedLyricsFile(
            LyricsFixtureProvider.uri(external).toString(), root.uri, "synthetic.lrc", "synthetic", ""))
        val index = object : LyricsIndexStore {
            var value: LyricsIndexSnapshot? = LyricsIndexSnapshot(files, roots.roots.value.map { it.uri }.toSet(), generatedAtEpochMs = 1)
            override suspend fun load() = value
            override suspend fun save(snapshot: LyricsIndexSnapshot) { value = snapshot }
            override suspend fun clear() { value = null }
        }
        val tree = object : LyricsTreeDataSource {
            override suspend fun scanRoot(root: LyricsRoot) = LyricsTreeScanResult.Success(files)
        }
        return DefaultLocalLyricsRepository(roots, index, LyricsIndexer(roots, tree, index, Dispatchers.IO),
            AndroidLyricsDocumentReader(context.contentResolver), LrcParser(), Dispatchers.IO, reader)
    }

    private fun song(id: Long, name: String) = Song(id, "Synthetic $id", "Original author", "Test album", 1,
        10_000, LyricsFixtureProvider.uri(name), "", "", null, volumeName = "primary", displayName = name)
}
