# TV provider execution status and tab order

Five production files. Apply to the confirmed VUEO baseline with the subsequent subtitle/UI patches; this ZIP contains no subtitle or addon-address files.

Runtime emits an execution callback only after acquiring the existing provider semaphore, and clears it from finally, including cancellation. Discovery snapshots expose runningProviders separately from the existing loadingProviders (unfinished/queued plus running). Stremio source-request start/end activity also updates active status; Stremio addon requests are not limited by the plugin semaphore. Existing progress counters retain their original meaning.

TV: All first; completed tabs next (source count shown, including 0), active slot holders next (spinner), then unfinished providers without a slot (static clock icon). Stable sorting preserves prior provider order inside each group. Stable chip IDs/focus requesters retained. Completed zero-result tabs remain visible to avoid disappearing between the execution-end and result updates. Cached sources alone do not mark an unfinished provider as completed. Preflight and queued providers use the waiting indicator.

The runtime concurrency remains 3 on low-memory devices, 4 otherwise. No timeout or scan-budget changes. Active means holding an execution slot, not necessarily an HTTP request in flight (a provider can be parsing or awaiting its response).

Validation: source wiring inspected from semaphore callback through discovery and TV chips; callback in finally, stable tab keys and unchanged concurrency/budgets checked. Schedule icon dependency already exists in TV build. Kotlin/Gradle compilation and TV remote-navigation tests NOT RUN: compiler/dependencies unavailable here.

Device checks: scan more providers than available slots; verify plugin spinners match occupied slots, queued tabs show clocks, completed tabs move ahead with counts, focused selected provider remains the same identity during reordering. Test zero results, errors, Stop, rescan, cached scans and episode changes.
