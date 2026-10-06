VUEO TV Responsiveness / Main-Thread Cleanup v89

Purpose
- Reduce TV remote/input latency and UI jank found during the v88 audit.
- Keep provider concurrency unchanged for now; first remove avoidable main-thread work.

TV-specific changes
1. Settings navigation
   - 90 ms category-focus debounce so rapidly traversed categories are not composed one-by-one.
   - Right navigation still selects/opens the focused category immediately.
   - Cancels stale panel focus-restore jobs.
2. Profile Settings
   - History/watchlist JSON parsing and User DNA analysis moved off the Compose/main thread.
   - Reuses one history/watchlist read for DNA instead of reading them twice.
3. TV Player
   - Transient buffering/seeking no longer writes full Library history when isPlaying briefly becomes false.
   - Deliberate pause and first-frame persistence write Library history on IO.
   - 400 ms progress poll stops rebuilding audio/subtitle track choices after preferences are restored.
4. TV image cache
   - Removes per-poster LruCache.snapshot() copies.
   - Uses an O(1) weak preview index keyed by image URL.
5. TV Performance Diagnostics viewer
   - Uses a bounded recent preview while the dialog is open.
   - Full 5,000-event export is built only for Copy/Save.
   - UI preview refresh reduced to every 1.5 s while recording.

Shared-core changes (used by TV + Mobile)
1. PluginStore repository descriptors are cached against the raw SharedPreferences JSON.
2. PluginHealthStore parsed health records are cached and invalidated on write/remove.
3. SourceDiscoveryEngine:
   - source-cache cleaning and provider planning are shifted to Dispatchers.Default;
   - configured plugin providers are computed once per scan and reused;
   - repeated cleanFresh() calls reuse the previous result when inputs did not change;
   - diagnostic sanitizing regexes are precompiled.
4. PerformanceDiagnostics adds a bounded preview API. Mobile behavior/output is otherwise unchanged.

Not changed
- Provider/QuickJS concurrency limits remain 3 on low-memory / 4 otherwise.
- Player source ranking/selection behavior is unchanged.
- TV transition, skip/focus, seek behavior, subtitle layout and next-episode logic are unchanged.
- No Gradle build was run.

Checks
- Kotlin parser/syntax pass via kotlinc error scan on every modified Kotlin file.
- Diff/whitespace and overlay-path checks performed.
