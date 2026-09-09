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
    val composition = classicWheelWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ClassicWheelScreenPanel(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            artworkEdgeDp = composition.artworkEdgeDp,
            compact = true,
            showStatusBar = composition.showStatusBar
        )
        Spacer(GlanceModifier.width(4.dp))
        ClassicWheelControlRegion(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            wheelWidthDp = composition.wheelWidthDp,
            modifier = GlanceModifier.width(composition.wheelWidthDp.dp).fillMaxHeight()
        )
    }
}

@Composable
internal fun ClassicWheelStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = classicWheelWidgetCompositionFor(NowPlayingWidgetLayout.STANDARD)
    Column(GlanceModifier.fillMaxSize()) {
        ClassicWheelScreenPanel(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            artworkEdgeDp = composition.artworkEdgeDp,
            compact = false,
            showStatusBar = composition.showStatusBar
        )
        Spacer(GlanceModifier.height(4.dp))
        ClassicWheelControlRegion(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            wheelWidthDp = composition.wheelWidthDp,
            modifier = GlanceModifier.fillMaxWidth().height(44.dp)
        )
    }
}

internal data class ClassicWheelWidgetComposition(
    val artworkEdgeDp: Int,
    val controlEdgeDp: Int,
    val wheelWidthDp: Int,
    val showStatusBar: Boolean
)

internal fun classicWheelWidgetCompositionFor(
    layout: NowPlayingWidgetLayout
): ClassicWheelWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> ClassicWheelWidgetComposition(
        artworkEdgeDp = 30,
        controlEdgeDp = 32,
        wheelWidthDp = 102,
        showStatusBar = false
    )
    NowPlayingWidgetLayout.STANDARD -> ClassicWheelWidgetComposition(
        artworkEdgeDp = 36,
        controlEdgeDp = 32,
        wheelWidthDp = 150,
        showStatusBar = true
    )
}

@Composable
private fun ClassicWheelScreenPanel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    artworkEdgeDp: Int,
    compact: Boolean,
    showStatusBar: Boolean
) {
    Box(
        modifier = modifier
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.metadataSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
        ) {
            if (showStatusBar) {
                ClassicWheelStatusBar(appearance)
            }
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight()
                    .padding(if (compact) 3.dp else 4.dp),
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
                Spacer(GlanceModifier.width(if (compact) 6.dp else 8.dp))
                DeviceMetadata(snapshot, appearance, GlanceModifier.defaultWeight(), compact)
            }
        }
    }
}

@Composable
private fun ClassicWheelStatusBar(appearance: NowPlayingWidgetAppearance) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(10.dp)
            .background(appearance.panelSurface.asGlanceColorProvider())
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "NOW PLAYING",
            style = TextStyle(
                color = appearance.metadataPrimaryText.asGlanceColorProvider(),
                fontSize = 6.sp,
                fontWeight = FontWeight.Bold
            ),
            maxLines = 1
        )
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .width(12.dp)
                .height(5.dp)
                .background(appearance.metadataPrimaryText.asGlanceColorProvider())
                .cornerRadius(1.dp)
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(appearance.metadataSurface.asGlanceColorProvider())
                    .cornerRadius(1.dp)
            ) {}
        }
    }
}

@Composable
private fun ClassicWheelControlRegion(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    controlEdgeDp: Int,
    wheelWidthDp: Int,
    modifier: GlanceModifier
) {
    val controlSurface = appearance.controlSurface ?: appearance.background
    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .width(wheelWidthDp.dp)
                .height(40.dp)
                .background(appearance.panelOutline.asGlanceColorProvider())
                .cornerRadius(20.dp)
                .padding(1.dp),
            contentAlignment = Alignment.Center
        ) {
            Box(
                modifier = GlanceModifier
                    .fillMaxSize()
                    .background(controlSurface.asGlanceColorProvider())
                    .cornerRadius(19.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(40.dp)
                        .background(appearance.panelSurface.asGlanceColorProvider())
                        .cornerRadius(20.dp)
                ) {}
            }
        }
        TransportControls(snapshot, controlEdgeDp, appearance)
    }
}

@Composable
internal fun PocketFlipCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketFlipWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            artworkEdgeDp = composition.artworkEdgeDp,
            compact = true
        )
        if (composition.usesCompressedVerticalHinge) {
            PocketFlipVerticalHinge(appearance)
        }
        PocketFlipControlDeck(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            modifier = GlanceModifier.width(102.dp).fillMaxHeight()
        )
    }
}

@Composable
internal fun PocketFlipStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketFlipWidgetCompositionFor(NowPlayingWidgetLayout.STANDARD)
    Column(GlanceModifier.fillMaxSize()) {
        PocketFlipDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
            artworkEdgeDp = composition.artworkEdgeDp,
            compact = false
        )
        if (!composition.usesCompressedVerticalHinge) {
            PocketFlipHorizontalHinge(appearance)
        }
        PocketFlipControlDeck(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            modifier = GlanceModifier.fillMaxWidth().height(38.dp),
            showDeckDetails = composition.showDeckDetails
        )
    }
}

internal data class PocketFlipWidgetComposition(
    val artworkEdgeDp: Int,
    val controlEdgeDp: Int,
    val usesCompressedVerticalHinge: Boolean,
    val showDeckDetails: Boolean
)

internal fun pocketFlipWidgetCompositionFor(
    layout: NowPlayingWidgetLayout
): PocketFlipWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> PocketFlipWidgetComposition(
        artworkEdgeDp = 30,
        controlEdgeDp = 32,
        usesCompressedVerticalHinge = true,
        showDeckDetails = false
    )
    NowPlayingWidgetLayout.STANDARD -> PocketFlipWidgetComposition(
        artworkEdgeDp = 44,
        controlEdgeDp = 32,
        usesCompressedVerticalHinge = false,
        showDeckDetails = true
    )
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
private fun PocketFlipVerticalHinge(appearance: NowPlayingWidgetAppearance) {
    Box(
        modifier = GlanceModifier
            .width(5.dp)
            .fillMaxHeight()
            .padding(horizontal = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelOutline.asGlanceColorProvider())
                .cornerRadius(2.dp)
        ) {}
    }
}

@Composable
private fun PocketFlipHorizontalHinge(appearance: NowPlayingWidgetAppearance) {
    Row(
        modifier = GlanceModifier.fillMaxWidth().height(7.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketFlipHingeSegment(appearance, GlanceModifier.width(34.dp))
        Spacer(GlanceModifier.width(3.dp))
        PocketFlipHingeSegment(appearance, GlanceModifier.defaultWeight())
        Spacer(GlanceModifier.width(3.dp))
        PocketFlipHingeSegment(appearance, GlanceModifier.width(34.dp))
    }
}

@Composable
private fun PocketFlipHingeSegment(
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier
) {
    Box(
        modifier = modifier
            .height(5.dp)
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(3.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.background.asGlanceColorProvider())
                .cornerRadius(2.dp)
        ) {}
    }
}

@Composable
private fun PocketFlipControlDeck(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    controlEdgeDp: Int,
    modifier: GlanceModifier,
    showDeckDetails: Boolean = false
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
                .background(appearance.background.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showDeckDetails) {
                Spacer(GlanceModifier.width(4.dp))
                PocketFlipHardwareScrew(appearance, edgeDp = 6)
                Spacer(GlanceModifier.width(5.dp))
                PocketFlipSpeakerGrille(appearance)
                Spacer(GlanceModifier.defaultWeight())
            }
            TransportControls(snapshot, controlEdgeDp, appearance)
            if (showDeckDetails) {
                Spacer(GlanceModifier.defaultWeight())
                PocketFlipSpeakerGrille(appearance)
                Spacer(GlanceModifier.width(5.dp))
                PocketFlipHardwareScrew(appearance, edgeDp = 6)
                Spacer(GlanceModifier.width(4.dp))
            }
        }
    }
}

@Composable
private fun PocketFlipSpeakerGrille(appearance: NowPlayingWidgetAppearance) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        repeat(3) { index ->
            if (index > 0) {
                Spacer(GlanceModifier.width(2.dp))
            }
            Box(
                modifier = GlanceModifier
                    .size(3.dp)
                    .background(appearance.panelOutline.asGlanceColorProvider())
                    .cornerRadius(2.dp)
            ) {}
        }
    }
}

@Composable
private fun PocketFlipHardwareScrew(
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
internal fun PocketDiscCompactWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketDiscWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
    Row(
        modifier = GlanceModifier.fillMaxSize(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        PocketDiscMediaCartridge(
            snapshot = snapshot,
            appearance = appearance,
            outerEdgeDp = composition.cartridgeEdgeDp,
            windowEdgeDp = composition.discWindowEdgeDp,
            artworkEdgeDp = composition.artworkEdgeDp,
            showMoldedDetails = composition.showMoldedDetails
        )
        Spacer(GlanceModifier.width(4.dp))
        PocketDiscDisplay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            compact = true
        )
        Spacer(GlanceModifier.width(4.dp))
        PocketDiscControlBay(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            modifier = GlanceModifier.width(98.dp).fillMaxHeight()
        )
    }
}

@Composable
internal fun PocketDiscStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketDiscWidgetCompositionFor(NowPlayingWidgetLayout.STANDARD)
    PocketDiscChassis(
        snapshot = snapshot,
        appearance = appearance,
        composition = composition
    )
}

internal data class PocketDiscWidgetComposition(
    val cartridgeEdgeDp: Int,
    val discWindowEdgeDp: Int,
    val artworkEdgeDp: Int,
    val controlEdgeDp: Int,
    val showMoldedDetails: Boolean
)

internal fun pocketDiscWidgetCompositionFor(
    layout: NowPlayingWidgetLayout
): PocketDiscWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> PocketDiscWidgetComposition(
        cartridgeEdgeDp = 40,
        discWindowEdgeDp = 34,
        artworkEdgeDp = 28,
        controlEdgeDp = 32,
        showMoldedDetails = false
    )
    NowPlayingWidgetLayout.STANDARD -> PocketDiscWidgetComposition(
        cartridgeEdgeDp = 88,
        discWindowEdgeDp = 76,
        artworkEdgeDp = 68,
        controlEdgeDp = 32,
        showMoldedDetails = true
    )
}

@Composable
private fun PocketDiscChassis(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    composition: PocketDiscWidgetComposition
) {
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            PocketDiscMediaCartridge(
                snapshot = snapshot,
                appearance = appearance,
                outerEdgeDp = composition.cartridgeEdgeDp,
                windowEdgeDp = composition.discWindowEdgeDp,
                artworkEdgeDp = composition.artworkEdgeDp,
                showMoldedDetails = composition.showMoldedDetails
            )
            Spacer(GlanceModifier.width(8.dp))
            Column(GlanceModifier.defaultWeight().fillMaxHeight()) {
                PocketDiscDisplay(
                    snapshot = snapshot,
                    appearance = appearance,
                    modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                    compact = false
                )
                PocketDiscPanelSeam(appearance)
                PocketDiscControlBay(
                    snapshot = snapshot,
                    appearance = appearance,
                    controlEdgeDp = composition.controlEdgeDp,
                    modifier = GlanceModifier.fillMaxWidth().height(36.dp),
                    showHardwareDetails = composition.showMoldedDetails
                )
            }
        }
    }
}

@Composable
private fun PocketDiscMediaCartridge(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    outerEdgeDp: Int,
    windowEdgeDp: Int,
    artworkEdgeDp: Int,
    showMoldedDetails: Boolean
) {
    Box(
        modifier = GlanceModifier
            .size(outerEdgeDp.dp)
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.panelCornerRadiusDp.dp)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.panelCornerRadiusDp - 1).coerceAtLeast(0).dp)
        ) {
            Box(
                modifier = GlanceModifier.fillMaxWidth().height(if (showMoldedDetails) 5.dp else 3.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .width(if (showMoldedDetails) 22.dp else 12.dp)
                        .height(2.dp)
                        .background(appearance.background.asGlanceColorProvider())
                        .cornerRadius(1.dp)
                ) {}
            }
            Box(
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                contentAlignment = Alignment.Center
            ) {
                PocketDiscMediaWindow(
                    snapshot = snapshot,
                    appearance = appearance,
                    windowEdgeDp = windowEdgeDp,
                    artworkEdgeDp = artworkEdgeDp
                )
            }
        }
        if (showMoldedDetails) {
            PocketDiscScrewPair(appearance, edgeDp = 5)
        }
    }
}

@Composable
private fun PocketDiscMediaWindow(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    windowEdgeDp: Int,
    artworkEdgeDp: Int
) {
    Box(
        modifier = GlanceModifier
            .size(windowEdgeDp.dp)
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius((windowEdgeDp / 2).dp)
            .padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.artworkSurface.asGlanceColorProvider())
                .cornerRadius(((windowEdgeDp - 4) / 2).coerceAtLeast(1).dp),
            contentAlignment = Alignment.Center
        ) {
            Artwork(snapshot, artworkEdgeDp, appearance)
            Box(
                modifier = GlanceModifier
                    .size(if (windowEdgeDp > 40) 12.dp else 7.dp)
                    .background(appearance.panelOutline.asGlanceColorProvider())
                    .cornerRadius(if (windowEdgeDp > 40) 6.dp else 4.dp)
                    .padding(2.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(appearance.accent.asGlanceColorProvider())
                        .cornerRadius(if (windowEdgeDp > 40) 4.dp else 2.dp)
                ) {}
            }
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
private fun PocketDiscPanelSeam(appearance: NowPlayingWidgetAppearance) {
    Box(
        modifier = GlanceModifier.fillMaxWidth().height(4.dp).padding(vertical = 1.dp),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(2.dp)
                .background(appearance.panelOutline.asGlanceColorProvider())
        ) {}
    }
}

@Composable
private fun PocketDiscControlBay(
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
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.controlCornerRadiusDp - 1).coerceAtLeast(0).dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (showHardwareDetails) {
                Spacer(GlanceModifier.width(3.dp))
                PocketDiscHardwareScrew(appearance, edgeDp = 5)
                Spacer(GlanceModifier.defaultWeight())
            }
            TransportControls(snapshot, controlEdgeDp, appearance)
            if (showHardwareDetails) {
                Spacer(GlanceModifier.defaultWeight())
                PocketDiscHardwareScrew(appearance, edgeDp = 5)
                Spacer(GlanceModifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun PocketDiscScrewPair(
    appearance: NowPlayingWidgetAppearance,
    edgeDp: Int
) {
    Row(GlanceModifier.fillMaxWidth()) {
        PocketDiscHardwareScrew(appearance, edgeDp)
        Spacer(GlanceModifier.defaultWeight())
        PocketDiscHardwareScrew(appearance, edgeDp)
    }
}

@Composable
private fun PocketDiscHardwareScrew(
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
