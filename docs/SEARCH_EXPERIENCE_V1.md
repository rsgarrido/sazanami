# Search Experience v1

Search keeps the existing page shell, search field, selection chrome, mini-player clearance,
navigation, theme, and settings controls. `MusicScreenBody` routes Search to
`LibrarySearchContent`; other library collections continue to use `MusicLibraryContent`.

`LibrarySearchIndex` builds an immutable, normalized snapshot of songs, existing album and
artist groups, and playlists. Snapshot construction and query ranking run on
`Dispatchers.Default`. Library or playlist changes rebuild the snapshot. No query triggers
a scan, database rebuild, or artwork extraction. Results retain their domain entities and
navigation identities. Artwork uses the existing song URI, `ArtistPicture`, and
`PlaylistArtwork` paths.

Ranking is exact title/name, starts-with, word-start, contains, then related metadata.
Song metadata includes artist, album, and album artist; album metadata includes its artists.
Ties use normalized title, category, then stable identity. Case, repeated/surrounding
whitespace, and punctuation separators are normalized without modifying stored metadata.

All groups nonempty categories and previews three results per category. Categories appear
in order of their strongest ranked hit, so an exact artist or album match can precede songs
matching only through metadata. All / Songs / Albums / Artists / Playlists chips focus the
same list; See all opens the complete category. Empty queries show “Search your library”;
unmatched queries and empty category filters have explicit no-results messages.

The shared `SongList` owns selection, selected styling, selection header binding, batch
action bar, and the shared song action sheet. Its optional lazy content slots support
grouped results without nested scrolling. Search enables explicit song overflow buttons
and adds Go to album / Go to artist navigation actions. Existing Play next, queue, named
queue, playlist, favorites, rating, tag-editing, and Home actions are reused. Album and
artist overflow menus also reuse their existing action sheet factories.

Selection is songs only. Query/category changes clear it; Back/cancel follows the existing
selection handler. Non-song row navigation and actions are disabled during selection.
Batch order follows the shared displayed-song ordering policy. Play next, Queue, named
queue, and playlist actions use the existing controllers, dialogs, and mutation paths.
Queue entries, duplicate handling, shuffle/repeat, and playlist ordering are unchanged.

`LibrarySearchIndexTest` covers ranking tiers, normalization, classification and identity,
all category filters, cross-category relevance and section order, idle/no-results,
deterministic ties, snapshot updates, playlist-only libraries, and shared selection
entry/toggle/cancel/batch ordering. Existing controller and action-policy tests exercise
the reused lower-level selection and action infrastructure.

## Automated validation

- Focused Search unit tests passed; the final full suite includes 11 passing Search tests.
- `:app:testDebugUnitTest`: 1,559 tests, 0 failures/errors, 7 skipped.
- `:app:lintDebug`: passed with 0 errors, 162 warnings, and 4 hints.
- `:app:assembleDebug`: passed.
- `git diff --check`: passed; new source/test files also passed whitespace checks.
- `:app:compileDebugAndroidTestKotlin`: failed in untouched queue tests:
  `AddToAnotherQueueDialogTest.kt:5` imports unresolved `assertDoesNotExist`;
  `QueueHubSheetTest.kt` has unresolved `onAllNodesWithText` calls. These existing
  instrumentation test infrastructure errors were left outside the Search milestone.

The Gradle validation tasks ran together with `--continue`, allowing unit tests, lint,
and APK assembly to finish despite the Android test compilation failure. Logs are in
`app/build/search-validation/`.

## Manual device checklist

- Search a term matching several entity types. Check category labels, artwork, three-item
  previews, strongest-match section order, all five chips, and See all.
- Clear the query; try an unmatched query and a category without matches.
- Try an exact song title, artist name, album name, `the`, `best damn`, and case,
  whitespace, or punctuation variants.
- Tap a song and verify playback; open an album, artist, and playlist.
- From a song overflow menu, use Play next, Add to queue, Add to another queue,
  Add to playlist, Go to album, and Go to artist.
- Long-press a song, select multiple results, and verify count and selected styling.
  Try Play next, Queue, a named queue via Selection actions, and Playlist.
- Check that batch insertion follows displayed result order, and that existing queue
  entries (including duplicates) and the playing entry remain correct.
- Cancel with Back and with the selection close button. Change query and category while
  selecting; verify selection is cleared and normal playback taps resume.
- During song selection, verify album/artist/playlist rows cannot open unrelated actions.
- Check album/artist overflow actions and light/dark themes, accents, large text,
  mini-player spacing, and bottom navigation clearance.
- Change library/playlist contents, then repeat the relevant search to verify refresh.

No device or emulator validation was performed. Persistent search history, fuzzy matching,
and mixed-entity selection remain outside this milestone's scope.
