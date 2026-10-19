package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.ui.player.modern.ModernSeekbarStyle
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerPresentationResolverTest {
    private val minimumSize = DpSize(840.dp, 480.dp)
    private val tabletLandscape = TabletLandscapePlayerContext(
        PlayerDeviceKind.TABLET, 800, DpSize(1200.dp, 800.dp)
    )
    // A real, complete existing pair exercises the future nomination seam without fake renderers.
    private val candidate = TabletLandscapePlayerCandidate(
        ResolvedPlayerPresentation(
            PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_DISC),
            PlayerTheme.POCKET_DISC.defaultTokens()
        ),
        minimumSize
    )

    @Test
    fun noCandidatePreservesEveryPrimaryIdentityAndItsCustomTokensAcrossWindowChanges() {
        val contexts = listOf(
            null,
            TabletLandscapePlayerContext(PlayerDeviceKind.PHONE, 412, DpSize(412.dp, 915.dp)),
            tabletLandscape.copy(availableSize = DpSize(800.dp, 1200.dp)),
            tabletLandscape,
            tabletLandscape.copy(availableSize = DpSize(700.dp, 500.dp))
        )
        PlayerTheme.entries.forEach { primary ->
            val customTokens = primary.defaultTokens().copy(shellColor = Color(0xFF123456))
            contexts.forEach { context ->
                val result = resolvePlayerPresentation(primary, customTokens, context)
                assertEquals(PlayerPresentationIdentity.Existing(primary), result.identity)
                assertSame(customTokens, result.tokens)
            }
        }
    }

    @Test
    fun onlyAnExplicitTabletWithLargeScreenConfigurationCanQualify() {
        PlayerDeviceKind.entries.forEach { kind ->
            assertEquals(kind == PlayerDeviceKind.TABLET,
                isTabletLandscapePlayerEligible(tabletLandscape.copy(deviceKind = kind), minimumSize))
        }
        assertFalse(isTabletLandscapePlayerEligible(
            tabletLandscape.copy(smallestScreenWidthDp = 599), minimumSize))
        assertTrue(isTabletLandscapePlayerEligible(
            tabletLandscape.copy(smallestScreenWidthDp = 600), minimumSize))
    }

    @Test
    fun phonePortraitWidePhoneAndRailSizedUnknownWindowCannotSelectACandidate() {
        val primary = PlayerTheme.CLASSIC_WHEEL
        listOf(
            TabletLandscapePlayerContext(PlayerDeviceKind.PHONE, 412, DpSize(412.dp, 915.dp)),
            TabletLandscapePlayerContext(PlayerDeviceKind.PHONE, 412, DpSize(915.dp, 412.dp)),
            tabletLandscape.copy(deviceKind = PlayerDeviceKind.PHONE),
            tabletLandscape.copy(deviceKind = PlayerDeviceKind.UNKNOWN)
        ).forEach { context ->
            assertEquals(PlayerPresentationIdentity.Existing(primary),
                resolvePlayerPresentation(primary, primary.defaultTokens(), context, candidate).identity)
        }
    }

    @Test
    fun tabletPortraitAndSquareWindowsFallBackEvenWhenTheBudgetFits() {
        listOf(DpSize(900.dp, 1200.dp), DpSize(900.dp, 900.dp)).forEach { available ->
            val context = tabletLandscape.copy(availableSize = available)
            val primary = PlayerTheme.RETRO_RACK
            assertFalse(isTabletLandscapePlayerEligible(context, minimumSize))
            assertEquals(PlayerPresentationIdentity.Existing(primary),
                resolvePlayerPresentation(primary, primary.defaultTokens(), context, candidate).identity)
        }
    }

    @Test
    fun actualUsableWidthAndHeightMustBothMeetTheImplementationBudget() {
        assertTrue(isTabletLandscapePlayerEligible(
            tabletLandscape.copy(availableSize = minimumSize), minimumSize))
        listOf(DpSize(839.dp, 480.dp), DpSize(840.dp, 479.dp)).forEach { available ->
            val context = tabletLandscape.copy(availableSize = available)
            assertFalse(isTabletLandscapePlayerEligible(context, minimumSize))
            val result = resolvePlayerPresentation(PlayerTheme.POCKET_FLIP,
                PlayerTheme.POCKET_FLIP.defaultTokens(), context, candidate)
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.POCKET_FLIP), result.identity)
        }
        // A different implementation can require more space; this is not a universal theme budget.
        assertFalse(isTabletLandscapePlayerEligible(tabletLandscape, DpSize(1201.dp, 800.dp)))
        assertFalse(isTabletLandscapePlayerEligible(tabletLandscape, DpSize(1200.dp, 801.dp)))
    }

    @Test
    fun missingContextAndInvalidMeasurementsCannotActivateACandidate() {
        val primary = PlayerTheme.POCKET_CASSETTE
        assertEquals(PlayerPresentationIdentity.Existing(primary),
            resolvePlayerPresentation(primary, primary.defaultTokens(), landscapeCandidate = candidate).identity)
        val invalidSizes = listOf(
            DpSize.Unspecified, DpSize(0.dp, 480.dp), DpSize(840.dp, (-1).dp),
            DpSize(Dp.Unspecified, 480.dp), DpSize(840.dp, Dp.Unspecified),
            DpSize(Dp.Infinity, 480.dp), DpSize(840.dp, Dp.Infinity)
        )
        invalidSizes.forEach { size ->
            assertFalse(isTabletLandscapePlayerEligible(tabletLandscape.copy(availableSize = size), minimumSize))
            assertFalse(isTabletLandscapePlayerEligible(tabletLandscape, size))
        }
    }

    @Test
    fun qualifyingNominationReturnsOneCompletePresentationAndFallsBackWithoutLosingPrimaryTokens() {
        val primary = PlayerTheme.CLASSIC_WHEEL
        val primaryTokens = primary.defaultTokens().copy(accentColor = Color.Green)
        val eligible = resolvePlayerPresentation(primary, primaryTokens, tabletLandscape, candidate)
        assertSame(candidate.presentation, eligible)
        assertSame(candidate.presentation.tokens, eligible.tokens)

        val resized = tabletLandscape.copy(availableSize = DpSize(700.dp, 500.dp))
        val fallback = resolvePlayerPresentation(primary, primaryTokens, resized, candidate)
        assertEquals(PlayerPresentationIdentity.Existing(primary), fallback.identity)
        assertSame(primaryTokens, fallback.tokens)
        assertSame(candidate.presentation,
            resolvePlayerPresentation(primary, primaryTokens, tabletLandscape, candidate))
    }

    @Test
    fun capabilitiesUseTheResolvedIdentityRatherThanThePrimarySelection() {
        val primary = PlayerTheme.CLASSIC_WHEEL
        val resolved = resolvePlayerPresentation(primary, primary.defaultTokens(), tabletLandscape, candidate)
        assertFalse(supportsNowPlayingMore(primary))
        assertFalse(supportsNowPlayingArtworkViewer(primary))
        assertTrue(supportsNowPlayingMore(resolved.identity))
        assertTrue(supportsNowPlayingArtworkViewer(resolved.identity))

        val defaultPair = candidate.copy(presentation = ResolvedPlayerPresentation(
            PlayerPresentationIdentity.Existing(PlayerTheme.DEFAULT), PlayerTheme.DEFAULT.defaultTokens()
        ))
        val defaultResolved = resolvePlayerPresentation(primary, primary.defaultTokens(), tabletLandscape, defaultPair)
        assertFalse(shouldLoadExpandedPlayerWaveform(primary, ModernSeekbarStyle.WAVEFORM_PREVIEW))
        assertTrue(shouldLoadExpandedPlayerWaveform(defaultResolved.identity, ModernSeekbarStyle.WAVEFORM_PREVIEW))
    }

    @Test
    fun existingIdentitiesRetainMoreArtworkAndWaveformPolicies() {
        PlayerTheme.entries.forEach { theme ->
            val identity = resolvePlayerPresentation(theme, theme.defaultTokens()).identity
            assertEquals(supportsNowPlayingMore(theme), supportsNowPlayingMore(identity))
            assertEquals(supportsNowPlayingArtworkViewer(theme), supportsNowPlayingArtworkViewer(identity))
            ModernSeekbarStyle.entries.forEach { style ->
                assertEquals(shouldLoadExpandedPlayerWaveform(theme, style),
                    shouldLoadExpandedPlayerWaveform(identity, style))
            }
        }
    }
}
