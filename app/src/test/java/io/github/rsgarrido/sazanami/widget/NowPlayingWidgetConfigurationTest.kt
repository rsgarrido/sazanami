package io.github.rsgarrido.sazanami.widget

import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class NowPlayingWidgetConfigurationTest {
    @Test
    fun savePersistsBeforeTargetedRefreshAndRendererSeesNewModeImmediately() = runBlocking {
        val widgetId = 71
        val storedModes = mutableMapOf(widgetId to WidgetAppearanceMode.SYSTEM_DYNAMIC)
        val events = mutableListOf<String>()
        var renderedAppearance: NowPlayingWidgetAppearance? = null

        val completed = saveAndRefreshWidgetAppearance(
            appWidgetId = widgetId,
            mode = WidgetAppearanceMode.SAZANAMI_DEFAULT,
            persist = { targetId, mode ->
                events += "persist:$targetId"
                storedModes[targetId] = mode
                true
            },
            refresh = { targetId ->
                events += "refresh:$targetId"
                renderedAppearance = resolveWidgetAppearance(
                    mode = checkNotNull(storedModes[targetId]),
                    preferences = AppPreferencesState(isLoaded = true)
                )
            }
        )

        assertTrue(completed)
        assertEquals(listOf("persist:71", "refresh:71"), events)
        assertSame(
            WidgetAppearanceRenderer.SAZANAMI_DEFAULT,
            renderedAppearance?.renderer
        )
    }

    @Test
    fun defaultToSystemRefreshesWithoutAnyPlaybackEvent() = runBlocking {
        val widgetId = 72
        var storedMode = WidgetAppearanceMode.SAZANAMI_DEFAULT
        var refreshedMode: WidgetAppearanceMode? = null
        var refreshCount = 0

        val completed = saveAndRefreshWidgetAppearance(
            appWidgetId = widgetId,
            mode = WidgetAppearanceMode.SYSTEM_DYNAMIC,
            persist = { _, mode ->
                storedMode = mode
                true
            },
            refresh = { targetId ->
                assertEquals(widgetId, targetId)
                refreshCount++
                refreshedMode = storedMode
            }
        )

        assertTrue(completed)
        assertEquals(1, refreshCount)
        assertSame(WidgetAppearanceMode.SYSTEM_DYNAMIC, refreshedMode)
    }

    @Test
    fun savingFollowModeRefreshesImmediately() = runBlocking {
        val widgetId = 73
        var storedMode = WidgetAppearanceMode.SYSTEM_DYNAMIC
        var refreshedMode: WidgetAppearanceMode? = null

        val completed = saveAndRefreshWidgetAppearance(
            appWidgetId = widgetId,
            mode = WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
            persist = { _, mode ->
                storedMode = mode
                true
            },
            refresh = {
                refreshedMode = storedMode
            }
        )

        assertTrue(completed)
        assertSame(WidgetAppearanceMode.FOLLOW_PLAYER_THEME, refreshedMode)
    }

    @Test
    fun followToEveryFixedModeRefreshesImmediately() = runBlocking {
        listOf(
            WidgetAppearanceMode.SAZANAMI_DEFAULT,
            WidgetAppearanceMode.SYSTEM_DYNAMIC,
            WidgetAppearanceMode.RETRO_RACK,
            WidgetAppearanceMode.POCKET_CASSETTE,
            WidgetAppearanceMode.CLASSIC_WHEEL,
            WidgetAppearanceMode.POCKET_FLIP,
            WidgetAppearanceMode.POCKET_DISC
        ).forEach { targetMode ->
            var storedMode = WidgetAppearanceMode.FOLLOW_PLAYER_THEME
            var refreshedMode: WidgetAppearanceMode? = null

            val completed = saveAndRefreshWidgetAppearance(
                appWidgetId = 74,
                mode = targetMode,
                persist = { _, mode ->
                    storedMode = mode
                    true
                },
                refresh = { refreshedMode = storedMode }
            )

            assertTrue(completed)
            assertSame(targetMode, refreshedMode)
        }
    }

    @Test
    fun changingOneWidgetDoesNotChangeOrRefreshAnotherWidget() = runBlocking {
        val firstId = 81
        val secondId = 82
        val storedModes = mutableMapOf(
            firstId to WidgetAppearanceMode.SYSTEM_DYNAMIC,
            secondId to WidgetAppearanceMode.FOLLOW_PLAYER_THEME
        )
        val refreshedIds = mutableListOf<Int>()

        val completed = saveAndRefreshWidgetAppearance(
            appWidgetId = firstId,
            mode = WidgetAppearanceMode.SAZANAMI_DEFAULT,
            persist = { targetId, mode ->
                storedModes[targetId] = mode
                true
            },
            refresh = { targetId -> refreshedIds += targetId }
        )

        assertTrue(completed)
        assertEquals(listOf(firstId), refreshedIds)
        assertSame(WidgetAppearanceMode.SAZANAMI_DEFAULT, storedModes[firstId])
        assertSame(WidgetAppearanceMode.FOLLOW_PLAYER_THEME, storedModes[secondId])
    }

    @Test
    fun failedPersistenceNeverRefreshesOrReportsSuccess() = runBlocking {
        var refreshCount = 0

        val completed = saveAndRefreshWidgetAppearance(
            appWidgetId = 91,
            mode = WidgetAppearanceMode.SYSTEM_DYNAMIC,
            persist = { _, _ -> false },
            refresh = { refreshCount++ }
        )

        assertFalse(completed)
        assertEquals(0, refreshCount)
    }

    @Test
    fun presentationRevisionAlwaysChangesIncludingAtOverflow() {
        assertEquals(1L, nextWidgetPresentationRevision(null))
        assertEquals(10L, nextWidgetPresentationRevision(9L))
        assertEquals(0L, nextWidgetPresentationRevision(Long.MAX_VALUE))
    }
}
