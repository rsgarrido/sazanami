package io.github.rsgarrido.sazanami.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlin.math.absoluteValue
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NowPlayingWidgetPreferencesInstrumentedTest {
    @Test
    fun configurationSaveLoadDeleteIsIsolatedByAppWidgetId() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = NowPlayingWidgetPreferences(context)
        val firstId = 100_000 + (System.nanoTime() % 100_000).toInt().absoluteValue
        val secondId = firstId + 1
        preferences.delete(firstId)
        preferences.delete(secondId)

        try {
            assertSame(
                WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
                preferences.load(firstId)
            )
            assertTrue(
                preferences.save(firstId, WidgetAppearanceMode.SAZANAMI_DEFAULT)
            )
            assertTrue(
                preferences.save(secondId, WidgetAppearanceMode.SYSTEM_DYNAMIC)
            )

            assertSame(
                WidgetAppearanceMode.SAZANAMI_DEFAULT,
                preferences.load(firstId)
            )
            assertSame(
                WidgetAppearanceMode.SYSTEM_DYNAMIC,
                preferences.load(secondId)
            )

            assertTrue(preferences.delete(firstId))
            assertSame(
                WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
                preferences.load(firstId)
            )
            assertSame(
                WidgetAppearanceMode.SYSTEM_DYNAMIC,
                preferences.load(secondId)
            )
        } finally {
            preferences.delete(firstId)
            preferences.delete(secondId)
        }
    }

    @Test
    fun invalidWidgetIdCannotCreateOrDeleteConfiguration() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val preferences = NowPlayingWidgetPreferences(context)

        assertFalse(
            preferences.save(
                AppWidgetManager.INVALID_APPWIDGET_ID,
                WidgetAppearanceMode.SYSTEM_DYNAMIC
            )
        )
        assertFalse(preferences.delete(AppWidgetManager.INVALID_APPWIDGET_ID))
        assertSame(
            WidgetAppearanceMode.FOLLOW_PLAYER_THEME,
            preferences.load(AppWidgetManager.INVALID_APPWIDGET_ID)
        )
    }

    @Test
    fun configurationResultIdentifiesOnlyTheConfiguredWidget() {
        val configuredId = 4242

        val result = widgetConfigurationResultIntent(configuredId)

        assertEquals(
            configuredId,
            result.getIntExtra(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        )
    }
}
