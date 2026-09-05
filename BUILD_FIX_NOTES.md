# VUEO TV 29A-R Build Fix

Fixes the Kotlin compilation failures captured in GitHub Actions run logs uploaded after 29A-R:

- Cross-module smart-cast failure for `LibraryPlaybackEntry.season` / `episode` in `VueoTvApp.kt`.
- `BoxWithConstraints.maxHeight` implicit receiver errors in `TvHomeScreen.kt`.
- Restores the profile PIN entry overlay as part of the explicitly preserved profile flow, resolving `TvPinEntryOverlay` and the secondary lambda/key inference errors in `TvProfilePickerScreen.kt`.

This patch does not restore legacy TV Home/Search/Library/Settings/Details/Player architecture.
