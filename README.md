VUEO TV Diagnostics Focus + Tab Layout v119

Base: v118 TV Details Back + Library Header
Scope: TV only. No Mobile or shared-core changes.
No Gradle build.

Fixes
1. Performance Diagnostics category tabs
   - Full/Home/Search/Details/Player/Sources/Episodes/Library/Settings/Provider/System/Other now share the full available row width instead of using a natural-width horizontal strip.
   - Tab spacing tightened from 6dp to 4dp and label size raised from 11sp to 12sp.
   - Explicit left/right D-pad neighbors keep category navigation deterministic.

2. Crash + Performance Diagnostics focus graph
   - Search Down now goes directly to the bottom action bar (Copy first), never into the log viewer.
   - The search clear (×) control follows the same Down -> bottom-bar behavior.
   - Log viewer can be entered from the bottom action bar only: Up on Copy/Save/Clear/Close enters the log.
   - While the log has focus, Up/Down scroll the log and do not escape at either scroll boundary.
   - Left or Right inside the log exits upward to Search.
   - This behavior is identical in Crash Diagnostics and Performance Diagnostics.

Unchanged
- Diagnostics collection/export logic
- Mobile diagnostics UI
- shared/core diagnostics
- v117/v118 motion and Details behavior
