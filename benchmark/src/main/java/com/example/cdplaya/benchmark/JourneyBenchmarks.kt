package com.example.cdplaya.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class JourneyBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun homeLaunchAndVerticalScroll() = measureJourney(
        setup = { selectHome() },
        measure = { scrollForward() }
    )

    @Test
    fun librarySongsListScroll() = measureLibrarySection("Songs", "List")

    @Test
    fun librarySongsGridScroll() = measureLibrarySection("Songs", "Grid: 3 columns")

    @Test
    fun albumsScroll() = measureLibrarySection("Albums")

    @Test
    fun artistsScroll() = measureLibrarySection("Artists")

    @Test
    fun searchQueryAndResultsScroll() = measureJourney(
        setup = { selectSearch() },
        measure = {
            enterSearchQuery("a")
            scrollForward()
        }
    )

    @Test
    fun albumTracksScroll() = measureLibraryDetail("Albums")

    @Test
    fun artistAlbumsAndTracksScroll() = measureLibraryDetail("Artists")

    @Test
    fun modernPlayerExpandAndCollapse() = measureExpandedPlayer(
        theme = "Sazanami Default"
    ) { collapsePlayer() }

    @Test
    fun modernPlayerSwipeBetweenTracks() = measureExpandedPlayer(
        theme = "Sazanami Default"
    ) { swipeExpandedPlayerLeft() }

    @Test
    fun expandedQueueOpenAndManipulate() = measureExpandedPlayer(
        theme = "Pocket Flip"
    ) {
        openQueue()
        scrollForward(2)
    }

    @Test
    fun switchPlayerThemes() = measureJourney(
        setup = { selectHome() },
        measure = { selectExpandedPlayerTheme("Classic Wheel") }
    )

    @Test
    fun pocketFlipExpandedSustained() = measureExpandedPlayer("Pocket Flip") {
        keepAnimatedPlayerVisible()
    }

    @Test
    fun pocketCassetteExpandedSustained() = measureExpandedPlayer("Pocket Cassette") {
        keepAnimatedPlayerVisible()
    }

    @Test
    fun classicWheelExpandedControl() = measureExpandedPlayer("Classic Wheel") {
        exerciseStaticPlayerControl()
    }

    @Test
    fun returnToShellWhilePlaybackContinues() = measureExpandedPlayer("Sazanami Default") {
        collapsePlayer()
        scrollForward(2)
    }

    private fun measureLibrarySection(
        section: String,
        viewOption: String? = null
    ) = measureJourney(
        setup = {
            selectLibrary()
            selectLibrarySection(section)
            viewOption?.let(::selectLibraryView)
        },
        measure = { scrollForward() }
    )

    private fun measureLibraryDetail(section: String) = measureJourney(
        setup = {
            selectLibrary()
            selectLibrarySection(section)
            selectLibraryView("Grid: 3 columns")
            openFirstLibraryItem()
        },
        measure = { scrollForward() }
    )

    private fun measureExpandedPlayer(
        theme: String,
        measure: SazanamiBenchmarkActions.() -> Unit
    ) = measureJourney(
        setup = {
            selectExpandedPlayerTheme(theme)
            startPlaybackFromFirstSong()
            expandPlayer()
            ensurePlaybackRunning()
        },
        measure = measure
    )

    private fun measureJourney(
        setup: SazanamiBenchmarkActions.() -> Unit,
        measure: SazanamiBenchmarkActions.() -> Unit
    ) {
        val actions = SazanamiBenchmarkActions()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable
            ),
            startupMode = StartupMode.WARM,
            iterations = BenchmarkConfig.fullIterations,
            setupBlock = {
                with(actions) {
                    launchAndWaitForShell()
                    setup()
                }
            }
        ) {
            // Only the interaction below is measured; launch, permission handling, theme
            // selection, and destination setup are outside the measured section.
            actions.measure()
        }
    }
}
