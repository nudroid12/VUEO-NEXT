# Entity Results background preparation — Phase 13, TV + Mobile

Apply after Phase 12: extract into the repository root and overwrite matching paths. No workflow/YML files are included. This patch contains both MobileEntityResults.kt and TvEntityResultsScreen.kt plus a shared EntityResultsPipeline helper. It does not replace app roots or Players.

## Evidence
The latest mobile log attributes a 449 ms callback gap to Mobile ENTITY_RESULTS / HOME. The HOME category in the summary does not mean the Home catalog caused it. Code inspection found synchronous ordering in composition on both Mobile and TV, and an entity-loading caller context that could resume JSON parsing, local catalog lookup and merging on Main. The log does not identify which of these operations caused the entire gap, and no equivalent TV device trace was provided.

## Changes
- Fetch entity results through a shared background producer. Existing TMDB/provider suspend calls, post-response parsing, local catalog lookup and merge work now start/resume on a per-request serialized Default dispatcher. Suspended network calls can still overlap; existing coroutine-owned partial accumulation retains single-caller sequencing.
- Deliver immutable list snapshots through a conflated channel. Intermediate pending batches may be replaced by newer batches; terminal success/error always carries the appropriate results. On failure, the latest partial list is retained even if an earlier partial was conflated.
- Collect UI state changes on the caller's Compose context. Request/retry/sort identity checks and coroutine cancellation prevent obsolete work from publishing into a new request.
- Prepare Newest/Popular ordering on Default. Preserve the existing SearchOrchestrator ordering rules, ranking, media mapping, Kids filtering and result limits. Switching sort does not start another network request.
- Retain the displayed same-target grid while an incremental batch or sort is being prepared. A changed target, engine or API configuration creates fresh screen state.
- Stop loading on success/failure; show an inline Retry on failure rather than silently leaving a loading indicator or reporting no results. Retry explicitly starts a new fetch.
- Record mobile_entity_load/mobile_entity_prepare and tv_entity_load/tv_entity_prepare timing. Load timing includes network waiting and must not be interpreted as blocked UI time. These timing records use the Details diagnostic tab. ENTITY_RESULTS failure events include only the exception class, not URLs, API keys or exception messages.

## TV focus
- Retain FocusRequester instances by media identity while results reorder.
- Avoid automatic grid focus restoration while either sort tab owns focus.
- Sort changes scroll to the first row while focus remains on the selected sort control, so Down can reach a composed first item.
- For grid restoration, scroll the saved focused item into the visible range before requesting focus. Focus movement alone no longer restarts the delayed restoration effect.

These focus changes are implemented but have not been tested with an actual remote.

## Validation
13 JUnit JVM tests passed against the actual shared pipeline and MediaItem models: worker/caller contexts, terminal delivery, conflated failure preserving partials, empty success/failure, snapshot copying, late callback rejection, producer cancellation, cancellation propagation, background preparation, cancellation of non-cooperative preparation, retry isolation and serialized inherited child work.

All three production Kotlin files passed PSI syntax checks. Static integration checks cover background producer/preparation use on both platforms, request/retry/sort ownership, Retry wiring and TV focus guards. The existing ordering implementation is called unchanged; the tests do not execute live providers or TMDB.

No full Android build, phone test, TV test or before/after frame-time measurement was run. The workspace lacks an Android SDK, and this supplied repository lacks its Gradle wrapper JAR. UI syntax checks are not Compose type checking. The 449 ms symptom is not claimed resolved until device validation.

## Device checks
1. On both platforms, open cast, production-company and network title pages. Test partial loading, empty results, slow sources and Back.
2. Toggle Newest/Popular rapidly; verify ordering and that switching sort does not trigger a new fetch.
3. Open a different entity or leave the page during loading; ensure old results do not replace the current target.
4. Test failed loading and Retry; the spinner should stop and partial results should remain until Retry is chosen.
5. On TV, focus each sort tab during result arrival, press Down into the grid, navigate several rows, sort again and return from detail. Check focus and scrolling with the remote.
6. Export fresh full performance logs from Mobile and TV to compare entity preparation timing and callback gaps.

UHDMovies source errors are excluded from this patch, as clarified by the user. Player/source recovery is not changed.
