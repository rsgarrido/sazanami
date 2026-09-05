# Android Auto v3 reliability investigation

Branch: `android-auto-v3`. No commit or push. Media3 remains at 1.10.1.

## Evidence and limits

The defects below were found by tracing the current code and adding focused regression tests.
There was no connected Android device or configured emulator during this investigation. Actual
vehicle startup timings, the session listener event sequence on the vehicle, and audible gapless
playback still need the manual checks below. Queue-button suppression has been confirmed in-car.
Code-path evidence is not a measurement of the owner's drive.

## 1. Root causes found

App defects:

- `onGetLibraryRoot` loaded the entire catalog before returning a root. That included preferences,
  cached songs, playlist membership, artwork URI registration, optional collage generation, and
  album/artist/song tree construction. Every subsequent browse request repeated the work.
- Empty categories were marked neither browsable nor playable because browsability depended on
  having children. An empty library must still expose valid containers.
- A failed cached-queue restore could escape the service initialization coroutine. It is now
  contained so a transient cache failure does not terminate the browse service.
- Phone-started playable items used the original song artwork URI. SAF artwork normalized for Auto
  browsing could therefore still be inaccessible in Now Playing.
- The manifest advertised `MEDIA_PLAY_FROM_SEARCH`, but `MainActivity` ignored both initial and
  subsequent voice intents.
- Partial playlist/album names could take precedence over exact song/artist matches. Plain genre
  queries and playlist/genre browser search results were incomplete.
- Repeated playlist tracks shared browse IDs; selection and shuffle also collapsed the selected
  occurrence to the first matching song ID or removed its other occurrences.

Likely upstream behavior: scrolling that resets only while playing matches
[AndroidX Media issue #2192](https://github.com/androidx/media/issues/2192). That open report also
reproduces in the Media3 demo, describes a refresh roughly every five seconds, and says pausing
avoids the problem. This milestone does not claim to fix that host behavior.

## 2. Cold-start diagnosis

The service is correctly exported with both MediaLibraryService and legacy MediaBrowserService
actions. Automotive metadata declares `uses name="media"`. No manifest change was justified.

The root's dependency on the full catalog was an app-controlled cold-start hazard. Opening the
phone app supplies a live library through `PlaybackLibraryBridge` and can finish artwork work,
reducing that hazard. This explains a plausible warm/cold difference; it does not prove why the
vehicle launcher omitted the app on each observed occasion.

The root now returns an immediate, stable item. Its four category descriptors also load without
Room, preferences, a scan, or artwork. Content loads independently. Repository construction no
longer initializes Coil, and database observation starts off the service main thread.

With no live bridge, the repository uses Room filtered by the saved folder selection. An empty
cache produces valid empty containers. A failed content read can be retried; it never prevents
returning the root. A stale cache remains a cache: this change does not start a full MediaStore
scan from the car. Missing media access or never-indexed music still requires phone-side setup.

## 3. Browse latency diagnosis

The avoidable blocking stages were full catalog reloads, per-playlist reference-index rebuilding,
collage image decoding/generation, and repeated complete browse-tree construction. The repository
now shares one load across concurrent readers, caches the tree, shares the manual-playlist
reference index, and uses an existing collage or a first cover without generating collages.

Room library/playlist/artwork edits, folder-selection changes, and bridge publications refresh the
catalog without recreating the service. A request-driven 30-second expiry covers time-dependent
smart playlists; no periodic task refreshes the player's queue. Subscription notifications are
sent only for changed children, with publications briefly coalesced.

New opt-in diagnostics distinguish `service`, `session`, `catalogInit`, `root`, `preferences`,
`roomSongs`, `catalog`, `playlists`, `artworkUris`, `browseIndex`, `visibleArtwork`, and `children`.
Suspending stages use paired asynchronous `CDP.Auto.*` trace slices. The first real-car capture
is still needed to identify which stage dominated the owner's particular delay and quantify the
improvement. No fabricated before/after millisecond figures are reported.

## 4. Artwork diagnosis

The full path remains song metadata → local content URI → existing read-only provider → stored
image, embedded reconstruction, or lazy Auto copy. Both providers were already exported with no
read permission; VisualAssetProvider also allows URI grants. Their declarations and security
behavior are unchanged.

- Embedded artwork URIs contain a MediaStore source reference and hash. The provider can recreate
  evicted cached bytes when the source still exists and is readable; a missing file is not by
  itself evidence that the URI is wrong.
- Folder artwork is registered as an opaque app-owned URI. The provider lazily materializes a
  bounded image using Sazanami's persisted source grant; it does not need the Activity.
- Custom artist/playlist art requires the stored asset variants. Missing custom backing files
  cannot be repaired merely by changing a permission.
- An interrupted first library enrichment can leave cached songs with no artwork/genres. Visible
  browse/search items and the current song can now attempt embedded-art extraction on demand.
  This is not a full library scan or a cold folder-art discovery pass.
- Current session artwork is prepared asynchronously for phone-started as well as Auto-started
  playback. Late catalog publication refreshes it. `SmoothPlaybackPlayer` retains the delegate's
  timeline, UIDs, tracks, and index while overriding only current combined artwork metadata.

Revisiting previously rebuilt metadata after enrichment or provider materialization. The new path
also refreshes current metadata and subscribed browsing when the underlying catalog improves.
First-load decoding and host artwork caching still require real-car verification.

## 5. Voice diagnosis

Activity voice intents now preserve query/extras and send the request through a MediaController
to the same service callback used by Auto. Initial launch and `onNewIntent` are both handled;
controller resources use Media3's ordinary Activity lifecycle handling. Ordinary phone playback
with resolved local items keeps its immediate callback path. No Assistant-specific retry, delay,
dynamic command policy, or lifecycle workaround is retained.

The shared parser accepts `RequestMetadata.searchQuery`, query extras, title/artist/album/playlist/
genre fields, media focus, and Media3 media type. It also handles Assistant phrases that retain
the app qualifier and album-by-artist phrases. Artist focus ignores incidental song metadata. Set
requests stage the existing phone context and let Media3 apply/prepare/play once; add requests
resolve items without replacing the current queue. Search/result callbacks include playlist and
genre songs.

Unfocused exact-match priority is song title, playlist, album, artist, then genre, before partial
collection matches. Ambiguous song titles retain deterministic human-search tie breaking.
Unmatched explicit queries are rejected, as are unresolved media IDs and an unavailable catalog;
no unresolved `MediaItem` is returned as a successful callback result. Empty/generic music
requests retain library fallback.
Canonical identity, history reconciliation, and strict playlist reference resolution are unchanged.

Cached title/artist/album/playlist data supports the same resolution when the phone is cold.
Cached genres are preserved if enrichment completed; a never-enriched cache cannot support an
unknown genre. Tests compare cold and warm sources, including differing artwork enrichment.
Google Assistant routing and acknowledgement remain best-effort host behavior rather than an
Android Auto v3 release gate.

## 6. Queue current-index diagnosis

`Media3PlaybackQueueRuntime.replaceTimeline` already resolves the durable entry ID in playback
order and calls `setMediaItems(items, currentIndex, savedPosition)` once. It does not first load
index zero and then seek. Duplicate entries use their entry IDs, not song IDs. Queue switching
and restoration already share this path; no persistence or runtime rewrite was needed.

The coordinator checks again for an already-live runtime after suspended cold resolution, so a
controller-created queue wins that restoration race. Compatibility preferences remain intact;
their saved song ID is used as a generic voice preference, not as an independent timeline.
There is no custom `onPlaybackResumption` callback; cold restoration continues to use the existing
service-owned queue coordinator.

The actual session exposes `SmoothPlaybackPlayer`, which forwards the physical timeline/index.
New device tests inspect that logical player directly, including duplicate IDs, a middle start,
repeat/shuffle state, progress changes, artwork metadata updates, and intentional crossfade.
They cannot be reported as executed without a device. Existing and new JVM tests verify the
runtime's atomic index/position arguments and queue switching/restoration behavior.

Auto playlist browse IDs now distinguish repeated occurrences. Unshuffled playback uses the
selected occurrence's index; shuffled playback removes only that occurrence when moving it first.

## 7. Android Auto Queue policy

No periodic app-side timeline replacement was found in source inspection. Because the playing-only
scroll reset matches upstream issue #2192, Sazanami now withholds only
`Player.COMMAND_GET_TIMELINE` from Media3's media-notification controller. Media3 uses that
controller's commands to populate the framework session queue read by Android Auto, so the built-in
Queue button should be absent. `isAutoCompanionController` is recorded for diagnosis but does not
lose commands; the notification controller is the narrower supported policy described by Media3.

The actual player timeline, `SmoothPlaybackPlayer`, Room queues, Queue Hub, named queues, and queue
restoration are unchanged. Play/Pause, Previous, Next, shuffle, and repeat commands are retained.
The existing timeline diagnostics remain available if the upstream behavior is revisited.

## 8. Gapless safety

`DualPlayerPlaybackCoordinator`, native EOS behavior, queue runtime, and audio processing code are
unchanged. Normal EOS still advances the active ExoPlayer's existing playlist on the same physical
player. Intentional crossfade still primes the full playlist at its target index and alone changes
physical authority. Existing gapless/crossfade tests are retained and included in validation.
Audible gapless and overlap checks remain part of the device handoff.

## 9. Files changed

Paths below are relative to this repository.

| File | Purpose |
| --- | --- |
| `app/src/main/java/io/github/rsgarrido/sazanami/MainActivity.kt` | Forward initial/subsequent voice intents through the ordinary Media3 controller path |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoControllerCommandPolicy.kt` | Hide the framework queue through a notification-controller capability |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoBrowseTree.kt` | Valid empty containers; distinguish repeated playlist occurrences |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoCatalogRepository.kt` | Shared cached snapshots/tree, invalidation, cheap collage fallback, visible artwork preparation |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoDiagnostics.kt` | Opt-in timings and session-facing player event diagnostics |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoRequestMetadata.kt` | Shared intent/Media3 query and focus parsing |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/AndroidAutoSearchResolver.kt` | Exact-before-partial voice ranking and genre/playlist search |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/PlaybackLibraryBridge.kt` | Thread-visible songs and observable catalog publication |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/PlaybackService.kt` | Immediate root/categories, catalog notifications, artwork and external request handling, selected occurrence |
| `app/src/main/java/io/github/rsgarrido/sazanami/player/SmoothPlaybackPlayer.kt` | Current artwork metadata enrichment preserving the delegate timeline |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/AndroidAutoBrowseTreeTest.kt` | Empty root/category and duplicate browse ID regressions |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/AndroidAutoCatalogRepositoryTest.kt` | Cold/concurrent reads, empty/failure recovery, live refresh, folder selection |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/AndroidAutoSearchResolverTest.kt` | Ranking, genre/playlist, cold/warm and ambiguity regressions |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/AndroidAutoControllerCommandPolicyTest.kt` | Notification queue suppression and retained transport-command policy |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/AndroidAutoRequestMetadataTest.kt` | Assistant query/focus/media-type request shapes |
| `app/src/test/java/io/github/rsgarrido/sazanami/player/Media3PlaybackQueueRuntimeTest.kt` | Exact duplicate restoration index under every repeat mode |
| `app/src/androidTest/java/io/github/rsgarrido/sazanami/player/AndroidAutoLogicalPlayerTest.kt` | Actual logical-player index, stable timeline and crossfade exposure |
| `app/src/androidTest/java/io/github/rsgarrido/sazanami/player/AndroidAutoMetadataContractTest.kt` | Activity/session voice equivalence and cold provider-backed playable artwork |
| `docs/android-auto-v3-reliability.md` | Findings, validation evidence, limitations and car checklist |

## 10–11. Automated validation

Focused command (88 tests, all passing):

```powershell
.\gradlew.bat :app:testDebugUnitTest --tests '*AndroidAuto*' --tests '*Media3PlaybackQueueRuntimeTest' --tests '*PlaybackQueueCoordinatorTest' --tests '*DualPlayerPlaybackCoordinatorTest' --console=plain
```

Full validation command:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest --continue --console=plain
```

The first combined run completed the unit suite and debug assembly and reproduced the known
instrumentation failures. Its lint analyzer stopped making observable progress in a Kotlin PSI
traversal, so the wrapper/daemon for that build were stopped. Validation was retried with
`--continue --offline --no-daemon --console=plain` in a fresh Gradle process.

- Full unit results: 1,548 tests, zero failures/errors, seven skipped.
- Instrumentation assembly: blocked by the existing unresolved `assertDoesNotExist` import in
  `AddToAnotherQueueDialogTest.kt` and `onAllNodesWithText` calls in `QueueHubSheetTest.kt`.
  Those files were not edited. The device tests added here remain unexecuted.
- `git diff --check`: passed.
- `adb devices`: no connected devices; no configured local AVD was available.

Final application verification after the exception-handling changes:

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:assembleDebug --offline --no-daemon --console=plain
```

Final refinement result: unit tests **BUILD SUCCESSFUL** with 1,548 total, zero failures/errors,
and seven skipped; lint **BUILD SUCCESSFUL** with zero errors; debug APK assembly
**BUILD SUCCESSFUL**. The instrumentation assembly still stops only on the unchanged
`AddToAnotherQueueDialogTest.kt` and `QueueHubSheetTest.kt` compile errors described above.
Lint: zero errors, 162 warnings, four hints. Debug APK:
`app/build/outputs/apk/debug/app-debug.apk` (34,319,225 bytes).

Logs are retained under `build/reports/android-auto-v3/`: `focused.log`, `first-validation.log`,
`final-validation.log` (combined run with the known instrumentation failure), and
`final-app-validation.log` (successful final application validation). The standard lint and
JUnit reports remain under `app/build/reports/`.

## 12. Manual Android Auto checklist

- [ ] Close the phone UI, reconnect Auto, and verify Sazanami in the launcher without opening the
  phone app. Repeat several times. Distinguish a background/process-cold app from a deliberately
  force-stopped app when reporting results.
- [ ] With the UI still cold, check immediate category labels and open Songs, Albums, Artists,
  and Playlists. Record separate label and content delays. Genres are searched but have no new
  top-level browse category in this milestone.
- [ ] Check first-presentation artwork in Now Playing, albums, songs, playlists, and custom
  playlist/artist art. Include phone-started playback, Auto-started playback, embedded covers,
  and granted folder covers. Compare a fully enriched cached library with a partial first scan.
- [ ] As a best-effort voice sanity check, try “Play Shattered Heart by The Warning on Sazanami”,
  “Play Avril Lavigne on Sazanami”, and “Play The Best Damn Thing by Avril Lavigne on Sazanami”.
  If Assistant routes the request, confirm song, artist context, and album order and optionally save
  the `SazanamiVoiceSearch` trace. Inconsistent Assistant discovery or acknowledgement is not a
  release blocker for this milestone.
- [ ] Start well below the first entries of a long queue. Restore it and switch away/back. Check
  the actual playing entry, saved position, and logged index/window count. Include a repeated
  track occurrence, shuffled playback order, and repeat off/all/one.
- [ ] Verify Android Auto does not show its built-in Queue button. Confirm Previous/Next still
  traverse the real Sazanami queue, and verify the phone Queue Hub, notification, lock screen,
  named queue switching, shuffle, and repeat behavior remain intact.
- [ ] With crossfade off, play a known gapless album across several boundaries and confirm the
  same physical player/native automatic transition. Check next/previous, notification, lock
  screen, Bluetooth, queue switching, restoration, shuffle/repeat, and listening history.
- [ ] Enable intentional crossfade for one A→B overlap. Check one logical item transition,
  correct incoming index, and full queue membership. Include smooth pause/resume, EQ,
  ReplayGain and the existing offload/crossfade policy in the audio sanity pass.

Enable the opt-in log before connecting the car:

```powershell
adb shell setprop log.tag.SazanamiAuto DEBUG
adb shell setprop log.tag.SazanamiVoiceSearch DEBUG
adb shell setprop log.tag.CrossfadeTrace DEBUG
adb logcat -v threadtime SazanamiAuto:D CrossfadeTrace:D SazanamiVoiceSearch:D '*:S'
```

Record the timestamps for the scenarios above and save a system trace with the app's existing
Perfetto configuration if needed (`tools/performance/sazanami-perfetto.pbtx`). Async sections use
`CDP.Auto.*`; browse index construction is `CDP.Auto.browseIndex`. Disable the extra log afterward:

```powershell
adb shell setprop log.tag.SazanamiAuto INFO
adb shell setprop log.tag.SazanamiVoiceSearch INFO
adb shell setprop log.tag.CrossfadeTrace INFO
```
