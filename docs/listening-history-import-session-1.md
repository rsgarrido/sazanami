# Listening History Import v1 — Session 1 parser contract

Session 1 adds a non-persistent input pipeline for Spotify Extended Streaming History. It detects
the source structure, incrementally parses an array, classifies each record, normalizes valid music
evidence, and produces a factual preview summary. It does not use Android, SAF, Room, or any
listening-history repository.

## Supported and rejected input

The supported document is a UTF-8 top-level JSON array whose objects use Extended Streaming
History field names. Detection uses structure rather than filenames. The strongest normal
signature is `ts` plus `ms_played`; combinations of Extended-only metadata, URI, reason, and skip
keys are also accepted so one semantically invalid first record does not reject an otherwise valid
file. Up to 20 complete objects are probed. This is a bounded compromise: actual Spotify exports
use one structure throughout, while an arbitrary JSON array with more than 20 unrelated objects
before its first Extended-shaped object is intentionally indeterminate.

The simpler Spotify Account Data shape (`endTime`, `artistName`, `trackName`, `msPlayed`) is
reported as `SPOTIFY_BASIC_ACCOUNT_HISTORY_UNSUPPORTED`. It is never interpreted as Extended
history. An empty array and valid JSON with no supported signature are `UNKNOWN_JSON`, because they
provide no affirmative format evidence. Invalid or truncated JSON is `MALFORMED_JSON`.

Unknown object fields are ignored. Optional fields may be absent or JSON null. Current privacy
fields and observed legacy decrypted privacy fields are therefore tolerated without being declared
on the DTO.

## Normalized evidence

`ImportedListeningRecord` is provider-neutral and contains:

- provider (`SPOTIFY`, with `LASTFM` reserved as a future domain value);
- a provider-stable external media ID without the `spotify:track:` wrapper, or null when music is
  supported only by metadata;
- media type;
- provider display title, artist, album, and album artist;
- optional source start, exact source end, and timestamp evidence;
- listened milliseconds;
- true/false/unknown skip evidence;
- deliberately uninterpreted completion evidence;
- raw nullable provider start/end reason tokens.

Spotify currently exports `master_metadata_album_artist_name`, not a full track-level credit. The
Spotify normalizer supplies that same exact value to both `trackArtist` and `albumArtist` and does
not invent featured artists. Nonblank display strings keep their original spelling, case,
punctuation, Unicode normalization, and surrounding whitespace. Blank structural values are
treated as absent. Blank reasons become null; other reason tokens retain exact case and spelling.

The normalized contract excludes IP addresses, country, platform, user agent, username, incognito
state, source filename, SAF URI, filesystem path, and raw JSON. Those values are neither returned,
logged, nor stored.

## Timestamp and duration policy

Spotify `ts` is parsed with `Instant.parse` as the provider's exact stream-end instant.
`sourceStartedAt` is always null, `sourceEndedAt` is the parsed `ts`, and timestamp evidence is
`SOURCE_END_ONLY`. The parser never subtracts `ms_played` to fabricate a start and never converts
the instant to a device timezone.

The analyzer receives a parser configured with an injectable `Clock`. A timestamp after the clock's
instant is invalid; Session 1 applies no undocumented clock-skew allowance. Missing, malformed,
out-of-range, and future timestamps are separate safe diagnostic reasons.

`ms_played` is provider evidence stored as a `Long`. Null and negative values are invalid. Zero,
positive values, and `Long.MAX_VALUE` are valid. No track-duration cap, play threshold, or
qualification decision is applied.

## Media classification

Valid, correctly typed Spotify URIs outrank descriptive nullable fields. More than one strong URI
type is `AMBIGUOUS_MEDIA_TYPE` rather than an arbitrary classification. A malformed
`spotify_track_uri` is `INVALID_TRACK_URI`; malformed episode, audiobook/chapter, or video URIs are
`INVALID_MEDIA_URI`.

- A valid `spotify:track:<id>` plus credible title and album-artist evidence is music.
- Title plus album-artist metadata without a track URI is music with no fabricated external ID.
- Episode URI or episode metadata is a podcast.
- Audiobook/chapter URI or metadata is an audiobook.
- Explicit `spotify:video:` URI or video title is video.
- A valid record with no reliable media evidence is unknown.

Descriptive fallback prioritizes audiobook, podcast, video, music, then unknown. Thus podcast
metadata is not made into music merely because music-shaped fields also happen to be present.
Non-music records are emitted as unsupported media evidence and counted, not treated as fatal.

## Streaming and resource ownership

`SpotifyExtendedStreamingParser.parse` accepts an `openStream` lambda and a synchronous callback.
The parser owns and closes the returned `InputStream` on completion, file failure, callback-requested
`STOP`, or a thrown callback exception. This makes early termination and future job cancellation
possible without leaking a descriptor.

The implementation removes an optional UTF-8 BOM, inspects the first meaningful byte, and uses
Kotlin serialization 1.9.0 `Json.decodeToSequence<JsonElement>` in explicit `ARRAY_WRAPPED` mode.
Each array element is held only as one `JsonElement` and decoded to the Spotify DTO before it is
normalized. At most the 20 format-probe elements are buffered. No `ListSerializer`, whole-array
DTO list, second parsing pass, or global parser state exists.

If a syntactically malformed document fails after callbacks have already received records, the
file result is fatal and the analyzer may attach safe partial counters. No persistence occurs, so
partial callbacks cannot publish history.

## Errors and preview

File failures distinguish unsupported Account Data, unknown valid JSON, malformed JSON, and an
unreadable stream. Messages are fixed safe text and do not echo source content.

Record failures retain counts by enum reason and at most 20 default examples containing only the
zero-based record index and reason. No raw JSON or metadata is retained in diagnostics. Reasons
cover timestamp, duration, URI, ambiguous media, missing credible music metadata, and invalid DTO
shape failures.

The incremental preview reports total examined records; valid music, podcast, audiobook, video,
unknown, and invalid counts; zero-millisecond music count; earliest/latest valid source end; safe
invalid-reason counts and examples; and a bounded exact unique external music-ID count. Exact ID
tracking defaults to 100,000 distinct IDs. If another distinct ID exceeds that bound, the set is
cleared and the value becomes null. Combining independently analyzed files also makes the unique
count null rather than claiming false cross-file uniqueness.

The preview intentionally does not report qualified plays, completion states, duplicate
fingerprints, duplicate ordinals, already-imported/new records, cross-file overlap, or local-track
matches.

## Fictional fixture inventory

All committed fixtures contain fictional metadata and reserved example IP addresses where needed.

- `spotify_extended_minimal_music.json`: ordinary music, UTC/fractional timestamps, reasons, skip.
- `spotify_extended_full_fields_current.json`: current optional/privacy fields and null media fields.
- `spotify_extended_legacy_decrypted_fields.json`: ignored legacy decrypted privacy fields.
- `spotify_extended_null_and_missing_optional.json`: null/missing values and metadata-only music.
- `spotify_extended_duplicate_records.json`: exact and duplicate-looking rows, all preserved.
- `spotify_extended_overlap_a.json` / `_b.json`: independent files with one overlapping row.
- `spotify_extended_reexport_initial.json` / `_later.json`: old rows repeated plus a new row.
- `spotify_extended_qualification_edges.json`: 0/1/29,999/30,000/30,001 milliseconds, skip states,
  `trackdone`, and an unknown reason token, all uninterpreted.
- `spotify_extended_identity_edges.json`: artist/album/ID differences, remaster/live text, accents,
  composed/decomposed Unicode, featured formatting, missing album/URI, unavailable-style metadata.
- `spotify_extended_non_music.json`: podcast, audiobook, unknown, metadata-only music, video, and
  conflicting strong URI evidence.
- `spotify_extended_timestamp_edges.json`: UTC boundaries, DST-adjacent instants, old/fractional,
  invalid, and injected-clock future data.
- `spotify_extended_invalid_records.json`: missing/invalid time, null/negative duration, bad URI,
  missing music metadata, and ambiguous media.
- `spotify_basic_account_history_unsupported.json`: explicitly rejected Account Data format.
- `spotify_extended_empty_array.json`: valid but structurally indeterminate JSON.
- `spotify_extended_malformed_truncated.json`: intentional file-level syntax failure.
- `spotify_extended_out_of_order.json`: source order preserved while min/max are independent.

`SyntheticSpotifyHistoryGenerator` writes deterministic 10k, 100k, or 500k fictional arrays
directly to an `OutputStream` without constructing DTO lists. It can add predictable podcast and
invalid rows. The 10k and 100k checks run normally; 500k is opt-in with
`-Dspotify.stress500k=true`.

## Hard Session 1 boundary

No persistence. No deduplication. No qualification. No identity reconciliation.

Room remains version 11 and backup remains schema 9. Session 1 does not read or write Room, create
identities/events/evidence, generate fingerprints or duplicate ordinals, apply Spotify's 30-second
policy, interpret `trackdone` as completion, or inspect local songs.
