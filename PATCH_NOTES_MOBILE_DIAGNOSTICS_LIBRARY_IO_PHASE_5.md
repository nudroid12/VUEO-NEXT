# Mobile diagnostics/Library IO — audit phase 5

Extract/upload at the VUEO-NEXT repository root after the previous patches. This ZIP replaces MobileProviderDiagnostics.kt and MobileLibrary.kt. Earlier image, player, Home and addon-reload changes are in other files and remain intact. No YML/workflows, dependencies or APK are included.

## Diagnostics

- Performance/Provider Diagnose preview summaries and raw previews are generated on Dispatchers.Default, including the first load. Runtime/crash log reads remain on IO.
- Search/block filtering and AnnotatedString styling now run on Dispatchers.Default through cancellable produceState jobs. Text consumes the prepared immutable result on Main. The preview remains bounded by the existing display policy; full Copy and Save are unaffected.
- Both diagnostics dialogs pause their recurring 1.25-second refresh while the host Activity is below STARTED and refresh when it becomes visible again. Disposing the dialog removes its lifecycle observer and cancels its composition jobs.
- Refresh no longer forces the reader to the bottom. Changing tab, search or raw/summary mode explicitly resets the reading position. A rolling bounded raw preview can still replace old content; this is pixel-position preservation rather than event-key anchoring.
- Full Copy text is assembled on IO with raw/summary mode and selected tab captured at click time. Clipboard and Toast operations remain on Main. Existing Save construction remains on IO.
- Clear work runs in background and restarts the preview effect, cancelling older in-flight preview reads. Recording enable/disable keeps its existing behavior.

## Library

- Watchlist JSON reading, sorting and media parsing run on IO instead of synchronous composition.
- A version update retains the current profile's displayed list until the replacement is ready. A profile/Kids-policy change clears the old presentation immediately and starts a fresh load.
- The provided store follows the active profile; profile IDs are checked before/after the read, and effect cancellation prevents a superseded load from publishing into the new composition.
- Initial loading has a progress indicator instead of incorrectly reporting an empty Library. A failed initial read offers Retry. Truly empty Favorites retain the existing empty state.
- This change adds no Library polling or network requests. The existing version/profile keys trigger reads.

## Validation

- Kotlin PSI syntax parsing passed for both replacement files.
- Reviewed remaining preview/export/style call sites, the settings toggle callback, clear/reload cancellation, lifecycle observer removal and the Library profile/version keys.
- No Android build, emulator or device test was run: this environment lacks Android SDK, and the supplied source lacks the Gradle wrapper JAR. Syntax validation does not type-check all Android/Compose APIs or measure real frame performance.

## Device checks after building

1. Enable diagnostics and open Runtime, Performance and Provider Diagnose. Switch summary/raw and tabs, type/delete a search and scroll while events arrive.
2. Confirm refresh does not force the view to the tail; check Copy returns the full selected log and Save produces the expected ZIP.
3. Clear with recording on/off, then rapidly change tabs/query; confirm the latest mode wins without crashes or stale long-lived output.
4. Background/lock with a dialog open, then return. Confirm refresh resumes and controls work. Repeat closing/reopening the dialogs.
5. Open Library with many Favorites, change grid/list, add/remove a Favorite, and return. Check initial loading, final empty state and refresh behavior.
6. Switch between standard/Kids profiles and age limits during navigation. Confirm Favorites remain filtered and another profile's entries are not displayed.
7. Compare frame/runtime logs under the same workload before claiming a measured improvement. This patch makes thread placement changes; no numeric speedup is claimed.
