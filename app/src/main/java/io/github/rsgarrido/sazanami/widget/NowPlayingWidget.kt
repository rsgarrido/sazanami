package io.github.rsgarrido.sazanami.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
        val store = NowPlayingWidgetSnapshotStore(context)
        provideContent {
            // Reading the revision makes service publications observable to an existing session.
            currentState<Preferences>()[NOW_PLAYING_PRESENTATION_REVISION]
            val snapshot = NowPlayingWidgetLiveState.snapshot ?: store.readCold()
            NowPlayingWidgetContent(snapshot)
        }
    }
}

class NowPlayingWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NowPlayingWidget()
}

@Composable
private fun NowPlayingWidgetContent(snapshot: NowPlayingWidgetSnapshot) {
    val layout = nowPlayingWidgetLayoutFor(LocalSize.current.height.value)
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(WIDGET_BACKGROUND)
            .cornerRadius(16.dp)
            .padding(8.dp),
        contentAlignment = Alignment.Center
    ) {
        if (!snapshot.hasMedia) {
            EmptyWidgetContent(layout)
        } else if (layout == NowPlayingWidgetLayout.STANDARD) {
            StandardWidgetContent(snapshot)
        } else {
            CompactWidgetContent(snapshot)
        }
    }
}

@Composable
private fun CompactWidgetContent(snapshot: NowPlayingWidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 44)
        Spacer(GlanceModifier.width(8.dp))
        Metadata(snapshot, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(4.dp))
        TransportControls(snapshot, 32)
    }
}

@Composable
private fun StandardWidgetContent(snapshot: NowPlayingWidgetSnapshot) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 88)
        Spacer(GlanceModifier.width(12.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            Metadata(snapshot, GlanceModifier.fillMaxWidth().defaultWeight())
            TransportControls(snapshot, 38)
        }
    }
}

@Composable
private fun EmptyWidgetContent(layout: NowPlayingWidgetLayout) {
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
            colorFilter = ColorFilter.tint(WIDGET_MUTED)
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight()) {
            Text(
                text = context.getString(R.string.widget_nothing_playing),
                style = TextStyle(color = WIDGET_FOREGROUND, fontSize = 15.sp, fontWeight = FontWeight.Medium),
                maxLines = 1
            )
            Text(
                text = context.getString(R.string.widget_open_sazanami),
                style = TextStyle(color = WIDGET_MUTED, fontSize = 12.sp),
                maxLines = 1
            )
        }
    }
}

@Composable
private fun Artwork(snapshot: NowPlayingWidgetSnapshot, edgeDp: Int) {
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
            .background(WIDGET_ARTWORK_BACKGROUND)
            .cornerRadius(10.dp),
        contentScale = ContentScale.Crop,
        colorFilter = if (presentation == WidgetArtworkPresentation.PLACEHOLDER) {
            ColorFilter.tint(WIDGET_MUTED)
        } else {
            null
        }
    )
}

@Composable
private fun Metadata(snapshot: NowPlayingWidgetSnapshot, modifier: GlanceModifier) {
    Column(
        modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = snapshot.title,
            style = TextStyle(color = WIDGET_FOREGROUND, fontSize = 14.sp, fontWeight = FontWeight.Medium),
            maxLines = 1
        )
        Text(
            text = snapshot.artist,
            style = TextStyle(color = WIDGET_MUTED, fontSize = 12.sp),
            maxLines = 1
        )
    }
}

@Composable
private fun TransportControls(snapshot: NowPlayingWidgetSnapshot, edgeDp: Int) {
    val context = LocalContext.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        TransportButton(
            icon = R.drawable.ic_widget_previous,
            description = context.getString(R.string.widget_previous),
            enabled = snapshot.canPrevious,
            edgeDp = edgeDp,
            action = actionRunCallback<PreviousWidgetAction>()
        )
        TransportButton(
            icon = if (snapshot.isPlaying) R.drawable.ic_widget_pause else R.drawable.ic_widget_play,
            description = context.getString(widgetPlayPauseDescriptionResource(snapshot.isPlaying)),
            enabled = snapshot.canPlayPause,
            edgeDp = edgeDp,
            action = actionRunCallback<PlayPauseWidgetAction>()
        )
        TransportButton(
            icon = R.drawable.ic_widget_next,
            description = context.getString(R.string.widget_next),
            enabled = snapshot.canNext,
            edgeDp = edgeDp,
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
    action: androidx.glance.action.Action
) {
    val modifier = GlanceModifier
        .size(edgeDp.dp)
        .padding(5.dp)
        .let { base -> if (enabled) base.clickable(action) else base }
    Image(
        provider = ImageProvider(icon),
        contentDescription = description,
        modifier = modifier,
        colorFilter = ColorFilter.tint(if (enabled) WIDGET_FOREGROUND else WIDGET_DISABLED)
    )
}

private val WIDGET_BACKGROUND = fixedWidgetColor(Color(0xFF1B1B1F))
private val WIDGET_ARTWORK_BACKGROUND = fixedWidgetColor(Color(0xFF303036))
private val WIDGET_FOREGROUND = fixedWidgetColor(Color(0xFFF3F0F7))
private val WIDGET_MUTED = fixedWidgetColor(Color(0xFFC9C5CF))
private val WIDGET_DISABLED = fixedWidgetColor(Color(0xFF716D77))
private fun fixedWidgetColor(color: Color) = ColorProvider(day = color, night = color)
private const val STANDARD_MIN_HEIGHT_DP = 96f
private val COMPACT_SIZE = DpSize(250.dp, 56.dp)
private val STANDARD_SIZE = DpSize(250.dp, 120.dp)
