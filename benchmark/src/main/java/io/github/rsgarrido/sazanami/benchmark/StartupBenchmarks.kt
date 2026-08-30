package io.github.rsgarrido.sazanami.benchmark

import androidx.benchmark.macro.BaselineProfileMode
import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class StartupBenchmarks {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartup_existingInstall() = measureStartup(StartupMode.COLD)

    @Test
    fun warmStartup_existingInstall() = measureStartup(StartupMode.WARM)

    @Test
    fun hotStartup_existingInstall() = measureStartup(StartupMode.HOT)

    private fun measureStartup(startupMode: StartupMode) {
        val actions = SazanamiBenchmarkActions()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.Partial(
                baselineProfileMode = BaselineProfileMode.UseIfAvailable
            ),
            startupMode = startupMode,
            iterations = BenchmarkConfig.fullIterations,
            setupBlock = {
                pressHome()
            }
        ) {
            // Measurement starts at launcher start and ends when MainActivity reports drawn.
            // Persistent Room/DataStore state is retained between iterations.
            with(actions) { launchAndWaitForShell() }
        }
    }
}
