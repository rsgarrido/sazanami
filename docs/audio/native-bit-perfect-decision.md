# Native Android Bit-Perfect Playback — Deferred

## Status

Deferred

## Decision date

2026-07-29

## Context

Android API 34 introduced preferred mixer attributes and optional bit-perfect
mixer behavior. Actual support depends on the phone, firmware, audio policy and
HAL, and connected USB device. High-resolution USB playback alone does not imply
bit-perfect mixer support.

Phase A established that Sazanami's current processor-backed Media3 sink converts
high-resolution integer PCM to PCM16 before the persistent equalizer processor.
A production exact path would require controlled reconstruction of the
authoritative player, renderers, and sink. The successful hardware activation
path could not be tested because both available Samsung/Dawn Pro 2 combinations
exposed only default mixer behavior. The expected user population is extremely
narrow, while hardware support remains inconsistent.

## Evidence

- Galaxy S22 Ultra + Dawn Pro 2: 21 default mixer attributes and 0 bit-perfect
  attributes.
- Galaxy S24 Ultra + Dawn Pro 2: 21 default mixer attributes and 0 bit-perfect
  attributes.
- Galaxy S9+ on Android 10: ordinary application behavior worked and the probe
  safely returned `API_TOO_OLD`.
- The processor-free test path could create exact PCM16 and packed PCM24
  AudioTracks.
- The existing persistent-EQ sink cannot preserve high-resolution integer PCM.

The complete API analysis, matrices, measurements, benchmark comparison, and
hardware observations remain in the
[Phase A feasibility report](phase-a-bit-perfect-feasibility.md).

## Decision

- Do not implement Phase B now.
- Do not ship a native bit-perfect setting or badge.
- Remove the Phase A runtime prototype from production.
- Preserve the Phase A evidence and Git history.
- Continue supporting normal USB DAC playback without claiming bit-perfect
  output.

## Consequences

Positive consequences:

- Simpler playback architecture.
- No dormant experimental provider or controller.
- No unused permission.
- No misleading user interface.
- Lower regression and maintenance risk.

Negative consequences:

- Sazanami will not currently offer native Android bit-perfect playback.
- A future implementation may require recreating or adapting the prototype.

## Re-entry criteria

Reconsider the decision only when:

1. Compatible phone/DAC hardware returns at least one real
   `MIXER_BEHAVIOR_BIT_PERFECT` attribute.
2. The team can validate exact matching, set success, query confirmation,
   listener callback, matching AudioTrack format, USB routing, processor-free
   playback, cleanup, disconnect/reconnect, and format transitions.
3. Android and OEM support becomes sufficiently reliable to justify the
   maintenance cost.
4. Media3 offers a cleaner supported processor-free high-resolution integer
   path, or another supported architecture is proven.
5. The likely user value justifies maintaining multiple playback
   configurations.

This feature is deferred, not permanently cancelled.
