package io.github.rsgarrido.sazanami.data

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mockito.Mockito.mock
import org.mockito.Mockito.`when`

class BatchMetadataTest {
    @Test
    fun `same Genre derives common while differing Genre derives mixed`() {
        val common = editor(target("a", genre = multi("Rock")), target("b", genre = multi("Rock")))
        val mixed = editor(
            target("a", genre = multi("Rock")),
            target("b", genre = multi("Alternative"))
        )

        assertEquals(
            BatchInitialValue.Common(multi("Rock")),
            common.fields.getValue(BatchMetadataField.GENRE).initial
        )
        assertEquals(
            BatchInitialValue.Mixed,
            mixed.fields.getValue(BatchMetadataField.GENRE).initial
        )
    }

    @Test
    fun `all empty is common empty and never an inferred clear`() {
        val state = editor(target("a"), target("b"))
        val publisher = state.fields.getValue(BatchMetadataField.PUBLISHER)

        assertEquals(BatchInitialValue.Common(text("")), publisher.initial)
        assertEquals(BatchEditIntent.Untouched, publisher.intent)
        assertTrue(state.plan().fieldChanges.isEmpty())
    }

    @Test
    fun `explicit replacement and clear remain distinct operations`() {
        val initial = editor(
            target("a", genre = multi("Rock"), comment = text("First")),
            target("b", genre = multi("Alternative"), comment = text("Second"))
        )
        val edited = initial
            .set(BatchMetadataField.GENRE, "Rock")
            .clear(BatchMetadataField.COMMENT)
        val plan = edited.plan()

        assertEquals(
            BatchEditIntent.Set(multi("Rock")),
            plan.fieldChanges.getValue(BatchMetadataField.GENRE).intent
        )
        assertEquals(
            BatchEditIntent.Clear,
            plan.fieldChanges.getValue(BatchMetadataField.COMMENT).intent
        )
        assertEquals(2, plan.changeCount)
    }

    @Test
    fun `set-to-empty remains distinct from explicit clear`() {
        val initial = editor(
            target("a", comment = text("First")),
            target("b", comment = text("Second"))
        )

        val setEmpty = initial.set(BatchMetadataField.COMMENT, "").plan()
        val clear = initial.clear(BatchMetadataField.COMMENT).plan()

        assertEquals(
            BatchEditIntent.Set(text("")),
            setEmpty.fieldChanges.getValue(BatchMetadataField.COMMENT).intent
        )
        assertEquals(
            BatchEditIntent.Clear,
            clear.fieldChanges.getValue(BatchMetadataField.COMMENT).intent
        )
    }

    @Test
    fun `untouched mixed field and untouched editor create no plan changes`() {
        val state = editor(
            target("a", genre = multi("Rock")),
            target("b", genre = multi("Alternative"))
        )

        assertEquals(BatchEditIntent.Untouched, state.fields.getValue(BatchMetadataField.GENRE).intent)
        assertTrue(state.plan().fieldChanges.isEmpty())
        assertNull(state.plan().artworkChange)
    }

    @Test
    fun `multi-value tracks compare typed value lists instead of mixed display text`() {
        val common = editor(
            target("a", genre = multi("Rock", "Alternative")),
            target("b", genre = multi("Rock", "Alternative"))
        )
        val mixed = editor(
            target("a", genre = multi("Rock", "Alternative")),
            target("b", genre = multi("Rock"))
        )

        assertEquals(
            BatchInitialValue.Common(multi("Rock", "Alternative")),
            common.fields.getValue(BatchMetadataField.GENRE).initial
        )
        assertEquals(BatchInitialValue.Mixed, mixed.fields.getValue(BatchMetadataField.GENRE).initial)
    }

    @Test
    fun `semicolon edit uses Session 2 split trim and discard-empty policy`() {
        val state = editor(target("a"), target("b"))
            .set(BatchMetadataField.COMPOSER, "One; Two ; ; Three")

        assertEquals(
            BatchEditIntent.Set(multi("One", "Two", "Three")),
            state.fields.getValue(BatchMetadataField.COMPOSER).intent
        )
    }

    @Test
    fun `restoring a common value collapses to untouched`() {
        val initial = editor(
            target("a", genre = multi("Rock")),
            target("b", genre = multi("Rock"))
        )
        val restored = initial
            .set(BatchMetadataField.GENRE, "Alternative")
            .set(BatchMetadataField.GENRE, "Rock")

        assertEquals(
            BatchEditIntent.Untouched,
            restored.fields.getValue(BatchMetadataField.GENRE).intent
        )
    }

    @Test
    fun `multiple edits plan contains exactly Genre Year and Composer`() {
        val plan = editor(target("a"), target("b"))
            .set(BatchMetadataField.GENRE, "Rock")
            .set(BatchMetadataField.DATE, "2026")
            .set(BatchMetadataField.COMPOSER, "Composer")
            .plan()

        assertEquals(
            setOf(
                BatchMetadataField.GENRE,
                BatchMetadataField.DATE,
                BatchMetadataField.COMPOSER
            ),
            plan.fieldChanges.keys
        )
    }

    @Test
    fun `selection capabilities are the intersection across every target`() {
        val full = MetadataFormatCapabilities.ALL_EDITABLE
        val limited = MetadataFormatCapabilities(
            setOf(EditableMetadataField.ALBUM, EditableMetadataField.GENRE)
        )
        val state = editor(
            target("flac", capabilities = full),
            target("limited", capabilities = limited)
        )

        assertTrue(state.supports(BatchMetadataField.ALBUM))
        assertTrue(state.supports(BatchMetadataField.GENRE))
        assertFalse(state.supports(BatchMetadataField.PUBLISHER))
        assertEquals(
            state,
            state.set(BatchMetadataField.PUBLISHER, "Should not enter the plan")
        )
    }

    @Test
    fun `artwork derives common mixed and none without byte comparisons`() {
        val cover = artwork("same-hash", "content://art/track-a")
        val secondCoverReference = artwork("same-hash", "content://art/track-b")
        val common = editor(
            target("a", artwork = cover),
            target("b", artwork = secondCoverReference)
        )
        val mixed = editor(target("a", artwork = cover), target("b"))
        val none = editor(target("a"), target("b"))

        assertEquals(BatchInitialValue.Common(cover), common.artwork.initial)
        assertEquals(BatchInitialValue.Mixed, mixed.artwork.initial)
        assertEquals(BatchInitialValue.Common(BatchArtworkValue.None), none.artwork.initial)
    }

    @Test
    fun `artwork replacement and explicit clear are reviewable plan operations`() {
        val initial = editor(target("a"), target("b", artwork = artwork("old")))
        val replacement = BatchArtworkReference("new-hash", "content://new")

        val replacePlan = initial.replaceArtwork(replacement).plan()
        val clearPlan = initial.clearArtwork().plan()

        assertEquals(
            BatchEditIntent.Set(BatchArtworkValue.Present(replacement)),
            replacePlan.artworkChange?.intent
        )
        assertEquals(BatchEditIntent.Clear, clearPlan.artworkChange?.intent)
    }

    @Test
    fun `song targets retain durable membership identity and file path`() {
        val first = song(41L, "/music/one.flac").toBatchMetadataTarget(editableTags())
        val second = song(42L, "/music/two.flac").toBatchMetadataTarget(editableTags())
        val plan = editor(first, second).plan()

        assertEquals(listOf(41L, 42L), plan.selectedTargets.map(BatchMetadataTargetId::mediaStoreId))
        assertEquals(listOf("/music/one.flac", "/music/two.flac"), plan.selectedTargets.map(BatchMetadataTargetId::filePath))
        assertTrue(plan.selectedTargets.all { target -> target.referenceKey.isNotBlank() })
        assertNotEquals(plan.selectedTargets[0].referenceKey, plan.selectedTargets[1].referenceKey)
    }

    private fun editor(vararg targets: BatchMetadataTarget): BatchMetadataEditorState =
        BatchMetadataEditorState.derive(targets.toList())

    private fun target(
        key: String,
        genre: BatchMetadataValue = multi(),
        comment: BatchMetadataValue = text(""),
        capabilities: MetadataFormatCapabilities = MetadataFormatCapabilities.ALL_EDITABLE,
        artwork: BatchArtworkValue = BatchArtworkValue.None
    ): BatchMetadataTarget = BatchMetadataTarget(
        id = BatchMetadataTargetId(key, key.hashCode().toLong(), "/music/$key.flac"),
        values = BatchMetadataField.entries.associateWith { field ->
            when (field) {
                BatchMetadataField.GENRE -> genre
                BatchMetadataField.COMMENT -> comment
                else -> if (field.isMultiValue) multi() else text("")
            }
        },
        capabilities = capabilities,
        artwork = artwork
    )

    private fun text(value: String) = BatchMetadataValue.Text(value)

    private fun multi(vararg values: String) = BatchMetadataValue.MultiValue(values.toList())

    private fun artwork(identity: String, previewUri: String = identity) = BatchArtworkValue.Present(
        BatchArtworkReference(identity = identity, previewUri = previewUri)
    )

    private fun editableTags() = EditableSongTags("Title", "Artist", "Album", "1", "2026")

    private fun song(id: Long, path: String): Song {
        val uri = mock(Uri::class.java)
        `when`(uri.toString()).thenReturn("content://media/external/audio/$id")
        return Song(
            id = id,
            title = "Title $id",
            artist = "Artist",
            album = "Album",
            trackNumber = 1,
            duration = 1_000,
            uri = uri,
            filePath = path,
            folderPath = "/music",
            albumArtUri = null,
            volumeName = "external",
            displayName = path.substringAfterLast('/'),
            relativePath = "Music/"
        )
    }
}
