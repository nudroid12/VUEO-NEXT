VUEO Subtitle Workspace + Details/Library Polish v116
======================================================
Baseline: v115
Scope: Mobile + TV UI/focus reliability, Mobile Details back responsiveness, Library cleanup
Shared core: UNCHANGED
Gradle build: NOT run (per VUEO project rule)

User-visible changes
--------------------
1. Subtitle refresh control moved into Languages
   - Mobile + TV subtitle Refresh is now an icon-only Sync action inside the Languages card.
   - Removed the old top-header Refresh pill/button.
   - Refresh status text remains under the Subtitles title.
   - TV D-pad focus keeps Refresh as a stable focus target while refreshing.
   - TV focus path: Languages/Off -> Up to Refresh; Refresh -> Down to Off; Right moves to the
     active subtitle track when a track column is available.

2. Mobile Details immediate-Back responsiveness
   - Details startup LaunchedEffect is now keyed by screen activity, so pressing Back while
     metadata/enrichment is still loading cancels that work immediately instead of waiting for
     AnimatedContent to dispose the outgoing Details tree.
   - In-flight local-library refresh/source-discovery work is invalidated/cancelled on exit.
   - Details -> Root uses a dedicated reverse transition with no 72ms incoming delay.
   - Normal forward navigation and Player transitions are unchanged.

3. Library header alignment + Cloud removal
   - Mobile Library header now owns the same 20dp left/right + 24dp top positioning pattern as Search.
   - Mobile list-level padding no longer shifts header/content as one block.
   - Cloud/My List selector row and Cloud empty state are removed from Mobile.
   - TV Cloud selector/empty state are removed as well; Library exposes My List directly.
   - TV Grid/List control moves into the Library header and focus restoration now targets it when
     there is no remembered poster.

4. Details My List state feedback
   - Mobile active My List icon is now an explicit Check instead of the less-obvious library glyph.
   - Mobile + TV keep an optimistic per-title watchlist override so a late startup hydration snapshot
     cannot repaint the button back to its pre-click state.
   - Add/remove still persists through the existing shared LibraryStore and triggers the normal
     library refresh callback.

Shared-core change
------------------
None. This patch changes Mobile/TV presentation and navigation behavior only.

Not changed
-----------
- Continue Watching/resume cursor rules.
- Player source/audio/subtitle discovery semantics.
- SmartSubs translation/retry behavior from v115.
- Next Episode prefetch rules.
- Subtitle live-style/reselect reliability behavior from v115.

Static validation
-----------------
- Kotlin lexical/delimiter balance: PASS on all 8 changed Kotlin files.
- Duplicate imports: PASS.
- Trailing whitespace: PASS.
- Patch overlay simulation over v115: PASS.
- Patch root/layout check: PASS (mobile/ + tv/ + README, no wrapper folder).
- Shared core unchanged.
- No Gradle build executed.
