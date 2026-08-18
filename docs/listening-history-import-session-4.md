# Listening History Import v1 — Session 4

Session 4 exposes the existing Spotify import pipeline through Settings without changing its
persistence model, fingerprint policy, qualification policy, Room schema, or backup schema.

## User flow

1. **Settings → Listening history → Import listening history** opens a dedicated utility screen.
2. The landing screen identifies **Spotify Extended Streaming History**, explains that Spotify's
   simpler Account Data history is unsupported, and states that processing is local.
3. **Select JSON files** launches an `ACTION_OPEN_DOCUMENT` picker with `CATEGORY_OPENABLE`,
   multi-select, read grants, and practical JSON/text/octet-stream MIME alternatives.
4. Selected documents remain transient in the `MusicViewModel` workflow. The UI shows the count
   and, for small selections, temporary display names. **Analyze** is always explicit.
5. Analysis reparses each stream, produces Session 1 factual counters, applies Session 2
   maximum-per-file multiplicity, and checks persisted evidence for the default Spotify profile.
   It creates no batch and no listening event.
6. Preview shows total records, music, date range, new, already imported, overlap ignored,
   unsupported/non-music, and invalid counts. A zero-new preview ends without executing a batch.
7. **Import history** invokes the Session 3 two-pass executor. Progress maps its real analyzing,
   importing, publishing, and completion phases. The ViewModel scope keeps work alive while the
   Activity is merely paused.
8. **Cancel** cancels the executor coroutine and remains in a cancelling state until Session 3's
   non-cancellable transactional cleanup returns. Back during import requires the same explicit
   cancellation confirmation.
9. Result values come from `ListeningImportExecutionResult`, not the earlier preview. **Done**
   returns to Settings; **Import more** clears transient documents and returns to the landing state.

## State machine

`SpotifyListeningHistoryImportController` owns a single `StateFlow<SpotifyImportUiState>`:

- `Landing → CheckingRecovery → Landing | StaleImportRecovery`
- `Landing | FilesSelected → FilesSelected`
- `FilesSelected → Analyzing → Preview | Error | FilesSelected (cancelled)`
- `Preview → Importing → Success | Error`
- `Importing → Cancelling → Cancelled`
- `StaleImportRecovery → CleaningStaleImport → Landing | StaleImportRecovery`
- `Preview | Error | Cancelled → FilesSelected`
- `Success → Landing` through Import more, or reset while returning to Settings through Done

State type checks block concurrent analysis, concurrent execution, picker changes during active
work, and button double taps.

## SAF and privacy boundary

`SafListeningHistoryImportFile` is the Android adapter for the core
`ListeningImportStreamSource`. Every `openStream()` calls `ContentResolver.openInputStream()`
again, which supports the preview pass and both executor passes without introducing `Uri` into the
parser or executor. A null stream is converted to an I/O failure. Parsers retain stream ownership
and close every opened stream.

URI strings and display names are not written to Room, backup JSON, fingerprints, logs, or
analytics. No raw Spotify JSON is persisted. No import-specific storage/media permission was
added, and no upload or network path exists. True process death discards the active picker and
preview state; v1 intentionally does not persist URI sessions.

## Stale import recovery

On entry, the controller checks pending batches belonging to the stable default Spotify source
profile. Pending IDs are ordered by start time and ID and remain internal. If any are present, the
file picker is blocked behind one recovery screen. Cleanup calls `cancelPendingBatch` for every
unfinished batch and verifies that none remain. This reuses Session 3 cleanup so unshared pending
events, their evidence links, and unreferenced identities are removed while published/shared
history remains unchanged. There is no resume path.

## Error mapping

- Spotify Account Data → specific Extended Streaming History guidance
- unknown/empty JSON → unsupported Spotify Extended Streaming History message
- malformed/truncated JSON → valid-JSON message
- null, inaccessible, or failed reopen → reselect-files message
- valid selection with no music → informational no-music message
- executor/persistence failure → retry-safe failure message after executor cleanup

A fatal file fails the whole multi-file selection. A temporary display name may identify that file
on screen, but is never persisted.

## Persistent versions

- Room: **11**
- Backup schema: **9**
- Spotify fingerprint: **1**
- Spotify qualification rule: **1**

Session 4 adds only DAO/repository query surface for pending batches; it makes no schema change.
