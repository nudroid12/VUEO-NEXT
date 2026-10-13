# VUEO For You — Trakt only (TV + mobile)

Apply this ZIP using the existing apply-ZIP workflow at repository root.
Paths start with mobile/, tv/ and shared/; no enclosing repository folder or YAML.
Based on VUEO-NEXT-main (48) with the latest For You Multi-Seed Rebuild patch.

For You candidate discovery now calls Trakt directly, independently of the selected
TMDB/Trakt recommendation engine. TMDB recommendation/similar results and Home
catalog candidates never enter the For You ranking pool, including loading passes
and API failures. History and Favorites remain seeds; ranking and up to 12 seeds
remain as in the prior rebuild.

Sparse profiles use Trakt trending movies and shows. Empty trending results may use
Trakt popular for that media type. Missing credentials or no available Trakt results
produce an empty For You pool, with no TMDB fallback. Existing UI displays the row
only when at least four results are available.

Because You Watch retains the selected engine; Trakt selection reuses the seed
batch, while TMDB selection obtains its direct results separately. Details and
metadata settings retain their existing behavior.

Discovery remains inside the 12-second For You budget; cache is scoped by profile,
Trakt client identity and seeds (15 minutes for a populated result; 90 seconds for
an empty result). Cancellation is propagated. Trakt discovery results have their
own 30-minute cache and retain the existing HTTP failure backoff.

Validation: 12 JUnit policy tests passed. Additional checks passed for Trakt-only
pool, no catalog/loading/failure fallback, cold-start discovery, blank credentials,
profile cache isolation, cache reuse, disabled personalization and cancellation.
Trakt client Kotlin compilation passed with JSON and HTTP boundary stubs.
No Android APK build or live Trakt/API/device recommendation test was performed.

Endpoint reference: https://developer.trakt.tv/ and https://developer.trakt.tv/docs/extended-info
