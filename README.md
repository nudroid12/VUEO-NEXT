VUEO TV Details Back + Library Header Parity v118

Base: v117 Motion Cross-Transition
Scope: TV only. No Mobile or shared-core changes.
No Gradle build.

Fixes
1. TV Details immediate-Back cancellation
   - TvDetailScreen now receives the live route-active state.
   - The Details startup LaunchedEffect is keyed by active state, so switching away from Details cancels core metadata and all child enrichment/library/artwork/recommendation/rating jobs immediately instead of waiting for AnimatedContent's outgoing tree to dispose.
   - DNA work also stops when Details is no longer active.
   - Existing v117 reverse cross-transition remains unchanged.
   - TV source discovery is already route-owned in VueoTvApp and is stopped whenever the route is not SOURCE; no duplicate discovery cancellation path was added to Details.

2. TV Library header parity with Search
   - Library top header padding changed from 82dp to 46dp, matching Search.
   - Left/right content alignment remains the same shared sidebar-aware start padding and 52dp end padding.
   - Library title typography now matches Search: 30sp Bold.
   - Grid/List control stays in the Library header and keeps its existing D-pad behavior.

Unchanged
- Mobile
- shared/core
- resume/continue-watching
- subtitle pipeline
- v117 motion timings
