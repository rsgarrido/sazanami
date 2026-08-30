package io.github.rsgarrido.sazanami.benchmark

internal const val TARGET_PACKAGE = "io.github.rsgarrido.sazanami"

internal object BenchmarkConfig {
    val smokeIterations: Int
        get() = instrumentationArgument("cdplaya.smokeIterations", 2)

    val fullIterations: Int
        get() = instrumentationArgument("cdplaya.fullIterations", 10)

    private fun instrumentationArgument(name: String, defaultValue: Int): Int {
        return androidx.test.platform.app.InstrumentationRegistry.getArguments()
            .getString(name)
            ?.toIntOrNull()
            ?.coerceAtLeast(1)
            ?: defaultValue
    }
}
