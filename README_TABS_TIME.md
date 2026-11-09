# TV source tab cleanup and scan timer

Apply after Discovery Integrity Fix. Five source files, including preceding status/integrity fixes.

Provider chip labels contain names only. Pending/running providers remain visible with their clock/spinner. Finished/cancelled/skipped providers without any currently playable source disappear. Providers with playable cached/current links remain visible. Existing All/finished/running/waiting ordering retained.

Left progress text now appends elapsed/total seconds from the actual plugin-runtime scan-budget boundary, using SystemClock.elapsedRealtime and the runtime-supplied budget (not a second hardcoded UI budget). UI updates every 250 ms without provider work or network calls. It clamps elapsed to budget and disappears after runtime budget work/cleanup concludes. Cache/coalesced calls with no local budget do not invent a timer. This timer measures the plugin execution scan-budget interval, not the entire addon/TMDB/preflight/translation pipeline. Per-provider timeouts remain separate.

Source wiring and ZIP integrity checked. No Android build, Kotlin execution or TV render test run in this environment. No timeout, concurrency, provider or subtitle behavior changes.
