package io.github.rsgarrido.sazanami.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.datastore.preferences.core.Preferences
import androidx.glance.ColorFilter
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.LocalContext
import androidx.glance.currentState
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.ContentScale
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.rsgarrido.sazanami.MainActivity
import io.github.rsgarrido.sazanami.R
import io.github.rsgarrido.sazanami.data.preferences.AppPreferencesRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

internal enum class NowPlayingWidgetLayout { COMPACT, STANDARD }

internal fun nowPlayingWidgetLayoutFor(heightDp: Float): NowPlayingWidgetLayout =
    if (heightDp >= STANDARD_MIN_HEIGHT_DP) NowPlayingWidgetLayout.STANDARD
    else NowPlayingWidgetLayout.COMPACT

internal fun widgetPlayPauseDescriptionResource(isPlaying: Boolean): Int =
    if (isPlaying) R.string.widget_pause else R.string.widget_play

class NowPlayingWidget : GlanceAppWidget() {
    override val stateDefinition = PreferencesGlanceStateDefinition
    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(COMPACT_SIZE, STANDARD_SIZE)
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val appWidgetManager = GlanceAppWidgetManager(context)
        val appWidgetId = appWidgetManager.getAppWidgetId(id)
        val store = NowPlayingWidgetSnapshotStore(context)
        val widgetPreferences = NowPlayingWidgetPreferences(context)
        val appPreferences = AppPreferencesRepository.getInstance(context)
        appPreferences.awaitLoadedState()
        provideContent {
            // Reading the revision makes service publications observable to an existing session.
            currentState<Preferences>()[NOW_PLAYING_PRESENTATION_REVISION]
            val snapshot = NowPlayingWidgetLiveState.snapshot ?: store.readCold()
            val appearance = resolveWidgetAppearance(
                mode = widgetPreferences.load(appWidgetId),
                preferences = appPreferences.state.value
            )
            NowPlayingWidgetContent(snapshot, appearance)
        }
    }

    override suspend fun onDelete(context: Context, glanceId: GlanceId) {
        val appWidgetId = GlanceAppWidgetManager(context).getAppWidgetId(glanceId)
        withContext(Dispatchers.IO) {
            NowPlayingWidgetPreferences(context).delete(appWidgetId)
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

@Composable
private fun NowPlayingWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val layout = nowPlayingWidgetLayoutFor(LocalSize.current.height.value)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(appearance.background.asGlanceColorProvider())
            .cornerRadius(appearance.widgetCornerRadiusDp.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!snapshot.hasMedia) {
            EmptyWidgetContent(layout, appearance)
        } else {
            when (widgetRendererLayoutFor(appearance.renderer, layout)) {
                WidgetRendererLayout.NEUTRAL_COMPACT ->
                    CompactWidgetContent(snapshot, appearance)
                WidgetRendererLayout.NEUTRAL_STANDARD ->
                    StandardWidgetContent(snapshot, appearance)
                WidgetRendererLayout.RETRO_RACK_COMPACT ->
                    RetroRackCompactWidgetContent(snapshot, appearance)
                WidgetRendererLayout.RETRO_RACK_STANDARD ->
                    RetroRackStandardWidgetContent(snapshot, appearance)
                WidgetRendererLayout.POCKET_CASSETTE_COMPACT ->
                    PocketCassetteCompactWidgetContent(snapshot, appearance)
                WidgetRendererLayout.POCKET_CASSETTE_STANDARD ->
                    PocketCassetteStandardWidgetContent(snapshot, appearance)
            }
        }
    }
}

@Composable
private fun CompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 44, appearance)
        Spacer(GlanceModifier.width(8.dp))
        Metadata(snapshot, GlanceModifier.defaultWeight(), appearance)
        Spacer(GlanceModifier.width(4.dp))
        TransportControls(snapshot, 32, appearance)
    }
}

@Composable
private fun StandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 88, appearance)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Metadata(snapshot, GlanceModifier.fillMaxWidth().defaultWeight(), appearance)
            TransportControls(snapshot, 38, appearance)
        }
    }
}

@Composable
private fun EmptyWidgetContent(
    layout: NowPlayingWidgetLayout,
    appearance: NowPlayingWidgetAppearance
) {
    val context = LocalContext.current
    Row(
        modifier = GlanceModifier
            .fillMaxSize()
            .clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(
            provider = ImageProvider(R.drawable.ic_widget_artwork_placeholder),
            contentDescription = null,
            modifier = GlanceModifier.size(if (layout == NowPlayingWidgetLayout.STANDARD) 64.dp else 40.dp),
            colorFilter = ColorFilter.tint(appearance.secondaryText.asGlanceColorProvider())
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_nothing_playing),
                style = TextStyle(
                    color = appearance.primaryText.asGlanceColorProvider(),
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium
                ),
                maxLines = 1
            )
            Text(
                text = context.getString(R.string.widget_open_sazanami),
                style = TextStyle(
                    color = appearance.secondaryText.asGlanceColorProvider(),
                    fontSize = 12.sp
                ),
                maxLines = 1
            )
        }
    }
}

@Composable
internal fun Artwork(
    snapshot: NowPlayingWidgetSnapshot,
    edgeDp: Int,
    appearance: NowPlayingWidgetAppearance
) {
    val context = LocalContext.current
    val artworkUri = widgetHostArtworkUri(context.packageName, snapshot.artworkUri)
    val presentation = widgetArtworkPresentation(artworkUri != null)
    Image(
        provider = when (presentation) {
            WidgetArtworkPresentation.ARTWORK ->
                androidx.glance.appwidget.ImageProvider(checkNotNull(artworkUri))
            WidgetArtworkPresentation.PLACEHOLDER ->
                ImageProvider(R.drawable.ic_widget_artwork_placeholder)
        },
        contentDescription = context.getString(R.string.widget_artwork),
        modifier = GlanceModifier
            .size(edgeDp.dp)
            .background(appearance.artworkSurface.asGlanceColorProvider())
            .cornerRadius(appearance.artworkCornerRadiusDp.dp),
        contentScale = ContentScale.Crop,
        colorFilter = if (presentation == WidgetArtworkPresentation.PLACEHOLDER) {
            ColorFilter.tint(appearance.artworkPlaceholderTint.asGlanceColorProvider())
        } else {
            null
        }
    )
}

@Composable
private fun Metadata(
    snapshot: NowPlayingWidgetSnapshot,
    modifier: GlanceModifier,
    appearance: NowPlayingWidgetAppearance
) {
    Column(
        modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = snapshot.title,
            style = TextStyle(
                color = appearance.metadataPrimaryText.asGlanceColorProvider(),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium
            ),
            maxLines = 1
        )
        Text(
            text = snapshot.artist,
            style = TextStyle(
                color = appearance.metadataSecondaryText.asGlanceColorProvider(),
                fontSize = 12.sp
            ),
            maxLines = 1
        )
    }
}

@Composable
internal fun TransportControls(
    snapshot: NowPlayingWidgetSnapshot,
    edgeDp: Int,
    appearance: NowPlayingWidgetAppearance
) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TransportButton(
            icon = R.drawable.ic_widget_previous,
            description = context.getString(R.string.widget_previous),
            enabled = snapshot.canPrevious,
            edgeDp = edgeDp,
            enabledColor = appearance.controlForeground,
            disabledColor = appearance.disabled,
            surfaceColor = appearance.controlSurface,
            cornerRadiusDp = appearance.controlCornerRadiusDp,
            action = actionRunCallback<PreviousWidgetAction>()
        )
        TransportButton(
            icon = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = context.getString(widgetPlayPauseDescriptionResource(snapshot.isPlaying)),
            enabled = snapshot.canPlayPause,
            edgeDp = edgeDp,
            enabledColor = appearance.accent,
            disabledColor = appearance.disabled,
            surfaceColor = appearance.controlSurface,
            cornerRadiusDp = appearance.controlCornerRadiusDp,
            action = actionRunCallback<PlayPauseWidgetAction>()
        )
        TransportButton(
            icon = R.drawable.ic_widget_next,
            description = context.getString(R.string.widget_next),
            enabled = snapshot.canNext,
            edgeDp = edgeDp,
            enabledColor = appearance.controlForeground,
            disabledColor = appearance.disabled,
            surfaceColor = appearance.controlSurface,
            cornerRadiusDp = appearance.controlCornerRadiusDp,
            action = actionRunCallback<NextWidgetAction>()
        )
    }
}

@Composable
private fun TransportButton(
    icon: Int,
    description: String,
    enabled: Boolean,
    edgeDp: Int,
    enabledColor: WidgetColorToken,
    disabledColor: WidgetColorToken,
    surfaceColor: WidgetColorToken?,
    cornerRadiusDp: Int,
    action: androidx.glance.action.Action
) {
    val surfaceModifier = if (surfaceColor == null) {
        GlanceModifier.size(edgeDp.dp)
    } else {
        GlanceModifier
            .size(edgeDp.dp)
            .background(surfaceColor.asGlanceColorProvider())
            .cornerRadius(cornerRadiusDp.dp)
    }
    val modifier = surfaceModifier
        .padding(5.dp)
        .let { base -> if (enabled) base.clickable(action) else base }
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        modifier = modifier,
        colorFilter = ColorFilter.tint(
            if (enabled) enabledColor.asGlanceColorProvider()
            else disabledColor.asGlanceColorProvider()
        )
    )
}

private const val STANDARD_MIN_HEIGHT_DP = 96f
private val COMPACT_SIZE = DpSize(250.dp, 56.dp)
private val STANDARD_SIZE = DpSize(250.dp, 120.dp)
