package io.github.rsgarrido.sazanami.ui

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MusicShellLayoutTest {
    @Test
    fun narrowAndShallowWindowsKeepBottomNavigation() {
        for ((width, height) in listOf(412 to 915, 800 to 1280, 839 to 800, 1000 to 399)) {
            assertEquals(MusicNavigationMode.Bottom,
                resolveMusicShellLayout(width.dp, height.dp).navigationMode)
        }
    }

    @Test
    fun railThresholdUsesUsableWindowDimensionsInEitherOrientation() {
        val insets = MusicShellInsets(left = 20.dp, top = 24.dp, right = 16.dp, bottom = 48.dp)
        assertEquals(MusicNavigationMode.Rail, resolveMusicShellLayout(876.dp, 472.dp, insets).navigationMode)
        assertEquals(MusicNavigationMode.Bottom, resolveMusicShellLayout(875.dp, 472.dp, insets).navigationMode)
        assertEquals(MusicNavigationMode.Bottom, resolveMusicShellLayout(876.dp, 471.dp, insets).navigationMode)
        assertEquals(MusicNavigationMode.Rail, resolveMusicShellLayout(900.dp, 1400.dp).navigationMode)
        assertEquals(MusicNavigationMode.Rail, resolveMusicShellLayout(1280.dp, 800.dp).navigationMode)
    }

    @Test
    fun largeTextRequiresEnoughContentRoomBesideTheRail() {
        assertEquals(MusicNavigationMode.Bottom,
            resolveMusicShellLayout(997.dp, 800.dp, fontScale = 1.5f).navigationMode)
        val layout = resolveMusicShellLayout(998.dp, 800.dp, fontScale = 1.5f)
        assertTrue(layout.usesRail)
        assertEquals(900.dp, 998.dp - layout.contentLeft - layout.contentRight)
    }

    @Test
    fun compactReservationsPreserveNavigationMiniTimerAndSelectionCases() {
        val layout = resolveMusicShellLayout(412.dp, 915.dp)
        assertEquals(0.dp, layout.contentLeft)
        assertEquals(82.dp, layout.miniPlayerBottomGap)
        assertEquals(202.dp, layout.padding(navigation = true, mini = true))
        assertEquals(120.dp, layout.padding(navigation = false, mini = true))
        assertEquals(130.dp, layout.padding(navigation = true, mini = false))
        assertEquals(282.dp, layout.padding(navigation = true, mini = true, timer = true))
        assertEquals(32.dp, layout.padding(navigation = false, mini = false, selection = true))
    }

    @Test
    fun railReservationsDoNotDependOnWhetherChromeIsVisible() {
        val layout = resolveMusicShellLayout(1280.dp, 800.dp,
            MusicShellInsets(left = 12.dp, top = 24.dp, right = 16.dp, bottom = 48.dp))
        assertEquals(110.dp, layout.contentLeft)
        assertEquals(24.dp, layout.contentTop)
        assertEquals(16.dp, layout.contentRight)
        assertEquals(840.dp, layout.miniPlayerMaxWidth)
        assertEquals(8.dp, layout.miniPlayerBottomGap)
        assertEquals(152.dp, layout.padding(navigation = true, mini = true))
        assertEquals(152.dp, layout.padding(navigation = false, mini = true))
        assertEquals(232.dp, layout.padding(navigation = true, mini = true, timer = true))
        assertEquals(72.dp, layout.padding(navigation = true, mini = false))
        assertEquals(56.dp, layout.padding(navigation = false, mini = false, selection = true))
    }

    private fun MusicShellLayout.padding(
        navigation: Boolean, mini: Boolean, timer: Boolean = false, selection: Boolean = false
    ) = bottomContentPadding(24.dp, navigation, mini, selection, timer)
}
