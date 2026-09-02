package io.github.rsgarrido.sazanami.controller

import io.github.rsgarrido.sazanami.ui.state.LibrarySelectionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LibrarySelectionControllerTest {
    @Test
    fun `enter and toggle maintain an entity scoped selection`() {
        val controller = LibrarySelectionController()

        controller.enter(LibrarySelectionEntity.SONG, "song-a")
        controller.toggle(LibrarySelectionEntity.SONG, "song-b")

        assertEquals(LibrarySelectionEntity.SONG, controller.uiState.value.entity)
        assertEquals(setOf("song-a", "song-b"), controller.uiState.value.selectedKeys)
    }

    @Test
    fun `toggling the final key exits selection`() {
        val controller = LibrarySelectionController()
        controller.enter(LibrarySelectionEntity.SONG, "song-a")

        controller.toggle(LibrarySelectionEntity.SONG, "song-a")

        assertFalse(controller.uiState.value.isActive)
        assertEquals(null, controller.uiState.value.entity)
    }

    @Test
    fun `entering another entity replaces the previous entity selection`() {
        val controller = LibrarySelectionController()
        controller.selectDisplayed(LibrarySelectionEntity.SONG, listOf("a", "b"))

        controller.enter(LibrarySelectionEntity.ALBUM, "album")

        assertEquals(LibrarySelectionEntity.ALBUM, controller.uiState.value.entity)
        assertEquals(setOf("album"), controller.uiState.value.selectedKeys)
    }

    @Test
    fun `select displayed is additive and retains hidden keys`() {
        val controller = LibrarySelectionController()
        controller.enter(LibrarySelectionEntity.SONG, "hidden")

        controller.selectDisplayed(LibrarySelectionEntity.SONG, listOf("visible-a", "visible-b"))

        assertEquals(
            setOf("hidden", "visible-a", "visible-b"),
            controller.uiState.value.selectedKeys
        )
    }

    @Test
    fun `reconcile removes only keys missing from the actual library`() {
        val controller = LibrarySelectionController()
        controller.selectDisplayed(LibrarySelectionEntity.SONG, listOf("keep", "removed"))

        controller.reconcile(LibrarySelectionEntity.SONG, setOf("keep", "other"))

        assertEquals(setOf("keep"), controller.uiState.value.selectedKeys)
        assertTrue(controller.uiState.value.isActive)
    }

    @Test
    fun `reconcile for another entity is ignored and clear exits`() {
        val controller = LibrarySelectionController()
        controller.enter(LibrarySelectionEntity.SONG, "song")

        controller.reconcile(LibrarySelectionEntity.ALBUM, emptySet())
        assertEquals(setOf("song"), controller.uiState.value.selectedKeys)

        controller.clear()
        assertFalse(controller.uiState.value.isActive)
    }
}
