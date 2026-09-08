package io.github.rsgarrido.sazanami.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
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

@Composable
internal fun ClassicWheelCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClassicWheelDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            artworkEdgeDp = 32,
            compact = true
        )
        Spacer(GlanceModifier.width(5.dp))
        TransportControls(snapshot, 32, appearance)
    }
}

@Composable
internal fun ClassicWheelStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClassicWheelArtworkPanel(
            snapshot = snapshot,
            appearance = appearance,
            outerEdgeDp = 88,
            artworkEdgeDp = 76
        )
        Spacer(GlanceModifier.width(10.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            ClassicWheelMetadataPanel(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
            Spacer(GlanceModifier.height(4.dp))
            Box(
                modifier = GlanceModifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                TransportControls(snapshot, 38, appearance)
            }
        }
    }
}

@Composable
private fun ClassicWheelArtworkPanel(
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
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .size((artworkEdgeDp + 4).dp)
                    .background(appearance.panelSurface.asGlanceColorProvider())
                    .cornerRadius(appearance.panelCornerRadiusDp.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Artwork(snapshot, artworkEdgeDp, appearance)
            }
        }
    }
}

@Composable
private fun ClassicWheelMetadataPanel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        DeviceMetadata(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(horizontal = 7.dp, vertical = 3.dp),
            compact = false
        )
    }
}

@Composable
private fun ClassicWheelDisplay(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    artworkEdgeDp: Int,
    compact: Boolean
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(if (compact) 3.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(artworkEdgeDp.dp)
                    .background(appearance.panelSurface.asGlanceColorProvider())
                    .cornerRadius(appearance.panelCornerRadiusDp.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Artwork(snapshot, artworkEdgeDp - 4, appearance)
            }
            Spacer(GlanceModifier.width(if (compact) 6.dp else 9.dp))
            DeviceMetadata(snapshot, appearance, GlanceModifier.defaultWeight(), compact)
        }
    }
}

@Composable
internal fun PocketFlipCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            artworkEdgeDp = 30,
            compact = true
        )
        Spacer(GlanceModifier.width(5.dp))
        TransportControls(snapshot, 32, appearance)
    }
}

@Composable
internal fun PocketFlipStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Column(GlanceModifier.fillMaxSize()) {
        PocketFlipDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            artworkEdgeDp = 44,
            compact = false
        )
        PocketFlipHinge(appearance)
        Box(
            modifier = GlanceModifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            TransportControls(snapshot, 38, appearance)
        }
    }
}

@Composable
private fun PocketFlipDisplay(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    artworkEdgeDp: Int,
    compact: Boolean
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(2.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 2).coerceAtLeast(0).dp)
                .padding(
                    horizontal = if (compact) 3.dp else 6.dp,
                    vertical = if (compact) 3.dp else 4.dp
                ),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = GlanceModifier
                    .size(artworkEdgeDp.dp)
                    .background(appearance.panelSurface.asGlanceColorProvider())
                    .cornerRadius(appearance.artworkCornerRadiusDp.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Artwork(snapshot, artworkEdgeDp - 4, appearance)
            }
            Spacer(GlanceModifier.width(if (compact) 6.dp else 9.dp))
            DeviceMetadata(snapshot, appearance, GlanceModifier.defaultWeight(), compact)
        }
    }
}

@Composable
private fun PocketFlipHinge(appearance: NowPlayingWidgetAppearance) {
    Box(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(5.dp)
            .padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(3.dp)
                .background(appearance.panelOutline.asGlanceColorProvider())
                .cornerRadius(2.dp)
        ) {}
    }
}

@Composable
internal fun PocketDiscCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketDiscArtwork(snapshot, appearance, outerEdgeDp = 40, artworkEdgeDp = 30)
        Spacer(GlanceModifier.width(6.dp))
        PocketDiscDisplay(
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
internal fun PocketDiscStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketDiscArtwork(snapshot, appearance, outerEdgeDp = 88, artworkEdgeDp = 70)
        Spacer(GlanceModifier.width(9.dp))
        Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
            PocketDiscDisplay(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                compact = false
            )
            Spacer(GlanceModifier.height(5.dp))
            TransportControls(snapshot, 38, appearance)
        }
    }
}

@Composable
private fun PocketDiscArtwork(
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
private fun PocketDiscDisplay(
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
        DeviceMetadata(
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
private fun DeviceMetadata(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    compact: Boolean
) {
    val linePolicy = widgetMetadataLinePolicyFor(
        if (compact) NowPlayingWidgetLayout.COMPACT else NowPlayingWidgetLayout.STANDARD
    )
    Column(
        modifier = modifier.clickable(actionStartActivity<MainActivity>()),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = snapshot.title,
            style = TextStyle(
                color = appearance.metadataPrimaryText.asGlanceColorProvider(),
                fontSize = if (compact) 12.sp else 14.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = linePolicy.titleMaxLines
        )
        if (!compact) {
            Spacer(GlanceModifier.height(2.dp))
        }
        Text(
            text = snapshot.artist,
            style = TextStyle(
                color = appearance.metadataSecondaryText.asGlanceColorProvider(),
                fontSize = if (compact) 10.sp else 12.sp
            ),
            maxLines = linePolicy.artistMaxLines
        )
    }
}
