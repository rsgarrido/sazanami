package com.example.cdplaya.data

import android.net.Uri
import org.jaudiotagger.tag.FieldKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotSame
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BatchMetadataExecutorTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun `execution freezes the reviewed plan before collaborators can mutate editor values`() {
        val mutableGenres = mutableListOf("Rock", "Alternative")
        val plan = plan(
            target("one"),
            changes = mapOf(BatchMetadataField.GENRE to set(multi(mutableGenres)))
        )
        var writtenValues = emptyList<String>()
        val executor = executor(
            capabilities = {
                mutableGenres.clear()
                mutableGenres += "Changed after confirmation"
                MetadataFormatCapabilities.ALL_EDITABLE
            },
            writer = { _, edits, _ ->
                writtenValues = edits.getValue(FieldKey.GENRE).values
                success()
            }
        )

        val result = executor.execute(plan, listOf(song("one")), null, BatchCancellationSignal())

        assertEquals(listOf("Rock", "Alternative"), writtenValues)
        assertEquals(listOf("Rock", "Alternative"),
            (result.frozenPlan.fieldChanges.getValue(BatchMetadataField.GENRE).intent as
                BatchEditIntent.Set).value.let { it as BatchMetadataValue.MultiValue }.values)
    }

    @Test
    fun `valid missing and mismatched stable targets are resolved without close-enough fallback`() {
        val file = temporaryFolder.newFile("track.flac").apply { writeBytes(byteArrayOf(1, 2, 3)) }
        val current = song(
            key = "valid",
            path = file.absolutePath,
            size = file.length(),
            modified = file.lastModified() / 1_000L
        )
        val valid = targetFrom(current)
        val missing = valid.copy(referenceKey = "missing")
        val replaced = valid.copy(fileSizeBytes = valid.fileSizeBytes + 1L)
        val replacementUri = mock(Uri::class.java)
        `when`(replacementUri.toString()).thenReturn("content://media/external/audio/replacement")
        val replacedMediaRow = current.copy(uri = replacementUri)
        val resolver = LibraryBatchTargetResolver()

        assertTrue(resolver.resolve(valid, listOf(current)) is BatchTargetResolution.Resolved)
        assertTrue(resolver.resolve(missing, listOf(current)) is BatchTargetResolution.Missing)
        assertTrue(resolver.resolve(replaced, listOf(current)) is BatchTargetResolution.Mismatch)
        assertTrue(
            resolver.resolve(valid, listOf(replacedMediaRow)) is BatchTargetResolution.Mismatch
        )
    }

    @Test
    fun `capability recheck prevents the entire target patch`() {
        var writeCount = 0
        val executor = executor(
            capabilities = { MetadataFormatCapabilities.NONE },
            writer = { _, _, _ -> writeCount++; success() }
        )

        val result = executor.execute(
            plan(target("one"), changes = mapOf(BatchMetadataField.COMMENT to clear())),
            listOf(song("one")),
            null,
            BatchCancellationSignal()
        )

        assertEquals(0, writeCount)
        assertEquals(BatchTargetStatus.UNSUPPORTED, result.targetResults.single().status)
    }

    @Test
    fun `missing target gets a per-file result and is never written`() {
        var writeCount = 0
        val executor = executor(writer = { _, _, _ -> writeCount++; success() })

        val result = executor.execute(
            plan(target("missing"), changes = mapOf(BatchMetadataField.ALBUM to set(text("New")))),
            currentSongs = emptyList(),
            preparedArtwork = null,
            cancellationSignal = BatchCancellationSignal()
        )

        assertEquals(0, writeCount)
        assertEquals(BatchTargetStatus.MISSING, result.targetResults.single().status)
    }

    @Test
    fun `explicit set clear set-empty and multi-value edits retain typed semantics`() {
        var captured = emptyMap<FieldKey, MetadataTextEdit>()
        val executor = executor(writer = { _, edits, _ -> captured = edits; success() })
        val changes = mapOf(
            BatchMetadataField.ALBUM to set(text("Album")),
            BatchMetadataField.COMMENT to clear(),
            BatchMetadataField.COPYRIGHT to set(text("")),
            BatchMetadataField.GENRE to set(multi(mutableListOf("Rock", "Indie"))),
            BatchMetadataField.COMPOSER to set(multi(mutableListOf("One", "Two"))),
            BatchMetadataField.ALBUM_ARTIST to set(multi(mutableListOf("A", "B")))
        )

        executor.execute(
            plan(target("one"), changes = changes),
            listOf(song("one")),
            null,
            BatchCancellationSignal()
        )

        assertEquals(listOf("Album"), captured.getValue(FieldKey.ALBUM).values)
        assertEquals(MetadataTextOperation.SET, captured.getValue(FieldKey.ALBUM).operation)
        assertEquals(MetadataTextOperation.CLEAR, captured.getValue(FieldKey.COMMENT).operation)
        assertEquals(emptyList<String>(), captured.getValue(FieldKey.COMMENT).values)
        assertEquals(MetadataTextOperation.SET, captured.getValue(FieldKey.COPYRIGHT).operation)
        assertEquals(listOf(""), captured.getValue(FieldKey.COPYRIGHT).values)
        assertEquals(listOf("Rock", "Indie"), captured.getValue(FieldKey.GENRE).values)
        assertEquals(listOf("One", "Two"), captured.getValue(FieldKey.COMPOSER).values)
        assertEquals(listOf("A", "B"), captured.getValue(FieldKey.ALBUM_ARTIST).values)
    }

    @Test
    fun `targets execute sequentially and later targets continue after a failure`() {
        val order = mutableListOf<String>()
        val executor = executor(writer = { song, _, _ ->
            order += song.displayName
            if (song.displayName == "two") {
                ExplicitMetadataPatchResult(false, "write failed", ExplicitPatchFailureKind.WRITE)
            } else {
                success()
            }
        })

        val result = executor.execute(
            plan(
                target("one"), target("two"), target("three"),
                changes = mapOf(BatchMetadataField.ALBUM to set(text("New")))
            ),
            listOf(song("three"), song("one"), song("two")),
            null,
            BatchCancellationSignal()
        )

        assertEquals(listOf("one", "two", "three"), order)
        assertEquals(
            listOf(BatchTargetStatus.SUCCESS, BatchTargetStatus.WRITE_FAILED, BatchTargetStatus.SUCCESS),
            result.targetResults.map(BatchTargetResult::status)
        )
    }

    @Test
    fun `verification failure has a distinct per-file status`() {
        val executor = executor(writer = { _, _, _ ->
            ExplicitMetadataPatchResult(
                false,
                "verification failed",
                ExplicitPatchFailureKind.VERIFICATION
            )
        })

        val result = executor.execute(
            plan(target("one"), changes = mapOf(BatchMetadataField.DATE to set(text("2026")))),
            listOf(song("one")), null, BatchCancellationSignal()
        )

        assertEquals(BatchTargetStatus.VERIFICATION_FAILED, result.targetResults.single().status)
    }

    @Test
    fun `cancellation requested during a file lets it complete and leaves remaining targets untouched`() {
        val signal = BatchCancellationSignal()
        val writes = mutableListOf<String>()
        val executor = executor(writer = { song, _, _ ->
            writes += song.displayName
            signal.cancel()
            success()
        })

        val result = executor.execute(
            plan(
                target("one"), target("two"), target("three"),
                changes = mapOf(BatchMetadataField.ALBUM to set(text("New")))
            ),
            listOf(song("one"), song("two"), song("three")), null, signal
        )

        assertEquals(listOf("one"), writes)
        assertTrue(result.wasCancelled)
        assertEquals(1, result.successCount)
        assertEquals(2, result.notProcessedCount)
    }

    @Test
    fun `cancel before start performs no writes`() {
        val signal = BatchCancellationSignal().apply(BatchCancellationSignal::cancel)
        var writes = 0
        val executor = executor(writer = { _, _, _ -> writes++; success() })

        val result = executor.execute(
            plan(
                target("one"), target("two"),
                changes = mapOf(BatchMetadataField.ALBUM to set(text("New")))
            ),
            listOf(song("one"), song("two")), null, signal
        )

        assertEquals(0, writes)
        assertTrue(result.wasCancelled)
        assertEquals(2, result.notProcessedCount)
    }

    @Test
    fun `cancel while last file completes reports full completion not cancellation`() {
        val signal = BatchCancellationSignal()
        val writes = mutableListOf<String>()
        val executor = executor(writer = { song, _, _ ->
            writes += song.displayName
            if (song.displayName == "three") signal.cancel()
            success()
        })

        val result = executor.execute(
            plan(
                target("one"), target("two"), target("three"),
                changes = mapOf(BatchMetadataField.ALBUM to set(text("New")))
            ),
            listOf(song("one"), song("two"), song("three")), null, signal
        )

        assertEquals(listOf("one", "two", "three"), writes)
        assertFalse(result.wasCancelled)
        assertEquals(3, result.successCount)
        assertEquals(0, result.notProcessedCount)
    }

    @Test
    fun `prepared artwork bytes are reused and empty plans perform no writes`() {
        val prepared = PreparedBatchArtwork(byteArrayOf(1, 2), "image/jpeg", 2, 2, "hash")
        val receivedArtwork = mutableListOf<PreparedBatchArtwork>()
        var writeCount = 0
        val executor = executor(writer = { _, _, artwork ->
            writeCount++
            receivedArtwork += (artwork as BatchArtworkExecutionEdit.Replace).artwork
            success()
        })
        val artworkPlan = BatchMetadataPlan(
            selectedTargets = listOf(target("one"), target("two")),
            fieldChanges = emptyMap(),
            artworkChange = BatchArtworkChange(
                BatchInitialValue.Common(BatchArtworkValue.None),
                BatchEditIntent.Set(
                    BatchArtworkValue.Present(BatchArtworkReference("picker", "content://expired"))
                )
            )
        )

        executor.execute(
            artworkPlan,
            listOf(song("one"), song("two")),
            prepared,
            BatchCancellationSignal()
        )
        executor.execute(
            BatchMetadataPlan(listOf(target("one")), emptyMap(), null),
            listOf(song("one")), null, BatchCancellationSignal()
        )

        assertEquals(2, writeCount)
        assertEquals(2, receivedArtwork.size)
        assertSame(receivedArtwork[0], receivedArtwork[1])
        assertNotSame(prepared, receivedArtwork[0])
        assertArrayEquals(prepared.bytes, receivedArtwork[0].bytes)
    }

    private fun executor(
        capabilities: (Song) -> MetadataFormatCapabilities = {
            MetadataFormatCapabilities.ALL_EDITABLE
        },
        writer: (Song, Map<FieldKey, MetadataTextEdit>, BatchArtworkExecutionEdit) ->
            ExplicitMetadataPatchResult
    ) = BatchMetadataExecutor(
        resolver = BatchTargetResolver { target, songs ->
            songs.firstOrNull { it.membershipKey() == target.referenceKey }
                ?.let(BatchTargetResolution::Resolved)
                ?: BatchTargetResolution.Missing("missing")
        },
        capabilityReader = BatchCapabilityReader(capabilities),
        writer = BatchTargetPatchWriter(writer)
    )

    private fun plan(
        vararg targets: BatchMetadataTargetId,
        changes: Map<BatchMetadataField, BatchMetadataFieldChange>
    ) = BatchMetadataPlan(targets.toList(), changes, null)

    private fun set(value: BatchMetadataValue) = BatchMetadataFieldChange(
        BatchInitialValue.Mixed,
        BatchEditIntent.Set(value)
    )

    private fun clear() = BatchMetadataFieldChange(
        BatchInitialValue.Mixed,
        BatchEditIntent.Clear
    )

    private fun text(value: String) = BatchMetadataValue.Text(value)

    private fun multi(values: MutableList<String>) = BatchMetadataValue.MultiValue(values)

    private fun target(key: String) = BatchMetadataTargetId(
        referenceKey = song(key).membershipKey(),
        mediaStoreId = key.hashCode().toLong(),
        filePath = "/music/$key.flac",
        volumeName = "external",
        displayName = key
    )

    private fun targetFrom(song: Song) = BatchMetadataTargetId(
        referenceKey = song.membershipKey(),
        mediaStoreId = song.id,
        filePath = song.filePath,
        volumeName = song.volumeName,
        displayName = song.displayName,
        title = song.title,
        artist = song.artist,
        contentUri = song.uri.toString(),
        relativePath = song.relativePath,
        durationMs = song.duration,
        fileSizeBytes = song.fileSizeBytes,
        dateModifiedEpochSeconds = song.dateModifiedEpochSeconds
    )

    private fun song(
        key: String,
        path: String = "/music/$key.flac",
        size: Long = 0L,
        modified: Long = 0L
    ): Song {
        val id = key.hashCode().toLong()
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = key,
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 1_000L,
            uri = uri,
            filePath = path,
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external",
            displayName = key,
            relativePath = "Music/",
            fileSizeBytes = size,
            dateModifiedEpochSeconds = modified
        )
    }

    private fun success() = ExplicitMetadataPatchResult(true, "verified")
}
