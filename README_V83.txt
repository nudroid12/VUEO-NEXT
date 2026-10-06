VUEO Mobile Home Poster Flick / Retained State Fix v83

Scope: MOBILE ONLY
Base: apply after v82 (and prior cumulative patches)
No TV files changed.
No shared-core files changed.
No Gradle build performed, per project workflow.

Changes:
1. Retain Mobile Home catalog snapshot across tab/page navigation.
2. Do not re-run Home catalog discovery on every return when the same contentVersion is already loaded and cache is still fresh.
3. Refresh again when contentVersion changes or Home cache is stale.
4. During a real refresh, merge partial fresh rows over the previous snapshot instead of replacing the whole Home with whichever provider finishes first.
5. Preserve featured carousel selection by stable media key (type:id) across Home recreation and catalog partial updates.
6. Re-throw CancellationException so a navigation-cancelled refresh is not incorrectly marked as completed.

Expected result:
Returning from another page to Home should keep the same hero/poster instead of briefly switching to a different provider's poster and then switching back.
