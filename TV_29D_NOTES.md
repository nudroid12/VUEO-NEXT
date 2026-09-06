# TV 29D — Library Rebuild: Mobile Parity + Nuvio TV Reference

Baseline: TV 29C.4 Nuvio-reference sidebar.

## Sources audited before implementation

### VUEO Mobile — canonical Library behavior
Reviewed the stable Mobile `LibraryScreen` implementation in both supplied VUEO full-source snapshots (`VUEO-NEXT-main (11)` and `(13)`); the Library function is byte-for-byte identical between those snapshots.

Mobile Library behavior carried to TV:
- visible saved content comes from `LibraryStore.watchlist()` / **My List**
- top-level Library choices are **My List** and **Cloud**
- Cloud is currently a placeholder, with local My List remaining available
- user can switch **Grid / List** presentation
- Grid/List preference is persisted using `vueo_library_ui` / `grid_view`
- empty My List copy follows Mobile semantics
- Continue Watching and History are **not** visible sections of the canonical Mobile Library screen

Shared LibraryStore may continue to contain playback/history/progress data for Home, Detail, Player and other behavior. 29D does not delete or fork that data; it only stops presenting those as dedicated Library sections.

### Nuvio TV — presentation / remote reference only
Reviewed supplied `NuvioTV-0.8.6-beta` Library sources:
- `ui/screens/library/LibraryScreen.kt`
- `ui/components/PosterCardDefaults.kt`
- `ui/theme/ComponentTokens.kt`
- `ui/theme/MotionFocusTokens.kt`

TV patterns adapted:
- one vertical Library canvas rather than stacked horizontal rails
- primary selectors above content
- responsive poster density based on a target poster width instead of a fixed 8-column rule
- poster focus memory and restore
- focus requester per media key
- poster target width inspired by Nuvio's 126dp token
- restrained ~1.02 focus scale / ~180ms motion

No Nuvio data model, sync runtime, tracking integrations or code dependency is imported into VUEO.

## 29D implementation

- Rebuilt `TvLibraryScreen.kt` from the 29C.4 baseline.
- Removed visible Continue Watching and Recently Watched sections.
- My List is the saved-content surface and still reads Shared Core `LibraryStore.watchlist()`.
- Added My List / Cloud selector row.
- Cloud matches current Mobile behavior: placeholder only.
- Added Grid / List toggle and persisted it using the same Mobile preference name/key.
- Grid is responsive around a Nuvio-reference target poster width and currently settles within a calm 4–6 column range depending on TV dp width; there is no fixed 8-column Library lock.
- List mode uses compact TV rows with poster, title and release/type metadata.
- LEFT from first logical content edge enters the 29C.4 Nuvio-reference sidebar.
- RIGHT from sidebar restores the last Library control/item where possible.
- Returning from Detail restores the last focused media key and scroll position through TV-only ephemeral UI memory. This memory stores no library/media data.
- D-pad activation remains KeyUp-only to preserve the one-OK contract.
- Library top spacing clears the 29C.4 collapsed route pill instead of reintroducing the old permanent rail inset.

## Explicitly not changed

- Mobile UI/source
- Shared Core LibraryStore
- Home Continue Watching behavior
- History/progress recording
- Detail / Source / Player
- 29C.4 global sidebar
- update popup

## Poster-density direction

The previous fixed **8-up** Search density must not be treated as a global TV design rule. Library now uses responsive target-width composition. Search can be re-calibrated separately later without coupling it to Library.
