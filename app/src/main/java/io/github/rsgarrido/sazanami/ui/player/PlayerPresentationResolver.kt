package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.runtime.Immutable
import androidx.compose.ui.unit.DpSize
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.theme.PlayerThemeTokens

/**
 * Identity of a complete mini/expanded/morph implementation, independent of the persisted primary
 * theme. Add landscape identities only when their matching endpoints and presentation policies exist.
 * They do not need to become values of [PlayerTheme] or appear in global appearance consumers.
 */
@Immutable
sealed interface PlayerPresentationIdentity {
    @Immutable
    data class Existing(val theme: PlayerTheme) : PlayerPresentationIdentity

    @Immutable
    data object PocketAdvance : PlayerPresentationIdentity
}

/** One value is forwarded to both endpoints and their morph dispatcher; neither resolves again. */
@Immutable
data class ResolvedPlayerPresentation(
    val identity: PlayerPresentationIdentity,
    val tokens: PlayerThemeTokens
)

/**
 * Explicit form-factor input, not inferred from navigation-rail eligibility. There is no existing
 * tablet/foldable/desktop classification policy in the app. Until one is supplied by the caller,
 * unknown devices and the separately listed form factors are ineligible.
 */
internal enum class PlayerDeviceKind { PHONE, TABLET, FOLDABLE, DESKTOP, UNKNOWN }

@Immutable
internal data class TabletLandscapePlayerContext(
    val deviceKind: PlayerDeviceKind,
    /** Large-screen qualification from device policy, separate from the resizable host's space. */
    val smallestScreenWidthDp: Int,
    /** Actual player-host space after persistent safe insets, not physical display dimensions. */
    val availableSize: DpSize
)

/**
 * A caller may nominate a complete player pair without deciding how it will eventually be selected.
 * The implementation supplies its own usable-space budget, including any large-text requirements.
 * No unfinished landscape identities or placeholder renderers are registered here.
 */
@Immutable
internal data class TabletLandscapePlayerCandidate(
    val presentation: ResolvedPlayerPresentation,
    val minimumSize: DpSize
)

internal fun isTabletLandscapePlayerEligible(
    context: TabletLandscapePlayerContext,
    minimumSize: DpSize
): Boolean {
    val available = context.availableSize
    return context.deviceKind == PlayerDeviceKind.TABLET &&
        context.smallestScreenWidthDp >= 600 &&
        available.isFiniteAndPositive() && minimumSize.isFiniteAndPositive() &&
        available.width > available.height &&
        available.width >= minimumSize.width && available.height >= minimumSize.height
}

/**
 * Pure presentation policy: never writes preferences or owns playback, navigation, or morph state.
 * The persisted tablet-landscape choice nominates Pocket Advance. Actual host constraints are
 * resolved above capability/endpoint consumers; the presentation boundary settles interrupted motion
 * before publishing a changed pair. This function never rewrites the primary selection or accents.
 */
internal fun resolvePlayerPresentation(
    primaryTheme: PlayerTheme,
    primaryThemeTokens: PlayerThemeTokens,
    context: TabletLandscapePlayerContext? = null,
    landscapeCandidate: TabletLandscapePlayerCandidate? = null
): ResolvedPlayerPresentation {
    if (context != null && landscapeCandidate != null &&
        isTabletLandscapePlayerEligible(context, landscapeCandidate.minimumSize)
    ) {
        return landscapeCandidate.presentation
    }
    return ResolvedPlayerPresentation(PlayerPresentationIdentity.Existing(primaryTheme), primaryThemeTokens)
}

private fun DpSize.isFiniteAndPositive(): Boolean =
    width.value.isFinite() && height.value.isFinite() && width.value > 0f && height.value > 0f
