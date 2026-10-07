VUEO Details Immediate-Back Responsiveness v121

Base: v120 Next Episode Handoff Optimization
Scope: Mobile + TV Details orchestration. No shared-core changes.
No Gradle build.

Goal
Remove the remaining delay when the user opens Details and immediately presses Back. Waiting on Details for a moment already made Back fast; the remaining hitch came from startup metadata/enrichment work still resuming and parsing on Main before Back could be handled.

Changes
1. Core Detail startup moved off Main
   - Mobile TMDB identity preparation now runs on Dispatchers.IO.
   - Mobile core Stremio metadata load + episode normalization now run off Main.
   - TV prepareDetailForCore() + loadCoreDetail() now run off Main.
   - Heavy fallback episode normalization runs on Dispatchers.Default.
   - The visible Detail shell is still published immediately from the catalog/search item.

2. Immediate Detail-session cancellation on Back
   - Mobile and TV retain a reference to the active Detail startup coroutine.
   - Back invalidates the Detail session and cancels that coroutine before outer navigation changes the route.
   - Mobile's visible back button uses the same cancellation path as system Back.
   - Mobile also invalidates library hydration, source discovery and episode prefetch before leaving Details.

3. Late-result protection
   - Each Detail open receives a generation/session token.
   - Library hydration, artwork, metadata, recommendations, enrichment and ratings verify that the session is still current before publishing Compose state.
   - Results from an outgoing or replaced Detail page cannot repaint the retained AnimatedContent tree.

4. Progressive enrichment no longer competes with Back on Main
   - TV artwork, related-title remote work, TMDB/rich enrichment, episode ratings and supplemental ratings run off Main.
   - Mobile local related scoring runs on Dispatchers.Default.
   - Mobile remote recommendations, TMDB/rich enrichment and MDBList requests run on Dispatchers.IO.
   - Compose state publication remains on Main after the background work completes.

5. Cancellation is no longer treated as a normal metadata failure in the Detail orchestration
   - CancellationException is rethrown in Mobile/TV Detail fallback paths.
   - Normal network/provider errors still fall back gracefully.

Unchanged
- v117 cross-transition motion
- Details UI/focus layout
- source selection behavior
- Player / Next Episode logic from v120
- Continue Watching / resume cursor rules
- shared/core
