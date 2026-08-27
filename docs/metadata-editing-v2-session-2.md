# Metadata Editing v2 - Session 2 advanced single-track editing

## UI scope

The existing `TagEditorScreen` remains the only editor. Title, Artist, Album, Track number, Date/year,
and artwork retain their existing placement. A full-width `Advanced metadata` control is collapsed
by default and expands inline within the existing scrolling form.

The expanded section contains:

- Album artist
- Track total
- Disc number and disc total
- Genre
- Composer
- Comment
- Publisher / Label
- Copyright
- BPM

No batch or multi-track workflow is introduced.

The existing year field remains mapped through jaudiotagger's cross-format `FieldKey.YEAR`, which
maps to date-style fields such as Vorbis `DATE` and MP4 day metadata. Its label is now `Date / year`
and it accepts text so a supported full date is not artificially restricted to a numeric keyboard.

## Normalized fields and capabilities

`AudioMetadata` now retains lists for Artist, Album Artist, Genre, and Composer instead of reducing
those fields to the first value. The UI model carries format capabilities supplied by the metadata
layer. MP3/ID3, FLAC/Vorbis comments, M4A/MP4, OGG Vorbis, WAV, and AIFF/ID3 all support the exposed
fields through jaudiotagger 3.0.1. Unknown formats expose no advanced capabilities. Compose only
asks the capability object whether a field is supported; it contains no extension or tag-format
checks.

Publisher / Label maps to jaudiotagger's `FieldKey.RECORD_LABEL`. BPM maps to `FieldKey.BPM` and is
manual metadata only. CDPlaya accepts a blank value or a whole number from 1 through 999; it does
not perform BPM analysis.

## Multi-value policy

Artist, Album Artist, Genre, and Composer are multi-value fields.

- The editor displays multiple embedded values joined with `; `.
- Merely opening the editor, changing another field, or changing artwork does not write these
  fields, so their original on-disk field/value representation remains untouched.
- When the user explicitly edits one of these fields, semicolons delimit values. Each segment is
  trimmed and empty segments are discarded.
- An explicit edit replaces only that metadata key's complete value set: the old key is removed,
  the first parsed value is set, and remaining values are added separately through jaudiotagger.
- A literal semicolon cannot be escaped in this simple editor. Files containing a literal semicolon
  are preserved exactly until that particular field is explicitly edited.

This policy avoids flattening multiple Vorbis comments or repeated ID3/MP4 values during unrelated
edits while keeping the UI approachable.

## Clearing and no-op behavior

Every field follows the same changed-field rule:

- Untouched blank state creates no edit and deletes nothing.
- A field changed from nonblank to blank produces a clear operation for that field only.
- A changed multi-value field whose parsed list is empty clears only that field.
- An empty patch with no selected artwork returns `No metadata changes to save.` without invoking
  jaudiotagger or changing the file.

Track/disc number-total fields continue to use jaudiotagger's paired-number handling, so changing or
clearing a total does not reconstruct the other half of an MP4/ID3 number pair.

## WAV behavior

The Session 1 read precedence and preservation rules remain unchanged. Explicit advanced WAV edits
are routed as follows:

- Genre, Composer, Comment, Publisher / Label, and Copyright are written to ID3 and RIFF INFO because
  jaudiotagger's `WavInfoTag` has defined mappings for them.
- BPM, track total, and disc fields are written to WAV ID3 only when INFO has no clean equivalent in
  the current library. Album artist is dual-written because jaudiotagger defines a RIFF INFO mapping.
- Unchanged rich ID3 fields and conflicting untouched INFO fields are not synchronized.
- Artwork remains ID3-only.
- `data`, `bext`, iXML, and unknown RIFF chunks remain under the Session 1 preservation policy.

## Session 3 implications

- Batch edits should reuse `MetadataTextEdit`, including its explicit empty-list clear operation.
- Batch UI must distinguish untouched fields from explicit clears; blank alone is insufficient.
- Multi-value batch fields should reuse the semicolon parsing policy or deliberately introduce a
  richer editor with migration rules.
- Capability intersection must be calculated across selected tracks before presenting batch fields.
- Single-file writes can be individually verified, but they do not imply a multi-file transaction or
  rollback guarantee.
