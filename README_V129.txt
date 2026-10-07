VUEO v129 — TV Retained Browsing Parents for Details
=====================================================

Scope
-----
TV only. Mobile and shared core are unchanged.

Goal
----
Extend the v128 retained Home → Details architecture to the other TV browsing
parents where Back should reveal the exact existing browsing surface instead of
recreating it:

- Search → Details
- Library → Details
- Cast / Production / Network Entity Results → Details

Home → Details remains retained exactly as introduced in v128.

Changes
-------
1. Generalize the v128 retained Details layer.
   - HOME, SEARCH, LIBRARY and ENTITY_RESULTS are now recognized as retained
     Details parent routes.
   - While Details is open, root AnimatedContent keeps the parent route mounted
     and Details is rendered as the top layer.
   - Final Details Back detaches the Details payload and reveals the already
     composed parent.

2. Search → Details.
   - Search query/results/session and LazyGrid position remain alive underneath
     Details.
   - Search BackHandlers are disabled while Details owns the foreground.
   - Result focus restore waits until Search becomes active again, preventing the
     hidden Search screen from stealing focus from Details.

3. Library → Details.
   - Library grid/list tree, scroll position and remembered media target remain
     alive underneath Details.
   - Library BackHandler is disabled while Details is foreground.
   - Existing Library focus restore now runs when Library becomes active again.

4. Entity Results → Details.
   - Cast / Production / Network results remain composed when a title is opened.
   - Newest/Popular state, loaded results and grid state are therefore retained.
   - The focused poster key is remembered and restored when Details closes.
   - Back from a title opened from Entity Results now returns to the same Entity
     Results page instead of discarding the entity page.
   - Back from Entity Results restores the Detail that originally opened that
     entity page, including its prior Detail back stack/return route.

5. Focus ownership.
   - Search, Library and Entity Results accept an `active` flag.
   - Their Back/focus restoration paths are suppressed while a retained Details
     overlay is active.
   - Details remains the sole foreground focus owner until it detaches.

6. Performance Diagnostics markers.
   Added/expanded markers include:
   - DETAIL_RETAINED_ATTACH parent=...
   - DETAIL_DETACHED retainedParent=true parent=...
   - DETAIL_BACK_REQUEST retainedParent=... parent=...
   - DETAIL_ROUTE_RETURN target=... retainedParent=...
   - DETAIL_PARENT_REVEALED parent=...
   - DETAIL_PARENT_FIRST_FRAME parent=...
   - SEARCH_COMPOSE_ENTER / SEARCH_COMPOSE_DISPOSE
   - LIBRARY_COMPOSE_ENTER / LIBRARY_COMPOSE_DISPOSE
   - ENTITY_RESULTS_COMPOSE_ENTER / ENTITY_RESULTS_COMPOSE_DISPOSE

Expected diagnostic signature
-----------------------------
For Search/Library/Entity Results → Details → Back, the corresponding parent
COMPOSE_DISPOSE marker should not appear between opening and closing Details.
DETAIL_PARENT_REVEALED and DETAIL_PARENT_FIRST_FRAME should follow Back closely.

Files changed
-------------
- tv/src/main/java/com/vueo/tv/ui/app/VueoTvApp.kt
- tv/src/main/java/com/vueo/tv/ui/search/TvSearchScreen.kt
- tv/src/main/java/com/vueo/tv/ui/library/TvLibraryScreen.kt
- tv/src/main/java/com/vueo/tv/ui/search/TvEntityResultsScreen.kt

Validation
----------
- Duplicate import check: PASS
- Trailing whitespace check: PASS
- Static delimiter balance: PASS
- Kotlin compiler parser smoke: PASS (no syntax/parser diagnostics; Android/Compose
  unresolved symbols are expected without the Android Gradle classpath)
- Retained-parent source assertions: PASS
- Changed-file scope audit: PASS
- Overlay simulation against v128 baseline: PASS
- No Gradle build was run, per project instruction.
