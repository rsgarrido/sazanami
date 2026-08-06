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
does not cascade to canonical identities.

## Backup 9

Backup schema 9 includes canonical identities, local bindings, baselines, visible detailed events,
ratings, source profiles, published batches, external IDs, published event evidence, and published
batch observations. Event attribution, timestamp evidence, qualification policy/version,
completion classification, publication state, and legacy source keys are serialized. Pending
events and non-published batches are excluded.

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
local binding, native event, another published imported event, external ID, legacy baseline, or any
other durable supported relationship. Import batches therefore have no cascading path to canonical
identities. A future managed garbage-collection operation must check every durable relationship;
batch deletion and identity GC are intentionally deferred.
