# Phase 5 advanced audio output report

## 1. Branch and commit

- Branch: `advanced-audio-offload-checkpoint-v1`
- Report commit: the commit containing this document
- Validation date: July 23, 2026
- Startup-fix commits:
  - `e6752d1 Fix PlaybackController initialization crash`
  - `145bff4 Add initialization-order regression tests`
- No branch push or pull request was performed.

## 2. Media3 version

Sazanami uses AndroidX Media3 1.9.1 for `media3-exoplayer` and `media3-session`.

The implementation uses Media3's track-selection audio-offload preferences,
`ExoPlayer.AudioOffloadListener`, and the analytics audio input-format callback.

## 3. Device model

Samsung SM-S908U1.

The device serial number and other stable device identifiers were not recorded.

## 4. Android version

Android 16, API level 36.

## 5. Tested routes

The bounded playback session used the built-in speaker route reported by Diagnostics.

- Built-in speaker: Playback and privacy-safe route reporting tested; actual offload activation
  was not conclusively observed.
- Wired audio: Not tested on the available device.
- USB audio: Not tested on the available device.
- Bluetooth classic: Not tested on the available device.
- Bluetooth LE: Not tested on the available device.
- HDMI: Not tested on the available device.
- Remote/cast: Not tested on the available device.

## 6. Tested codecs and formats

Playback was exercised from the existing library without recording a systematic codec-by-codec
compatibility matrix.

- MP3: Offload compatibility not systematically recorded.
- AAC/M4A: Offload compatibility not systematically recorded.
- FLAC: Offload compatibility not systematically recorded.
- Other library formats: Offload compatibility not systematically recorded.

Pure mapping tests cover known and unknown renderer-format fields, FLAC display formatting,
compressed-versus-PCM encoding handling, sample rate, channel count, bitrate, and gapless
encoder delay/padding.

## 7. Offload preference behavior

The debug APK installed successfully over the existing app without clearing app data.

Code and unit-test observations:

- The durable default is Disabled for new and existing installations.
- Disabled maps to Media3 `AUDIO_OFFLOAD_MODE_DISABLED`.
- Automatic maps to Media3 `AUDIO_OFFLOAD_MODE_ENABLED`.
- `AUDIO_OFFLOAD_MODE_REQUIRED` is not selected or exposed by Sazanami.
- Automatic retains Media3's normal decoded-playback fallback.
- A live preference change changes only the player's track-selection parameters; Sazanami does not
  rebuild the playlist, seek, or recreate the service.
- Backup schema 3 stores the preference as an optional field. A missing or unknown field restores
  as Disabled, and schemas 1 through 3 remain accepted.

Interactive Settings toggling was tested during playback. Disabled was confirmed as the initial
setting. Changing Disabled to Automatic preserved the same playback session: position advanced
from 108,215 ms to 115,242 ms and the current song did not restart. Changing back to Disabled
succeeded.

## 8. Actual offload-active observations

Actual offload activation was not conclusively observed during the bounded device session.

Sazanami reports active offload only from Media3's `onOffloadedPlayback` callback. Enabling
Automatic by itself does not produce an Active status.

Diagnostics continued to present requested preference and actual offload state separately.

## 9. Sleeping-for-offload observations

Sleeping-for-offload was not observed during the bounded device session.

Sazanami reports the sleeping state only from Media3's
`onSleepingForOffloadChanged` callback, and only while offloaded playback is active.

## 10. Gapless results

Gapless transition behavior under Automatic was not tested.

Automatic requests gapless support from Media3 and does not require playback-speed support.
According to Media3's contract, the gapless constraint prevents offload for a gapless stream when
the renderer cannot preserve gapless playback. Sazanami does not force offload at the expense of
gapless transitions.

## 11. ReplayGain compatibility

The existing ReplayGain implementation writes one normalized multiplier to Media3 `Player.volume`.
It does not use an `AudioProcessor`, platform `AudioEffect`, native code, or an external DSP SDK.
Phase 5 preserves that path and does not disable or apply ReplayGain a second time.

ReplayGain behavior during confirmed active offload was not tested.

## 12. Queue, seek, and transition results

Existing focused unit tests for queue duplicates/order, navigation, ReplayGain helpers, and
same-session artwork publication passed during development. The offload preference update is
isolated to `TrackSelectionParameters` and preserves unrelated selection parameters in a focused
test.

Playback started successfully with offload Disabled. Queue/Up Next, shuffle, repeat, artwork, and
the MediaStyle notification remained functional after changing Disabled to Automatic and back to
Disabled. Interactive next/previous, seek, queue mutation, codec changes, and metadata replacement
were not systematically tested.

## 13. Screen-off and background observations

The bounded foreground session remained running without a crash, ANR, AndroidRuntime error, or
ActivityManager error. Screen-off and extended background playback were not tested, so this is not
evidence of background offload activity or sleeping-for-offload.

## 14. Battery and thermal observations

Not measured. No battery-life, power-reduction, or thermal claim is made from this bounded session.

## 15. Known unsupported or unmeasured scenarios

- Android versions before API 33 cannot reliably identify the active media route using the
  media-attribute routing API. Sazanami reports Unknown when connected-device inventory cannot
  prove the active route.
- A device, route, codec, gapless requirement, or current playback configuration may keep
  Automatic requested while actual offload remains inactive. This is normal fallback, not an
  error.
- The platform callback does not provide a precise rejection reason, so Sazanami does not invent
  one.
- Bluetooth codec, final mixer format, DAC format, analog output quality, and hardware resampling
  are not inferred.
- Route product names are omitted from copied diagnostics, and device addresses are never stored
  in audio UI state.
- Bluetooth, USB, and wired routes were not tested during the bounded session.

## 16. Source format versus renderer and output

The Diagnostics Source format describes Media3's audio renderer input. It may include MIME type,
codec string, sample rate, channel count, bitrate, PCM encoding for raw PCM, and encoder
delay/padding when Media3 reports them.

The bounded device session verified privacy-safe diagnostic output: requested and actual offload
state remained separate, the route was reported generically, and no stable device identifier or
hardware address was exposed.

This is not the final Android mixer, transport, DAC, or analog output format. Android or the
connected device may mix, process, resample, encode, or transmit the stream differently.

## 17. Bit-perfect feasibility notes

Offload is not proof of bit-perfect playback. Source format is not proof of hardware output format.
Sazanami therefore does not display a bit-perfect, no-resampling, or high-resolution-output claim.

A defensible bit-perfect determination would require route- and device-specific evidence of the
actual framework and hardware path, including the final mixer/transport format and confirmation
that no gain, processing, conversion, or re-encoding occurred. The current public callbacks do not
provide all of that evidence.

No experimental direct-output or custom audio engine was added in this phase.

## 18. Future DSP compatibility notes

Future DSP that requires decoded PCM may be incompatible with offload. Such a feature should
explicitly request normal decoded playback while preserving the user's effect, rather than
silently disabling the effect or forcing offload.

The current ReplayGain path uses player volume and remains independent from custom audio
processing. Any future equalizer, limiter, resampler, crossfade, or custom processor requires a
new compatibility review.

## 19. Exact reproduction steps

1. Build the debug APK with `gradlew.bat assembleDebug`.
2. Install it over the existing app without clearing app data.
3. Open Settings and confirm Audio offload initially shows Disabled.
4. Start playback and open Diagnostics.
5. Confirm Source is labeled as renderer input information and Route contains no device address.
6. Confirm Offload preference and actual Offload status are separate.
7. Select Automatic while paused, then play MP3, AAC/M4A, and FLAC files.
8. Record whether Diagnostics changes from requested-not-active to Active.
9. Turn the screen off for a bounded period and record whether processor sleeping appears.
10. Exercise a gapless album transition, next/previous, seek, queue duplicates, shuffle, repeat,
    notification controls, and metadata/artwork replacement.
11. Repeat on built-in speaker, Bluetooth, and USB/wired routes when available.
12. Test ReplayGain Off, Track, Album, and Smart modes without changing the selected track.
13. Disable offload while playing and verify current media, position, play/pause, queue, shuffle,
    repeat, notification, and Android Auto state remain intact.
14. Copy Diagnostics and verify that it contains no file path, hardware address, or unsupported
    output-quality claim.

## 20. Startup regression and bounded device validation

### Startup regression

The initial Phase 5 build crashed while constructing `PlaybackController`.
`_audioOutputState` was accessed before its textual initialization because an early `init` block
launched collection of `AdvancedAudioRuntimeBridge.state`. With `Dispatchers.Main.immediate`, the
`StateFlow` emitted its current value synchronously, and the collector called
`_audioOutputState.update` while the JVM field was still null.

The fix moved collector startup after every backing `MutableStateFlow` and dependent field was
initialized. `release()` now explicitly cancels the advanced-audio runtime collector and resets
audio output state. Six regression tests execute construction with immediate dispatcher semantics
to cover controller construction, the initial emission, immediately available audio output state,
single collector startup, release cancellation, and `MusicViewModel` construction.

### Device and launch

- Device: Samsung SM-S908U1.
- OS: Android 16, API level 36.
- The debug APK was installed over existing app data.
- Three cold launches reached Home successfully: 817 ms, 833 ms, and 785 ms.
- No startup crash occurred after the initialization fix.

### Playback and diagnostics

- Audio offload default was confirmed as Disabled.
- Playback started successfully with Disabled.
- Changing Disabled to Automatic preserved the current song and playback session.
- Position advanced from 108,215 ms to 115,242 ms; the song did not restart.
- Changing back to Disabled succeeded.
- Queue/Up Next, shuffle, repeat, artwork, the MediaStyle notification, and Diagnostics remained
  functional.
- Requested and actual offload state remained separate, and privacy-safe diagnostic output was
  verified.
- The app stayed running during the bounded session with no crash, ANR, AndroidRuntime error, or
  ActivityManager error.

## 21. Limitations

Automated checkpoint validation completed with:

- all debug unit tests passing;
- debug APK and debug Android-test APK assembly passing;
- lint passing;
- all 18 connected Android tests passing on the SM-S908U1 after the initialization regressions
  were added;
- the debug APK installing over existing app data;
- `git diff --check` passing; and
- a clean worktree after the validation commits.

Benchmark suites and long device tests were not rerun for this documentation update. Generated
profiles were not altered.

The bounded manual validation does not close these evidence gaps:

- Actual offload activation was not conclusively observed.
- Sleeping-for-offload was not observed.
- MP3, AAC/M4A, and FLAC offload compatibility was not systematically recorded codec by codec.
- Bluetooth, USB, and wired routes were not tested.
- Gapless transition behavior under Automatic was not tested.
- ReplayGain behavior during confirmed active offload was not tested.
- No battery-life, power-reduction, or thermal-improvement claim is made.
- Offload remains explicitly not evidence of bit-perfect playback.
