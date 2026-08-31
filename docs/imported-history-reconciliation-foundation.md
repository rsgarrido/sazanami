# Imported history reconciliation foundation

Sazanami stores imported-history reconciliation as a durable, non-destructive relationship from a
historical listening identity to a canonical local/playable identity. Room 12 adds
`listening_identity_reconciliations`; Backup 10 and canonical listening-history format 2 preserve
the same confirmed links through backup identity IDs and restore-time ID remapping.

The table has one row per source identity: `sourceIdentityId` is the primary key,
`targetIdentityId` has a non-unique index, and `reconciledAt` records the user-action timestamp.
Both identity foreign keys use restrictive deletion behavior. This permits many historical sources
to target one local identity, while preventing one source from targeting multiple identities.

Link creation requires a source identity with published non-native imported history and no local
binding. It requires a target identity with at least one real persisted local binding. A source may
not already be a source or target, and a target may not be a source. The mixed-role prohibition
prevents chains and cycles without recursive graph traversal. Batch creation validates every source
and the target in one Room transaction before inserting any row. Normal `link` never overwrites an
existing link; relinking is explicitly `unlink` followed by a newly confirmed `link`.

Target playability is a creation-time condition. Once a link exists, it remains when a binding is
marked missing or the file is temporarily unavailable. Cleanup treats identities in either link
role as referenced, and restrictive foreign keys are the final safeguard. Unlink removes only the
relationship and allows later cleanup to apply its normal reference rules.

Reconciliation never moves or rewrites listening events, provider external IDs, imported event
fingerprints/evidence, duplicate ordinals, ratings, metadata snapshots, qualification facts,
completion facts, or timestamps. Spotify external IDs and dedupe evidence remain owned by the
historical identity. Source and target ratings remain independent and survive link/unlink even when
they conflict. Canonical presentation uses the target's rating without copying it to or deleting it
from the historical source.

Backup 10 validates identity existence, non-self links, source uniqueness, disjoint source/target
roles, historical source evidence, local target binding evidence, and non-negative timestamps before
atomic replacement. A missing-marked binding remains valid local evidence. Backup 9 migrates to
canonical format 2 with an empty reconciliation list; no identity relationship is inferred.

Statistics canonicalizes confirmed sources to their targets before grouping, ranking, and limiting.

## Candidate discovery

Session 2 adds a provider-neutral, request-scoped candidate-discovery service. It reads all eligible
historical sources and active local targets in two batched Room queries, then performs matching in a
pure Kotlin engine. Candidate discovery operates once per `ListeningTrackIdentity`, not once per
event. It is transient and adds no Room entity, column, suggestion record, rejection record, or
backup data.

A reviewable source must have at least one non-native event whose publication state is
`import_published`, must have no local binding, and must not be a confirmed reconciliation source.
The query groups published imported events per identity and returns provider presence, imported
event count, qualified play count, total recorded listening time, completed count, first listen,
last listen, and stable external-ID presence. Pending imported events and native events do not make
an identity reviewable and do not contribute to these metrics. The reconciliation table, rather
than metadata resemblance, is authoritative for excluding already linked sources.

The review UI treats the current `LibraryUiState.songs` collection as the authoritative target
catalog. It includes playable songs that have no listening binding yet and overlays any durable
binding identity while retaining current tags for presentation and matching. Candidate discovery
is read-only: a transient target creates no binding until explicit confirmation. Confirmation
rechecks current library membership and performs binding creation/reactivation plus link creation in
one transaction, so a stale or rejected target cannot leave a partial binding. Historical-only and
unavailable identities are not offered as new targets. The compact projection contains identity and
binding IDs, durable reference key, title, artist, album, album artist, duration, display name,
derived extension, and normalized safe relative-folder text; candidate UI models do not retain
duplicate complete `Song` objects.

## Search normalization and evidence

Candidate search deliberately has multiple comparison-only representations:

1. The conservative key applies `Locale.ROOT` lowercase and edge trimming. A unique
   title/artist/album result in this tier has `EXACT` confidence.
2. The canonical key additionally applies Unicode NFC, collapses repeated/Unicode separator
   whitespace, and equates single- and double-quote variants, common Unicode dash characters,
   ellipsis/three-period forms, BOM/zero-width-space
   artifacts, and known UTF-8/Windows-1252 smart-quote decoding artifacts. A unique complete
   metadata result in this tier has `CANONICAL_EXACT` confidence.
3. The accent key decomposes Unicode and removes combining marks.
4. The bounded punctuation key removes periods only when they occur between letters and normalizes
   whitespace around `#`.
5. A bounded edit-distance lookup may compare a title only after canonical artist and album have
   narrowed the candidate bucket (or canonical artist when imported album metadata is absent).

Accent, bounded-punctuation, and edit-distance forms are lookup aids only and have `FUZZY`
confidence, never deterministic identity proof. Canonical equivalence is deterministic only when
title, artist, and album are present and exactly one plausible local reference remains across all
lookup tiers. Original source and target strings remain in the result and are never rewritten.
These functions are separate from portable song identity, Spotify fingerprinting, imported
evidence, and persisted normalized identity fields.

Each candidate explains its title, artist, and album relation (`EXACT`, `CANONICAL`, `FUZZY`,
`MISSING`, `DIFFERENT`, or `VERSION_VARIANT`), its missing source fields, matched fields, raw and
canonical source/target metadata, candidate count, final confidence, non-deterministic reason,
version relation, and category. Implemented candidate categories are:

- `STRONG_METADATA`: one unique conservative title/artist/album candidate.
- `CANONICAL_METADATA`: one unique safe-canonical title/artist/album candidate.
- `TYPOGRAPHY_VARIANT`: one candidate found only through accent, bounded punctuation, or spelling
  similarity.
- `INCOMPLETE_EVIDENCE`: one bounded candidate found with missing source album or artist evidence.
- `VERSION_SENSITIVE`: one candidate shares the conservative artist and base title but version
  markers differ.
- `AMBIGUOUS`: multiple plausible targets, or a bounded result whose bucket contains more targets.

Item disposition remains independently `SUGGESTED`, `AMBIGUOUS`, or `NO_CANDIDATE`. Final confidence
is `EXACT`, `CANONICAL_EXACT`, `FUZZY`, `AMBIGUOUS`, or `UNMATCHED`; only unique complete `EXACT` and
`CANONICAL_EXACT` items report `isDeterministic`. No category creates a link, and there is no
selected candidate field, confidence percentage, or implicit first match.

## Versions, missing metadata, and ambiguity

Version detection is deterministic and contextual. Parenthetical/bracketed text, dash suffixes,
and conservative terminal forms detect live, remaster/remastered, remix, edit/radio edit, version,
demo, acoustic, mono, stereo, anniversary, deluxe, and session markers. Album names beginning with
`Live` are also protected. Marker words are retained for evidence; they are stripped only from a
temporary base-title lookup used to find a possible `VERSION_SENSITIVE` choice. A leading word in a
normal title such as `Live Wire` is not treated as a version suffix.

A missing album permits title-and-artist lookup. One result is incomplete; multiple local albums or
recording versions are ambiguous. A missing artist permits only a bounded title lookup. A missing or
blank title produces no candidate. Blank fields are treated as missing for lookup without changing
their persisted representation.

Candidate lists are capped at eight. `hasMoreCandidates` indicates truncation, including an
over-broad title-only bucket, and the item remains explicitly ambiguous. Candidates sort by
normalized title, artist, album, and identity ID. The review queue sorts strong, canonical,
typography, incomplete, and version-sensitive
suggestions first, then ambiguous items, then no-candidate items; ties use imported event count,
last-listened time, title, and source identity ID. Hash-map iteration order never reaches the API.

## Fragmentation and performance

URI-less fragments remain separate historical identities. Distinct provider IDs with identical
visible metadata also remain separate. Each source can independently suggest the same local target,
which preserves the Session 1 many-sources-to-one model for a later confirmed `linkMany` action.
Session 2 does not create a transient display bundle because the unbundled result already preserves
all source metrics and deterministic shared-target evidence without implying a merge.

The matcher builds maps for conservative and weaker artist/title/album, artist/title, title-only,
and version-base keys. Work is O(historical identities + local targets + visited bucket contents),
not an imported-by-local nested comparison. Synthetic JVM tests cover 1,000 historical sources
against 5,000 local targets, thousands of unrelated targets, and large common-title buckets.

Manual target search filters the complete current in-memory library by title, artist, and album,
sorts deterministically, and only then applies its 100-result cap. It is the authoritative fallback
when deterministic candidate discovery cannot express a semantic or localized-title relationship.

## Automatic deterministic reconciliation and batch binding

Session 2 uses the matcher result as a strict automatic-link boundary. An unresolved imported
identity is eligible only when its final confidence is `EXACT` or `CANONICAL_EXACT`, its candidate
is unique and untruncated, and the matcher's complete-metadata rules report it deterministic.
`FUZZY`, `AMBIGUOUS`, `UNMATCHED`, incomplete, and version-conflicting results remain manual-review
work. Automatic reconciliation does not select a preferred item from multiple canonical-equivalent
local songs.

Automatic reconciliation runs after a Spotify import has been durably published and after a real
library snapshot publication. The shared reconciler serializes those triggers, reads only unresolved
published imported identities, and is idempotent because confirmed sources are excluded by the
authoritative reconciliation relationship. A later library rescan can therefore resolve a formerly
unmatched identity, while an already manual- or automatically-linked source is not reconsidered or
overwritten. Failures in this post-publication step do not change a successful import into a failed
import.

The local binding service accepts many source-to-current-song requests, resolves each distinct local
reference once, validates reconciliation roles with bulk DAO queries, and inserts all new
relationships in one outer Room transaction. Existing same-target relationships are reported as
already linked; different-target relationships are conflicts and remain unchanged. One commit
coalesces reconciliation-table invalidation for the batch. The result reports requested, newly
linked, already linked, conflicts, and validation failures. The same binding authority handles a
single manual confirmation.

Manual confirmation updates the already-loaded review model from the committed link result instead
of re-querying all historical aggregates and rebuilding all candidates. A full reload remains a
defensive fallback for a malformed result and is still used where unlink semantics require it. An
open review is refreshed once after an external automatic batch adds links. No link path updates,
copies, or duplicates `listening_events`; fifty historical plays owned by one imported identity
still produce one reconciliation relationship.

## Exception-review screen

Session 3 presents the remaining human decisions as `Review`, `Unmatched`, and `Linked`, combined
with independent `Tracks`, `Albums`, and `Artists` browsing modes. Tracks remain the authoritative
identity-level rows. Album keys combine normalized imported artist and album metadata, and artist
keys use normalized imported artist metadata; these groups are transient navigation and summary
projections, not persistence identities or reconciliation targets.

The controller prepares one in-memory presentation row per imported identity after a reconciliation
snapshot loads. Each row contains Unicode-safe search text for imported title, artist, and album,
stable normalized grouping and sort keys, status, a specific display reason, and an optional unique
proposed target. Search, sorting, filtering, and grouping operate on that prepared identity state and
never query or reaggregate listening events per keystroke or per card. Historical plays is the
default stable sort; title, artist, and album sorts use normalized metadata and source identity as a
final tie-breaker.

Review filters distinguish title/punctuation/version formatting, accent/diacritic differences,
similar titles, and ambiguous candidates. These are display classifications only. Punctuation-only
examples such as hyphen/space or trailing-exclamation differences remain explicit review work, and
version wording remains reviewable. The UI does not feed these labels back into deterministic
matching or automatic reconciliation.

Selection is available only for Review rows with exactly one untruncated proposed target. An album
can select its eligible visible review rows, while an artist remains a navigation hierarchy without
a one-tap artist link. `Link selected` sends heterogeneous source-to-target requests through the
Session 2 batch service in one transaction. Clean results update the prepared screen state once;
already-linked, conflict, or failure results cause one authoritative refresh and never overwrite an
existing relationship. Linked and unmatched identities use the same search and grouping projections,
and unlink remains the existing reversible relationship deletion.

## Final v1 behavior

Reconciliation is optional. Unmatched imported identities remain legitimate historical rows and
continue contributing to global Statistics; a user does not need to review an entire provider
archive before it becomes useful. Candidate discovery itself remains read-only. The automatic
orchestrator consumes only deterministic exact/canonical results; every other suggestion still
requires explicit confirmation.

Statistics canonicalizes identity before grouping, ranking, and applying Top-N limits. Top Tracks,
Top Artists, Top Albums, custom historical ranges, and provider-neutral per-playable-track metrics
therefore aggregate all confirmed historical aliases under their local target without multiplying
events. Link, unlink, and explicit relink change grouping only: overview totals, listening time,
attempts, completions, trends, timestamps, and source provenance remain factual and unchanged.
Recently Played and Most Played use the same canonical history but only return a real currently
resolvable local song. They do not synthesize fake playable songs for imported-only or unavailable
targets.

Stable Spotify identity and URI-less history intentionally behave differently:

- A stable Spotify external ID remains owned by its historical identity. Re-importing the same
  export uses unchanged fingerprint v1 evidence and duplicate ordinals, so it inserts no events.
  A later export reuses that same historical identity; genuinely new events automatically flow
  through its existing reconciliation. The identity needs to be reconciled only once.
- URI-less occurrences may create separate historical fragments. Existing linked fragments remain
  independent sources that can share one canonical target. A future fragment is evaluated as a new
  identity and is auto-linked only if it independently satisfies the unique deterministic boundary;
  otherwise it requires explicit reconciliation.

Backup 10 preserves identities, local bindings (including `missingSince`), native and imported
events, source profiles, external IDs, fingerprint evidence, duplicate ordinals, ratings, and
reconciliation links. Validation occurs before atomic replacement. Backup 9 restores without
inventing links and can be reconciled normally afterward.

A confirmed link survives local file disappearance, folder exclusion, or temporary storage
unavailability. Canonical Statistics remain grouped, while playable projections omit the missing
target. When the same durable local song is resolved again, its binding reactivates and playable
projections return without relinking. Cleanup, import cancellation/failure, and stale-pending-batch
recovery protect both sides of every persisted reconciliation.

The canonical per-playable-track metrics API is the provider-neutral bridge intended for a future
Smart Playlist milestone; v1 does not implement Smart Playlist rules or UI.

## Intentional v1 limitations

- Future URI-less occurrences can create new fragments that require additional confirmation.
- Translation, transliteration, romanization, and localized-title equivalence are not inferred.
- Featured-artist and unusual metadata forms may not produce a suggestion.
- Fuzzy, ambiguous, incomplete, version-sensitive, and unmatched items require individual manual
  handling; there is no Match All or batch-approval UI yet.
- Candidate discovery uses deterministic normalization and current local-library metadata, not a
  Spotify API, network lookup, or external catalog.
- Temporarily unavailable local targets remain linked but non-playable until normal library
  resolution succeeds.
- Last.fm import/reconciliation and Smart Playlists remain deferred.
