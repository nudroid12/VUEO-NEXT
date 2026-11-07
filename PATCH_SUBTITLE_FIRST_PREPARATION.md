# Generic subtitle first-preparation optimization

Based on VUEO-NEXT-main (63).zip. Apply these files at the VUEO repository root and build/install Mobile or TV normally. No Cloudbank update is required.

Production changes:
- SubtitleCipherDecoder remembers the last successful key within each file and tries it first on the next encrypted cue. All other configured keys remain eligible for mixed-key files.
- Reuse the compiled control-character regex instead of compiling String.matches for every candidate.
- Snapshot subtitle transform registration under the session-cache lock; decrypt/normalize outside that lock. Publish prepared bytes only if registration is still the same. Configuration changes/eviction invalidate in-flight results.
- Both readiness and Media3 full-sidecar paths use the same prepare-and-cache method. Each invocation transforms once; this does not introduce per-URL preparation single-flight across those two paths.

Existing Cipher reuse, cache bounds, subtitle headers, full-file preparation, timeouts, language selection, video loading and SmartSubs activation behavior remain. No prewarm, new network requests, provider-specific key/host/logic or YAML.

Validation completed in this environment:
- Java production decoder compilation with Eclipse ECJ on OpenJDK 17 and Android Base64 test stub.
- Existing decoder checks: both keys, preserved timestamps, mixed plaintext, idempotence, VTT plaintext, wrong-key rejection, size bound; 3,000 alternating-key cues.
- Added 3,000 second-key-only cues.
- Kotlin compilation/test of the production cache object extracted verbatim, with fixture models; existing CacheCheck passed.
- Deterministic cache concurrency check with a blocking decoder fixture: cache reads/registration proceed while transform is blocked, changed registration rejects stale publication. The concurrency fixture substitutes the decoder only to control scheduling; it is not a production decoder replacement and must be compiled separately from the production decoder.

No full Android/Media3 build, APK, device playback or measured device speedup. This reduces repeated key failures for files primarily using a later key, and removes cache-lock contention. It does not eliminate initial file download or Media3 parsing. Alternating-key files may still try multiple keys per cue. Cipher key success uses the existing padding/UTF-8 validation, not authenticated encryption.

For cache tests, extract object SubtitleSessionCache from SubtitleSessionDataSource.kt into a temporary Kotlin source with its package and imports for java.util.LinkedHashMap and SubtitleTextTransform. Compile it with SubtitleTextNormalizer.kt and fixture media model data classes matching SubtitleCipherKey and SubtitleTextTransform. Use the real decoder for CacheCheck; use only concurrency/BlockingDecoder.kt for CacheConcurrencyCheck.
