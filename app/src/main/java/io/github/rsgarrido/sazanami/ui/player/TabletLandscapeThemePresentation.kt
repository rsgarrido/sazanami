package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.minimumPocketAdvanceSize
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

/** Nomination only. The existing resolver remains responsible for every eligibility/fallback rule. */
internal fun tabletLandscapePlayerCandidate(
    theme: TabletLandscapeTheme,
    primaryThemeTokens: PlayerThemeTokens,
    fontScale: Float
): TabletLandscapePlayerCandidate? = when (theme) {
    TabletLandscapeTheme.NORMAL_PLAYER -> null
    // Reserve the durable choice without a renderer, size budget, or presentation identity.
    // Even a restored/development preference must keep using the complete primary player pair.
    TabletLandscapeTheme.POCKET_HORIZON -> null
    TabletLandscapeTheme.POCKET_ADVANCE -> TabletLandscapePlayerCandidate(
        // Advance keeps its fixed palette; these tokens still belong to the independent primary theme.
        ResolvedPlayerPresentation(PlayerPresentationIdentity.PocketAdvance, primaryThemeTokens),
        minimumPocketAdvanceSize(fontScale)
    )
}
