VUEO — Library History Delete / Reset (Mobile + TV)
Based on VUEO-NEXT-main (56), including the Library History UI added in the prior patch.

INSTALL
Upload mobile/, tv/ and shared/ from this ZIP to the VUEO repository root using GitHub. Replace matching files, then build/install the APK with the existing workflow. No YML/workflow changes. Provider/player subtitle files are not included.

USE
Open Library > History. Mobile: long-press a poster or list row. TV: hold OK on a poster or list row. Choose Delete history. The confirmation is only:
Delete history?
Cancel | Delete
Cancel changes nothing. A normal click continues opening details.

RESET SCOPE
Delete resets the title's viewing state for the profile that opened the action. For series, every locally stored episode is cleared, not just the episode shown on the History card. It removes history, Continue Watching/title cursors, the Home Continue Watching cache, marked-watched and dismissed-Continue-Watching markers. Playback resume positions/durations and in-process cursors are cleared even if the episode has fallen out of the bounded recent-history list.
Favorites, media metadata and other profiles/titles are retained. A new watch after deletion records progress normally.
Timestamp guards prevent previously queued playback/history saves from recreating the old state. The existing Trakt watched=false action queues the title's history removal when connected/history sync is enabled. A profile/title cutoff rejects old Trakt imports while allowing genuinely newer viewing after the reset. The existing VUEO sync machinery observes the local history/cursor/marker removals; online delivery to other devices still follows existing sync settings and connectivity.
A minimal local deletion timestamp remains as bookkeeping to reject stale writes/imports; it is not watch history.

VALIDATION
20 JVM checks passed against the actual LibraryStore, PlaybackStore, MediaModels, ContinueWatchingPolicy and LibraryEpisodeCache production sources. Android preferences/profile/Trakt/metadata/codec dependencies use isolated fixtures (no real accounts/network). Checks include all-episode deletion, Home cache removal, watched/dismissed flags, Favorites preservation, other title/profile safety, cursors outside history and memory-only cursors, stale queued writes, stale Trakt show/movie imports, removal hook, fresh viewing, repeat deletion and pinned profile targeting.
Kotlin syntax parsing passed for the five modified production files; the unchanged history presentation helper is included as a dependency. Full APK assembly, real cloud/Trakt integration and physical Mobile/TV interaction tests were not run here. Use the existing GitHub build and test long-press > Delete history > Cancel, then repeat with Delete and reopen the title.
