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
    val composition = pocketCassetteWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
    Column(GlanceModifier.fillMaxSize()) {
        PocketCassetteShellSeam(appearance, screwEdgeDp = 3)
        Spacer(GlanceModifier.height(1.dp))
        Row(
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketCassetteCompactLabel(
                snapshot = snapshot,
                appearance = appearance,
                artworkEdgeDp = composition.artworkEdgeDp,
                reelEdgeDp = composition.reelEdgeDp,
                reelCount = composition.staticReelCount,
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(4.dp))
            PocketCassetteTransportStrip(
                snapshot = snapshot,
                appearance = appearance,
                controlEdgeDp = composition.controlEdgeDp,
                modifier = GlanceModifier.width(102.dp).height(36.dp)
            )
        }
    }
}

@Composable
internal fun PocketCassetteStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketCassetteWidgetCompositionFor(NowPlayingWidgetLayout.STANDARD)
    Column(GlanceModifier.fillMaxSize()) {
        PocketCassetteStandardFace(
            snapshot = snapshot,
            appearance = appearance,
            artworkEdgeDp = composition.artworkEdgeDp,
            reelEdgeDp = composition.reelEdgeDp,
            reelCount = composition.staticReelCount,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight()
        )
        Spacer(GlanceModifier.height(4.dp))
        PocketCassetteTransportStrip(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            modifier = GlanceModifier.fillMaxWidth().height(36.dp),
            showHardwareDetails = composition.showHardwareDetails
        )
    }
}

internal data class PocketCassetteWidgetComposition(
    val artworkEdgeDp: Int,
    val reelEdgeDp: Int,
    val staticReelCount: Int,
    val controlEdgeDp: Int,
    val showHardwareDetails: Boolean
)

internal fun pocketCassetteWidgetCompositionFor(
    layout: NowPlayingWidgetLayout
): PocketCassetteWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> PocketCassetteWidgetComposition(
        artworkEdgeDp = 30,
        reelEdgeDp = 8,
        staticReelCount = 2,
        controlEdgeDp = 32,
        showHardwareDetails = false
    )
    NowPlayingWidgetLayout.STANDARD -> PocketCassetteWidgetComposition(
        artworkEdgeDp = 52,
        reelEdgeDp = 12,
        staticReelCount = 2,
        controlEdgeDp = 32,
        showHardwareDetails = true
    )
}

@Composable
private fun PocketCassetteCompactLabel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    artworkEdgeDp: Int,
    reelEdgeDp: Int,
    reelCount: Int,
    modifier: GlanceModifier
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
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Artwork(snapshot, artworkEdgeDp, appearance)
            Spacer(GlanceModifier.width(4.dp))
            PocketCassetteLabelMetadata(
                snapshot = snapshot,
                appearance = appearance,
                compact = true,
                modifier = GlanceModifier.defaultWeight()
            )
            Spacer(GlanceModifier.width(3.dp))
            PocketCassetteReelWindow(
                appearance = appearance,
                reelEdgeDp = reelEdgeDp,
                reelCount = reelCount,
                modifier = GlanceModifier.width(30.dp).height(22.dp)
            )
        }
    }
}

@Composable
private fun PocketCassetteStandardFace(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    artworkEdgeDp: Int,
    reelEdgeDp: Int,
    reelCount: Int,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(4.dp)
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(snapshot, artworkEdgeDp, appearance)
                Spacer(GlanceModifier.width(7.dp))
                Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                    PocketCassetteLabelMetadata(
                        snapshot = snapshot,
                        appearance = appearance,
                        compact = false,
                        modifier = GlanceModifier.fillMaxWidth().defaultWeight()
                    )
                    Spacer(GlanceModifier.height(3.dp))
                    PocketCassetteReelWindow(
                        appearance = appearance,
                        reelEdgeDp = reelEdgeDp,
                        reelCount = reelCount,
                        modifier = GlanceModifier.fillMaxWidth().height(22.dp)
                    )
                }
            }
            PocketCassetteScrewPair(appearance, screwEdgeDp = 5)
        }
    }
}

@Composable
private fun PocketCassetteLabelMetadata(
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
            text = snapshot.title.uppercase(Locale.ROOT),
            style = TextStyle(
                color = appearance.metadataPrimaryText.asGlanceColorProvider(),
                fontSize = if (compact) 10.sp else 12.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = if (compact) linePolicy.titleMaxLines else 1
        )
        Text(
            text = snapshot.artist.uppercase(Locale.ROOT),
            style = TextStyle(
                color = appearance.metadataSecondaryText.asGlanceColorProvider(),
                fontSize = if (compact) 8.sp else 9.sp
            ),
            maxLines = linePolicy.artistMaxLines
        )
    }
}

@Composable
private fun PocketCassetteReelWindow(
    appearance: NowPlayingWidgetAppearance,
    reelEdgeDp: Int,
    reelCount: Int,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(5.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius(4.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(appearance.metadataSecondaryText.asGlanceColorProvider())
            ) {}
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                repeat(reelCount) { index ->
                    if (index > 0) {
                        Spacer(GlanceModifier.defaultWeight())
                    }
                    PocketCassetteReel(appearance, reelEdgeDp)
                }
            }
        }
    }
}

@Composable
private fun PocketCassetteReel(
    appearance: NowPlayingWidgetAppearance,
    edgeDp: Int
) {
    Box(
        modifier = GlanceModifier
            .size(edgeDp.dp)
            .background(appearance.metadataSecondaryText.asGlanceColorProvider())
            .cornerRadius((edgeDp / 2).dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius(((edgeDp - 4) / 2).coerceAtLeast(1).dp)
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(appearance.accent.asGlanceColorProvider())
                    .cornerRadius(((edgeDp - 6) / 2).coerceAtLeast(1).dp)
            ) {}
        }
    }
}

@Composable
private fun PocketCassetteTransportStrip(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    controlEdgeDp: Int,
    modifier: GlanceModifier,
    showHardwareDetails: Boolean = false
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.controlCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.controlCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showHardwareDetails) {
                PocketCassetteScrew(appearance, edgeDp = 6)
                Spacer(GlanceModifier.defaultWeight())
            }
            TransportControls(snapshot, controlEdgeDp, appearance)
            if (showHardwareDetails) {
                Spacer(GlanceModifier.defaultWeight())
                PocketCassetteScrew(appearance, edgeDp = 6)
            }
        }
    }
}

@Composable
private fun PocketCassetteShellSeam(
    appearance: NowPlayingWidgetAppearance,
    screwEdgeDp: Int
) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(screwEdgeDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketCassetteScrew(appearance, screwEdgeDp)
        Spacer(GlanceModifier.width(4.dp))
        Box(
            modifier = GlanceModifier
                .defaultWeight()
                .height(1.dp)
                .background(appearance.panelOutline.asGlanceColorProvider())
        ) {}
        Spacer(GlanceModifier.width(4.dp))
        PocketCassetteScrew(appearance, screwEdgeDp)
    }
}

@Composable
private fun PocketCassetteScrewPair(
    appearance: NowPlayingWidgetAppearance,
    screwEdgeDp: Int
) {
    Row(GlanceModifier.fillMaxWidth()) {
        PocketCassetteScrew(appearance, screwEdgeDp)
        Spacer(GlanceModifier.defaultWeight())
        PocketCassetteScrew(appearance, screwEdgeDp)
    }
}

@Composable
private fun PocketCassetteScrew(
    appearance: NowPlayingWidgetAppearance,
    edgeDp: Int
) {
    Box(
        modifier = GlanceModifier
            .size(edgeDp.dp)
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius((edgeDp / 2).dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.background.asGlanceColorProvider())
                .cornerRadius(((edgeDp - 2) / 2).coerceAtLeast(1).dp)
        ) {}
    }
}

@Composable
private fun RetroMetadata(
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
            text = snapshot.title.uppercase(Locale.ROOT),
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
            text = snapshot.artist.uppercase(Locale.ROOT),
            style = TextStyle(
                color = appearance.metadataSecondaryText.asGlanceColorProvider(),
                fontSize = if (compact) 10.sp else 11.sp
            ),
            maxLines = linePolicy.artistMaxLines
        )
    }
}
