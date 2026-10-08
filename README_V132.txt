VUEO v132 - TV Home Focus + Deterministic Vertical Navigation

Scope: TV Home only.

Changes
1. Home return focus
   - Every fresh Home composition starts at first row / first card.
   - Retained Details -> Home also resets to first row / first card.
   - Sidebar close still returns to current Home content and does not force a reset.

2. Deterministic vertical D-pad navigation
   - Up/Down is intercepted at the Home row level.
   - Navigation moves exactly one adjacent row at a time.
   - Current horizontal card index is carried to the target row when possible.
   - Deep rows can no longer spatial-search directly into the floating navigation bar.
   - Floating navigation is reachable only by pressing Up from the first row.
   - Down on the final row stays in Home content.

3. Vertical smoothness
   - Removed the custom frame-by-frame row distance estimator.
   - Row alignment now uses LazyColumn native animateScrollToItem().
   - Vertical composition cache increased from 232dp to 520dp so adjacent rows are ready during held D-pad navigation.
   - Cached rows still do not eagerly load poster images unless visible.

Unchanged
- Horizontal Left/Right behavior.
- Home data/catalog/Continue Watching logic.
- Retained Details architecture from v128/v129.
- Search, Library, Sources, Player, subtitles, Mobile.
- GitHub workflows.

Validation
- Static delimiter/import/whitespace checks: PASS
- Kotlin parser smoke: no syntax/parser errors found; Android/Compose symbols are unresolved without project classpath as expected.
- Patch overlay simulation: PASS
- Gradle build: NOT RUN (project rule).
