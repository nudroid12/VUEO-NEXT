VUEO v130 — TV Continue Watching Fast Hydration
================================================

Base
----
v129 TV Retained Browsing Parents for Details.

Scope
-----
TV Home startup/presentation plus shared LibraryStore read helpers.
Mobile behavior is unchanged. Player, Sources, subtitles, Watch Next and the
v128/v129 retained Details architecture are unchanged.

Goal
----
Keep the fast Home first paint introduced earlier while making Continue
Watching appear as soon as local playback cursors are available, instead of
waiting for Home catalog streaming and the full library/recommendation pass.

Root cause
----------
Before v130, the Home library/presentation coroutine was keyed by `catalogRows`.
During startup, `runtime.homeRows()` can publish several partial catalog lists.
Each partial list changed that key, cancelling/restarting the coroutine that was
parsing LibraryStore. Continue Watching could therefore be postponed until the
catalog stream became stable even though the local playback data itself was
already on disk.

Changes
-------
1. Decouple library hydration from catalog streaming.
   - Continue Watching hydration is keyed by profile/refresh/library revision,
     not by `catalogRows`.
   - Partial catalog publishes can no longer restart local CW parsing.

2. Add a fast cursor-only first stage.
   - `LibraryStore.fastContinueWatching()` reads the dedicated per-title CW
     cursors plus hidden/marked-watched keys only.
   - In-progress movie/series cursors can be published immediately.
   - Completed series cursors are deliberately deferred to the exact pass so
     next-episode resolution never guesses without full history.

3. Add an exact Home-only snapshot.
   - `LibraryStore.homeSnapshot()` reads History + dedicated CW cursors once.
   - It preserves the existing ContinueWatchingPolicy semantics, including
     latest actively watched episode and completed-series next released episode.
   - It skips watchlist parsing because Home does not need watchlist data for
     Continue Watching/recommendation hydration.

4. Split Home presentation stages.
   - cached/catalog rows remain paintable immediately;
   - fast CW can appear independently;
   - exact CW + History follows;
   - personalized For You/Because You Watched scoring remains a later
     background step and no longer blocks CW visibility.

5. Diagnostics.
   Added markers:
   - HOME_CW_FAST_BEGIN
   - HOME_CW_FAST_PUBLISHED count=...
   - HOME_CW_FULL_BEGIN
   - HOME_CW_FULL_PUBLISHED count=... history=...
   - HOME_CW_FIRST_FRAME count=...

Expected behavior
-----------------
Cold/warm TV app startup should keep the existing fast Home first frame. If an
in-progress CW cursor exists, the Continue Watching row should appear shortly
after that first frame even while catalog providers continue loading.

A completed series may appear only after HOME_CW_FULL_PUBLISHED because full
history is required to select the correct next released unwatched episode.

Files changed
-------------
- shared/core/src/main/java/com/vueo/shared/core/storage/LibraryStore.kt
- tv/src/main/java/com/vueo/tv/ui/home/TvHomeScreen.kt

Validation
----------
- No Gradle build was run, per project instruction.
