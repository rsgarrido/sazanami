# Metadata Editing v2 - Session 3 batch planning

## Boundary

Session 3 introduces selection, batch state derivation, capability intersection, and a reviewable
plan. It does not call a metadata writer, iterate over files to save changes, or expose progress,
partial-success, retry, or rollback behavior.

## Selection and identity

The Library Songs header exposes a batch-metadata action when at least two songs are available. It
opens a full-screen checkbox selector based on the existing playlist song-selection interaction.
Selections are keyed by `Song.membershipKey()`, not list positions. The prepared batch target keeps
that durable reference key together with the MediaStore ID and file path for Session 4 resolution.

Metadata is read while the selection is prepared. The batch editor then works exclusively from its
immutable target snapshots; changing library ordering cannot retarget the plan.

## State model

Initial values and user intent are separate:

- `BatchInitialValue.Common(value)` includes common empty values.
- `BatchInitialValue.Mixed` means selected tracks differ.
- `BatchEditIntent.Untouched` means no future write is authorized.
- `BatchEditIntent.Set(value)` is an explicit replacement, including a set-to-empty value.
- `BatchEditIntent.Clear` is an explicit removal operation.

Therefore mixed, empty, untouched, set, and clear cannot collapse into a nullable string. Restoring
an edited field to its original common value collapses back to `Untouched`.

Album, Album Artist, Date/year, Genre, Composer, Comment, Publisher/Label, Copyright, BPM, Disc
number, Disc total, and Artwork are modeled. Title, Track number, and batch writes remain excluded.

## Multi-values

Album Artist, Genre, and Composer use typed `MultiValue(List<String>)` snapshots. Tracks are compared
using those lists, so multiple values inside one track remain distinct from differences between
tracks. Explicit input reuses Session 2's semicolon split, trim, and discard-empty rule.

## Capabilities

Every target carries metadata-layer capabilities. `MetadataFormatCapabilities.intersection()`
retains only fields supported by every selected target. Unsupported fields remain visible but
disabled with an explanation. Session 3 does not offer apply-to-supported-files-only behavior.

## Artwork

Artwork derives as common present, common absent, or mixed. Equality reuses the SHA-256 hash already
encoded in Sazanami embedded-artwork references and falls back to the resolved URI for other artwork
sources, avoiding new image-byte comparisons. Replacement keeps the selected URI as a reviewable
reference; clear is explicit. Neither operation writes artwork in Session 3.

## Session 4 input

`BatchMetadataPlan` contains stable selected targets and only explicit field/artwork changes. Each
change retains the old common-or-mixed state and the new set-or-clear intent for review.

Session 4 must:

- resolve every stable target again before writing and detect missing/replaced files;
- validate set-to-empty separately from explicit clear rather than mapping both to an empty patch;
- freeze and review the plan before execution;
- convert each field operation to the existing single-file typed patch only at execution time;
- re-check per-file capabilities and file identity because selection-time state may be stale;
- define ordering, cancellation, verification, partial success, and rollback policy explicitly.
