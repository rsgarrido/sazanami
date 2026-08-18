# Listening history import foundation (Room 11 / backup 9)

This document defines the source-neutral persistence contract introduced in Listening History
Import v1, Session 1. It deliberately does not define a file format, Spotify JSON fields, a picker,
preview UI, background worker, reconciliation UI, or a service-specific qualification rule.

## Detailed event semantics

`listening_events` remains the single detailed-history table used by analytics. An event now has
nullable `startedAt` and `endedAt` evidence plus mandatory `attributionAt`. All ranges, trend
buckets, recent ordering, detailed bounds, qualified-play bounds, and time-based rankings use
`attributionAt` exclusively.

`timestampEvidence` distinguishes `native_exact` from `source_end_only`. Native events require an
exact start and end and use their start as attribution. A source-end-only event has no asserted
start, uses the exact source end as both `endedAt` and `attributionAt`, and never derives a start by
subtracting listening time.

Qualification stores both a source-neutral policy owner (`cdplaya`, `spotify`, `lastfm`, or
`other_import`) and an integer policy version. Completion is independently classified as `none`,
`native_natural`, or `source_documented_natural`. The nullable `endReason` column is native-only in
new data; legacy imported rows may retain their old value for compatibility without gaining a
source-natural completion classification.

Publication is `native`, `import_pending`, or `import_published`. Every user-visible history and
analytics query excludes pending rows. Native recording writes `native_exact`, `cdplaya`, `native`,
and either `native_natural` or `none`, preserving the existing qualification thresholds and
playback behavior.

`sourceEventKey` is retained as a legacy compatibility key. The nullable `importBatchId` column is
also retained only to decode Room 10 and backup 8 provenance and is deprecated in Kotlin. New
ownership and deduplication must never depend on either field.

## Import ledger tables

### `listening_import_sources`

One row represents a locally managed external source profile. Its generated `id` is the primary
key; `stableUuid` is unique. `(sourceType, accountIdentityDigest)` is unique when a digest exists;
SQLite permits multiple null digests. Source type and display ordering are indexed. References
from batches and evidence use `NO ACTION`, so profile deletion requires a managed operation. The
table is backed up. It contains a display label and optional one-way account digest, never a raw
username, file location, URI, IP address, platform, device, or user agent.

### `listening_import_batches`

A generated `id` is the primary key and `stableUuid` is unique. The table records lifecycle status,
parser and qualification versions, source range, bounded progress/result counters, failure
category, and an app/schema marker. Source profile, status, and start are indexed. The source
profile foreign key is `NO ACTION`. Only published batches are exported; active worker state is
not. No filename, path, URI, username, raw export, or raw source row is stored.

### `listening_track_external_ids`

A generated `id` is the primary key. `(sourceType, externalId)` is unique and resolves to one
canonical identity; identity and the catalog key are indexed. The identity foreign key cascades
because an external ID has no meaning without its canonical identity. Published durable mappings
are backed up. Profile/account identity is intentionally absent from catalog uniqueness.

### `imported_listening_event_evidence`

`eventId` is both the primary key and a cascading foreign key to `listening_events`, giving an
imported event at most one evidence row. The source profile foreign key is `NO ACTION` and indexed.
`(sourceProfileId, fingerprintVersion, fingerprint, duplicateOrdinal)` is unique and is the future
deduplication contract. Normalized start/end reason diagnostics, tri-state skipped evidence, and
match disposition are retained; raw JSON and personal/device/network fields are not. Evidence for
published events is backed up.

### `listening_import_batch_events`

`(batchId, eventId)` is the composite primary key. Both foreign keys cascade because the row is
only an observation link; a reverse event index supports shared-event checks. Published links are
backed up. This many-to-many ledger lets repeated or overlapping batches observe the same logical
event without making a batch its owner.

## Room 10 to 11 compatibility

The migration explicitly rebuilds `listening_events`. IDs, event UUIDs, local-binding nullability,
source keys, listening values, and native end reasons are copied. Existing rows receive
`attributionAt = startedAt` and `native_exact` because Room 10 asserted exact starts and ends.
Native rows receive the CDPlaya policy, native publication, and native-natural completion only for
the old natural-end reason.

For each non-native source present in Room 10, migration creates one deterministic unscoped legacy
profile with no account digest. Every distinct non-null legacy `(source, importBatchId)` becomes a
deterministic published synthetic batch and its events receive observation links. No modern
fingerprint evidence or real account identity is invented. Legacy imported completion remains
`none`, even when an old native-shaped reason is preserved.

The complete supported migration chain includes 10→11 and remains non-destructive. Room's exported
version-11 schema is committed under `app/schemas`.

## Publication and cleanup transaction

The source-neutral repository transaction verifies that a batch is pending and that both its total
observation count and newly pending event count match caller expectations. In one Room transaction
it publishes only pending events, transitions the batch to published, and writes completion time.
Only the `listening_events` publication update invalidates analytics; batch progress alone is not an
observed analytics table.

Cancellation deletes only unshared pending events, removes that batch's links, and marks the batch
cancelled. A published event shared by another batch is neither hidden nor deleted. Batch deletion
does not cascade to canonical identities. Recovery also removes identities left globally
unreferenced by a prior partial cleanup; local bindings, listening events, legacy baselines, and
ratings each protect an identity, while pending-only external mappings cascade with an otherwise
unreferenced identity.

## Backup 9

Backup schema 9 includes canonical identities, local bindings, baselines, visible detailed events,
ratings, source profiles, published batches, external IDs, published event evidence, and published
batch observations. Event attribution, timestamp evidence, qualification policy/version,
completion classification, publication state, and legacy source keys are serialized. Pending
events and non-published batches are excluded. Identities, external mappings, and source profiles
that exist only because of unfinished pending work are also excluded.

Restore validation runs before replacement mutation. It rejects duplicate profile/batch UUIDs,
duplicate non-null profile digests per source, missing profile/identity/event/batch references,
duplicate external catalog keys, duplicate evidence fingerprint ordinals, duplicate links, pending
rows, incompatible native/import evidence, unsupported enums, and inconsistent timestamp evidence.
Restore remains one Room transaction and remaps identity, binding, profile, batch, and event IDs
before restoring ratings, external IDs, evidence, and observation links.

Backup 8 is upgraded in memory. Native timestamps become native attribution. Source-tagged events
remain source-tagged. Non-null legacy batch IDs create the same kind of unscoped synthetic profile,
published batch, and observation links used by the Room migration. No account digest or fingerprint
is fabricated. Versions 1–7 continue through their existing migration chain before conversion to
9.

## Identity retention

Removing one batch must not delete a canonical identity while it is retained by a rating, current
local binding, native event, another published imported event, or legacy baseline. Import batches
therefore have no cascading path to canonical identities. An external ID is catalog evidence for
an identity, not independent user history; it cascades only when an identity has no durable
supported relationship left.

## Spotify Extended Streaming History v1 behavior

CDPlaya supports one or multiple Spotify Extended Streaming History JSON files. Selection and
parsing are local to the device. Repeating an export, selecting overlapping exports, or importing a
later re-export uses the persisted fingerprint/ordinal evidence so that existing occurrences remain
single events and only genuinely new occurrences are published. Published imported events join the
same Statistics overview, date ranges, All Time bounds, trends, Top Tracks, Top Artists, Top Albums,
listening-time totals, qualification counts, and completion counts as native detailed history.
Backup 9 exports and restores that published history, its identity ratings, provider mappings, and
deduplication evidence.

An imported historical identity is valid without a local track binding. It can appear in Statistics
from its metadata snapshot, but it is omitted from playable Recently Played and Most Played library
surfaces until an actual local binding exists. CDPlaya never fabricates a playable `Song` from
Spotify metadata.

The simpler Spotify Account Data streaming-history format is not supported. Podcasts and
audiobooks are not imported as music history. Last.fm and Stats.fm are not providers in v1. Identity
reconciliation is conservative: there is no fuzzy matching or merge UI, and URI-less records are
not guaranteed to reuse an identity across distinct occurrences or exports. Active file selections
and imports cannot resume after process death; the next entry cleans unfinished pending work before
allowing a new import.

## Session 6 scale and recovery validation

All device measurements below were collected on the connected Samsung S22 Ultra (`SM-S908U1`).
No S9+ scale run was performed. Inputs were deterministic synthetic Spotify JSON generated into the
test app cache and removed after each test; no large export fixture or database is committed.

The unchanged production executor baseline imported 10,000 events reusing 100 Spotify track IDs in
6.135 seconds through 20 transactions of 500 occurrences. The final 100,000-event high-reuse test
used 100 track IDs and 100,000 distinct fingerprints. It published exactly 100,000 events through
200 transactions in 58.573 seconds: 12.509 seconds analysis, 44.539 seconds persistence, and 1.525
seconds publication. The complete All Time snapshot, including trend and Top Tracks/Artists/Albums,
took 0.750 seconds. Backup 9 export took 10.679 seconds and contained exactly 100,000 visible events,
100,000 evidence rows, and 100,000 observations. The database occupied approximately 91.9 MB.

Re-importing the same 100,000-record export added zero events and observed all 100,000 existing
occurrences in 31.996 seconds. A deterministic 120,000-record later export then observed the same
100,000 and added exactly 20,000 events in 52.116 seconds. The many-to-many batch ledger therefore
grew as designed without duplicating events, identities, external mappings, or evidence.

A separate 100,000-event high-cardinality import created exactly 100,000 identities and 100,000
external mappings, with zero pending rows. It completed in 103.543 seconds (12.793 seconds analysis,
89.173 seconds persistence, 1.576 seconds publication); overview and ranking queries took 1.750
seconds and the database occupied approximately 132.4 MB.

Production preview/planning completed for 500,000 distinct fingerprints in 71.266 seconds. Sampled
managed-heap use peaked at approximately 154.8 MB with a 268.4 MB test-process heap limit. The
selection algorithm remains O(distinct fingerprints); no constant-memory claim is made and no spill
fallback was added. A 300,000-record, three-file overlap test (100,000 records per file) selected
exactly 140,000 union occurrences and suppressed 160,000 overlaps in 2.593 seconds, preserving
max-per-file multiplicity.

The optional 500,000-event high-reuse Room import also completed. It published 500,000 events using
100 identities and 1,000 transactions in 352.161 seconds: 60.728 seconds analysis, 259.061 seconds
persistence, and 32.371 seconds atomic publication. The post-import overview query took 0.399
seconds and the database occupied approximately 470.2 MB. This validates that shape on the S22
Ultra only; it is a stress ceiling, not a guarantee for every device or every 500,000-identity
dataset.

Cancellation injected after 40 committed chunks (20,000 pending events from a 100,000-record input)
removed all pending events, evidence, observations, pending-only external mappings, and pending-only
identities in 0.627 seconds. A caught persistence failure injected at the same boundary exercised
the failed-batch path against another 100,000-record input; it removed all 20,000 pending events and
their dependent state in 0.635 seconds while retaining a safe failed-batch record. A process-death
simulation created three stale batches containing 20,000, 10,000, and 5,000 pending events, plus a
shared published event and rating. One hundred
pending rows were deleted first to simulate an interrupted prior cleanup. The original recovery
left 100 orphan identities/mappings; Session 6 added the schema-neutral unreferenced-identity sweep
described above. After that hardening, cleanup completed in 1.936 seconds, preserved published
history and its rating, and a second cleanup was a 6 ms no-op. Cleanup failure remains a blocked,
retryable controller state without exposing exception text.

Backup creation pages database event reads in groups of 1,000 but the final Backup 9 model and its
converted event list are retained in memory for serialization, so backup memory remains O(visible
history). The successful 100,000-event export found no release-blocking memory issue; 500,000-event
backup was not attempted. Restore remains chunked at 500 rows and was covered by the existing
Backup 9 round-trip suite rather than repeated at 100,000 events in this session.

Known v1 boundaries remain unchanged: URI-less occurrences can fragment into separate historical
identities; interrupted imports are cleaned rather than resumed; no background import service is
provided; and the `trackdone` plus `skipped=false` natural-completion interpretation still awaits
confirmation against a current real Spotify `Read Me First` document.
