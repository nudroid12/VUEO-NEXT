# For You / Because You Watched loading and cache repair

Based on the supplied VUEO-NEXT-main (50).zip. TV and mobile.
Repository-root patch, no YAML or enclosing repository folder.

Home now observes a shared app-owned recommendation feed after library hydration.
Catalog partials rebuild presentation only; they no longer cancel/restart remote recommendation
work or clear a completed For You row. Tab changes detach the UI observer while bounded
background refresh continues and retains its results for the next visit.

For You and Because You Watched run and publish independently. Each keeps its last compatible
result while refreshing. A changed Because seed cannot reuse the old seed's list.
New watched/Favorites exclusions are applied to restored cards immediately.
Existing ForYouPolicy ranking, seed selection/weights and direct Because source order remain.
For You continues to source Trakt candidates as in repo (50); Because follows the selected engine.

Trakt uses short cache locks and keyed request coalescing, with a global maximum of three
HTTP requests in flight. Same-title callers reuse the completed request rather than duplicate it.
Existing HTTP timeouts, body size limits, cancellation and retry/backoff are retained.

Disk snapshots are scoped by profile, selected engine, credentials fingerprint and source options.
Credentials are never written to the snapshot or filename in clear text.
Cache is atomic, bounded to 16 snapshots of at most 256 KB each, and stored in Android cacheDir.
Fresh results are reused for 15 minutes when seed/meaningful library signatures match.
An empty/failed refresh keeps compatible existing rows and can retry after 90 seconds on a later
observation. Stale successful results remain displayable for up to 24 hours while refreshing;
failed attempts do not extend that age. Android may evict cache files.
No-result rows retain the existing UI minimum-item rules.

Validation:
- Production shared changes compiled with Kotlin 2.3.21 against JVM Android/JSON/TMDB fixtures
  and the real coroutines / OkHttp libraries.
- 16 JUnit tests passed: original ranking/eligibility tests plus cache-window and seed/exclusion tests.
- Mock HTTP checks passed: independent publication in both directions, three concurrent requests,
  duplicate-title coalescing, warm-cache reuse, observer cancellation survival and profile isolation.
- Fresh disk restoration tested in a separate JVM process: both rows restored without HTTP calls.
- All production Kotlin changes parsed with Kotlin PSI without syntax errors.
- ZIP root paths and integrity verified.

This is not a full Android APK build or physical TV/mobile rendering test. Mock checks do not
measure real Trakt/TMDB latency. Use normal GitHub Actions builds and verify navigation on devices.
