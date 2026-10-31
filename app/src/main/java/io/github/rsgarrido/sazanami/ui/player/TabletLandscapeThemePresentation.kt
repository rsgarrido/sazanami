package io.github.rsgarrido.sazanami.ui.player

import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.minimumPocketAdvanceSize
import io.github.rsgarrido.sazanami.ui.player.pockethorizon.minimumPocketHorizonSize
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

/** Nomination only. The existing resolver remains responsible for every eligibility/fallback rule. */
internal fun tabletLandscapePlayerCandidate(
    theme: TabletLandscapeTheme,
    primaryThemeTokens: PlayerThemeTokens,
    fontScale: Float
): TabletLandscapePlayerCandidate? = when (theme) {
    TabletLandscapeTheme.NORMAL_PLAYER -> null
    TabletLandscapeTheme.POCKET_HORIZON -> TabletLandscapePlayerCandidate(
        ResolvedPlayerPresentation(PlayerPresentationIdentity.PocketHorizon, primaryThemeTokens),
        minimumPocketHorizonSize(fontScale)
    )
    TabletLandscapeTheme.POCKET_ADVANCE -> TabletLandscapePlayerCandidate(
        // Advance keeps its fixed palette; these tokens still belong to the independent primary theme.
        ResolvedPlayerPresentation(PlayerPresentationIdentity.PocketAdvance, primaryThemeTokens),
        minimumPocketAdvanceSize(fontScale)
    )
}
