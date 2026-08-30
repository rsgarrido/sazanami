package com.example.cdplaya.benchmark

import androidx.benchmark.macro.CompilationMode
import androidx.benchmark.macro.FrameTimingMetric
import androidx.benchmark.macro.StartupMode
import androidx.benchmark.macro.StartupTimingMetric
import androidx.benchmark.macro.junit4.MacrobenchmarkRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MacrobenchmarkSmokeSuite {
    @get:Rule
    val benchmarkRule = MacrobenchmarkRule()

    @Test
    fun coldStartupSmoke() {
        val actions = SazanamiBenchmarkActions()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(StartupTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.COLD,
            iterations = BenchmarkConfig.smokeIterations,
            setupBlock = { pressHome() }
        ) {
            with(actions) { launchAndWaitForShell() }
        }
    }

    @Test
    fun homeScrollSmoke() {
        val actions = SazanamiBenchmarkActions()
        benchmarkRule.measureRepeated(
            packageName = TARGET_PACKAGE,
            metrics = listOf(FrameTimingMetric()),
            compilationMode = CompilationMode.None(),
            startupMode = StartupMode.WARM,
            iterations = BenchmarkConfig.smokeIterations,
            setupBlock = {
                with(actions) {
                    launchAndWaitForShell()
                    selectHome()
                }
            }
        ) {
            actions.scrollForward(1)
        }
    }
}
