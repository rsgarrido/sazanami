package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import io.github.rsgarrido.sazanami.data.PlayerTheme
import io.github.rsgarrido.sazanami.data.TabletLandscapeTheme
import io.github.rsgarrido.sazanami.ui.player.pocketadvance.minimumPocketAdvanceSize
import io.github.rsgarrido.sazanami.ui.player.pockethorizon.minimumPocketHorizonSize
import io.github.rsgarrido.sazanami.ui.player.theme.defaultTokens
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Test

class TabletLandscapeThemePresentationTest {
    private val landscape = TabletLandscapePlayerContext(PlayerDeviceKind.TABLET, 800, DpSize(1600.dp, 900.dp))

    @Test
    fun stableIdsRoundTripAndMissingOrUnknownIdsUseTheNormalPlayer() {
        assertEquals("normal_player", TabletLandscapeTheme.NORMAL_PLAYER.id)
        assertEquals("pocket_advance", TabletLandscapeTheme.POCKET_ADVANCE.id)
        assertEquals("pocket_horizon", TabletLandscapeTheme.POCKET_HORIZON.id)
        assertEquals(TabletLandscapeTheme.entries.size, TabletLandscapeTheme.entries.map { it.id }.toSet().size)
        TabletLandscapeTheme.entries.forEach { assertEquals(it, TabletLandscapeTheme.fromId(it.id)) }
        listOf(null, "", "future_theme", "POCKET_ADVANCE", "POCKET_HORIZON", "0").forEach {
            assertEquals(TabletLandscapeTheme.NORMAL_PLAYER, TabletLandscapeTheme.fromId(it))
        }
        assertEquals(PlayerTheme.DEFAULT, PlayerTheme.fromId(TabletLandscapeTheme.POCKET_ADVANCE.id))
        assertEquals(PlayerTheme.DEFAULT, PlayerTheme.fromId(TabletLandscapeTheme.POCKET_HORIZON.id))
    }

    @Test
    fun onlyCompleteThemesAreSelectableInTheirExistingOrder() {
        assertEquals(listOf(TabletLandscapeTheme.NORMAL_PLAYER, TabletLandscapeTheme.POCKET_ADVANCE,
            TabletLandscapeTheme.POCKET_HORIZON),
            TabletLandscapeTheme.selectableThemes)
    }

    @Test
    fun restoredHorizonSelectionUsesItsOwnBudgetAndPreservesEveryPrimaryThemeThroughFallbackAndReturn() {
        val contexts = listOf(null,
            landscape.copy(availableSize = DpSize(800.dp, 1200.dp)),
            landscape.copy(availableSize = DpSize(800.dp, 800.dp)),
            landscape.copy(availableSize = DpSize(700.dp, 500.dp)),
            landscape.copy(smallestScreenWidthDp = 599),
            landscape.copy(availableSize = DpSize(Float.NaN.dp, 800.dp)),
            landscape.copy(availableSize = DpSize(1200.dp, Float.POSITIVE_INFINITY.dp))) +
            PlayerDeviceKind.entries.filter { it != PlayerDeviceKind.TABLET }.map { landscape.copy(deviceKind = it) }
        PlayerTheme.entries.forEach { primary ->
            val tokens = primary.defaultTokens().copy(shellColor = Color.Red, accentColor = Color.Green)
            listOf(1f, 2f).forEach { fontScale ->
                val saved = TabletLandscapeTheme.fromId("pocket_horizon")
                val candidate = requireNotNull(tabletLandscapePlayerCandidate(saved, tokens, fontScale))
                val minimum = minimumPocketHorizonSize(fontScale)
                assertEquals(minimum, candidate.minimumSize)
                assertEquals(PlayerPresentationIdentity.PocketHorizon, candidate.presentation.identity)
                assertSame(tokens, candidate.presentation.tokens)
                val tooSmall = listOf(
                    landscape.copy(availableSize = DpSize(minimum.width - 1.dp, minimum.height)),
                    landscape.copy(availableSize = DpSize(minimum.width, minimum.height - 1.dp)))
                (contexts + tooSmall).forEach { context ->
                    val resolved = resolvePlayerPresentation(primary, tokens, context, candidate)
                    assertEquals(PlayerPresentationIdentity.Existing(primary), resolved.identity)
                    assertSame(tokens, resolved.tokens)
                }
                listOf(landscape, landscape.copy(availableSize = minimum)).forEach { context ->
                    val active = resolvePlayerPresentation(primary, tokens, context, candidate)
                    assertSame(candidate.presentation, active)
                    assertSame(tokens, active.tokens)
                }
                assertEquals("pocket_horizon", saved.id)
            }
        }
    }

    @Test
    fun fontScaleChangesEligibilityWithoutChangingTheSavedChoiceOrPrimaryColors() {
        val primary = PlayerTheme.CLASSIC_WHEEL
        val tokens = primary.defaultTokens().copy(shellColor = Color.Red, accentColor = Color.Green)
        val saved = TabletLandscapeTheme.fromId("pocket_horizon")
        val host = landscape.copy(availableSize = DpSize(1000.dp, 650.dp))
        listOf(1f, 2f, 1f).forEach { scale ->
            val resolved = resolvePlayerPresentation(primary, tokens, host,
                tabletLandscapePlayerCandidate(saved, tokens, scale))
            assertEquals(if (scale == 1f) PlayerPresentationIdentity.PocketHorizon
                else PlayerPresentationIdentity.Existing(primary), resolved.identity)
            assertSame(tokens, resolved.tokens)
            assertEquals(TabletLandscapeTheme.POCKET_HORIZON, saved)
        }
    }

    @Test
    fun normalSelectionDoesNotNominateEvenAnEligibleWindow() {
        PlayerTheme.entries.forEach { primary ->
            val tokens = primary.defaultTokens().copy(shellColor = Color.Red)
            val candidate = tabletLandscapePlayerCandidate(TabletLandscapeTheme.NORMAL_PLAYER, tokens, 1f)
            assertNull(candidate)
            val resolved = resolvePlayerPresentation(primary, tokens, landscape, candidate)
            assertEquals(PlayerPresentationIdentity.Existing(primary), resolved.identity)
            assertSame(tokens, resolved.tokens)
        }
    }

    @Test
    fun productionNominationUsesTheExistingBudgetAndAllEligibilityRules() {
        val tokens = PlayerTheme.CLASSIC_WHEEL.defaultTokens()
        val candidate = requireNotNull(tabletLandscapePlayerCandidate(TabletLandscapeTheme.POCKET_ADVANCE, tokens, 2f))
        assertEquals(minimumPocketAdvanceSize(2f), candidate.minimumSize)
        assertEquals(PlayerPresentationIdentity.PocketAdvance, candidate.presentation.identity)
        assertSame(tokens, candidate.presentation.tokens)
        assertSame(candidate.presentation, resolvePlayerPresentation(PlayerTheme.CLASSIC_WHEEL, tokens, landscape, candidate))
        listOf(landscape.copy(availableSize = DpSize(800.dp, 1200.dp)),
            landscape.copy(deviceKind = PlayerDeviceKind.PHONE),
            landscape.copy(availableSize = DpSize(700.dp, 500.dp))).forEach { context ->
            val fallback = resolvePlayerPresentation(PlayerTheme.CLASSIC_WHEEL, tokens, context, candidate)
            assertEquals(PlayerPresentationIdentity.Existing(PlayerTheme.CLASSIC_WHEEL), fallback.identity)
            assertSame(tokens, fallback.tokens)
        }
    }

    @Test
    fun primaryChangesFallbackReturnAndDisablingNeverOverwriteThePrimaryTokens() {
        listOf(PlayerTheme.CLASSIC_WHEEL, PlayerTheme.POCKET_CASSETTE).forEach { primary ->
            val tokens = primary.defaultTokens().copy(accentColor = Color.Green)
            val candidate = tabletLandscapePlayerCandidate(TabletLandscapeTheme.POCKET_ADVANCE, tokens, 1f)
            val active = resolvePlayerPresentation(primary, tokens, landscape, candidate)
            assertEquals(PlayerPresentationIdentity.PocketAdvance, active.identity)
            val portrait = resolvePlayerPresentation(primary, tokens,
                landscape.copy(availableSize = DpSize(800.dp, 1200.dp)), candidate)
            assertEquals(PlayerPresentationIdentity.Existing(primary), portrait.identity)
            assertSame(tokens, portrait.tokens)
            assertEquals(active, resolvePlayerPresentation(primary, tokens, landscape, candidate))
            val disabled = resolvePlayerPresentation(primary, tokens, landscape,
                tabletLandscapePlayerCandidate(TabletLandscapeTheme.NORMAL_PLAYER, tokens, 1f))
            assertEquals(PlayerPresentationIdentity.Existing(primary), disabled.identity)
            assertSame(tokens, disabled.tokens)
        }
    }
}
