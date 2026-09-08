package io.github.rsgarrido.sazanami.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Glance sessions observe this per-instance revision. Presentation data remains application state
 * in [NowPlayingWidgetSnapshotStore]; the revision only invalidates the RemoteViews composition.
 */
internal val NOW_PLAYING_PRESENTATION_REVISION = longPreferencesKey("presentation_revision")

internal suspend fun invalidateNowPlayingWidgetPresentations(context: Context) {
    val appContext = context.applicationContext
    val widget = NowPlayingWidget()
    val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(NowPlayingWidget::class.java)
    glanceIds.forEach { glanceId ->
        updateAppWidgetState(appContext, glanceId) { preferences ->
            val revision = preferences[NOW_PLAYING_PRESENTATION_REVISION] ?: 0L
            preferences[NOW_PLAYING_PRESENTATION_REVISION] =
                if (revision == Long.MAX_VALUE) 0L else revision + 1L
        }
        widget.update(appContext, glanceId)
    }
}
