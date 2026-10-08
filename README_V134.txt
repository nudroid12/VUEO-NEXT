VUEO v134 - TV Focus Restore + Vertical D-pad + Focus Border + Search Filter Focus

Scope: TV only. Based on the current v133 app state.

Changes
1. Home vertical D-pad
   - Up/Down now targets the exact poster in the adjacent row instead of focusing the LazyRow container.
   - One press maps to one adjacent row.
   - If the target poster is not composed yet, its row/card is brought into composition and the exact FocusRequester is retried across frames.
   - The current horizontal/card column is preserved when possible.

2. Details -> Home focus restore
   - Fresh Home entry still starts at first row / first card.
   - Returning from a retained Details layer no longer increments the Home reset token.
   - Home re-enters its existing focus-restorer boundary, restoring the exact row/card that opened Details.

3. Home Continue Watching focus border
   - Continue Watching LazyRow now reserves vertical focus-paint inset as well as horizontal inset.
   - Prevents the scaled focused card border from being clipped at the top/bottom.

4. Search focus border
   - Search result grid reserves an 8dp focus-paint inset at the top and matching extra bottom allowance.
   - Prevents the scaled first/last visible poster border from being clipped vertically.

5. Search filter -> results focus
   - After a Type/Sort/Genre change, Down to results now scrolls item 0 into composition and retries its exact FocusRequester across frames.
   - The filter only consumes Down when a result-focus handoff is actually scheduled.
   - Pending result-focus jobs are cancelled when another filter change resets the grid.

Not changed
- Continue Watching startup/cache behavior from v133.
- Player, Sources, subtitles, Watch Next, Mobile, shared core.
- GitHub Actions workflows.

Validation
- Delimiter balance: PASS
- Duplicate imports: PASS
- Trailing whitespace: PASS
- Targeted source assertions: PASS
- Overlay simulation: PASS
- No .github/workflows in patch: PASS
- Gradle build: NOT RUN (project rule)
