VUEO v128 — TV Details → Home Retained Layer + Performance Trace
=================================================================

Scope
-----
TV only. Mobile and shared core behavior are unchanged.

Problem confirmed by the user's Performance Diagnostics log
-------------------------------------------------------------
The Back action was not waiting 5–8 seconds before changing route. The log showed
TV HOME was recorded first, followed by an approximately 6.2 second UI stall / frame
gap. That means the expensive path was after navigation had already returned Home.

Changes
-------
1. Retain Home composition for direct Home → Details sessions.
   - Root AnimatedContent continues displaying HOME while Details is presented as a
     dedicated top layer.
   - Back from that Details layer removes Details and reveals the existing Home tree.
   - Home rows, hero state, scroll state and retained focus tree are not recreated for
     this direct Home ↔ Details round trip.
   - Search/Library-origin Details retain the existing route architecture.

2. Keep v124 hard-detach semantics on Back.
   - Details still cancels its startup job before outer navigation.
   - selectedMedia / selected episode / resume payload are cleared on final Details Back.
   - The retained Home path does not keep an outgoing heavy Details composition alive
     for a reverse animation.

3. Preserve the v117 forward motion feel for the retained Details layer.
   - Retained Home Details enters with the same small 0.990 → 1.0 depth scale and a
     short 175 ms fade.
   - Final Back prioritizes immediate Home reveal instead of retaining Details for an
     exit animation.

4. Focus ownership for retained Home.
   - Home BackHandler is disabled while Details covers Home.
   - Any Home poster action dialog is dismissed when Home becomes inactive.
   - When Details closes, Home requests its existing focus-restorer target on the next
     frame, preserving the remembered row/card rather than reinitializing Home.
   - The root focus group now wraps both the base route and retained Details overlay so
     modal focus save/restore still sees the active Details tree.

5. Targeted Performance Diagnostics markers.
   Full/Details/Home raw logs can now correlate:
   - DETAIL_CANCEL_REQUEST
   - DETAIL_CANCEL_SIGNALLED
   - DETAIL_BACK_REQUEST
   - DETAIL_ROUTE_RETURN
   - DETAIL_RETAINED_ATTACH
   - DETAIL_DETACHED
   - HOME_COMPOSE_ENTER / HOME_COMPOSE_DISPOSE
   - HOME_ACTIVE
   - HOME_REVEALED
   - HOME_FIRST_FRAME
   - HOME_FOCUS_READY

Expected diagnostic signature after the fix
-------------------------------------------
For a direct Home → Details → Back test, HOME_COMPOSE_DISPOSE should NOT appear between
opening and closing Details. HOME_REVEALED and HOME_FIRST_FRAME should be adjacent in
time instead of being separated by the previous multi-second stall.

Files changed
-------------
- tv/src/main/java/com/vueo/tv/ui/app/VueoTvApp.kt
- tv/src/main/java/com/vueo/tv/ui/detail/TvDetailScreen.kt
- tv/src/main/java/com/vueo/tv/ui/home/TvHomeScreen.kt

Validation
----------
- Static Kotlin delimiter balance: PASS
- Duplicate import check: PASS
- Kotlin compiler parser smoke: PASS (no parser/syntax diagnostics; Android/Compose
  unresolved symbols are expected without the Android Gradle classpath)
- Changed-file scope audit: PASS
- Overlay simulation against v127 baseline: PASS
- No Gradle build was run, per project instruction.
