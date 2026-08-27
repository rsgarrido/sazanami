# Metadata Editing v2 - Session 1 foundation audit

## Existing architecture

- The Compose entry point is `TagEditorScreen`, opened from `MusicScreen`. Its current UI model is
  `EditableSongTags` (title, artist, album, track number and year); artwork selection is carried as
  a separate Android `Uri`.
- `MusicViewModel` delegates reads and writes to `TagEditorRepository`. Text and artwork writes use
  jaudiotagger 3.0.1 and mutate the tag object read from the file. The app does not have native/JNI
  tag code; native code is limited to offline waveform decoding.
- `TagEditorActions` requests MediaStore write permission on Android 11+, writes on the IO
  dispatcher, calls `MediaScannerConnection.scanFile`, waits briefly, and then asks
  `LibraryController` to rescan and reconcile saved song references.
- The library index is a MediaStore query. Before this session, title, artist, album, track and year
  always came from MediaStore; only artwork was enriched directly from the file. Missing MediaStore
  strings fell back to `Unknown Title`, `Unknown Artist`, and `Unknown Album` (Android can itself
  expose the filename as WAV title).
- Artwork reading first uses `MediaMetadataRetriever`, then jaudiotagger through a descriptor or a
  temporary local copy, and caches the extracted image behind `EmbeddedArtworkProvider`. Artwork
  writing optimizes the selected image to JPEG, replaces only artwork fields, and now verifies its
  hash after rereading.
- Failures are returned to the editor as `TagEditorResult`; exceptions and Android linkage errors
  do not crash the editor.

## Writable formats

The app now claims only extensions for which this jaudiotagger build registers a writer:

- MP3
- FLAC
- M4A / MP4
- OGG Vorbis
- WAV
- AIFF (`.aif`, `.aiff`)

`.opus` was removed from the editing allow-list because jaudiotagger 3.0.1 does not register an
OPUS extension reader/writer. The dependency also has writers for a few formats the app does not
currently expose (for example WMA, M4B/M4P, OGA, AIFC and DSF); this session does not broaden the UI
claim to those formats.

## Normalized model

`AudioMetadata` is the format-independent embedded model. It represents title, multiple artists,
album, album artist, track/disc numbers and totals, date, genre, composer, comment, publisher,
copyright, BPM, and artwork. `EmbeddedMetadataReader` is the jaudiotagger adapter. The existing
five-field Compose model is derived from it, so advanced fields can be added later without exposing
ID3 frames, Vorbis comments, MP4 atoms, or RIFF identifiers to the UI.

## WAV findings and policy

jaudiotagger reads both RIFF `LIST/INFO` and ID3-in-WAV into `WavTag`. Its default active-tag rule is
ID3 when ID3 exists, otherwise INFO. Its default save rule writes both chunks. CDPlaya previously
changed only the active tag before invoking that save rule. For an untagged WAV this produced an
edited ID3 chunk and a newly written empty INFO chunk; for a dual-tag WAV it could leave stale INFO.
MediaStore/framework readers can prefer or expose INFO and therefore continued to return filename /
unknown fallbacks even though CDPlaya and another ID3-aware editor could see the edit.

The policy is now:

1. Direct WAV reads use ID3 values first and fill only missing fields from INFO.
2. Reading/scanning never synchronizes or writes conflicting representations.
3. MediaStore remains the source of file discovery and base library rows. Valid directly parsed WAV
   values override its title, artist, album, album artist, track, and year; missing embedded values
   retain MediaStore fallbacks.
4. An explicit user edit writes only fields that changed. A changed field supported by INFO is
   written to both ID3 and INFO; ID3-only fields and artwork remain in ID3. This is intentional
   field-level dual writing, not whole-tag synchronization.
5. The file is reread and every changed text field (in both applicable WAV representations) and any
   selected artwork are verified before success is reported.

jaudiotagger's WAV reader records all RIFF chunks. Its writer removes/replaces metadata chunks while
shifting the remaining bytes, so `data`, `bext`, iXML, and unknown chunks are intended to survive.
It also retains unrecognized INFO tuples. It rejects writes when chunk data is malformed. This is
covered with a generated minimal PCM WAV containing an unknown chunk, but real BWF/iXML files still
need device validation.

## Preservation and safety

Text saves calculate a field patch against the current embedded/editor values and mutate only those
keys. Clearing track/year deletes only those fields. Unexposed fields, custom tags, ReplayGain,
artwork, and unknown frames/comments remain on the tag object; artwork is touched only when the user
selects replacement artwork. A FLAC tag-level regression explicitly verifies ReplayGain survives a
genre mutation.

jaudiotagger provides format-specific writers. Several use temporary-file strategies, but its WAV
writer is an in-place RIFF chunk editor. CDPlaya now pre-reads and post-write rereads/validates, but it
cannot claim an application-level atomic replacement for WAV under Android scoped-storage grants.
The dependency is designed to preserve unrelated chunks and refuses known-corrupt chunk layouts;
nevertheless, interruption during the dependency's in-place WAV write remains a limitation to test
on representative storage providers. No audio payload is decoded or re-encoded by this path.

Likewise, preservation of arbitrary malformed tags or every vendor-specific construct cannot be
guaranteed beyond jaudiotagger's parser/writer support. The app intentionally does not claim universal
compatibility with all WAV readers. jaudiotagger can also normalize/remove duplicate WAV metadata
chunks while saving; those duplicate metadata chunks are not covered by the preservation guarantee.

## Session 2 implications

- Build advanced fields on `AudioMetadata` and field patches, not on jaudiotagger classes.
- Define per-field clearing and multi-value artist semantics before exposing advanced fields.
- Keep WAV INFO capability checks in the adapter; fields such as artwork/BPM that INFO cannot hold
  must remain ID3-only.
- Do not infer batch transactions from the single-file writer. Batch behavior needs separate UX,
  progress, cancellation, and partial-failure design.
