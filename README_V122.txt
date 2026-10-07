VUEO Entity Results Newest + Popular v122
=========================================
Baseline: latest uploaded repo VUEO-NEXT-main (41) / compatible with v121 because v121 touches different Details files.
Scope: Cast + Production Company + Network entity-result pages, Mobile + TV.
Shared core: CHANGED.
Gradle build: NOT run (per VUEO project rule).

Behavior
--------
- Every Cast / Production / Network result page now has two presentation tabs:
  Newest | Popular
- Default tab: Newest.
- Switching tabs is LOCAL ONLY. It does not call TMDB again, rerun addon actor search,
  rediscover providers, reload posters, or create another entity request.
- One fetched/merged master result list is retained; tab changes only reorder that list.

Newest
------
- Orders by release year / first-air year descending.
- Unknown dates go to the bottom.
- If years tie, known TMDB popularity is used as a secondary stable tie-break,
  then the original merged order is preserved.

Popular
-------
- Uses TMDB popularity when present.
- Addon/local-only rows that have no popularity score remain after scored rows and
  preserve their original provider/merge order instead of triggering per-title TMDB lookups.
- This intentionally avoids N additional network calls just to rank addon results.

Shared ranking metadata
-----------------------
- MediaItem gains optional transient `popularity: Double?` at the END of the model so
  existing positional constructors remain source-compatible.
- TMDB actor/company/network discovery now carries popularity into MediaItem.
- Duplicate addon + TMDB rows preserve the highest known popularity score after merge.
- Production/Network TMDB master results are ranked by popularity rather than the old
  rating-first ordering, giving the Popular tab a real popularity baseline.

Mobile
------
- Adds full-width Newest / Popular controls under the entity header.
- Reorders the existing grid immediately from in-memory results.
- Loading/partial results continue to appear progressively and are ordered according to
  the currently selected tab.

TV
--
- Adds Newest / Popular focusable controls beneath the entity title.
- Left/Right moves between tabs.
- Down enters the first poster.
- Up from first-row posters returns to the currently selected sort tab.
- Existing first-poster autofocus is preserved after results arrive.

Performance
-----------
- Tab changes are O(n log n) local list sorting only (entity pages cap results to a small
  browse-sized set).
- No extra network request, JSON fetch, addon discovery, or image reload is introduced by
  changing Newest/Popular.
- Existing single entity discovery request remains the expensive operation.

Static validation
-----------------
- Kotlin lexical/delimiter scan: PASS on all 6 changed Kotlin files.
- Kotlin parser smoke: PASS on all 6 changed Kotlin files.
- Duplicate imports: PASS.
- Whitespace/diff check: PASS.
- Behavior assertions: PASS (sort state is not part of entity-fetch LaunchedEffect keys).
- Overlay simulation over uploaded repo (41): PASS.
- Patch root/layout check: PASS.
- No Gradle build executed.
