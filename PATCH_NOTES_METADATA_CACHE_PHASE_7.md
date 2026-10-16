# Details metadata cache/shared fetch — audit phase 7

Apply at the VUEO-NEXT repository root after the earlier patches. Three shared/core Kotlin files are included: UnifiedMediaEngine.kt (replacement based on the latest supplied Kids engine), MetadataRequestCache.kt and MetadataCachePolicy.kt (new). Mobile and TV use this engine. No YML/workflows, dependencies or APK are included.

## Behavior

- Raw positive addon metadata responses are cached in RAM, rather than caching the final merge with a particular card's title/poster/episodes. Existing metadata merge precedence and fallback selection remain in loadMeta for every caller.
- Keys include the addon instance identity, exact type/ID and Kids age-limit namespace. Caches belong to one engine; public addon metadata is not mixed with user history, ratings-account preferences or a different engine's configuration.
- Up to 32 addon responses are retained, with a five-minute monotonic-clock TTL and an 8 MiB accounting budget. Accounting estimates string/list/object costs; it is not a measured hard VM-allocation ceiling. An oversized response can be delivered to its requesting screen but is not cached. In-flight responses and the screen's own objects are outside the retained-cache budget.
- Concurrent requests for the same addon/type/ID/policy key share one fetch. Each caller retains its existing primary/fallback timeout. Cancelling one caller does not cancel work needed by another; the last departing caller cancels unfinished work. Errors/null results are not retained, allowing retries.
- Addon install/replacement/removal and actual enable/disable changes invalidate cached entries and detach old flights. Previously subscribed callers can finish, but new callers cannot join old-generation work, and its late results cannot repopulate the new cache generation.
- Kids permission is checked on metadata delivery using the current age policy and the existing merge's certification precedence (input certification first, otherwise candidate certification). Raw metadata can be cached; allow/deny decisions are never cached. Existing source/playback/content guards remain in place.

## Scope/limits

This caches core addon meta requests used by Details and other engine.loadMeta callers. It does not cache the final screen, TMDB enrichment, ratings, recommendations, account data or video/subtitle streams. A repeated Details open may still perform independent enrichment work outside this cache.

TTL can keep episode/description changes stale for up to five minutes. Invalidating addon configuration or restarting the process also clears the applicable RAM cache. Existing catalog/image-cache controls have not been repurposed as a new metadata-refresh UI.

Permission checks do not introduce a new certification-ranking policy: existing input-first certification precedence is preserved. This is not a full audit of every screen's Kids behavior.

## Validation

- Compiled the production cache and metadata policy with Kotlin 2.3.21/coroutines 1.10.2, using the actual MediaModels and KidsContentPolicy sources, without mock cache/media/policy implementations.
- 32 behavioral checks passed: positive hits, TTL expiry, LRU/weight eviction, oversize bypass, null/error retries, shared request ownership, one-waiter versus final-waiter cancellation, retry after cancellation, generation invalidation/late completion, distinct keys, accounting growth and current Kids age/certification decisions.
- Kotlin PSI syntax parsing passed for all three production files. Reviewed both primary/fallback meta-call replacements and all engine mutation invalidation paths.
- UnifiedMediaEngine's full Android dependency graph was not compiled, and no APK/device test was run. This workspace lacks Android SDK and the supplied repo lacks the Gradle wrapper JAR. Cache/policy JVM tests do not validate real addon networking or Compose/TV integration.

To run standalone checks with Kotlin and a coroutines JVM JAR:

```sh
kotlinc shared/core/src/main/java/com/vueo/shared/core/extensions/MetadataRequestCache.kt shared/core/src/main/java/com/vueo/shared/core/extensions/MetadataCachePolicy.kt shared/core/src/main/java/com/vueo/shared/core/media/MediaModels.kt shared/core/src/main/java/com/vueo/shared/core/profile/KidsContentPolicy.kt validation/metadata-cache/MetadataCacheCheck.kt -cp "$COROUTINES_JAR" -include-runtime -d metadata-cache-check.jar
java -cp "metadata-cache-check.jar:$COROUTINES_JAR" com.vueo.shared.core.extensions.MetadataCacheCheckKt
```

## Device checks

1. Open the same title from Home/Search/Library repeatedly within five minutes. Verify metadata/core requests are reused while each card's shell/title/artwork merge remains correct.
2. Test movie and series sharing similar IDs, titles supplied by different addons, and series with many episodes.
3. Leave Details immediately during a slow fetch; verify cancellation/navigation stays responsive. Exercise simultaneous Details/CW hydration requests where available.
4. Toggle, remove or refresh the metadata addon and reopen Details. Confirm old cache/configuration is not reused for new requests.
5. Switch standard/Kids profiles and age limits. Confirm rating checks and existing playback restrictions still apply, including missing certification.
6. Retry after offline/error/null responses; confirm a temporary failure is not cached as success.
7. Repeat after the five-minute TTL. Compare actual core requests/latency before claiming a numeric improvement; no measured speedup is claimed by this patch.
