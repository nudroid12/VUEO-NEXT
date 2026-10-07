VUEO v124 - TV Details Back Hard-Detach + Metadata Cancellation Fix

Base expectation: v123 Home First-Paint + TV Header, with prior v120-v122 patches.
Scope: TV Details return path + shared metadata cancellation. Shared fix benefits Mobile too.
No Gradle build.

Problem
- Returning from TV Details to Home could still take roughly 5-8 seconds when Back was pressed soon after opening Details.
- Waiting on Details first made Back fast.

Remaining causes found
1. TV root return kept selectedMedia alive while AnimatedContent retained the outgoing DETAIL route.
   That meant the complete Details presentation (episodes/cast/artwork/focus work) could stay mounted during the return handoff.
2. UnifiedMediaEngine.loadMeta() wrapped addon metadata requests in runCatching around the 8,000 ms primary timeout and 4,000 ms fallback timeout.
   Cancellation must never be converted into an ordinary metadata failure/fallback path.
3. The TV Details file is made self-contained here with the earlier off-Main/cancellation work, so this patch does not rely on that behavior being present from another overlay.

Changes
- TvDetailScreen
  * Back cancels the active Detail startup Job before navigation.
  * Outgoing Details hard-detaches when active=false instead of composing the full presentation during AnimatedContent retention.
  * Core metadata, identity preparation, artwork/enrichment/ratings and remote related work stay off Main.
  * CancellationException is always rethrown.
  * Generation/session guard prevents late results from repainting an abandoned Detail.

- VueoTvApp.closeDetail()
  * On root return (no nested Detail back-stack), clears selectedMedia, selected episode/library entry and resume cursor in the same state update as route return.
  * Nested Details back-stack behavior is unchanged.

- UnifiedMediaEngine.loadMeta()
  * Primary and fallback addon metadata requests now explicitly rethrow CancellationException.
  * Added ensureActive() checkpoints before merge/fallback continuation.
  * Existing 8s primary and 4s fallback network timeouts remain unchanged for genuine network failures; they simply no longer own Back navigation.

Unchanged
- v117 motion timings
- Home layout / v123 first-paint behavior
- v122 Newest | Popular entity results
- v120 Next Episode handoff logic
- source selection/provider ranking

Validation
- Kotlin parser smoke: PASS
- duplicate imports: PASS
- trailing whitespace/tabs: PASS
- overlay simulation: PASS
- Gradle build: NOT RUN
