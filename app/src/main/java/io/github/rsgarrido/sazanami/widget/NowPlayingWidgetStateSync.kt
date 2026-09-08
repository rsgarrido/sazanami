package io.github.rsgarrido.sazanami.widget

import android.content.Context
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.glance.GlanceId
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.updateAppWidgetState

/**
 * Glance sessions observe this per-instance revision. Presentation data remains application state
 * in [NowPlayingWidgetSnapshotStore]; the revision only invalidates the RemoteViews composition.
 */
internal val NOW_PLAYING_PRESENTATION_REVISION = longPreferencesKey("presentation_revision")

/** Invalidates and renders one widget without waking unrelated widget instances. */
internal suspend fun invalidateNowPlayingWidgetPresentation(
    context: Context,
    glanceId: GlanceId
) {
    invalidateNowPlayingWidgetPresentation(
        context = context.applicationContext,
        glanceId = glanceId,
        widget = NowPlayingWidget()
    )
}

internal suspend fun invalidateNowPlayingWidgetPresentations(context: Context) {
    val appContext = context.applicationContext
    val widget = NowPlayingWidget()
    val glanceIds = GlanceAppWidgetManager(appContext).getGlanceIds(NowPlayingWidget::class.java)
    glanceIds.forEach { glanceId ->
        invalidateNowPlayingWidgetPresentation(appContext, glanceId, widget)
    }
}

private suspend fun invalidateNowPlayingWidgetPresentation(
    context: Context,
    glanceId: GlanceId,
    widget: NowPlayingWidget
) {
    updateAppWidgetState(context, glanceId) { preferences ->
        preferences[NOW_PLAYING_PRESENTATION_REVISION] = nextWidgetPresentationRevision(
            preferences[NOW_PLAYING_PRESENTATION_REVISION]
        )
    }
    widget.update(context, glanceId)
}

internal fun nextWidgetPresentationRevision(current: Long?): Long =
    if (current == Long.MAX_VALUE) 0L else (current ?: 0L) + 1L
