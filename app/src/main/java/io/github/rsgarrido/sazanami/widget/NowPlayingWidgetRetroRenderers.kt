package io.github.rsgarrido.sazanami.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.LocalSize
import androidx.glance.background
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.cornerRadius
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.RowScope
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
    val composition = retroRackWidgetCompositionFor(NowPlayingWidgetLayout.COMPACT)
    RetroRackFaceplate(
        appearance = appearance,
        modifier = GlanceModifier.fillMaxSize()
    ) {
        RetroRackDisplayBay(
            snapshot = snapshot,
            appearance = appearance,
            modifier = GlanceModifier.defaultWeight().fillMaxHeight(),
            artworkEdgeDp = composition.artworkEdgeDp,
            compact = true,
            artworkFrameInsetDp = composition.artworkFrameInsetDp
        )
        Spacer(GlanceModifier.width(4.dp))
        RetroRackTransportBay(
            snapshot = snapshot,
            appearance = appearance,
            controlEdgeDp = composition.controlEdgeDp,
            modifier = GlanceModifier.width(98.dp).fillMaxHeight()
        )
    }
}

@Composable
internal fun RetroRackStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = retroRackWidgetCompositionFor(
        layout = NowPlayingWidgetLayout.STANDARD,
        widgetHeightDp = LocalSize.current.height.value,
        widgetWidthDp = LocalSize.current.width.value
    )
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.widgetCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.panelSurface.asGlanceColorProvider())
                .cornerRadius((appearance.widgetCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(1.dp)
        ) {
            if (composition.headerAboveArtwork) {
                RetroRackModuleHeader(appearance, composition.headerHorizontalInsetDp)
            }
            RetroRackDisplayBay(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight(),
                artworkEdgeDp = composition.artworkEdgeDp,
                compact = false,
                artworkFrameInsetDp = composition.artworkFrameInsetDp
            )
            Spacer(GlanceModifier.height(3.dp))
            RetroRackTransportBay(
                snapshot = snapshot,
                appearance = appearance,
                controlEdgeDp = composition.controlEdgeDp,
                modifier = GlanceModifier.fillMaxWidth().height(36.dp),
                showHardwareDetails = composition.showHardwareDetails
            )
        }
    }
}

internal data class RetroRackWidgetComposition(
    val artworkEdgeDp: Int,
    val controlEdgeDp: Int,
    val headerAboveArtwork: Boolean,
    val headerHorizontalInsetDp: Int,
    val artworkFrameInsetDp: Int,
    val outerBezelRetained: Boolean,
    val showHardwareDetails: Boolean
)

internal fun retroRackWidgetCompositionFor(
    layout: NowPlayingWidgetLayout,
    widgetHeightDp: Float = 120f,
    widgetWidthDp: Float = 250f
): RetroRackWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> RetroRackWidgetComposition(
        artworkEdgeDp = 28,
        controlEdgeDp = 32,
        headerAboveArtwork = false,
        headerHorizontalInsetDp = 0,
        artworkFrameInsetDp = 0,
        outerBezelRetained = true,
        showHardwareDetails = false
    )
    NowPlayingWidgetLayout.STANDARD -> RetroRackWidgetComposition(
        artworkEdgeDp = expandedArtworkEdgeDpFor(
            widgetHeightDp = widgetHeightDp,
            widgetWidthDp = widgetWidthDp,
            reservedVerticalSpaceDp = 63,
            minimumEdgeDp = 42
        ),
        controlEdgeDp = 32,
        headerAboveArtwork = true,
        headerHorizontalInsetDp = 6,
        artworkFrameInsetDp = 2,
        outerBezelRetained = true,
        showHardwareDetails = true
    )
}

@Composable
private fun RetroRackFaceplate(
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    content: @Composable RowScope.() -> Unit
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
            content()
        }
        RetroRackScrewPair(appearance, edgeDp = 4)
    }
}

@Composable
private fun RetroRackDisplayBay(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    modifier: GlanceModifier,
    artworkEdgeDp: Int,
    compact: Boolean,
    artworkFrameInsetDp: Int
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
                .padding(if (compact) 2.dp else 1.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (compact) {
                Artwork(snapshot, artworkEdgeDp, appearance)
            } else {
                Box(
                    modifier = GlanceModifier
                        .size(artworkEdgeDp.dp)
                        .background(appearance.panelOutline.asGlanceColorProvider())
                        .cornerRadius(appearance.artworkCornerRadiusDp.dp)
                        .padding(artworkFrameInsetDp.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Artwork(snapshot, artworkEdgeDp - (artworkFrameInsetDp * 2), appearance)
                }
            }
            Spacer(GlanceModifier.width(if (compact) 5.dp else 7.dp))
            RetroMetadata(
                snapshot = snapshot,
                appearance = appearance,
                modifier = GlanceModifier.defaultWeight(),
                compact = compact
            )
        }
    }
}

@Composable
private fun RetroRackTransportBay(
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
                RetroRackScrew(appearance, edgeDp = 5)
                Spacer(GlanceModifier.defaultWeight())
            }
            TransportControls(snapshot, controlEdgeDp, appearance)
            if (showHardwareDetails) {
                Spacer(GlanceModifier.defaultWeight())
                RetroRackScrew(appearance, edgeDp = 5)
                Spacer(GlanceModifier.width(3.dp))
            }
        }
    }
}

@Composable
private fun RetroRackModuleHeader(
    appearance: NowPlayingWidgetAppearance,
    horizontalInsetDp: Int
) {
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .height(10.dp)
            .background(appearance.panelSurface.asGlanceColorProvider())
            .padding(horizontal = horizontalInsetDp.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RetroRackScrew(appearance, edgeDp = 5)
        Spacer(GlanceModifier.width(5.dp))
        Spacer(GlanceModifier.defaultWeight())
        Box(
            modifier = GlanceModifier
                .width(12.dp)
                .height(5.dp)
                .background(appearance.accent.asGlanceColorProvider())
                .cornerRadius(1.dp)
        ) {}
    }
}

@Composable
private fun RetroRackScrewPair(
    appearance: NowPlayingWidgetAppearance,
    edgeDp: Int
) {
    Row(GlanceModifier.fillMaxWidth()) {
        RetroRackScrew(appearance, edgeDp)
        Spacer(GlanceModifier.defaultWeight())
        RetroRackScrew(appearance, edgeDp)
    }
}

@Composable
private fun RetroRackScrew(
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
                modifier = GlanceModifier.defaultWeight().fillMaxHeight()
            )
            Spacer(GlanceModifier.width(4.dp))
            PocketCassetteTransportStrip(
                snapshot = snapshot,
                appearance = appearance,
                controlEdgeDp = composition.controlEdgeDp,
                modifier = GlanceModifier
                    .width(102.dp)
                    .height(composition.transportDeckHeightDp.dp)
            )
        }
    }
}

@Composable
internal fun PocketCassetteStandardWidgetContent(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance
) {
    val composition = pocketCassetteWidgetCompositionFor(
        layout = NowPlayingWidgetLayout.STANDARD,
        widgetHeightDp = LocalSize.current.height.value,
        widgetWidthDp = LocalSize.current.width.value
    )
    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(appearance.panelOutline.asGlanceColorProvider())
            .cornerRadius(appearance.widgetCornerRadiusDp.dp)
            .padding(1.dp)
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(appearance.background.asGlanceColorProvider())
                .cornerRadius((appearance.widgetCornerRadiusDp - 1).coerceAtLeast(0).dp)
                .padding(1.dp)
        ) {
            PocketCassetteStandardFace(
                snapshot = snapshot,
                appearance = appearance,
                artworkEdgeDp = composition.artworkEdgeDp,
                modifier = GlanceModifier.fillMaxWidth().defaultWeight()
            )
            Spacer(GlanceModifier.height(3.dp))
            PocketCassetteTransportStrip(
                snapshot = snapshot,
                appearance = appearance,
                controlEdgeDp = composition.controlEdgeDp,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .height(composition.transportDeckHeightDp.dp),
                showHardwareDetails = composition.showHardwareDetails
            )
        }
    }
}

internal data class PocketCassetteWidgetComposition(
    val artworkEdgeDp: Int,
    val controlEdgeDp: Int,
    val transportDeckHeightDp: Int,
    val showReelDecoration: Boolean,
    val usesFullWidthTransportDeck: Boolean,
    val outerFrameRetained: Boolean,
    val showHardwareDetails: Boolean
)

internal fun pocketCassetteWidgetCompositionFor(
    layout: NowPlayingWidgetLayout,
    widgetHeightDp: Float = 120f,
    widgetWidthDp: Float = 250f
): PocketCassetteWidgetComposition = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> PocketCassetteWidgetComposition(
        artworkEdgeDp = 30,
        controlEdgeDp = 32,
        transportDeckHeightDp = 36,
        showReelDecoration = false,
        usesFullWidthTransportDeck = false,
        outerFrameRetained = false,
        showHardwareDetails = false
    )
    NowPlayingWidgetLayout.STANDARD -> PocketCassetteWidgetComposition(
        artworkEdgeDp = expandedArtworkEdgeDpFor(
            widgetHeightDp = widgetHeightDp,
            widgetWidthDp = widgetWidthDp,
            reservedVerticalSpaceDp = 55,
            minimumEdgeDp = 52
        ),
        controlEdgeDp = 32,
        transportDeckHeightDp = 40,
        showReelDecoration = false,
        usesFullWidthTransportDeck = true,
        outerFrameRetained = true,
        showHardwareDetails = true
    )
}

@Composable
private fun PocketCassetteCompactLabel(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    artworkEdgeDp: Int,
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
        }
    }
}

@Composable
private fun PocketCassetteStandardFace(
    snapshot: NowPlayingWidgetSnapshot,
    appearance: NowPlayingWidgetAppearance,
    artworkEdgeDp: Int,
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
        ) {
            Row(
                modifier = GlanceModifier.fillMaxSize().padding(horizontal = 3.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Artwork(snapshot, artworkEdgeDp, appearance)
                Spacer(GlanceModifier.width(8.dp))
                PocketCassetteLabelMetadata(
                    snapshot = snapshot,
                    appearance = appearance,
                    compact = false,
                    modifier = GlanceModifier.defaultWeight().fillMaxHeight()
                )
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
    val linePolicy = retroWidgetMetadataLinePolicyFor(
        if (compact) NowPlayingWidgetLayout.COMPACT else NowPlayingWidgetLayout.STANDARD,
        standardTitleMaxLines = 3
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
            maxLines = linePolicy.titleMaxLines
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
    val linePolicy = retroWidgetMetadataLinePolicyFor(
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

internal fun retroWidgetMetadataLinePolicyFor(
    layout: NowPlayingWidgetLayout,
    standardTitleMaxLines: Int = 2
): WidgetMetadataLinePolicy = when (layout) {
    NowPlayingWidgetLayout.COMPACT -> WidgetMetadataLinePolicy(
        titleMaxLines = 1,
        artistMaxLines = 1
    )
    NowPlayingWidgetLayout.STANDARD -> WidgetMetadataLinePolicy(
        titleMaxLines = standardTitleMaxLines.coerceIn(2, 3),
        artistMaxLines = 1
    )
}
