VUEO — Library History (Mobile + TV)
Baseline: supplied VUEO-NEXT-main (56).zip

INSTALL
Upload mobile/, tv/ and shared/ from this ZIP to the VUEO repository root through GitHub, replacing the matching files. Build and install the APK with the existing workflow. No YML/workflow changes are included.

BEHAVIOR
Library now has Favorites and History selectors on both platforms. Favorites remains the default on Mobile; TV remembers its last selected section when returning from details. Existing Grid/List controls apply to either section. Select a card to open details.
History uses LibraryStore.history() with the existing active-profile and kids filtering. Most recently watched titles appear first, once per media type/ID, with the latest episode/progress caption. The original per-episode history is retained; this is only a display projection. Unknown duration shows watched minutes when available.
The local Favorites/History snapshot is read on Dispatchers.IO when Library opens/refreshes. No network requests, new history database, polling, or changes to how playback writes history. TV selector navigation supports Left/Right between sections and the view mode, Down to the first poster, Up back to the selected section, and sidebar return. Existing poster/list detail actions are retained.

FILES
MobileLibrary.kt — section selection, existing data read, history captions/empty states.
TvLibraryScreen.kt — section selection, data read, captions and focus routing.
LibraryHistoryPresentation.kt — tiny shared sorting/deduplication/caption projection.

VALIDATION
11 Kotlin/JVM checks passed using the actual MediaItem and extracted unchanged LibraryPlaybackEntry declaration: newest-first, latest episode per title, input unchanged, episode/progress labels, season zero, empty history, unknown duration, neutral label, bounded progress, separate media types and projection of supplied entries only. Kotlin syntax parsing passed for all three modified/new production files.
Full Android release APK assembly and physical Mobile/TV navigation testing were not run in this environment. Use the existing GitHub build, then check switching sections in both Grid and List, opening details and returning, profile switching, and the empty-history state.
