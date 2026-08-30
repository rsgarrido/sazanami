# Phase A: Android USB bit-perfect feasibility

> **Historical feasibility report**
>
> This document records the Phase A prototype and its evidence. The experimental
> runtime implementation described here was later removed from the production
> application after native Android bit-perfect playback was deferred. The
> original implementation remains available in Git history. See
> [Native Android Bit-Perfect Playback — Deferred](native-bit-perfect-decision.md).

This document records feasibility evidence only. It does not describe a production
bit-perfect feature and it does not claim that ordinary Sazanami playback is
bit-perfect.

## 1. Git state

- Required branch: `bit-perfect-feasibility-v1`
- Base commit: `a6b0d5b1860f2116a94204772da2da19a481c0de`
- Base description: merge of the stable Media3 1.10.1 upgrade
- Implementation commit: `fc1c6ff` — Add USB audio feasibility observer and probe
- Verification commit: `855cb26` — Add bit-perfect feasibility verification
- Initial Phase A report commit: `a62536f` — Document Phase A feasibility evidence
- Closure commits: the focused hardware-discovered diagnostics fix and the
  report commit that contains this document; exact hashes are supplied in the
  delivery summary because a commit cannot contain its own hash.
- Push status: not pushed
- Final status: Phase A changes committed; the preserved user file below remains
  untracked.
- Preserved unrelated file: untracked `STATUS_REPORT.md`

## 2. Media3 1.10.1 API findings

The resolved `media3-exoplayer-1.10.1-sources.jar` and AAR contain:

- `AudioOutputProvider`, including immutable `FormatConfig`, `OutputConfig`, and
  `FormatSupport` values.
- `AudioTrackAudioOutputProvider.Builder`, including the API-24-gated
  `setAudioTrackBuilderModifier`.
- `ForwardingAudioOutputProvider` and `ForwardingAudioOutput`.
- `AudioTrackAudioOutput.getAudioTrack()`.
- `DefaultAudioSink.Builder.setAudioOutputProvider`.

For raw PCM, `AudioTrackAudioOutputProvider.getOutputConfig` preserves the
encoding, sample rate, and channel configuration supplied in `FormatConfig`.
However, `DefaultAudioSink` first chooses its processing path. With float output
disabled it converts non-PCM16 raw input to PCM16 and runs the ordinary processor
chain. With float output enabled, high-resolution integer PCM is converted to
float and the ordinary processor chain is bypassed. In 1.10.1,
`FormatConfig.enableHighResolutionPcmOutput` is populated directly from the
sink's float-output flag; there is no independent builder switch for preserving
high-resolution integer PCM while retaining ordinary processors.

The default `AudioOutput.canReuseAudioOutput` decision is strict
`OutputConfig.equals`. Encoding, sample rate, channel mask, tunneling, offload,
buffer size, audio attributes, session request, virtual device, playback
parameters, and gapless-offload flags therefore participate in reuse. A changed
sample rate or encoding normally requires a new output without reconstructing
ExoPlayer.

Current online reference pages describe a newer surface in places. For example,
the current `AudioTrackAudioOutputProvider` reference lists
`getAudioCapabilities()`, and the current `AudioTrackAudioOutput` reference
documents a public `audioTrack` field. Neither is present as that API in the
resolved 1.10.1 source; 1.10.1 uses the supported `getAudioTrack()` method.

Official reference points used alongside the resolved sources and installed
stubs: [Android bit-perfect playback guidance](https://developer.android.com/media/platform/improve-audio-playback),
[AudioManager](https://developer.android.com/reference/android/media/AudioManager),
[AudioTrack](https://developer.android.com/reference/android/media/AudioTrack),
[DefaultAudioSink.Builder](https://developer.android.com/reference/androidx/media3/exoplayer/audio/DefaultAudioSink.Builder),
and [AudioTrackAudioOutput](https://developer.android.com/reference/androidx/media3/exoplayer/audio/AudioTrackAudioOutput).

### Android platform contracts

The installed API 36 stubs confirm that the preferred-mixer API introduced in API
34 consists of:

- `AudioManager.getSupportedMixerAttributes`
- `setPreferredMixerAttributes`
- `getPreferredMixerAttributes`
- `clearPreferredMixerAttributes`
- preferred-attribute listener registration/removal
- `AudioMixerAttributes.MIXER_BEHAVIOR_BIT_PERFECT`

The supported USB output types are USB device, USB accessory, and USB headset.
The setter and clearer require the normal `MODIFY_AUDIO_SETTINGS` manifest
permission. A match must use the same media `AudioAttributes`, device, encoding,
sample rate, and channel mask. `AudioTrack.getFormat()` provides encoding,
sample rate, position mask, and index mask; routing is truthful only while the
track is playing, using `getRoutedDevice()` through API 35 and the potentially
multi-route `getRoutedDevices()` API on API 36.

## 3. Current Sazanami path

```text
Source Format (analytics callback)
  -> Media3 decoder
  -> decoded raw PCM
  -> DefaultAudioSink integer/float conversion policy
  -> persistent EqualizerAudioProcessor
  -> AudioOutputProvider.FormatConfig
  -> AudioOutputProvider.OutputConfig
  -> AudioTrackAudioOutput
  -> platform AudioTrack
  -> Android routed output device
```

The live `EqualizerRenderersFactory` forces `setEnableFloatOutput(false)` and
installs exactly one persistent `EqualizerAudioProcessor`. Media3 1.10.1
`DefaultAudioSink` therefore converts high-resolution decoded PCM to PCM16 before
the ordinary processor chain. `EqualizerAudioProcessor.onConfigure` independently
rejects every encoding other than PCM16. Its exact bypass tests prove only that
the PCM16 bytes supplied to it are copied exactly; they do not prove preservation
of a high-resolution source.

`DefaultAudioSink.Builder.setAudioOutputProvider` is the single-sink integration
point. A forwarding provider can observe the post-processor `FormatConfig`, the
resolved `OutputConfig`, and output lifecycle while leaving delegation unchanged.
When the returned output is `AudioTrackAudioOutput`, Media3 1.10.1 exposes the
underlying `AudioTrack` through the public `getAudioTrack()` method; reflection is
not required.

### Evidence caveats

- Source format is not final output format.
- PCM16 processor bypass is not proof of high-resolution preservation.
- A supported mixer attribute is not proof that it was activated.
- A successful preferred-attribute set call is not proof that AudioTrack matched.
- A DAC sample-rate display is secondary evidence.
- No analog-output claim is made.
- No support is inferred for untested devices.
- Device and vendor bit-perfect support is optional.

### Boundary verification

## 4. Processor-backed matrix

The device-side sink configuration test uses the same
`EqualizerRenderersFactory`, persistent `EqualizerAudioProcessor`, forwarding
provider, and Media3 1.10.1 classes as the service.

| Decoded source | Processor input | Processor output | Media3 output | Actual AudioTrack | Processor buffers | Result |
|---|---|---|---|---|---|---|
| PCM16, 44.1 kHz, stereo | PCM16, 44.1 kHz, stereo | PCM16, 44.1 kHz, stereo | PCM16, 44.1 kHz, stereo | Not created by configuration-only case | 0 | Preserved at PCM16 boundary |
| PCM16, 48 kHz, stereo | PCM16, 48 kHz, stereo | PCM16, 48 kHz, stereo | PCM16, 48 kHz, stereo | Not created by configuration-only case | 0 | Preserved at PCM16 boundary |
| packed PCM24, 96 kHz, stereo | PCM16, 96 kHz, stereo | PCM16, 96 kHz, stereo | PCM16, 96 kHz, stereo | Not created by configuration-only case | 0 | High-resolution encoding lost before processor |
| packed PCM24, 192 kHz, stereo | PCM16, 192 kHz, stereo | PCM16, 192 kHz, stereo | PCM16, 192 kHz, stereo | Not created by configuration-only case | 0 | High-resolution encoding lost before processor |

The processor buffer counter is captured during real playback, but the
configuration-only matrix deliberately does not feed audio buffers. Existing
PCM16 exact-bypass tests remain unchanged. Their conclusion is:

```text
Processor bypass is byte-exact for the PCM16 bytes it receives.
```

They do not establish:

```text
The original high-resolution source reached AudioTrack without conversion.
```

## 5. Processor-free matrix

| Requested raw PCM | Processor input | Processor output | Media3 OutputConfig | Actual created AudioTrack | Processor buffers | Result |
|---|---|---|---|---|---|---|
| PCM16, 44.1 kHz, stereo | Not installed | Not installed | PCM16, 44.1 kHz, stereo | PCM16, 44.1 kHz, stereo | 0 | Exact format created |
| PCM16, 48 kHz, stereo | Not installed | Not installed | PCM16, 48 kHz, stereo | PCM16, 48 kHz, stereo | 0 | Exact format created |
| packed PCM24, 96 kHz, stereo | Not installed | Not installed | packed PCM24, 96 kHz, stereo | packed PCM24, 96 kHz, stereo | 0 | Exact format created |
| packed PCM24, 192 kHz, stereo | Not installed | Not installed | packed PCM24, 192 kHz, stereo | packed PCM24, 192 kHz, stereo | 0 | Exact format created |
| packed PCM24, 96 kHz through processor-free float sink | Not installed | Not installed | float PCM, 96 kHz, stereo | Not created by configuration-only case | 0 | Float is a separate representation, not integer equivalence |

This proves that `AudioTrackAudioOutputProvider` can preserve high-resolution
integer PCM when it receives that final raw format. It does not provide a
supported switch that makes `DefaultAudioSink` retain packed integer PCM:
`DefaultAudioSink` converts to PCM16 when float output is disabled and converts
high-resolution input to float when float output is enabled.

### Exact matching implementation

The matcher compares encoding, sample rate, and channel mask and requires an
attribute whose behavior is explicitly `MIXER_BEHAVIOR_BIT_PERFECT`. It never
chooses a nearest rate, alternate encoding, wider container, float substitute,
or default mixer behavior. Platform list order is the deterministic tie-breaker
for otherwise identical exact matches.

The debug-only activation sequence is:

1. Reject non-API-34, zero/multiple USB outputs, unknown output facts, offload,
   tunneling, non-unity speed, active EQ/limiter, and ReplayGain/player gain.
2. Query and snapshot every supported attribute for the single USB output.
3. Select one exact bit-perfect attribute.
4. Register the listener, set the preferred attribute, and query it back.
5. Set the same USB output as ExoPlayer's preferred device.
6. Stop/prepare the existing authoritative ExoPlayer, preserving its playlist,
   position, play intent, session, repeat, and shuffle state so the matching
   AudioTrack is created after the preferred attribute is established.
7. Confirm actual AudioTrack format and route while playback writes.
8. Clear the exact tracked media-attributes/device pair on stop, output release,
   playback error/end, USB removal, explicit stop, activation failure, or service
   destruction.

Cleanup is idempotent. The post-clear query must return null before cleanup is
reported confirmed. The release listener, preferred-attribute listener, raw
device object, and selected platform attribute remain internal and are never
placed in the public snapshot.

### Test fixtures

The JVM test generator creates 40 ms stereo RIFF/WAVE files containing a
low-amplitude 997 Hz sine and an initial impulse. No binary fixture is stored and
no fixture enters a production APK.

| Generated fixture | SHA-256 |
|---|---|
| PCM16, 44.1 kHz | `d7ed2a9b06dfb7efe224106edce61005b2da2b9a28d6734f4e46dd2584650283` |
| PCM16, 48 kHz | `a055c095e0714d50f6bafda84f2be2e60f1a48cde9c3de4ecb0f792f26a41a07` |
| packed PCM24, 96 kHz | `78cf7803c733ddd3019d8f96b3cd45a1cca1aabd809e5f8a3998d3ace2b4ae34` |
| packed PCM24, 192 kHz | `9373b70353694bd5986c8fde6c5d339526abc0e6d9936407b369799c8190622d` |

FLAC decoder fixtures were not added. The decisive sink/provider tests use
Media3 raw `Format` values after the decoder boundary, avoiding production APK
bloat while isolating the question under test.

## 6. USB mixer capability

Primary connected device:

- Model: Samsung `SM-S908U1`
- Android: 16
- API: 36
- Device control: authorized wireless ADB while USB-C was occupied by the DAC
- USB DAC safe label: Moondrop Dawn Pro 2
- Physical topology: DAC connected directly to the S22 Ultra over USB-C; no hub
- USB audio outputs enumerated by the probe: exactly one
- Actual playback route: `USB`
- Supported mixer attributes: 21
- Default-behavior attributes: 21
- Bit-perfect-behavior attributes: 0
- Exact bit-perfect matches: 0

The Dawn Pro 2 played normally and exposed the following attributes through
`AudioManager.getSupportedMixerAttributes`. Encoding `2` is PCM16, encoding
`21` is packed PCM24, encoding `22` is PCM32, and mask `12` is stereo. Every
returned combination used `MIXER_BEHAVIOR_DEFAULT`; none used
`MIXER_BEHAVIOR_BIT_PERFECT`.

| # | Encoding | Rate (Hz) | Mask | Mixer behavior |
|---:|---:|---:|---:|---|
| 1 | 2 | 48,000 | 12 | DEFAULT |
| 2 | 2 | 88,200 | 12 | DEFAULT |
| 3 | 2 | 96,000 | 12 | DEFAULT |
| 4 | 2 | 176,400 | 12 | DEFAULT |
| 5 | 2 | 192,000 | 12 | DEFAULT |
| 6 | 2 | 352,800 | 12 | DEFAULT |
| 7 | 2 | 384,000 | 12 | DEFAULT |
| 8 | 21 | 48,000 | 12 | DEFAULT |
| 9 | 21 | 88,200 | 12 | DEFAULT |
| 10 | 21 | 96,000 | 12 | DEFAULT |
| 11 | 21 | 176,400 | 12 | DEFAULT |
| 12 | 21 | 192,000 | 12 | DEFAULT |
| 13 | 21 | 352,800 | 12 | DEFAULT |
| 14 | 21 | 384,000 | 12 | DEFAULT |
| 15 | 22 | 48,000 | 12 | DEFAULT |
| 16 | 22 | 88,200 | 12 | DEFAULT |
| 17 | 22 | 96,000 | 12 | DEFAULT |
| 18 | 22 | 176,400 | 12 | DEFAULT |
| 19 | 22 | 192,000 | 12 | DEFAULT |
| 20 | 22 | 352,800 | 12 | DEFAULT |
| 21 | 22 | 384,000 | 12 | DEFAULT |

The normal Media3/AudioTrack observations were:

| Source confirmed by Sazanami | Processor | Media3 OutputConfig | Actual AudioTrack | Route | Audible result |
|---|---|---|---|---|---|
| FLAC, 44.1 kHz, stereo; source bit depth not reported | PCM16, 44.1 kHz, 299 observed buffers | PCM16, 44.1 kHz, mask 12, offload/tunneling false | PCM16, 44.1 kHz, mask 12, session 25561 | USB | Normal |
| FLAC, 96 kHz, stereo; source bit depth not reported | Persistent processor received buffers; final post-fix configuration snapshot was not captured | PCM16, 96 kHz, mask 12, offload/tunneling false | PCM16, 96 kHz, mask 12, session 25529 | USB | Normal |

The 96 kHz source had an exact encoding/rate/mask match among the default
attributes, but no exact attribute with bit-perfect behavior. The probe
therefore rejected `NO_BIT_PERFECT_ATTRIBUTE` without selecting or setting an
attribute. Playback support and a USB route do not imply Android bit-perfect
support.

The Galaxy S9+ Android 10 and Galaxy Tab S9 were not connected during this
workspace run. API-29 safety is enforced by the separate API-34 implementation
class, the SDK-gated factory, lint, min-SDK compilation, and JVM fake tests, but
new physical regression on those devices remains outstanding.

### Later Galaxy S24 Ultra result

A later manual test used a device identified by the user as a Samsung Galaxy S24
Ultra with a Moondrop Dawn Pro 2 connected directly through USB-C. Its exact
Android version, API level, firmware, and model code were not reported.

The source was a stereo FLAC at 96,000 Hz; source bit depth was not reported.
Ordinary playback routed through the DAC over USB. The actual AudioTrack used
PCM16 encoding `2`, 96,000 Hz, and stereo channel mask `12`. The persistent
equalizer processor observed 118 buffers, so this was ordinary processor-backed
playback and is not evidence of bit-perfect output or source bit-depth
preservation.

Android returned 21 supported mixer attributes. Each of PCM16 encoding `2`,
packed PCM24 encoding `21`, and PCM32 encoding `22` was returned at 48,000,
88,200, 96,000, 176,400, 192,000, 352,800, and 384,000 Hz with stereo channel
mask `12`. Every attribute used `MIXER_BEHAVIOR_DEFAULT`; none used
`MIXER_BEHAVIOR_BIT_PERFECT`.

| Observation | Result |
|---|---|
| Supported mixer attributes | 21 |
| Bit-perfect attributes | 0 |
| Probe rejection | `NO_BIT_PERFECT_ATTRIBUTE` |
| Selected exact attribute | None |
| Preferred set | Not attempted / Unknown |
| Preferred query | None |
| Cleanup | `NOT_REQUIRED` |

Both tested combinations therefore produced the same relevant capability result:

```text
Galaxy S22 Ultra + Dawn Pro 2: 0 BIT_PERFECT attributes
Galaxy S24 Ultra + Dawn Pro 2: 0 BIT_PERFECT attributes
```

These two observations do not establish identical behavior for every Galaxy S22,
Galaxy S24, Samsung phone, Dawn Pro 2, firmware, or phone/DAC combination.

## 7. Experimental activation

- Preconditions implemented: debug build, API 34+, exactly one USB sink, known
  non-offloaded/non-tunneled output, unity playback speed, inactive equalizer
  and limiter, ReplayGain off, and player volume 1.0.
- Physical preconditions used: EQ disabled, limiter disabled, ReplayGain off,
  offload disabled, implicit app playback speed 1.0, and internal ExoPlayer
  volume 1.0. Phone/DAC listening volume remained a downstream safe listening
  control.
- Candidate: PCM16, 96 kHz, stereo, mask 12.
- Selection result: rejected with `NO_BIT_PERFECT_ATTRIBUTE` because all 21
  returned attributes were default behavior.
- Preferred set result: not attempted (`Unknown`), which is distinct from a
  failed set call.
- Preferred query result: null/none.
- Preferred listener result: not applicable because no set was attempted.
- Exact AudioTrack/route confirmation: not applicable to exact activation; the
  ordinary AudioTrack independently matched PCM16/96 kHz/stereo and routed to
  USB.
- Processor buffer-free exact-stream confirmation: not applicable because no
  exact stream was activated; ordinary playback continued through the
  persistent processor path.
- Cleanup result: `NOT_REQUIRED`; there was no owned preferred attribute to
  clear.
- Failure behavior: pure fake-backend tests cover no/multiple USB devices, no
  bit-perfect match, setter failure, listener failure, confirmation mismatch,
  idempotent success cleanup, and post-clear confirmation. Runtime exceptions
  converge on cleanup, and normal preferred-device routing is cleared only if
  this probe applied it.

The first hardware report exposed a truthfulness defect in the unsupported
path: the event history recorded `NO_BIT_PERFECT_ATTRIBUTE`, but the final
snapshot erased the rejection when returning to `OFF`, showed a false set
result although no set occurred, and left cleanup `PENDING`. The closure fix:

- preserves the rejection when mode returns to `OFF`;
- leaves set result unknown unless a set call actually occurred; and
- resolves a pending cleanup to `NOT_REQUIRED` when nothing was set.

Two focused JVM tests cover the state transition and controller outcome. The
corrected hardware rerun reported:

```text
Probe mode: OFF
Preferred set result: Unknown
Preferred query result: None
Cleanup result: NOT_REQUIRED
Rejection reason: NO_BIT_PERFECT_ATTRIBUTE
```

The same fix also truthfully retained the earlier
`OUTPUT_FORMAT_UNKNOWN` precondition rejection after a fresh process start.

## 8. Architecture decision

```text
Can Sazanami retain one ExoPlayer and one persistent sink?
NO

Can the existing persistent equalizer processor remain installed during
high-resolution direct playback?
NO

Can Media3 1.10.1 expose an exact PCM format suitable for Android preferred
mixer attributes?
PARTIALLY

Can the tested Android device and USB DAC activate Android's BIT_PERFECT
mixer behavior?
NO — DEVICE/HAL COMBINATION DID NOT EXPOSE BIT-PERFECT ATTRIBUTES

Is controlled player reconstruction required for the production design?
YES
```

Production-option disposition:

- One persistent sink: rejected if it means the current unchanging,
  processor-backed sink.
- One player with dynamically recreated `AudioOutput`: useful for ordinary
  format/rate changes, but insufficient for removing the processor or preserving
  packed high-resolution integer PCM.
- One player with switchable sink configuration: not exposed as a supported
  live mutation by the resolved Media3 1.10.1 builder/sink contracts.
- Controlled player reconstruction: recommended for Phase B, retaining exactly
  one authoritative player/session at a time and restoring all queue/session
  state.
- Another supported Media3 approach: remains an option only if Phase B proves it
  with resolved APIs and target hardware; this phase does not authorize a custom
  sink or direct USB stack.

Rationale:

- One authoritative ExoPlayer, PlaybackService, and MediaLibrarySession can be
  retained at any instant.
- One *unchanging processor-backed sink* cannot serve both normal EQ playback
  and high-resolution direct playback. The current sink converts high-resolution
  integer PCM to PCM16 before the persistent equalizer, even when the equalizer
  is bypassed.
- Media3's provider can expose and create exact PCM16 and packed PCM24
  AudioTracks, but the stock `DefaultAudioSink` does not independently enable
  high-resolution integer output. Its public high-resolution switch is tied to
  float output and bypasses the ordinary processor chain.
- Phase B should therefore prototype controlled reconstruction of the single
  authoritative player/renderers/sink for a processor-free exact session, with
  full queue/session/history restoration. It must choose only a format actually
  exposed by the tested USB mixer's bit-perfect attributes. A packed-24-only DAC
  may require another supported Media3 audio-sink approach; this phase does not
  authorize a custom production sink.
- The experimental same-player stop/prepare path proves output recreation can be
  requested without creating a second player, but it does not solve removal of
  the persistent processor or stock-sink packed-integer conversion. Production
  should not rely on that path alone.

## 9. Automated verification

Pre-change:

- `.\gradlew.bat --no-daemon :app:compileDebugKotlin :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :app:assembleDebugAndroidTest --stacktrace`
  passed in 4m06s. JVM: 582 tests, 0 failures, 0 errors, 4 skipped.
- `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace`
  passed in 57s: 45 tests on `SM-S908U1`/API 36, 0 failures, 0 skips.

Post-change focused evidence:

- `.\gradlew.bat --no-daemon :app:dependencyInsight --configuration debugRuntimeClasspath --dependency androidx.media3:media3-exoplayer`
  passed and resolved every Media3 module to stable 1.10.1.
- `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.cdplaya.player.feasibility.*' --stacktrace`
  passed in 53s: 32 tests, 0 failures/errors/skips.
- The focused connected feasibility command passed in 44s: 4 tests, 0
  failures/errors/skips. The first harness iteration exposed a null platform
  spatializer in a standalone context-backed provider; the test-only provider
  was corrected to use Media3's supported nullable-context constructor.
  Production retains ExoPlayer's context-backed provider.
- `.\gradlew.bat --no-daemon :app:lintDebug --stacktrace` passed in 4m12s after
  correcting three new explicit API-guard/opt-in errors. No lint baseline,
  assertion, API guard, or warning policy was weakened.

Final ordinary aggregate:

- `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :benchmark:assembleBenchmarkBenchmark :app:assembleDebugAndroidTest --stacktrace`
  passed in 5m17s. JVM XML totals: 614 tests, 0 failures, 0 errors,
  4 skipped, 125 suites. Lint: 0 errors, 74 warnings, 1 hint. Debug,
  minified release/R8, app benchmark, benchmark-module, and instrumentation
  APK assembly all passed.
- `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace`
  passed in 54s on `SM-S908U1`/API 36: 49 tests, 0 failures, 0 errors,
  0 skipped (20.693s test time). The test-services `No UID` app-ops line is a
  harmless runner setup message and did not fail the task.

Opt-in existing performance verification:

- `.\gradlew.bat --no-daemon '-Dequalizer.performance=true' '-Dequalizer.longRun=true' :app:testDebugUnitTest --tests 'com.example.cdplaya.player.equalizer.performance.*' --stacktrace`
  ran 5 tests in 42s: 4 passed and 1 failed. The sixty-minute equivalent
  long-run test passed (`3,600` equivalent seconds, `42,188` calls,
  `172,802,048` frames) with no buffer growth or state loss. The existing
  processor allocation benchmark failed its unchanged 16-byte/call threshold
  for `graphic-moderate` at 29.192 bytes/call.
- `.\gradlew.bat --no-daemon '-Dequalizer.performance=true' :app:testDebugUnitTest --tests 'com.example.cdplaya.player.equalizer.performance.EqualizerProcessorBenchmarkTest' --rerun-tasks --stacktrace`
  reproduced the unchanged assertion in 2m26s, this time for
  `graphic-worst-high-rate-surround` at 29.328 bytes/call. Its median real-time
  factor was 0.0682 and maximum was 0.1116. The scenario-to-scenario movement
  indicates JVM allocation-measurement sensitivity, but the failure is retained
  as a real outstanding verification result. No unrelated performance code or
  assertion was changed.

### Comparative allocation benchmark closure

The closure comparison used detached base `a6b0d5b1860f2116a94204772da2da19a481c0de`
and Phase A HEAD `a62536f76daab1a4a8cb74a9309245f5d62aaf37` in separate
worktrees on the same Windows 11 machine. Both used Gradle 9.4.1, launcher JVM
23.0.2, the pinned Java 21 daemon toolchain, identical Gradle/wrapper
configuration, the same Android SDK supplied through `ANDROID_HOME` and
`ANDROID_SDK_ROOT`, and the `SAMSUNG MODE` power plan. Runs were serial, with no
overlapping build or device workload.

Every invocation used the same command after one excluded warm-up per revision:

```powershell
.\gradlew.bat --no-daemon `
  "-Dequalizer.performance=true" `
  :app:testDebugUnitTest `
  --tests "com.example.cdplaya.player.equalizer.performance.EqualizerProcessorBenchmarkTest" `
  --rerun-tasks `
  --stacktrace
```

Cells below are `allocated bytes/call / median real-time factor / maximum
real-time factor`. “Not reported” means the unchanged per-scenario assertion
stopped the test before the later scenario.

| Revision/run | flat-bypass | graphic-moderate | graphic-worst-high-rate-surround | Later reported scenarios | Assertion |
|---|---:|---:|---:|---|---|
| Base warm-up | 0.048 / 0.0002625 / 0.020775 | 0.744 / 0.003225 / 0.063300 | 29.328 / 0.0681375 / 0.126473 | Not reported | Fail: worst |
| HEAD warm-up | 0.048 / 0.0002063 / 0.088331 | 29.432 / 0.0060188 / 0.086156 | Not reported | Not reported | Fail: graphic |
| Base 1 | 0.048 / 0.0003750 / 0.017588 | 0.744 / 0.0025875 / 0.042900 | 29.328 / 0.0624094 / 0.091392 | Not reported | Fail: worst |
| HEAD 1 | 0.936 / 0.0002063 / 0.099469 | 0.456 / 0.0024563 / 0.032325 | 28.968 / 0.0602391 / 0.087277 | Not reported | Fail: worst |
| Base 2 | 0.048 / 0.0002063 / 0.062138 | 0.744 / 0.0026063 / 0.033713 | 29.328 / 0.0621609 / 0.111628 | Not reported | Fail: worst |
| HEAD 2 | 0.048 / 0.0002063 / 0.065100 | 0.456 / 0.0024750 / 0.055838 | 28.824 / 0.0590063 / 0.114783 | Not reported | Fail: worst |
| Base 3 | 0.936 / 0.0002250 / 0.050119 | 0.192 / 0.0027563 / 0.026606 | 29.184 / 0.0606422 / 0.102783 | Not reported | Fail: worst |
| HEAD 3 | 0.936 / 0.0001688 / 0.051563 | 28.968 / 0.0026813 / 0.073275 | Not reported | Not reported | Fail: graphic |
| Base 4 | 0.936 / 0.0001688 / 0.057713 | 0.608 / 0.0025688 / 0.091350 | 0.048 / 0.0585094 / 0.092583 | See full pass table below | Pass |
| HEAD 4 | 0.048 / 0.0002250 / 0.039581 | 0.952 / 0.0039938 / 0.027394 | 29.328 / 0.0600234 / 0.104269 | Not reported | Fail: worst |
| Base 5 | 0.048 / 0.0002813 / 0.029644 | 0.744 / 0.0025125 / 0.084844 | 29.328 / 0.0603844 / 0.146611 | Not reported | Fail: worst |
| HEAD 5 | 0.936 / 0.0001500 / 0.081056 | 0.552 / 0.0026250 / 0.030469 | 28.824 / 0.0586078 / 0.094716 | Not reported | Fail: worst |

Base run 4 was the only invocation that reached every scenario:

| Scenario | Bytes/call | Median RTF | Maximum RTF |
|---|---:|---:|---:|
| `parametric-realistic` | 0.048 | 0.0077438 | 0.035438 |
| `parametric-high-q-small-buffer` | 0.048 | 0.0055500 | 0.080963 |
| `parametric-high-q-no-headroom` | 0.048 | 0.0052500 | 0.058613 |
| `parametric-all-types-surround` | 0.048 | 0.0082031 | 0.028500 |
| `parametric-realistic-limiter` | 0.048 | 0.0048000 | 0.032531 |

Measured outcomes were base 1 pass/4 failures and HEAD 0 passes/5 failures. The
base worst-scenario allocation median was 29.328 bytes/call with a
0.048–29.328 spread; the four HEAD runs that reached that scenario had a 28.896
median and 28.824–29.328 spread. The roughly 29-byte charge moved from the last
scenario to `graphic-moderate` on both prior HEAD evidence and this comparison,
and disappeared entirely on base run 4. This is consistent with fresh JVM/test
worker initialization or allocation-instrumentation/scenario-order sensitivity.

Conclusion: the threshold failure existed at the exact pre-Phase-A base, and
Phase A HEAD did not increase the measured steady allocation. No Phase
A-specific allocation regression was demonstrated. Production DSP,
feasibility instrumentation, and the unchanged 16-byte assertion were therefore
left untouched. The temporary base worktree was removed and pruned; active
branch/HEAD remained intact.

### Hardware-closure verification

The hardware-discovered truthfulness fix changes only session diagnostics and
cleanup state. It does not touch DSP, the equalizer processor hot path, Media3
format selection, or the 16-byte allocation assertion.

- Focused state/controller command:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest --tests 'com.example.cdplaya.player.feasibility.BitPerfectFeasibilityStateTest' --tests 'com.example.cdplaya.player.feasibility.UsbMixerFeasibilityControllerTest' --stacktrace`
  passed: 14 tests, 0 failures/errors/skips.
- Final aggregate command:
  `.\gradlew.bat --no-daemon :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleRelease :app:assembleBenchmark :benchmark:assembleBenchmarkBenchmark :app:assembleDebugAndroidTest --stacktrace`
  passed in 4m54s with 217 actionable tasks. The final forced JVM rerun passed
  in 2m07s: 615 tests, 0 failures, 0 errors, 4 skipped, 125 suites. Lint had
  0 fatal issues, 0 errors, 74 warnings, and 1 informational issue. Debug,
  minified release/R8, app benchmark, benchmark-module, and instrumentation APK
  assembly all passed.
- Sixty-minute-equivalent command:
  `.\gradlew.bat --no-daemon '-Dequalizer.performance=true' '-Dequalizer.longRun=true' :app:testDebugUnitTest --tests 'com.example.cdplaya.player.equalizer.performance.EqualizerProcessorLongRunTest' --rerun-tasks --stacktrace`
  passed in 2m30s: 3,600 equivalent seconds, 42,188 calls, and 172,802,048
  frames.
- The unchanged allocation command was rerun on corrected HEAD. Flat bypass
  reported 0.048 bytes/call, median RTF 0.0002250, maximum RTF 0.074513.
  `graphic-moderate` reported 29.728 bytes/call, median RTF 0.0032438,
  maximum RTF 0.168094, and failed the unchanged 16-byte assertion. This is
  consistent with the completed base/HEAD measurement-sensitivity evidence; the
  diagnostics-only fix did not introduce processor work.
- Connected command targeted the authorized wireless serial through
  `ANDROID_SERIAL`:
  `.\gradlew.bat --no-daemon :app:connectedDebugAndroidTest --stacktrace`
  passed in 1m18s on `SM-S908U1`/Android 16: 49 tests, 0 failures, 0 errors,
  0 skipped, 21.15s XML test time. The Dawn Pro 2 remained attached. The
  test-services `No UID` app-ops line remained a harmless runner setup message.

## 10. Device validation

| Device | Android/API | APK SHA-256 | Route and scenarios | Result / not tested |
|---|---|---|---|---|
| Samsung Galaxy S22 Ultra `SM-S908U1` | Android 16 / API 36 | debug `0909BC8CFCD2BFE29234533C748D4A750C04EFBBBD75B676289FFFE59F471BFB` | Normal phone output; Dawn Pro 2 USB output; 44.1/96 kHz FLAC; full ordinary playback checklist; disconnect/reconnect; background/screen-off; process restart; 49 connected tests | Install preserved data. Corrected APK cold-launched in 1,086 ms and post-cleanup process restart cold-launched in 793 ms. Ordinary and DAC playback remained normal, routes returned to USB after reconnect, no new click/pop or audible problem was reported, and process-scoped error logs were clean. Exact activation was unsupported because the device/HAL returned no bit-perfect attribute. |
| Samsung Galaxy S9+ | Not connected / expected Android 10 target | Not installed | None | Physical API-29 regression not performed. |
| Samsung Galaxy Tab S9 | Not connected; version not inferred | Not installed | None | Additional modern-device regression not performed. |

Final APK artifacts:

| Artifact | Bytes | SHA-256 |
|---|---:|---|
| `app-debug.apk` | 27,151,713 | `0909BC8CFCD2BFE29234533C748D4A750C04EFBBBD75B676289FFFE59F471BFB` |
| `app-release-unsigned.apk` | 4,222,045 | `0379009A2EEEB4EC7B742D612218718FA44FD110EF94D31335B93FCE5B7E5AEF` |
| `app-benchmark.apk` | 18,062,801 | `61B13AF53AD95094F2C9F00222CB074053499BABE2622D842AB9051678E495C0` |
| `benchmark-benchmarkBenchmark.apk` | 46,455,401 | `2AE21E05061C75B0AECBED05BCFDEF4FD4007F0A1B61E752C526D168B4F6913B` |
| `app-debug-androidTest.apk` | 1,161,648 | `E1B50BAA4634CE2A3784D3F6FBCB9AD67A7F2C3EC12C53666756AD13C280DEB5` |

## 11. Files changed

| File | Purpose and runtime impact | Scope / Phase A reason |
|---|---|---|
| `app/src/main/AndroidManifest.xml` | Adds normal `MODIFY_AUDIO_SETTINGS`; no runtime prompt. | Production manifest contract required by the probed API. |
| `app/src/main/java/com/example/cdplaya/player/PlaybackService.kt` | Owns the single provider/controller, explicit activation, state-preserving output recreation, and cleanup callbacks. | Probe invocation is debug-rejected outside debug builds; authoritative ownership remains production-safe. |
| `app/src/main/java/com/example/cdplaya/player/equalizer/EqualizerAudioProcessor.kt` | Publishes input/output format and buffer count only while observing; DSP is unchanged. | Low-cost dormant production instrumentation needed to answer the processor boundary question. |
| `app/src/main/java/com/example/cdplaya/player/equalizer/EqualizerRenderersFactory.kt` | Installs the forwarding provider in the existing sink. | Dormant production-safe observation point; no second sink/player. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityModels.kt` | Framework-free session evidence and enums. | Non-persisted Phase A facts. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityReportFormatter.kt` | Produces sanitized copied diagnostics. | Experimental/debug reporting without raw identifiers. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityRuntimeBridge.kt` | Bounded in-process state and service control bridge; closure fix preserves unsupported/precondition rejection facts after returning to Off without inventing a set attempt. | Dormant unless explicitly invoked; no ownership duplication. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/ExactMixerAttributeMatcher.kt` | Strict encoding/rate/mask/behavior matching. | Pure Phase A eligibility evidence. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/FeasibilityAudioOutputProvider.kt` | Delegates Media3 behavior while observing format, real AudioTrack, reuse, and lifecycle. | Production-safe forwarding seam; activation remains experimental. |
| `app/src/main/java/com/example/cdplaya/player/feasibility/UsbMixerFeasibilityController.kt` | API-gated USB enumeration, set/query/listen/clear, and exception-safe cleanup; closure fix resolves a no-set pending state to cleanup not required. | Experimental Android 14+ probe with API-29-safe loading. |
| `app/src/main/java/com/example/cdplaya/ui/settings/DiagnosticsScreen.kt` | Adds explicit debug-only Observe, Exact probe, Stop/Clear, and Copy controls. | No preference, badge, or production claim. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityArchitectureTest.kt` | Enforces one service/player/session and no production preference/badge. | Phase A scope guard. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityStateTest.kt` | Tests safe defaults, immutability, bounded events, exact actual-track confirmation, and truthful unsupported rejection retention. | Phase A state integrity. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/DeterministicWavFixtureGeneratorTest.kt` | Generates and hashes copyright-free fixtures. | Test-only evidence; no APK asset. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/ExactMixerAttributeMatcherTest.kt` | Covers exact matches and every mismatch/ambiguity class. | Pure Phase A selection proof. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/FeasibilityAudioOutputProviderTest.kt` | Covers delegation, snapshots, reuse, lifecycle, and failure publication. | Phase A observer verification. |
| `app/src/test/java/com/example/cdplaya/player/feasibility/UsbMixerFeasibilityControllerTest.kt` | Covers SDK/device rejection, default-only hardware, no-set cleanup, set/listener/query failures, and idempotent cleanup. | Hardware-independent cleanup proof. |
| `app/src/androidTest/java/com/example/cdplaya/player/feasibility/BitPerfectFeasibilityInstrumentationTest.kt` | Exercises real Media3 sink/provider and actual AudioTrack creation plus safe API-36 USB inspection. | Device-only Phase A boundary evidence. |
| `docs/audio/phase-a-bit-perfect-feasibility.md` | Records contracts, matrices, commands, caveats, hashes, and architecture decision. | Phase A deliverable; no runtime impact. |

## 12. Privacy and cleanup audit

- No USB address, serial, stable ID, URI, file path, or product name is stored in
  feasibility state or copied diagnostics.
- No raw device identifier is copied to the clipboard or committed.
- Only safe route categories leave the Android adapter/provider.
- Product labels are intentionally omitted from copied evidence.
- Preferred mixer state is media-usage/device-pair scoped, session-only, and
  cleared on success and failure paths.
- Listener removal and provider/output release are idempotent and tested.
- Service destruction, USB removal, playback error/end, explicit stop, and
  output release call cleanup.
- Startup clears a stale media preferred attribute only for USB outputs visible
  to the same UID; it does not clear arbitrary non-USB or untracked pairs.
- The Dawn Pro 2 was connected, but no bit-perfect attribute was exposed and no
  preferred mixer attribute was selected or set. Explicit Stop/Clear and a
  subsequent process restart both left the probe Off with no queried preferred
  attribute and no stale state.

## 13. Known limitations

- The tested S22 Ultra/Dawn Pro 2 device/HAL combination exposed only default
  mixer behavior. A real preferred set/query/listener callback, exact-route
  confirmation, buffer-free exact stream, post-set clear, and bit-perfect
  format transition therefore could not be exercised.
- Normal USB playback was observed at 44.1 and 96 kHz. No confirmed 48 or
  192 kHz source was exercised, and Sazanami diagnostics did not report source
  bit depth, so no 16-bit or 24-bit source claim is made.
- The DAC's own sample-rate display was not recorded; it would have been
  secondary evidence only.
- No physical Android 10 or Tab S9 regression was possible in this run.
- Real 44.1 and 96 kHz FLAC files were played through the service-owned
  ExoPlayer queue, while the deterministic test matrices continue to isolate
  the post-decoder raw-PCM sink/provider boundary.
- Processor-backed cases configure the real sink but do not create or feed the
  AudioTrack. Processor-free cases create and inspect actual AudioTracks.
- Queue/history/session continuity across controlled reconstruction is not
  implemented or proven; Phase B must prove it before production adoption.
- The stock Media3 1.10.1 sink cannot preserve packed high-resolution integer PCM
  while retaining the ordinary processor chain, and its float path is not
  equivalent to packed integer PCM.
- Manual disconnect/reconnect, background, screen-off, transport, queue,
  repeat/shuffle, EQ/limiter/ReplayGain/offload, notification, and ordinary
  playback checks passed without a reported new audible problem. These are
  human observations, not electrical bitstream measurements.
- The existing opt-in processor allocation microbenchmark failed its unchanged
  16-byte/call threshold on base, original HEAD, and corrected HEAD with a
  movable roughly 29-byte current-thread charge. Phase A did not change DSP
  performance code or weaken the assertion.
- Supported USB hardware is still required before any future phase can claim
  Android bit-perfect activation. The application architecture remains
  feasible, but this exact phone/DAC pair is not eligible for that path.

## 14. Final result

Phase A result: APP ARCHITECTURE FEASIBLE — SUPPORTED USB HARDWARE STILL REQUIRED
