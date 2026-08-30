# Sazanami performance benchmarks

This module runs Macrobenchmark and Baseline Profile tests against the non-debuggable,
shell-profileable `benchmark` app build. The app variant inherits the current release
minification and resource-shrinking choices (both are currently disabled), uses only the
local debug signing key, and does not change production release signing.

## Device prerequisites

- API 28 or newer for Macrobenchmark; API 33 or newer is recommended for profile generation.
- A physical device is required for representative timing and thermal comparisons.
- Install music locally and grant audio/image access. Journey benchmarks preserve app data and
  expect at least one playable track; library/detail journeys expect a non-empty library.
- Disable system animations only if a recorded comparison explicitly uses that same setting for
  both runs. Keep refresh rate, brightness, audio route, battery, and charging state consistent.

## Commands

Discover the exact tasks available to this checkout:

```powershell
.\gradlew.bat :app:tasks --all
.\gradlew.bat :benchmark:tasks --all
```

Task discovery with AGP 9.2.1 and Baseline Profile plugin 1.4.1 produced these commands:

```powershell
.\gradlew.bat :app:assembleBenchmark
.\gradlew.bat :benchmark:assembleBenchmarkBenchmark
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest
.\gradlew.bat :app:generateReleaseBaselineProfile
```

Run an individual suite with quoted instrumentation class filtering (PowerShell requires the
quotes around dotted `-P` property names):

```powershell
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.MacrobenchmarkSmokeSuite"
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.BaselineProfileComparisonBenchmarks"
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.StartupBenchmarks"
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.JourneyBenchmarks"
.\gradlew.bat :app:generateReleaseBaselineProfile
```

Use instrumentation arguments `sazanami.smokeIterations` and `sazanami.fullIterations` to override
the default 2-iteration smoke and 10-iteration full suites.

Gradle writes benchmark JSON and Perfetto traces below `benchmark/build/outputs/` and connected
test results below `benchmark/build/reports/androidTests/connected/`. Generated profile sources
are copied by the Baseline Profile plugin under `app/src/release/generated/baselineProfiles/`.
Do not commit raw traces, APKs, or generated benchmark build outputs.

The reproducible thermal protocol, results table, limitations, and standalone Perfetto capture
command are in `docs/performance/phase4-performance-report.md`.

Debug builds are useful for correctness only. They are debuggable and are not representative
performance measurements; all reported timing must come from the release-like benchmark variant.
