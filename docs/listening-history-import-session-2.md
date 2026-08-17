# Listening History Import v1 — Session 2 policy and deduplication contract

Session 2 turns normalized Spotify music records into versioned provider-evidence fingerprints and
compares multiplicity-aware occurrence keys with the existing Room 11 import ledger. It does not
create canonical track identities or listening events.

## Evidence basis and Spotify qualification policy v1

Spotify officially states that a song stream is counted after at least 30 seconds of listening in
[How we count streams](https://support.spotify.com/nz/artists/article/how-we-count-streams/).
Spotify's [Understanding your data](https://support.spotify.com/in-en/article/understanding-your-data/)
officially describes Extended Streaming History as including the UTC stream-end time, milliseconds
played, unique Spotify Track URI, start/end reasons, and skipped information. CDPlaya uses those
facts as evidence for its own import policy; it does not claim to reproduce every internal Spotify
counting rule.

The literal reason tokens are a separate evidence category. Spotify's public support page describes
the fields and directs users to the export's `Read Me First - Extended Streaming History` document,
but does not publish a stable token registry on that page. `trackdone` is widely observed in real
exports as the natural-finish end token; examples and other observed values are catalogued in
[Ortham's analysis of an Extended Streaming History export](https://blog.ortham.net/posts/2024-12-21-spotify-streaming-history-part-1/).
CDPlaya treats this token mapping as an observed export convention that should eventually be
checked against a real current `Read Me First` file, not as a permanent public API promise.

`SpotifyImportPolicy` owns qualification policy `spotify`, rule version 1:

- `listenedMs >= 30_000` qualifies by `time_threshold`.
- Exact case-sensitive normalized end token `trackdone` plus `skipped = false` proves
  `source_documented_natural`, qualifies a short naturally completed track, and records
  `natural_end` as the qualification reason.
- `trackdone` plus `skipped = true` or unknown does not prove completion. Duration can still qualify
  the record independently.
- `fwdbtn`, `backbtn`, `endplay`, `unexpected-exit`, unknown, missing, differently cased, and future
  tokens do not prove completion.
- Unknown tokens are retained and never cause policy evaluation to fail.

Reason normalization is intentionally minimal: Unicode-aware surrounding whitespace is trimmed and
an empty result becomes null. Case, punctuation, accents, and internal whitespace are preserved.
The parser's raw evidence remains available on `ImportedListeningRecord`; policy evaluation is pure,
JVM-testable, and never creates a Room entity.

## Spotify provider-evidence fingerprint v1

`SpotifyListeningImportFingerprint` uses fingerprint version 1. The canonical fields, in fixed
order, are:

1. provider enum (`SPOTIFY`);
2. source end epoch seconds;
3. source end nanosecond adjustment;
4. media type enum;
5. stable Spotify external media ID, nullable;
6. fallback provider track title, nullable;
7. fallback provider track/album artist, nullable;
8. fallback provider album title, nullable;
9. fallback provider album artist, nullable;
10. exact validated `listenedMs`;
11. minimally normalized `reason_start`, nullable;
12. minimally normalized `reason_end`, nullable;
13. skipped tri-state (`TRUE`, `FALSE`, or `UNKNOWN`).

When a stable Spotify ID exists, fields 6–9 are encoded as null. Display metadata changes therefore
do not turn the same provider occurrence into a new occurrence. When the ID is absent, fields 6–9
preserve the provider strings exactly: no lowercasing, punctuation removal, accent folding, Unicode
normalization, transliteration, feature/remaster/live stripping, internal whitespace collapsing, or
fuzzy matching occurs. Null and empty remain different. Album artist and track artist are both kept
because the provider-neutral Session 1 record contains both; Spotify currently supplies the same
album-artist value for both.

Epoch seconds plus nanoseconds preserve any fractional precision accepted by `Instant.parse`; no
device timezone or default locale participates. `listenedMs` is never rounded, clamped, or bucketed.
The profile ID is outside the hash and scopes persistence instead. Qualification outcome,
qualification policy/version, and completion classification are also excluded, so a future policy
change does not change event-evidence identity.

Filename, SAF URI, path, source record index, batch UUID, import time, IP address, country, platform,
device, user agent, private/incognito state, username, and raw JSON are excluded.

## Canonical byte encoding and SHA-256

The encoder writes JVM `DataOutputStream` big-endian bytes:

- four-byte ASCII magic `CDPL`;
- unsigned two-byte format/fingerprint version;
- unsigned two-byte field count;
- each field in strictly ascending ID order as an unsigned two-byte field ID and one-byte type;
- type `0` is null with no payload;
- type `1` is UTF-8 with a four-byte signed nonnegative byte length and exact bytes;
- type `2` is an eight-byte signed integer;
- type `3` is a four-byte signed integer.

The encoder rejects missing, extra, or reordered fields. Explicit types and lengths distinguish null
from empty and prevent delimiter ambiguity. Hashing is SHA-256 and the persisted/domain
representation is exactly 64 lowercase hexadecimal characters. Tests pin both a canonical-byte
vector and fingerprint vector
`a683395ff3ddee73cdfa4e8345d4eda9e4dce0bbb78898f5fc7df472d9bf553d`.

Cryptographic SHA-256 collisions are negligible here. Identical legitimate records are a different
problem: their exported evidence is semantically indistinguishable, so multiplicity is represented
with duplicate ordinals rather than collision-resolution data.

## Duplicate ordinals and multi-file selection

Within one source file, the first occurrence of a fingerprint receives ordinal 0, the second 1,
and so on. Another fingerprint starts at 0. `X, X, X` therefore becomes `X/0, X/1, X/2`; none is
discarded merely for sharing evidence.

Selected files are overlapping views of one provider history. For each fingerprint, desired
multiplicity is the maximum count in any selected file, not the sum. Files `X X Y` and `X X Y Z`
produce `X × 2, Y × 1, Z × 1`; three occurrences are reported as overlap suppressed. First-source
fingerprint order is retained deterministically, while ordinal order is always ascending.

`ListeningImportSelectionBuilder` accepts fingerprints incrementally per file. It does not retain
normalized records. It retains one selection maximum-count map plus the active file-count map; for
the first file the active map is adopted directly. Memory is therefore O(distinct fingerprints),
not constant or strictly bounded. A later overlapping file can temporarily require both maps.

## Persistent deduplication and preview facts

An occurrence is already imported only when the existing Room 11 evidence contains the same:

`(sourceProfileId, fingerprintVersion, fingerprint, duplicateOrdinal)`.

The existing composite unique index begins with exactly those columns. Lookups are profile scoped,
version scoped, and use `fingerprint IN (...)` batches of 500 (hard maximum 900), avoiding both a
query per occurrence and SQLite's common bind-variable limit. Only requested ordinal keys are
returned to the planner. No evidence from another provider/profile is loaded as a match.

The planner holds only one lookup chunk and can stream `NEW`/`ALREADY_IMPORTED` decisions to a
callback. Its factual result exposes:

- importable music occurrences across selected files;
- selected music occurrences after max-per-file overlap handling;
- overlapping occurrences suppressed within the selection;
- distinct fingerprints;
- already-imported occurrences;
- new occurrences.

It does not expose or imply identity-match counts. Repeating the same export after its complete
evidence exists produces zero new occurrences. A later export with `X × 3` against persisted
`X/0, X/1` reports only `X/2` as new.

When no provider account identifier is available, the repository can reuse the locally stable
`spotify-default-profile-v1` source profile. Its account digest is null; no username or listening
metadata is invented as account identity. Session 4 can select this profile without requiring
profile-management UI.

Backup schema 9 already serializes source profiles and evidence version/hash/ordinal. After restore,
the same occurrence lookup remains `ALREADY_IMPORTED`; no backup version change is needed.

## Session 2 boundaries

- Room remains version 11 and Backup remains schema 9.
- No local identity reconciliation.
- No imported `ListeningEvent` persistence.
- No external-track-to-local binding.
- No import UI.
- No Session 3 publication, cancellation, or pending-event workflow was added.
