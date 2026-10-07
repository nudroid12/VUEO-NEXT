VUEO v110 — Mobile Details Entry Responsiveness

Baseline
- Apply after v109.
- Patch paths start at repo root. There is no wrapper directory.

Scope
- Mobile poster/card -> Details responsiveness only.
- TV Details is NOT changed. Its existing progressive shell/background hydration remains intact.
- No transition timing/easing changes.
- No playback, source discovery, next-episode prefetch, subtitle refresh, or diagnostics behavior changes.

What changed
1) First Mobile Details composition is now storage-free
- Removed synchronous User DNA build from first composition.
- Removed synchronous watchlist lookup from first composition.
- Removed synchronous continue-watching/history parsing from first composition.
- Initial Details state uses the navigation payload and optional initialLibraryEntry immediately.

2) Progressive local hydration after the Details shell is visible
- Watchlist, history, dedicated playback cursors and DNA are hydrated asynchronously.
- Library JSON parsing runs on Dispatchers.IO.
- Pure DNA analysis runs on Dispatchers.Default.
- Episode/resume state is updated after hydration without blocking entry into Details.

3) One-pass LibraryStore snapshot for Details
- Added LibraryStore.detailSnapshot().
- It reads watchlist JSON once, history JSON once and continue-watching cursor JSON once.
- The parsed history is reused to derive both continue-watching and playback cursor collections.
- This avoids the old repeated history/watchlist parsing performed by separate public accessors.

4) Async refresh after playback/library updates
- Player library changes now refresh the Details library snapshot asynchronously instead of parsing storage on the UI thread.
- A generation guard prevents an older hydration request from overwriting a newer one.

Shared-core note
- shared/core LibraryStore gains the generic LibraryDetailSnapshot + detailSnapshot() API.
- Only Mobile Details uses this new API in v110.
- No TV source file or TV behavior is changed by this patch.

Expected UX
Before:
  tap poster -> synchronous local JSON/DNA work -> transition/details
After:
  tap poster -> Details shell/transition immediately -> local watchlist/resume/DNA state hydrates in background

Validation performed
- No Gradle build was run.
- Static delimiter/lexical scan: PASS.
- Duplicate-import scan: PASS.
- Trailing-whitespace scan: PASS.
- git diff --check on changed Kotlin files: PASS.
- Heavy synchronous LibraryStore/DNA calls removed from MobileDetails first-composition path: PASS.
- Patch overlay/apply simulation against v109 baseline: PASS.
