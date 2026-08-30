package io.github.rsgarrido.sazanami.benchmark

import androidx.benchmark.macro.junit4.BaselineProfileRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class BaselineProfileGenerator {
    @get:Rule
    val baselineProfileRule = BaselineProfileRule()

    @Test
    fun startupProfile() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = true
    ) {
        pressHome()
        val actions = SazanamiBenchmarkActions()
        with(actions) { launchAndWaitForShell() }
    }

    @Test
    fun commonJourneysProfile() = baselineProfileRule.collect(
        packageName = TARGET_PACKAGE,
        includeInStartupProfile = false
    ) {
        val actions = SazanamiBenchmarkActions()
        with(actions) {
            launchAndWaitForShell()
            selectHome()
            scrollForward(1)
            selectLibrary()
            selectLibrarySection("Songs")
            selectLibraryView("List")
            scrollForward(2)
            selectLibrarySection("Albums")
            scrollForward(1)
            selectSearch()
            enterSearchQuery("a")
            selectExpandedPlayerTheme("Pocket Flip")
            startPlaybackFromFirstSong()
            expandPlayer()
            clickPlayPause()
            openQueue()
        }
    }
}
