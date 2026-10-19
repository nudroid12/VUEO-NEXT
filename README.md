# VUEO provider catalogue metadata cache bridge

App source patch for both Mobile and TV, based on VUEO-NEXT-main (55).zip. Copy shared/ into the repository root, replace PluginRuntime.kt, add ProviderMetadataCache.kt, then build/install the app normally. If PluginRuntime.kt has changed locally, merge the single eight-line vueoMetadataCache binding shown in CACHE_BRIDGE.diff instead of overwriting unrelated changes. This patch does not replace either Search Identity patch: their modified files are separate. No YML files are included.

The bridge exposes a synchronous JSON-string function vueoMetadataCache to providers with get/put/remove operations for JSON-object metadata. It performs no disk or network I/O. Cache scope is repository manifest URL + provider ID + provider version; a provider upgrade cannot reuse old entries. TTL is capped at five minutes, with monotonic expiration, 64 total entries, 65,536 characters per value and 524,288 value characters overall. Cache access is synchronized and least-recently-used entries are evicted when limits are reached.

AnimeWorld 2.0.4 stores only its verified catalogue identity and published season/episode route data. It does not store signed streams, player/API responses, playlists or subtitle configuration. The selected episode is fetched and revalidated on every provider execution. App termination drops the cache; scans after expiration use normal verified discovery again.

Validation: 12 native Kotlin cache checks passed for namespace/version isolation, expiration, malformed requests and memory limits. Together with 52 provider replay/regression checks, 64 checks passed. The full Android app/QuickJS binding has not been built or tested on a device here.
