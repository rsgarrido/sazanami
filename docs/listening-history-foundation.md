# Native listening-history and ratings persistence foundation

Database version 9 adds durable historical track identities, local-track bindings,
finalized listening events, and exact baselines for the aggregate history that existed
before event storage. Production Recently Played and Most Played now read the
baseline-plus-qualified-event projections. Native playback writes detailed finalized
attempts to `listening_events` from the authoritative `PlaybackService` player.

Migration 8→9 creates one identity, one local binding, and one legacy baseline for every
existing `song_play_stats` row. It deliberately creates no synthetic listening events,
because the old aggregate count does not contain individual timestamps, listened
durations, completion state, or qualification evidence.

Manual JSON backup schema 7 contains the complete canonical version-9 history. Its
independently versioned `canonicalListeningHistory` section has format version 1 and
stores identities, bindings, baselines, finalized events, and a count/time-boundary
summary. Database-generated identity and binding IDs are backup-local references only;
restore inserts new rows and remaps every foreign-key reference. Stored normalization,
event UUIDs, enum storage strings, session/source provenance, qualification facts and
rule versions are preserved rather than recalculated.

## Song ratings v1 foundation

Database version 10 adds `song_ratings`. A rating is an integer from 1 through 5 and
belongs to `listening_track_identities.id`, not to a MediaStore ID, URI, path, binding,
title, album, or artist. An unrated identity has no row; clearing a rating deletes its
row. The first write sets both `ratedAt` and `updatedAt`, a changed value preserves
`ratedAt` and advances `updatedAt`, and an equal value is a no-op. Clearing and rating
again begins a new lifecycle. Rating history is not stored.

Favorites remain completely independent: neither feature infers or changes the other.
Metadata-identical identities can carry different ratings. A rating remains when a
local binding or file disappears, while deleting the historical identity cascades to
its rating. Rating a current song resolves its exact `Song.membershipKey()` binding and
transactionally creates the normal identity/binding foundation when absent. Clearing
uses exact lookup only and never creates an identity for an unknown song.

Migration 9→10 only creates the rating table, its foreign key, and the rating index;
it generates no ratings and preserves all existing data. Manual backup schema 8 adds a
format-version-1 `songRatings` section whose entries reference canonical listening
history backup identity IDs. Export is ordered by that identity reference. Restore
validates all entries before mutation, inserts canonical identities first, and remaps
rating references inside the same Room transaction. Duplicate references, missing
identities, values outside 1–5, negative timestamps, reversed timestamps, and unknown
rating sub-formats are rejected. Schema 7 and older backups migrate with zero ratings;
favorites and aggregate history never become ratings. `song_play_stats` remains outside
rating export and restore.

This began as a non-UI foundation. Session 5 now connects the same persistence contract
to song-oriented Compose UI, sorting/filtering, and Statistics Top Tracks. Album and
artist ratings remain derived summaries for later work, not independently editable
records.

Schema 6 and older backups remain readable. Each old aggregate history row becomes its
own identity, exact-evidence binding, and legacy baseline, even when metadata is
identical. Counts and first/last timestamps are preserved and no detailed events or
listening durations are fabricated.

## Service-owned native recording

`PlaybackServiceListeningAdapter` is attached directly to the ExoPlayer owned by
`PlaybackService`, so Compose, notification, Bluetooth/headset, MediaSession, Android
Auto, automatic playlist, and repeat-one commands converge on the same callback stream.
The adapter timestamps callbacks immediately, processes them through one coroutine
consumer, and performs Room writes independently on an IO scope. A slow identity lookup
does not lose initial playback time because the captured monotonic callback time is
installed before each pure-recorder command.

Playable `MediaItem`s carry centralized, validated extras containing a unique item
instance ID, an exact durable local reference key, and immutable `SongReference`
evidence. The service resolves an existing local binding only by the exact reference
key. Otherwise it transactionally creates a new historical identity and binding;
normalized metadata is a snapshot/index only and never a merge key. Missing or malformed
extras cause that item to be skipped safely rather than guessed from its title.

Sessions are created only on confirmed `isPlaying == true`, not on preload. Automatic
and repeat transitions finalize the old attempt as `NATURAL_END`; repeat-one immediately
gets a new playback session ID even though the item instance is unchanged. User/direct
and current-item playlist replacement transitions use `TRANSITION`. Same-current-item
queue edits do not end a session. `STATE_ENDED`, player error, `STATE_IDLE`/stop, and
graceful service destruction map to natural end, error, and stopped finalization as
appropriate. Only within-item Media3 seek and seek-adjustment discontinuities are sent
to the recorder; automatic transition discontinuities are ignored.

Finalized drafts are inserted with Room conflict-ignore semantics. Event UUID and
playback-session unique indexes make callback duplication idempotent. Insert failures
are logged without track metadata and never crash or block playback. Native events keep
source `CDPLAYA`, null import fields, and never update `song_play_stats`.

The old `PlaybackController` aggregate recorder and its UI-side progress qualification
tracker have been removed. Playback-position observation remains for player progress and
checkpointing, but it no longer decides listening qualification or writes history.
`PlaybackService` is the only owner of new native listening-event semantics, covering UI,
notification, Bluetooth/headset, MediaSession, automatic transitions, and repeat-one.

Active sessions are in memory only. Graceful service destruction is finalized and its
outstanding insert is drained asynchronously, but abrupt process death can lose the
unfinished attempt. There are no periodic checkpoints or process-death recovery yet.

## Baseline-plus-event statistics queries

`ListeningStatsRepository` is the independent statistics/query boundary for future
history screens and reports. It is not connected to `LibraryController`, Compose,
Recently Played, or Most Played yet. All aggregation is performed by SQLite; repository
mapping only converts bounded aggregate rows into domain models.

### Counts, time, and timestamps

For a historical track identity, all-time play count is the frozen
`legacy_listening_baselines.historicalPlayCount` plus the number of detailed events whose
stored `qualifiedAsPlay` value is true. Qualification is never recalculated. Domain
models expose total, legacy, and detailed counts separately.

Confirmed listening time is the sum of `listening_events.listenedMs` for every finalized
attempt, including non-qualified attempts. Legacy counts contribute no duration because
the old aggregate rows did not record it; duration is neither inferred nor estimated.
Natural completions are detailed events whose persisted `endReason` is `NATURAL_END`.
Non-qualified attempts are detailed events whose stored `qualifiedAsPlay` value is
false.

Detailed events are classified and ordered by `startedAt`. This is the timestamp of the
recorded playback attempt, is consistent across every statistics query, and matches the
existing `(source, startedAt)`, `(qualifiedAsPlay, startedAt)`, and
`(trackIdentityId, startedAt)` indices. First/latest known qualified play is the
minimum/maximum of qualified detailed `startedAt` and the legacy baseline's first/last
known timestamp. Non-qualified attempts do not move Recently Played.

### Date ranges and source provenance

Detailed ranges are half-open epoch-millisecond intervals:
`[startInclusive, endExclusive)`. Callers resolve day, month, year, timezone, or custom
calendar boundaries before calling the repository; SQL does no device-local calendar
calculation. Ranged results are detailed-only because legacy plays cannot be allocated
to a day, month, or year.

A null source selection means all detailed sources. Any explicit detailed-source filter
applies only to events and excludes the legacy baseline even if legacy inclusion was
requested. In particular, neither a Sazanami nor an import filter claims provenance for
legacy counts. Legacy inclusion is effective only for unfiltered all-time queries; no
`LEGACY` event source was added.

### Analytics ranges and trends

The non-UI analytics foundation resolves calendar selections with an injected
`java.time.Clock` and a provider that returns the current `ZoneId` for every request.
It never caches the device zone. Today is the current local day; Last 7 Days includes
today and the preceding six days; Last 30 Days includes today and the preceding 29
days; This Month spans the first day of the current month to the first day of the next;
and This Year spans local January 1 to the following January 1. All Time has no event
date constraint. The default selection is Last 30 Days.

Custom selections store `LocalDate` values. Their end date is inclusive at the
selection boundary and resolves to the start of the following local day, producing the
same `[startInclusive, endExclusive)` epoch contract used by every statistics query.
A reversed custom range is rejected rather than swapped. Presets and custom dates are
resolved again in the current zone on refresh, so a zone change alters epoch boundaries
without changing a custom selection's calendar dates.

All calendar boundaries use `LocalDate.atStartOfDay(zone)` and zoned calendar
arithmetic. Today uses elapsed local-hour buckets and therefore has 23 buckets on a
Los Angeles spring-forward day, 25 on a fall-back day, and 24 normally. Repeated fall
hours remain separate chronological buckets with distinct offsets; a missing spring
hour is not fabricated. Other preset policies are: local days for Last 7 Days, Last 30
Days, and This Month; local months for This Year; local days for custom ranges of at
most 90 inclusive days; local months through 36 calendar months; and local years for
longer custom ranges. All Time aligns matching detailed-event bounds to local months
when the covered interval is at most 36 months and to local years otherwise. No matching
detailed events means an empty All Time trend. Extremely long year ranges use wider
year steps so the complete interval remains represented without exceeding 400 buckets.

Each trend bucket assigns an entire finalized attempt by its persisted `startedAt`,
including an attempt that ends in a later day. Recorded time is `SUM(listenedMs)` for
all detailed attempts; qualified plays count stored `qualifiedAsPlay = true`; total
attempts count every finalized detailed event; and natural completions count persisted
`NATURAL_END` reasons. Legacy aggregate plays are never placed into trend buckets and
never receive estimated duration. An explicit source set filters overview, trend,
rankings, and detailed bounds consistently and excludes legacy counts.

Kotlin supplies validated, contiguous boundaries to one bounded SQLite `VALUES` CTE.
Only integer bucket indices are SQL literals; both timestamps per bucket and explicit
source storage strings are bound. The 400-bucket policy uses at most 803 bindings with
the three currently defined sources, below the conservative 900-binding guard. A left
join returns zero-valued rows for empty periods, and SQLite aggregates the event table
without materializing events in Kotlin or issuing one query per bucket.

One Room read transaction captures detailed bounds, overview, the complete trend,
qualified-play rankings (10 tracks, 5 albums, and 5 artists), and legacy/detailed
coverage facts. Date boundaries for finite selections are generated before the
transaction; All Time boundaries are generated from the detailed bounds read inside
that transaction so their event coverage is coherent. Mapping rows into immutable
domain models happens after the transaction. Existing `(source, startedAt)`,
`(qualifiedAsPlay, startedAt)`, and `(trackIdentityId, startedAt)` indexes remain in
use, so this work adds no table, index, migration, or database-version change.

`ListeningAnalyticsController` is inactive by default and owns no scope. Activation
starts a fresh current-zone load and observes only the Room tables read by the snapshot;
deactivation removes that observer, cancels in-flight work, and retains the last
snapshot, selected range, and trend metric. Range changes publish the selection
immediately, retain the previous snapshot while refreshing, cancel stale work, and use
a request generation check before publication. Conflated invalidations refresh only the
active selection. Initial and refresh failures expose retryable domain errors; refresh
failure retains the last snapshot. Retry clears the error before loading. Switching
between recorded listening time and qualified plays changes state only because every
bucket already contains both metrics.

### Track, album, and artist grouping

Track statistics group strictly by `listening_track_identities.id`. MediaStore IDs,
bindings, titles, and normalized metadata never merge historical identities. Each track
row includes one deterministic preferred binding: an available binding before a missing
one, then newest `lastSeenAt`, then lowest binding ID. An identity without a binding is
still returned.

Album reporting is a grouping only. Its key is normalized album artist plus normalized
album; when album artist is absent it conservatively falls back to normalized track
artist. Blank values use explicit unknown keys. A compilation with a consistent album
artist such as `Various Artists` groups together; compilations without consistent album
artist may remain split by track artist. Artist reporting groups by normalized track
artist and never changes song-level artist metadata. Distinct track identities and
normalized album keys determine track/album counts.

When multiple display snapshots share a grouping key, SQL selects the lexicographically
smallest nonblank display value. This is stable and deterministic across query runs;
unknown groups receive `Unknown Album` or `Unknown Artist`.

### Projections and recent attempts

The production Recently Played projection contains one row per identity with a
qualified legacy or detailed play, ordered by latest known qualified play descending and
then identity ID ascending. The Most Played projection orders by combined play count
descending, latest known qualified play descending, and identity ID ascending. Both
retain unresolved historical identities and exact binding/reference evidence.

## Production cutover and reactive library mapping

`ListeningStatsRepository.observeProductionHistory()` observes only
`listening_events`, `legacy_listening_baselines`, `listening_track_identities`, and
`local_track_bindings`. Room invalidation is conflated, and each refresh reads Recently
Played, Most Played, and all binding evidence inside one database transaction. A newer
invalidation cancels an older refresh before it can publish a stale snapshot. Collection
is owned by the long-lived `LibraryController` scope; cancelling that scope removes the
Room observer, and no repository-owned or global scope is created.

The controller combines that database snapshot with its immutable
`SongReferenceIndex` and visible membership-key snapshot. Mapping runs off the main
thread, publishes both lists in one UI-state update, and is cancelled/restarted when
either history or the current library changes. Consequently database inserts, rescans,
and folder-selection changes all use the same resolution path without polling or a
service-to-UI refresh callback.

Resolution preserves projection order and omits unresolved or ambiguous identities. It
tries the deterministic preferred exact binding first, then other known bindings for the
same identity. Resolver confidence tiers may use local ID/URI, source path, file
signature, portable key, and legacy key evidence stored by those bindings; there is no
full-library title-only or fuzzy match. A preferred exact match wins. If fallback
evidence is ambiguous or resolves to conflicting current songs, the identity is omitted.
Distinct identity IDs are never merged even when their metadata snapshots are identical.
The index includes the reference library while the visible membership set enforces the
current folder selection, so hiding a folder removes only the playable row. History is
not deleted, and a later rescan or folder re-inclusion can resolve it again.

The projection model exposes all known local bindings for production resolution. The
database query still exposes one deterministic preferred binding on ordinary statistics
rows for compatibility with the Session 4 API.

Recent detailed events are separate from Recently Played. They include qualified and
non-qualified stops, transitions, errors, and natural completions, support source and
date filters, and order by `startedAt` descending then event row ID descending. Baseline
counts are never fabricated as events.

### SQL performance and transitional limitation

Overview, track, album, artist, projections, and recent-event results use CTE aggregation,
joins, SQL grouping, deterministic ordering, and explicit limits. They do not load the
event table into Kotlin and do not issue per-track binding queries. Existing version-9
indices cover source/date filtering, qualified/date filtering, track/date grouping, and
binding selection, so no schema version or new index is required for this layer.

The version-9 baseline is a frozen copy of history present during migration. Production
totals combine only that frozen baseline with qualified detailed events. Current
`song_play_stats` values are not copied into baselines or events and are not a third
statistics source. No heuristic reconciliation is performed. A development device used
while both branch-only recorders were temporarily active may therefore show small count
or ordering differences after cutover; those aggregate test writes are intentionally
ignored because folding them in could double count events and mix qualification rules.

`song_play_stats` remains at database version 9 for migration verification, old-backup
compatibility, legacy-reference maintenance, and development rollback inspection.
Playback no longer mutates it, production history screens never read it, and schema-7
export does not use it. A new schema-7 restore clears this noncanonical compatibility
table. A migrated schema-6 restore may also restore its aggregate rows for legacy code,
but canonical totals come only from the separately converted baselines and events, so
the compatibility rows cannot double-count Recently Played or Most Played.

## Manual backup and restore schema 7

Canonical export reads identities by ID, bindings by identity then binding ID,
baselines by identity, and events by start time then event row ID. All four reads occur
in one Room read transaction, so finalized playback inserted during export is wholly
before or after the captured history snapshot. Event cursors are read in pages of 1,000.
The JSON encoder writes directly to the destination stream and uses compact JSON, so it
does not allocate a second full serialized string. The in-memory `AppBackup` model still
retains the collected records while serialization runs; database cursor and JSON-string
memory are bounded, but the current serializer is not a record-at-a-time parser. Import
batch IDs remain opaque nullable event provenance because no canonical import-batch
table exists yet. The existing UI reports completion rather than per-page progress.

Before restore mutates data, schema version, history format version, summary counts,
references, ownership, durations, timestamps, play counts, enum strings, and every
database uniqueness key are validated. Favorites, playlists, the compatibility table,
and the four canonical history tables are then replaced inside one Room transaction.
Canonical deletion order is events, baselines, bindings, identities; insertion order is
identities, bindings, baselines, events. Identity and binding maps translate backup-local
IDs to restored IDs, and baseline/event inserts use batches of 500. Any validation or
database failure leaves all database-backed categories at their prior state. Preferences
remain in their existing DataStore boundary and are applied after the Room commit.

Room invalidation from the committed transaction refreshes production Recently Played
and Most Played without an app restart, rescan, tab change, or artificial playback
event. An active in-memory playback attempt is intentionally not serialized, stopped,
or rewritten. If it finalizes after restore, the service inserts exactly one new event
after the restored snapshot.

Backups remain local to the user-selected document destination and add no upload or
network behavior. Schema 7 may contain track metadata, timestamps, listening behavior,
and local-reference evidence including paths and content URIs. Backup code does not log
the JSON, metadata, paths, or event history; structural errors use privacy-safe messages.

## Pure listening-session recorder (qualification rule v1)

`ListeningSessionRecorder` remains a pure Kotlin state machine for one native Sazanami
playback attempt. Android, Media3, Room, UI, and lifecycle dependencies remain outside
its contract in the service adapter and repository boundaries.

The recorder is constructed with a monotonic clock, a wall clock, and an event UUID
generator. Its command API is:

* `startSession(request)` creates an inactive session with zero listened time.
* `onPlaybackStarted(playbackSessionId)` opens an actively-playing segment.
* `onPlaybackSuspended(playbackSessionId)` closes that segment for either pause or
  buffering.
* `onPositionDiscontinuity(playbackSessionId)` closes and immediately reopens a segment
  if playback is active. It neither accepts nor infers a media position.
* `snapshot()` returns immutable current state.
* `finalizeSession(playbackSessionId, endReason)` closes an active segment and returns
  one immutable `FinalizedListeningEventDraft`.

Every callback command carries the playback session ID. A delayed command for an older
session is rejected instead of mutating the current session.

### Actual listening time

Only elapsed monotonic time during explicitly active-playing segments is accumulated.
Wall-clock time is used for the persisted `startedAt`, `endedAt`, and `createdAt`
timestamps only, so civil-time changes cannot alter `listenedMs`. Snapshot time includes
the elapsed portion of an open segment without closing that segment.

Pause and buffering have identical recorder semantics: both close the active segment,
and time remains stopped until another explicit playback-started command. A seek closes
the current segment exactly once, preserves all accumulated time, and reopens at the
same monotonic instant when still playing. Forward seeks cannot add listening evidence;
backward seeks never subtract evidence, and listening to replayed sections can make
`listenedMs` exceed the duration snapshot.

If a faulty monotonic clock moves backward, readings are clamped to the recorder's last
observed high-water mark. The rollback interval therefore contributes zero time; time
starts accumulating again only after the clock passes that high-water mark. Elapsed
subtraction and accumulation saturate at `Long.MAX_VALUE` rather than overflowing.

If the wall clock moves before the session start, final `endedAt` and `createdAt` are
clamped to `startedAt` so the Room v9 finalized-event constraints remain valid.

### Qualification rule v1

The time threshold is:

```text
min(durationMs / 2 + durationMs % 2, 240_000 ms)
```

This is an overflow-safe integer ceiling of half the duration, capped at four minutes.
Listening equal to the threshold qualifies; one millisecond below it does not. There is
no minimum duration. Null, zero, and negative durations cannot qualify by elapsed time.

A proven `NATURAL_END` always qualifies, including before the time threshold and when
duration is invalid or unknown. Stops, errors, transitions, and unknown endings do not
provide natural-completion evidence. Time qualification is sticky once reached, while a
later natural ending upgrades its reason from `TIME_THRESHOLD` to `NATURAL_END`. The
draft stores both the reason and `qualificationRuleVersion = 1`; persistence does not
need to recalculate them later.

### Idempotency and session boundaries

Duplicate play and suspend commands do not reopen, close, or count a segment twice.
Repeated discontinuities at the same monotonic instant add zero time. Starting an
identical request for the currently active playback session is idempotent. A same-ID
request with different identity, binding, or duration data is rejected as conflicting,
and a different session cannot replace an unfinalized one.

Finalizing without an active session produces no synthetic event. Successful
finalization clears the active session so another can start, while the most recently
finalized session ID is retained to reject both its duplicate finalization and a delayed
duplicate start. A genuinely new session therefore needs a new playback session ID.
UUID generation occurs only for successful finalization. Native drafts always use
source `CDPLAYA` and leave import fields null. The separate `toEntity()` mapper is the
Room boundary; the recorder itself neither constructs nor inserts Room entities.

## Statistics destination and overview (Session 3)

Statistics is a full-screen `MusicPrimaryDestination`, not a bottom-navigation item or
library tab. A rounded Statistics icon button sits immediately before Settings in the
Home header, leaving the scrollable music content undisturbed. The button is
navigation-only and never queries analytics. Opening it preserves the underlying
Home/Library/Search state, activates the single
ViewModel-owned `ListeningAnalyticsController`, and renders one lifecycle-collected
`ListeningAnalyticsUiState`. Back closes Statistics, deactivates the controller, and
reveals the preserved shell state. Deactivation cancels Room observation and current
work while retaining the last successful snapshot and selected range for reopening.
The screen is one Material app-shell implementation shared by every player theme.

The range row scrolls horizontally and offers Today, Last 7 days, Last 30 days, This
month, This year, All time, and Custom; Last 30 days remains the default. A selection is
published immediately while prior metrics remain visible during refresh. The
full-screen Material date-range picker requires both endpoints. Picker milliseconds are
interpreted as UTC calendar dates, and picker initialization uses each `LocalDate` at
UTC start of day. The inclusive calendar end is passed to the controller; the range
resolver then re-resolves device timezone and local day boundaries at each load.

The overview reports detailed `confirmedDetailedListeningMs`, total qualified plays,
detailed natural completions, and detailed non-qualified attempts. Duration formatting
uses whole minutes below an hour, hours plus minutes below a day, and days plus hours
thereafter; negative input is clamped to zero. Counts use locale-aware whole-number
formatting. All Time play totals may include the preserved legacy baseline, but no
duration, completion, attempt, date-range, or trend detail is inferred from it. The
inline coverage card and detailed dialog state this explicitly.

With no successful snapshot the screen shows progress or a retryable error without
zero metric cards. Background refresh retains the last snapshot and uses a thin progress
line in a permanently reserved six-dp slot beneath the range chips, so neither an
explanation sentence nor a layout shift occurs. Refresh failure likewise keeps the
snapshot and adds a retry card. Empty All Time history and empty finite ranges use distinct messages;
legacy-only All Time remains non-empty and shows its preserved play count, whereas a
finite range cannot assign legacy plays to dates.

Metric cards use one column on narrow or large-font configurations and two columns at
ordinary phone width. Chips scroll rather than compress. The Home title receives the
remaining flexible header width and ellipsizes before the separate 48-dp Statistics and
Settings targets can overlap. Back, info, range, Retry, Home entry, progress, metric,
and dialog semantics are exposed, with 48 dp shell icon and chip targets. The picker
uses the full window so narrow devices do not compress its calendar and actions.

The controller and selected range survive ordinary navigation and configuration through
the ViewModel. The Statistics `LazyListState` is saveable at the `MusicScreen` level, so
range changes, dialogs, overlays, and closing/reopening the destination in the current
saved shell retain scroll. Full process-death range restoration is deferred because the
current `AndroidViewModel` has no `SavedStateHandle`; adding a second Compose-owned range
would create an unsafe dual source of truth.

### Deferred work

Active-session persistence, periodic checkpoints, process-death recovery for transient
Statistics selection, rating UI and sorting/filtering, album/artist rating summaries,
chart tooltips and scrubbing, full ranking destinations, Spotify/Last.fm imports and
matching, Wrapped, smart playlists, cloud synchronization, and shareable reports remain
deferred.

## Statistics trend and ranked listening (Session 4)

Statistics completes its read-only Analytics v1 presentation with two sections below
the overview and history-coverage card. `Listening trend` consumes the bounded bucket
list from the existing atomic snapshot; Compose neither queries events nor rebuilds
calendar boundaries. A purpose-built Compose `Canvas` draws rounded vertical bars in
the app-shell accent over theme outline grid and baseline colors. The chart uses a
fixed visual area and a shared linear maximum for the selected series. Zero stays
empty, the smallest nonzero value receives a two-pixel minimum, `Long` ratios are
calculated through `Double`, and totals saturate rather than overflow.

The controller-owned selector switches between recorded detailed listening time and
qualified plays. Both values already exist on every bucket, so the intent only copies
UI state: it does not resolve a range, read Room, change the selected range, or replace
the retained snapshot. The selection survives destination deactivation/reactivation,
range refreshes, and invalidation refreshes for the ViewModel lifetime, matching the
existing process-persistence boundary.

Series through 30 buckets fit the viewport. Denser series use a conservative minimum
slot width inside a horizontally scrollable chart container, capped by the repository's
400-bucket contract; the surrounding Statistics screen remains one vertical
`LazyColumn`. Labels are selected at bounded, evenly distributed indices and always
include the first and last period. Java-time formatters use the snapshot's resolved
zone and current locale. Short daily ranges use weekdays; longer days use month names
and day numbers; months include a year when the series crosses a year boundary; years
use four digits. Repeated fall-back hours include their localized GMT offset, while a
spring-forward hour is never fabricated.

The maximum and a concise `Most active` summary communicate scale. Peak selection uses
the chosen metric and breaks ties toward the greatest bucket start timestamp. An
all-zero series has no peak. Container semantics announce the metric, period count,
saturating total, peak, and displayed range without enumerating hundreds of bars. Chip
selection is semantic as well as visual, and horizontal scrolling retains standard
accessibility actions. All Time without detailed events explains that detailed
activity will appear after playback; an empty finite series says there is no trend data
in the range. Legacy aggregate counts never become bars or recorded time.

`Top listening` uses a second controller-owned, in-memory category selection: Tracks
by default, then Artists or Albums. Switching categories reads the already-loaded
snapshot lists, performs no query, preserves the selected range and trend metric, and
survives Statistics deactivation for the ViewModel lifetime. Repository order is used
unchanged: qualified plays rank up to 10 tracks, 5 artists, and 5 albums. Rows keep play
count prominent and label detailed duration as recorded supporting time. Track rows
show their historical title, artist, and album; album rows show album artist; artist
rows use the repository display name. Long text ellipsizes independently of the metric
column, category chips scroll on narrow screens, and each category owns its own empty
message.

Track rows are deliberately text-first in this version. Every historical identity ID
remains a separate stable-key row, including metadata-identical identities and tracks
whose local file is missing or excluded. Statistics does not title-match, fuzzily
merge, hide unresolved history, or issue per-row binding queries. The present
Music/Library boundary does not expose the bounded exact-resolution result alongside
the analytics snapshot, so artwork and playback are deferred instead of adding a
second resolver path. Consequently no track row claims click/play semantics, while
artist and album rows likewise expose no misleading detail navigation. These choices
kept Session 4 responsive on narrow phones; Session 5 adds only a compact read-only
rating indicator without changing ranking or playback behavior.

## Rating UI and Songs sorting/filtering (Session 5)

Every action sheet whose target is a concrete playable `Song`, including playlist song
rows, includes a localized `Rate song` action after the non-destructive playback and
favorite actions and before destructive actions. The sheet completes its hide animation
and dismisses before invoking the ViewModel intent, so the shared modern dialog is not
rendered behind it. Opening an action sheet does not resolve or create an identity.

`SongRatingUiController` owns the active song, lookup-only load, persisted and locally
selected values, loading/saving flags, retryable load/save/clear error categories, and
both reactive rating maps. Opening replaces and cancels any prior
load. A generation plus exact membership-key check prevents a late result for another
song from changing the visible dialog. Closing cancels its load. Compose sends intents
only and never calls Room or launches a dialog coroutine.

The dialog presents title, optional artist, and five separate 48-dp star buttons. Each
button selects one whole-star value from 1 through 5 and exposes its star-count
description; filled and outlined shapes distinguish selected and remaining stars without
depending on color. The group announces the current selection. An unrated song begins
with no selected star and disabled Save. Save validates 1 through 5, delegates to the
repository's exact resolve-or-create path, and closes only after success. Saving an equal
value remains the repository no-op. Clear appears only for a persisted rating, uses the
exact lookup-only delete path, and cannot create an identity. Cancel changes no
persistence. Failures retain the dialog and selection with privacy-safe inline text. At
large font scale or narrow dialog width, actions stack; star targets remain 48 dp and
long metadata ellipsizes.

Room exposes one reactive left-join projection from `song_ratings` to
`local_track_bindings`. The repository converts it into both
`trackIdentityId -> SongRating` and exact `referenceKey -> SongRating` snapshots. The
identity map retains a rating when no current binding exists, allowing an unresolved
historical Top Track to display it. The reference map includes only exact bindings;
defensive grouping omits an ambiguous reference rather than selecting an identity. The
UI collects this projection once at the ViewModel boundary and performs map lookups, so
there is no query or Flow per library or ranking row.

The main Songs list and grid alone show a compact numeric star indicator for rated
songs, with `Rated X out of 5` semantics; unrelated Home, Search, recent/most-played,
queue, playlist, artist-detail, and album-detail rows do not gain rating badges. Top
Tracks uses the identity map for the same non-interactive indicator. Artist and Album
ranking rows remain unrated, and rating invalidation updates Top Tracks without
rerunning or reordering the analytics snapshot.

The Songs header adds a rating-filter menu beside the existing sort control. Its exact
choices are All, Rated, and Unrated, defaulting to All. `LibraryController` retains the
choice for its ViewModel lifetime, including library snapshot publication, but only
`SongsTabContent` applies it. Transformation
order is the existing visible/folder-selected Songs source, text search, rating filter,
then in-memory sort. Changes immediately affect active Rated/Unrated membership, while
excluded or missing songs never enter the playable library.

Rating is available only in the Songs sort menu. It orders 5 through 1, places unrated
songs last, then breaks ties by normalized title and the stable exact membership key.
It returns a new list and keeps metadata-identical songs distinct. Other tabs retain
their own valid selections, so switching tabs preserves the Songs Rating choice without
exposing it to Albums, Artists, Favorites, or other tabs.

Independently editable album/artist ratings, derived album/artist rating summaries,
rating controls in players, half-stars, rating history, minimum/exact-star filters,
rating smart collections, imports, and Wrapped remain deferred.

## Listening Analytics & Ratings v1 final hardening (Session 6)

The completed v1 inventory is Room version 10 identity-owned whole-star ratings;
manual backup schema 8; calendar ranges and bounded trend aggregation; the Statistics
overview, trend, and track/artist/album rankings; Home-header navigation; the shared
rating dialog; Songs badges, Rating sort, and All/Rated/Unrated filtering; and reactive
Top Tracks rating badges. Favorites remain an unrelated boolean collection, and no
album or artist rating value is inferred from song ratings.

Backup export captures favorites, playlists, preferences, canonical identities and all
binding evidence, frozen legacy baselines, finalized events, and ratings. Ratings refer
to backup-local identity IDs and are remapped to newly generated Room IDs during a
full-replacement restore. Validation of history and ratings precedes mutation. The Room
portion of restore—including favorites, playlists, compatibility aggregate history,
canonical history, and ratings—is one transaction; malformed references, duplicate
rating references, values outside 1 through 5, and invalid timestamps roll it back.
Preferences are DataStore-backed and are replaced only after the Room transaction
commits, so the operation cannot provide a cross-storage-engine atomicity guarantee.
Schema 7 restores canonical history with no ratings, while schema 6 and older convert
aggregate rows to baselines without fabricating events or ratings.

There is one ViewModel-owned rating observation for both library and Statistics
surfaces. It returns bulk identity and exact-reference maps; rows perform map lookups
and never open their own Flow or query. A failed observation retains its last successful
maps. Statistics activation owns one conflated Room invalidation observation and one
latest-wins snapshot job. Closing Statistics, removing its host composition, or clearing
the ViewModel cancels that work. Rating-dialog lookup/write jobs are generation-guarded
and cancelled when another song opens or the dialog closes. No polling, `GlobalScope`,
fake refresh event, or per-row coroutine exists.

All analytics aggregation remains in SQLite. A snapshot performs a bounded set of
aggregate queries inside one Room transaction; it does not load listening events into
Kotlin and does not execute SQL per bucket, track, artist, album, or rating row. Trend
input is limited to 400 buckets and 900 bindings, with the current three-source maximum
using 803. Rankings return at most 10 tracks, 5 artists, and 5 albums. The Songs rating
filter and sort are intentionally in memory and remembered by their list, search,
filter, sort, and rating-map inputs. Existing source/date, qualified/date, and
track/date indexes cover the reviewed plans. Some snapshot sections necessarily scan
the selected event range independently; no additional version-10 index removes those
aggregations, and no version-11 index is currently recommended without device profiling
that demonstrates a release-relevant regression.

Accessibility treats Home Statistics, Settings, Statistics Back, coverage Info, range
chips, trend metric chips, and ranking category chips as separate targets with at least
48 dp interaction height. Selected controls expose selected state as well as color.
Overview cards and the bounded chart expose concise combined descriptions; error and
refresh states use live-region semantics. Ranking rows remain non-clickable and announce
rank, metadata, plays, recorded time, and a track rating at most once. An unrated row
does not announce zero. The five stars are separate 48-dp radio-button targets with a
group value and filled/outlined state; dialog errors stay local.

Narrow and large-font layouts use flexible or scrollable controls rather than
device-specific branches. Header actions retain fixed targets while titles ellipsize;
range, trend, and ranking chips scroll horizontally; overview cards switch to one
column; ranking metrics stack; date-picker actions stack; and the rating dialog uses a
full-width bounded surface with smaller narrow-screen padding, stacked actions, and
vertical scrolling. Critical metric values and Save/Clear/Cancel actions remain
reachable at 280-dp-class width and 2x font scale.

Known v1 limitations are deliberate. Legacy aggregate plays have no exact event dates,
durations, completion facts, or source provenance, so they appear only in eligible
All Time counts/rankings. Statistics selection, trend metric, category, and scroll are
retained for the ViewModel/saved Compose lifetime but not fully restored after process
death. Historical Top Tracks do not yet resolve exact current artwork or a safe playback
destination; rows therefore remain text-first and non-clickable. Generated baseline
profiles had obviously stale project-owned playback-history recorder entries removed,
but full profile regeneration remains deferred to the dedicated device journey.

Technical merge readiness requires the focused and full JVM suites, lint, debug and
Android-test packaging, and every milestone-focused connected-test group to pass with a
clean `git diff --check`. Final merge readiness additionally requires the user to
complete real version-8 export/restore replacement validation, upgrade/persistence
checks, S22 Ultra and S9+ UI validation, large-font/landscape/TalkBack checks, and normal
playback, notification, Bluetooth, theme, EQ, lyrics, queue, playlist, scan, and folder
regression checks.

Deferred work remains player rating controls; album/artist rating summaries; half-star
ratings; rating history; exact/minimum-star filters; chart tooltips; full ranking
destinations; Spotify/Last.fm imports; Smart Collections; Wrapped; active-session and
analytics-selection process-death recovery; exact current-song resolution, artwork, and
playback for historical Top Tracks; shareable reports; and cloud synchronization.
