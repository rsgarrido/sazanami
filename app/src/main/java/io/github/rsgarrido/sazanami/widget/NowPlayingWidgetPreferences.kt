package io.github.rsgarrido.sazanami.widget

import android.appwidget.AppWidgetManager
import android.content.Context

internal class NowPlayingWidgetPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE
    )

    fun load(appWidgetId: Int): WidgetAppearanceMode {
        if (!appWidgetId.isValidWidgetId()) {
            return WidgetAppearanceMode.FOLLOW_PLAYER_THEME
        }
        return WidgetAppearanceMode.fromStorageValue(
            preferences.getString(key(appWidgetId), null)
        )
    }

    /** Synchronous persistence ensures the host never receives RESULT_OK before configuration. */
    fun save(appWidgetId: Int, mode: WidgetAppearanceMode): Boolean {
        if (!appWidgetId.isValidWidgetId()) return false
        return preferences.edit().putString(key(appWidgetId), mode.storageValue).commit()
    }

    fun delete(appWidgetId: Int): Boolean {
        if (!appWidgetId.isValidWidgetId()) return false
        return preferences.edit().remove(key(appWidgetId)).commit()
    }

    private fun key(appWidgetId: Int) = "appearance_mode_$appWidgetId"

    private companion object {
        const val PREFERENCES_NAME = "now_playing_widget_preferences"
    }
}

private fun Int.isValidWidgetId(): Boolean =
    this > 0 && this != AppWidgetManager.INVALID_APPWIDGET_ID
