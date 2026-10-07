VUEO Home First-Paint + TV Header Spacing v123

Base: v122 Entity Newest | Popular
Scope: Mobile + TV startup/Home orchestration, plus TV Search/Library title position.
No shared-core changes. No Gradle build.

Goals
- Reduce non-essential work competing with the first Home render.
- Remove duplicate TV LibraryStore parsing during Home hydration.
- Keep Mobile startup from waiting on every remote Stremio manifest.
- Move only the TV Search/Library title headers slightly inward; all other layout stays unchanged.

Changes
1. TV Home first paint
   - Cached catalog rows are converted to presentation rows immediately in retained state.
   - Home no longer waits for Continue Watching JSON hydration before it can paint cached/catalog content.
   - Continue Watching + History now come from one LibraryStore.detailSnapshot() read instead of continueWatching() followed by history().
   - Recommendation scoring remains on Dispatchers.Default after the local snapshot is available.

2. TV background-work staggering
   - Continue Watching metadata repair starts only after Home has rows, waits for a rendered frame, then settles for 700 ms.
   - Adjacent hero artwork/image prefetch moves from 120 ms to 750 ms.
   - Primary focused hero artwork is unchanged and still loads immediately.
   - Catalog concurrency, addon preparation and source/provider behavior are unchanged.

3. Mobile startup critical path
   - Remote addon manifest installation no longer keeps booting=true.
   - Cache/profile/local setup completes first, then the app can render.
   - Configured manifests continue sequentially in a child coroutine to avoid a parallel network/CPU burst.
   - A single contentVersion refresh is issued after the configured addon set settles, instead of restarting Home once per addon.
   - Home has an addonsPrepared gate so a cache miss stays in the lightweight loading state rather than briefly showing an empty-home state before manifests finish.

4. Mobile Home background metadata
   - Continue Watching metadata repair waits until addons are prepared and Home has content, then starts after a 900 ms settle.
   - Existing retained Home presentation, one-pass LibraryStore.detailSnapshot(), recommendation scoring and catalog caching remain intact.

5. TV header title spacing only
   - Search title: +16 dp start padding.
   - Library title: +16 dp start padding.
   - Search field, filters, Library Grid/List button, cards, content padding and all other UI are unchanged.

Unchanged
- v122 Newest | Popular entity behavior
- v121 Details immediate-back logic (no Details files touched)
- v120 Next Episode handoff behavior
- TV catalog request gate (max 4)
- Home recommendation algorithm
- Search/Library content layout apart from the two title Text positions above
