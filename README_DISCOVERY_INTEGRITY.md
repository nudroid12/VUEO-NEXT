# Discovery integrity fixes

Apply over VUEO-TV-Provider-Execution-Status-Patch.zip. Five production source files are replaced; no subtitle, addon address, playback, timeout, concurrency or provider script changes.

1. Runtime status callbacks enqueue events without publishing UI while holding an execution slot. Observer exceptions (including error reporting) are contained; actual coroutine cancellation still propagates. Progress observers and source snapshot UI observers are also protected. Final source ingestion still consumes the returned discovery result.
2. Planned provider keys and actual runtime targets now call one shared eligibleDiscoveryTargets function, preserving runtime eligibility/type/platform/filter rules and existing health/adaptive ordering. Anime-capable targets no longer disappear from the preliminary configured list through the old narrower type filter.
3. Only returned provider diagnostics mark plugin providers completed. Budget/Stop/cancellation leaves unresolved providers Cancelled; mapping skips or unavailable discovery results leave unresolved providers Skipped. Skipped/Cancelled do not become fake completed zero-result entries. Terminal sets are included in progressive and both final snapshots. Existing native source request events still mark attempted addon results completed; outstanding addon requests are marked cancelled when source collection closes.

TV tab order: All, completed, running, waiting, then cancelled/skipped. Completed tabs show counts; terminal unchecked tabs show Cancelled/Skipped labels, not a zero implying a successful check. In-flight scans joined by another discovery do not occupy new local execution slots; they remain waiting for the shared result.

Source checks passed: shared eligibility call chain, non-blocking status wiring, observer failure isolation and cancellation propagation, removal of blanket mark-complete, terminal snapshot fields, unchanged 3/4 slot limit and 135/120-second budget, partial-runtime-cache guard retained.

Kotlin lifecycle tests extracted from production methods are included. NOT EXECUTED here (kotlinc unavailable). No full Android build, concurrency integration test or TV navigation test was run. Source checks and ZIP integrity are not substitutes for compilation/device verification.

Device checks: scan many providers through completion and budget expiry; Stop while queued/running; zero-result/error providers; cache hits; coalesced scans; anime-only enabled providers; source selection while results arrive. Confirm already-returned sources remain available and unattempted providers never show completed zero results. Existing diagnostic PROVIDER_BEGIN/END and scan outcome distinguish actual attempts from budget cancellation. Observer failures log DISCOVERY_OBSERVER_ERROR.

Standalone validation with Kotlin installed:
  python3 validation/discovery-integrity/check.py .
