package io.github.rsgarrido.sazanami.ui.player.pocketadvance

import androidx.compose.ui.geometry.Rect

internal const val PocketAdvanceAlbumOpenDurationMillis = 240

/** Logical LCD dp only: these bounds never participate in the player morph's measurements. */
internal data class PocketAdvanceAlbumArchiveLayout(
    val cataloguePanelWidth: Float,
    val recordPanelWidth: Float,
    val catalogueArtwork: Rect,
    val recordArtwork: Rect,
    val gap: Float
)

internal fun pocketAdvanceAlbumArchiveLayout(width: Float, height: Float, fontScale: Float): PocketAdvanceAlbumArchiveLayout {
    val safeWidth = width.coerceAtLeast(1f)
    val safeHeight = height.coerceAtLeast(1f)
    val textScale = fontScale.takeIf { it.isFinite() && it > 0f }?.coerceAtLeast(1f) ?: 1f
    val gap = minOf(12f, safeWidth * .04f)
    val cataloguePanel = (safeWidth - gap) * .43f
    val recordPanel = maxOf(112f, (safeWidth - gap) * .30f).coerceAtMost(cataloguePanel)
    val catalogueSize = minOf(cataloguePanel - 8f, safeHeight - 78f * textScale - 12f, 304f).coerceAtLeast(1f)
    val catalogueTop = ((safeHeight - catalogueSize - 78f * textScale - 8f) / 2f).coerceAtLeast(0f)
    val recordSize = minOf(recordPanel - 16f, safeHeight * .42f, 192f).coerceAtLeast(1f)
    val recordTop = minOf(8f, (safeHeight - recordSize).coerceAtLeast(0f))
    return PocketAdvanceAlbumArchiveLayout(
        cataloguePanel, recordPanel,
        Rect((cataloguePanel - catalogueSize) / 2f, catalogueTop,
            (cataloguePanel + catalogueSize) / 2f, catalogueTop + catalogueSize),
        Rect((recordPanel - recordSize) / 2f, recordTop,
            (recordPanel + recordSize) / 2f, recordTop + recordSize), gap
    )
}

internal fun isPocketAdvanceArchiveArtworkSchemeLocal(scheme: String?): Boolean =
    scheme == null || scheme.equals("content", true) || scheme.equals("file", true) || scheme.equals("android.resource", true)
