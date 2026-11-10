# Entity catalog ordering and focus stability
For Cast/Production/Network TITLE RESULTS screens, based on repo65 plus Entity Tabs Grid Focus Fix. Replaces the previous entity-screen patch. Only TvEntityResultsScreen.kt changes. White selected Newest/Popular, no tick, and focus-driven tab selection retained.

Ordering is prepared off Main. New list and sort-reset request are published together; requestScrollToItem overrides lazy-grid key anchoring at remeasure. Removes independent pre-sort scrolling. Down waits for preparation and focuses the current viewport's first item, without scroll-to-zero. Grid focus is tracked so background result batches do not reorder a catalog during browsing; pending results are prepared on leaving the grid. Automatic focus restoration does not run while the grid already has focus. Failed focus and sort publication have ENTITY_RESULTS diagnostics.

Apply CHANGES.diff to repo65 OR AFTER_PREVIOUS_PATCH.diff after the previous entity-screen patch, not both. Alternatively copy included production file. Unrelated player/Home/backend patches unchanged. This file serves ACTOR, COMPANY and NETWORK.

Validation: scoped source checks, both patch baselines and ZIP CRC. No Android build/emulator/device validation available. Verify Popular/Newest first posters before and after Down, rapid tab switches + Down, Left/Right and grid scrolling, return from details, and same flows for company/network.
