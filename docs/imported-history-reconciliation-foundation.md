# Imported history reconciliation foundation

CDPlaya stores imported-history reconciliation as a durable, non-destructive relationship from a
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
they conflict; Session 1 does not choose or persist an effective canonical rating.

Backup 10 validates identity existence, non-self links, source uniqueness, disjoint source/target
roles, historical source evidence, local target binding evidence, and non-negative timestamps before
atomic replacement. A missing-marked binding remains valid local evidence. Backup 9 migrates to
canonical format 2 with an empty reconciliation list; no identity relationship is inferred.

Session 1 intentionally changes no analytics presentation. Current Statistics may continue to show
source and target separately.

## Candidate discovery

Session 2 adds a provider-neutral, request-scoped candidate-discovery service. It reads all eligible
historical sources and active local targets in two batched Room queries, then performs matching in a
pure Kotlin engine. Candidate discovery operates once per `ListeningTrackIdentity`, not once per
event. It is transient and adds no Room entity, column, suggestion record, rejection record, or
backup data.

A reviewable source must have at least one non-CDPlaya event whose publication state is
`import_published`, must have no local binding, and must not be a confirmed reconciliation source.
The query groups published imported events per identity and returns provider presence, imported
event count, qualified play count, total recorded listening time, completed count, first listen,
last listen, and stable external-ID presence. Pending imported events and native events do not make
an identity reviewable and do not contribute to these metrics. The reconciliation table, rather
than metadata resemblance, is authoritative for excluding already linked sources.

An eligible target must have a currently active (`missingSince IS NULL`) persisted local binding and
must not be a reconciliation source. Historical-only and unavailable identities are not offered as
new targets. One deterministic active binding is projected per identity. The compact projection
contains identity and binding IDs, durable reference key, title, artist, album, album artist,
duration, display name, derived extension, and normalized safe relative-folder text. It never
returns an absolute filesystem path or duplicates a complete `Song` object.

## Search normalization and evidence

Candidate search deliberately has multiple representations:

1. The conservative key applies Unicode NFC, `Locale.ROOT` lowercase, trim, and whitespace
   collapse. A unique title/artist/album result in this tier is `STRONG_METADATA`.
2. The typography key additionally equates curly and straight apostrophes and common Unicode dash
   characters.
3. The accent key decomposes Unicode and removes combining marks.
4. The bounded punctuation key removes periods only when they occur between letters and normalizes
   whitespace around `#`.

The weaker forms are lookup aids only and produce `TYPOGRAPHY_VARIANT`, never identity proof.
Original source and target strings remain in the result and are never rewritten. These functions
are separate from portable song identity, Spotify fingerprinting, imported evidence, and persisted
normalized identity fields.

Each candidate explains its title, artist, and album relation (`EXACT`, `NORMALIZED`, `MISSING`,
`DIFFERENT`, or `VERSION_VARIANT`), its missing source fields, its version relation, and its
category. Implemented candidate categories are:

- `STRONG_METADATA`: one unique conservative title/artist/album candidate with compatible version
  evidence.
- `TYPOGRAPHY_VARIANT`: one candidate found only through typography, accent, or bounded punctuation
  normalization.
- `INCOMPLETE_EVIDENCE`: one bounded candidate found with missing source album or artist evidence.
- `VERSION_SENSITIVE`: one candidate shares the conservative artist and base title but version
  markers differ.
- `AMBIGUOUS`: multiple plausible targets, or a bounded result whose bucket contains more targets.

Item disposition is independently `SUGGESTED`, `AMBIGUOUS`, or `NO_CANDIDATE`. No category is
auto-link authority. There is no selected candidate field, confidence percentage, or implicit first
match.

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

Candidate lists are capped at eight. `hasMoreCandidates` indicates truncation or an intentionally
suppressed over-broad title-only bucket. Candidates sort by normalized title, artist, album, and
identity ID. The review queue sorts strong, typography, incomplete, and version-sensitive
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

The existing library search UI's `filterSongsForSearch` can filter the current in-memory `Song`
list by title, artist, and album for a later manual “Choose another” surface. Session 2 does not
couple that Compose/UI helper to the domain service or alter global search.

## Session boundaries

No candidate result creates a reconciliation link. The candidate service has no dependency on the
mutating reconciliation repository and performs only SELECT operations. It does not change events,
ratings, external IDs, fingerprints/evidence, metadata snapshots, local bindings, or confirmed
links.

- No metadata-derived automatic linking.
- No canonical Statistics aggregation yet.
- No reconciliation UI yet.
- No Smart Playlists work yet.
