package io.github.rsgarrido.sazanami.ui.player

import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TabletLandscapePlayerEnvironmentTest {
    private val tablet = TabletLandscapePlatformSnapshot(
        configurationSmallestWidthDp = 800,
        maximumWindowSmallestWidthDp = 800,
        hasActivity = true,
        isNormalUiMode = true,
        hasHingeAngleSensor = false,
        hasPcFeature = false,
        isDefaultDisplay = true,
        isInMultiWindowMode = false,
        isInPictureInPictureMode = false
    )

    @Test
    fun configuredAndPotentialWindowClassMustBothBeLargeEnough() {
        assertEquals(PlayerDeviceKind.TABLET, classify(tablet).deviceKind)
        assertEquals(PlayerDeviceKind.TABLET, classify(tablet.copy(
            configurationSmallestWidthDp = 600, maximumWindowSmallestWidthDp = 600
        )).deviceKind)
        assertEquals(PlayerDeviceKind.PHONE, classify(tablet.copy(
            configurationSmallestWidthDp = 599
        )).deviceKind)
        assertEquals(PlayerDeviceKind.PHONE, classify(tablet.copy(
            maximumWindowSmallestWidthDp = 599
        )).deviceKind)
        assertEquals(650, classify(tablet.copy(maximumWindowSmallestWidthDp = 650)).smallestScreenWidthDp)
    }

    @Test
    fun aPhoneDoesNotBecomeATabletMerelyBecauseItsHostIsWide() {
        val environment = classify(tablet.copy(
            configurationSmallestWidthDp = 412, maximumWindowSmallestWidthDp = 412
        ))
        assertFalse(isTabletLandscapePlayerEligible(
            TabletLandscapePlayerContext(environment.deviceKind, environment.smallestScreenWidthDp,
                DpSize(1400.dp, 800.dp)),
            DpSize(840.dp, 480.dp)
        ))
    }

    @Test
    fun knownHingedDevicesRemainExplicitlyExcludedEvenWhenLarge() {
        assertEquals(PlayerDeviceKind.FOLDABLE,
            classify(tablet.copy(hasHingeAngleSensor = true)).deviceKind)
    }

    @Test
    fun desktopExternalAndAmbiguousMultiwindowContextsAreExcluded() {
        assertEquals(PlayerDeviceKind.DESKTOP, classify(tablet.copy(hasPcFeature = true)).deviceKind)
        assertEquals(PlayerDeviceKind.DESKTOP, classify(tablet.copy(isDefaultDisplay = false)).deviceKind)
        listOf(
            tablet.copy(isNormalUiMode = false),
            tablet.copy(isInMultiWindowMode = true),
            tablet.copy(isInPictureInPictureMode = true)
        ).forEach { snapshot ->
            assertEquals(PlayerDeviceKind.UNKNOWN, classify(snapshot).deviceKind)
        }
    }

    @Test
    fun unavailableActivityOrMetricsNeverClaimTabletEligibility() {
        listOf(
            tablet.copy(hasActivity = false),
            tablet.copy(maximumWindowSmallestWidthDp = 0),
            tablet.copy(configurationSmallestWidthDp = 0),
            tablet.copy(maximumWindowSmallestWidthDp = -1)
        ).forEach { snapshot ->
            assertEquals(PlayerDeviceKind.UNKNOWN, classify(snapshot).deviceKind)
        }
    }

    @Test
    fun deviceClassDoesNotReplacePortraitAndUsableSpaceChecks() {
        val environment = classify(tablet)
        fun eligible(size: DpSize) = isTabletLandscapePlayerEligible(
            TabletLandscapePlayerContext(environment.deviceKind, environment.smallestScreenWidthDp, size),
            DpSize(840.dp, 480.dp)
        )
        assertTrue(eligible(DpSize(1200.dp, 800.dp)))
        assertFalse(eligible(DpSize(800.dp, 1200.dp)))
        assertFalse(eligible(DpSize(839.dp, 600.dp)))
        assertFalse(eligible(DpSize(1000.dp, 479.dp)))
    }

    private fun classify(snapshot: TabletLandscapePlatformSnapshot) =
        classifyTabletLandscapePlayerEnvironment(snapshot)
}
