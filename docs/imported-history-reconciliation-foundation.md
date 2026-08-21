# Imported history reconciliation persistence foundation

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

- No candidate discovery yet.
- No canonical Statistics aggregation yet.
- No reconciliation UI yet.
- No Smart Playlists work yet.
