package io.github.rsgarrido.sazanami.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.background
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import io.github.rsgarrido.sazanami.MainActivity
import java.util.Locale

@Composable
internal fun RetroRackCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 40, appearance)
        Spacer(GlanceModifier.width(6.dp))
        RetroRackMetadataPanel(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            compact = true
        )
        Spacer(GlanceModifier.width(4.dp))
        TransportControls(snapshot, 32, appearance)
    }
}

@Composable
internal fun RetroRackStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Artwork(snapshot, 88, appearance)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            RetroRackMetadataPanel(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                compact = false
            )
            Spacer(GlanceModifier.height(4.dp))
            TransportControls(snapshot, 38, appearance)
        }
    }
}

@Composable
private fun RetroRackMetadataPanel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    compact: Boolean
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        RetroMetadata(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            compact = compact
        )
    }
}

@Composable
internal fun PocketCassetteCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketCassetteArtworkLabel(snapshot, appearance, outerEdgeDp = 40, artworkEdgeDp = 30)
        Spacer(GlanceModifier.width(6.dp))
        PocketCassetteMetadataPanel(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            compact = true
        )
        Spacer(GlanceModifier.width(4.dp))
        TransportControls(snapshot, 32, appearance)
    }
}

@Composable
internal fun PocketCassetteStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketCassetteArtworkLabel(snapshot, appearance, outerEdgeDp = 88, artworkEdgeDp = 76)
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            PocketCassetteMetadataPanel(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                compact = false
            )
            Spacer(GlanceModifier.height(4.dp))
            TransportControls(snapshot, 38, appearance)
        }
    }
}

@Composable
private fun PocketCassetteArtworkLabel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    outerEdgeDp: Int,
    artworkEdgeDp: Int
) {
    Box(
        modifier = GlanceModifier
            .size(outerEdgeDp.dp)
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp),
            contentAlignment = Alignment.Center
        ) {
            Artwork(snapshot, artworkEdgeDp, appearance)
        }
    }
}

@Composable
private fun PocketCassetteMetadataPanel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    compact: Boolean
) {
    Box(
        modifier = modifier
            .background(appearance.panelSurface.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(3.dp)
    ) {
        RetroMetadata(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 2).coerceAtLeast(0).dp)
                .padding(horizontal = 6.dp, vertical = 2.dp),
            compact = compact
        )
    }
}

@Composable
private fun RetroMetadata(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    compact: Boolean
) {
    Column(
        modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = snapshot.title.uppercase(Locale.ROOT),
            style = TextStyle(
                color = appearance.metadataPrimaryText.asGlanceColorProvider(),
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Text(
            text = snapshot.artist.uppercase(Locale.ROOT),
            style = TextStyle(
                color = appearance.metadataSecondaryText.asGlanceColorProvider(),
                fontSize = if (compact) 10.sp else 11.sp
            ),
            maxLines = 1
        )
    }
}
