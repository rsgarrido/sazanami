# Phase 4 performance checkpoint

## Measurement environment

1. **Project branch:** `performance-baseline-checkpoint-v1`
2. **Measured project state:** app source at `18cee21` (`Complete performance checkpoint`) plus the benchmark-harness fixes and generated profiles included by the device-validation closeout commit.
3. **Device model:** Samsung `SM-S908U1`, 12 GB RAM, eight CPU cores.
4. **Android version:** Android 16, API 36.
5. **Build variant:** `benchmark` app build: non-debuggable, shell-profileable, debug-signed locally, release minification/resource-shrinking settings inherited (both currently disabled).
6. **App version:** `1.0` (`versionCode` 1).
7. **Library size:** 370 songs, 32 albums, and 24 artists visible to the benchmark build.
8. **Screen refresh rate:** Not recorded during the completed runs.
9. **Battery/charging state:** The session began at 69% and approximately 37.3 °C while powered; the long session later reached 100%. USB/charging state was not controlled, so results are local engineering measurements rather than laboratory comparisons.
10. **Brightness/audio output:** Not recorded. The device and local library were kept consistent within each completed suite.

## Tool versions and run configuration

- Android Gradle Plugin: 9.2.1
- Gradle wrapper: 9.4.1
- Kotlin: 2.2.10
- AndroidX Benchmark/Baseline Profile plugin: 1.4.1
- AndroidX ProfileInstaller: 1.4.1
- AndroidX UIAutomator: 2.4.0
- AndroidX Tracing: 1.3.0
- Default full iterations: 10 (`cdplaya.fullIterations` overrides)
- Default smoke iterations: 2 (`cdplaya.smokeIterations` overrides)

The stable Baseline Profile plugin 1.4.1 reports that its declared compatibility testing predates AGP 9.0. Despite that warning, the module compiled under AGP 9.2.1, connected profile collection completed, both generated files were non-empty, and the release-like APK contained the compiled profile assets.

## Pre-optimization inspection

Pocket Flip used a continuously repeating Compose `Animatable` while playback was active. The animation was advanced by the Compose frame clock, so a 60/120 Hz display could invalidate its meter Canvas at the display cadence. Every draw rebuilt a two-element meter list and recalculated local waveform sampling, smoothing, seeded band movement, and 48 LCD segments. The loop was disposed by theme replacement/collapse, but was not explicitly gated under an expanded-player sheet and had no local-silence gate.

Pocket Cassette used another frame-clock `Animatable` for reel rotation. Its state was read in `PocketCassetteWindow`, causing the artwork, label, window, and mechanism call subtree to participate in high-frequency recomposition. Dynamic reel/tape geometry was recalculated per frame, and a two-element roller `List` was allocated in the draw path. Playback pause stopped the animation, while overlay and explicit lifecycle gates were absent.

Retro Rack used the same frame-clock meter pattern as Pocket Flip and allocated meter levels per draw. Classic Wheel did not contain a comparable continuous visualizer loop and remains the control theme. Modern’s continuous work is limited to user-triggered carousel/drag animations; its waveform bars are derived from the existing 500 ms playback progress state rather than an independent frame loop.

The expanded player is only composed while expanded and the selected theme is a direct `when` branch, so collapse and theme replacement dispose the old theme. Playback progress is collected inside the overlay subtree, not at the app-shell root. Lifecycle-aware route collection already prevents broad stopped-Activity state observation, but delay-based visualizer scheduling would not have been safe; the new limiter therefore retains the lifecycle-aware Compose frame clock and adds an explicit STARTED gate.

Library lists/grids generally use stable item keys. `contentType` is not supplied, but no benchmark evidence was available to justify a speculative list rewrite. Cache-first library publication, the MediaStore refresh, DataStore preferences, controller connection, and reconciliation remain in their existing order.

## Implemented instrumentation and bounded rendering

Constant privacy-safe trace sections cover preferences-ready, cache-first/final library publication, MediaStore index query, library classification/enrichment, artwork repair, index construction, reconciliation planning, playback connection, metadata replacement, queue replacement, and Pocket Flip/Pocket Cassette/Retro Rack update/draw work. No song metadata or paths are included.

All retro visualizer phase sources now use a 30 Hz admission limiter over Compose frame callbacks. A 120 Hz display can still provide smooth frame synchronization, but at most roughly 30 expensive state updates are accepted per second. The loops stop or suspend when playback pauses, the theme is replaced, the player collapses, the Activity falls below STARTED, a full-screen/sheet overlay covers the player, or the local Pocket Flip/Retro Rack waveform energy is below the silence gate. Caller-owned `FloatArray` buffers replace per-update meter-list allocation. Pocket Cassette reel state is read only in its mechanism Canvas, and roller geometry no longer creates a per-draw list.

These changes are supported by source inspection and deterministic cadence/lifecycle tests. They are not claimed as measured CPU, frame-time, power, or thermal improvements.

## Results

### StartupTimingMetric

The complete startup class passed all three cases with 10 iterations each in one device session.

| Scenario | Minimum | Median | Maximum |
|---|---:|---:|---:|
| Cold existing-install startup | 346.0 ms | 389.0 ms | 491.8 ms |
| Warm existing-install startup | 137.4 ms | 164.5 ms | 394.2 ms |
| Hot existing-install startup | 76.5 ms | 102.6 ms | 122.8 ms |

The separate two-iteration smoke suite also passed. Its cold-start minimum/median/maximum was 428.9/431.4/433.9 ms. The Home-scroll smoke case reported frame CPU P50/P95 of 7.57/11.42 ms and frame-overrun P50/P95 of 7.30/11.19 ms (68 and 81 frames in the two samples).

### FrameTimingMetric critical journeys

| Journey set | Completed validation |
|---|---|
| Home, Songs list/grid, Albums, Artists, Search, album detail, artist detail | All paths passed device validation. The full measurement invocation completed 10 iterations for these paths. |
| Modern expand/collapse and track swipe, queue, theme switch, Pocket Flip, Classic Wheel, return to shell | All paths passed device validation. The full measurement invocation completed 10 iterations for these paths. |
| Pocket Cassette sustained | The full-class invocation exposed a paused-playback setup condition and therefore produced no RenderThread slices. After playback became an explicit precondition, the targeted 10-iteration retry passed. |

The diagnostic class run initially passed 14 of 16 journeys at one iteration. Album detail and Classic Wheel then passed targeted one-iteration retries after selector and static-control fixes. A later full measurement invocation ran for approximately 43 minutes: 15 journey methods completed their 10 iterations, while Pocket Cassette exposed the setup issue described above and subsequently passed its targeted 10-iteration retry. Another attempted full-class rerun was manually cancelled around its midpoint to release the phone; it is excluded from completed results.

Exact percentile values for the full journey cases are not reported because later connected invocations replaced the raw Gradle output directory before a stable summary was retained. The successful test status is recorded, but missing frame metrics are not reconstructed or invented.

### Visualizer before/after, memory, CPU, and thermal

- Pocket Flip before/after: Not measured comparably. The pre-optimization state was not captured on this device, so no hardware improvement is claimed.
- Pocket Cassette before/after: Not measured comparably. The completed post-change journey validation does not provide a before/after result.
- Classic Wheel control: The corrected control journey passed, but its raw percentile summary was not retained.
- Memory observations: No stable memory metric was collected.
- CPU/Perfetto observations: Macrobenchmark generated traces for completed iterations, but no retained, comparable CPU summary was analyzed. Source inspection and deterministic tests—not a CPU measurement—support the 30 Hz cadence conclusion.
- Thermal observations: No controlled, comparable ten-minute thermal run completed. The original heating concern remains a real-device follow-up; no thermal improvement is claimed.

### Baseline Profile comparison

- No-compilation cold startup (10 iterations): minimum 425.6 ms, median 468.1 ms, maximum 507.5 ms.
- Baseline Profile cold startup (10 iterations, `BaselineProfileMode.Require`): minimum 409.0 ms, median 482.7 ms, maximum 719.4 ms.
- The Baseline Profile median was 14.6 ms (3.1%) slower in this run. This does **not** demonstrate a startup improvement; background work, ordering, and device variance remain possible contributors.
- Generated `baseline-prof.txt`: 33,942 generated profile rules (3,745,170 bytes).
- Generated `startup-prof.txt`: 26,810 generated profile rules (2,792,178 bytes).

Both generated files are tracked under `app/src/release/generated/baselineProfiles/`. Every non-empty line matched generated profile-rule syntax, and no build log or error payload was present. The release-like APK was inspected and contained `assets/dexopt/baseline.prof` and `assets/dexopt/baseline.profm`; the required-mode comparison also confirmed that a Baseline Profile was available for compilation.

## Reproduction commands

```powershell
.\gradlew.bat :app:assembleBenchmark
.\gradlew.bat :benchmark:assembleBenchmarkBenchmark

# Reduced smoke suite
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.MacrobenchmarkSmokeSuite"

# Full startup and frame suites
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.StartupBenchmarks"
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.JourneyBenchmarks"

# Generate Baseline and focused Startup Profiles
.\gradlew.bat :app:generateReleaseBaselineProfile

# Same-device compilation comparison
.\gradlew.bat :benchmark:connectedBenchmarkBenchmarkAndroidTest `
  "-Pandroid.testInstrumentationRunnerArguments.class=io.github.rsgarrido.sazanami.benchmark.BaselineProfileComparisonBenchmarks"
```

Benchmark JSON and traces are written below `benchmark/build/outputs/`; connected reports are below `benchmark/build/reports/androidTests/connected/`. Do not commit these artifacts.

For an additional 60-second Pocket Flip Perfetto capture, install/launch the benchmark build, select Pocket Flip, begin playback, expand it, then run:

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
Get-Content -Raw tools\performance\cdplaya-perfetto.pbtx |
  & $adb shell perfetto --txt -c - -o /data/misc/perfetto-traces/cdplaya-phase4.perfetto-trace
& $adb pull /data/misc/perfetto-traces/cdplaya-phase4.perfetto-trace benchmark\build\outputs\perfetto\
```

## Thermal method for the next device run

Use the same physical device, benchmark build, track, playback state, brightness, audio route, refresh-rate mode, starting battery percentage, and approximate starting temperature. Disconnect charging when practical. Let the phone return near baseline between the app shell, Modern, Classic Wheel, Pocket Flip, and Pocket Cassette runs. Record both paused and playing cases, run each expanded theme for ten minutes, and capture at least one Pocket Flip Perfetto trace. Treat all observations as comparative and device-specific, not laboratory energy measurements.

## Automated validation

The final closeout validation completed successfully on July 22, 2026:

```powershell
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest lintDebug `
  :app:assembleBenchmark :benchmark:assembleBenchmarkBenchmark --stacktrace
.\gradlew.bat connectedDebugAndroidTest --stacktrace
```

The combined host/build command reported `BUILD SUCCESSFUL` with 164 actionable tasks (13 executed and 151 up-to-date). `connectedDebugAndroidTest` then passed 12 tests on the `SM-S908U1` and reported `BUILD SUCCESSFUL` with 69 actionable tasks. The AndroidX Test Services setup printed a harmless `No UID for androidx.test.services` app-ops message before the successful connected run.

The merged benchmark manifest contains `<profileable android:shell="true" />` and no debuggable application attribute. APK signature verification reported the local `Android Debug` certificate, while the production release configuration remained untouched. APK inspection reconfirmed both compiled dexopt profile assets. `git diff --check` passed. No build directory, APK, app bundle, Perfetto trace, benchmark JSON, or connected-test build report is tracked by Git.

## Limitations and remaining concerns

- Exact full-journey percentile summaries were not retained before subsequent connected invocations replaced the raw output directory. Successful test completion is documented, but those values are intentionally omitted.
- The later repeated full-journey invocation was cancelled and is not represented as a completed measurement.
- No controlled before/after CPU, memory, power, or thermal comparison completed; no improvement in those dimensions is claimed.
- The 30 Hz cadence preserves the intended retro motion mathematically and is covered by deterministic tests, but visual review at both fixed 60 Hz and adaptive/120 Hz is still desirable.
- Charging, brightness, audio route, and refresh-rate state were not controlled or fully recorded during the long device session.
- Baseline Profile plugin 1.4.1’s AGP 9.2.1 warning should be rechecked when a newer stable plugin declares AGP 9 support.
- The same-device Baseline Profile comparison did not show a median startup improvement, so it should be repeated under a tighter device protocol before drawing a performance conclusion.
