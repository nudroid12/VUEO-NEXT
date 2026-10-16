# Addon response byte limit — audit phase 9

Apply at the VUEO-NEXT repository root after earlier patches. The shared transport applies to Mobile and TV. This ZIP replaces StremioHttpClient.kt, adds BoundedStremioResponse.kt and includes a shared/core unit test. No YML/workflows, dependency changes or APK are included.

## Behavior

- The existing 8 MiB declared-length check is retained, and the actual response stream is now bounded too. Unknown-length/chunked responses and misleading small Content-Length values cannot bypass the limit.
- Byte accounting uses the body exposed by OkHttp, including transparently decompressed gzip responses. The limit is bytes, not decoded character count.
- The reader requests at most one byte beyond the limit into its own accumulation buffer. Okio/network/decompression buffers may prefetch their own segment; they are not claimed to consume exactly one excess byte from the wire.
- Oversized responses throw IOException and close the source. Partial/truncated JSON is never returned as a successful response.
- OkHttp's normal charset/BOM-aware string decoding is reused without first copying the entire buffered body into a second byte array.
- The existing continuation cancellation hook still calls Call.cancel(). The bounded reader also checks continuation activity between reads and before decoding. A blocking read is interrupted through the existing OkHttp cancellation path.
- HTTPS validation, request headers, pooled client, HTTP-status handling and timeout values remain unchanged. Other networking clients, video buffers and subtitle playback transport are outside this patch.

The limit bounds each response's accumulated bytes. Decoded Strings, JSON objects, OkHttp buffers and simultaneous requests also use memory; this is not a global 8 MiB heap ceiling.

## Validation

- Compiled both production transport files and the test against the actual OkHttp 5.4.0, Okio 3.16.4 and coroutines 1.10.2 JVM libraries using Kotlin 2.3.21.
- All 12 JUnit tests passed: unknown length within/over limit; exact 8 MiB boundary; decompressed gzip limit; dishonest small declared length; early declared oversize rejection; empty/zero limit; IO failure/source close; cancellation before/during read; byte-versus-character accounting; and charset/BOM equivalence (UTF-8, Latin-1, UTF-16LE/BE, UTF-32LE/BE).
- Stream tests use actual Okio/OkHttp response-body APIs and an instrumented in-memory Source; they make no live network request. Gzip testing uses actual Okio compression/decompression. Live HTTPS cancellation and server behavior were not exercised.
- No full Android APK build/device test was run: this environment lacks Android SDK and the supplied repo lacks the Gradle wrapper JAR.

The tests are under shared/core/src/test/java/com/vueo/shared/core/stremio/BoundedStremioResponseTest.kt and use the repository's existing JUnit dependency. In a configured Android build environment:

```sh
./gradlew :shared:core:testDebugUnitTest --tests com.vueo.shared.core.stremio.BoundedStremioResponseTest
```

## Device checks

1. Load built-in/additional addon manifests, Home catalogs, Search, movie Details, long episode lists and stream discovery on Mobile/TV.
2. Verify normal UTF-8/UTF-16 metadata responses remain readable.
3. With a controlled test addon, exercise chunked and gzip responses above/below 8 MiB. Confirm oversize requests fail cleanly and the app remains usable.
4. Back out during a slow response; confirm no stale callback updates and cancellation still works.
5. Compare memory under equivalent workloads before claiming a measured reduction. No numeric performance improvement is claimed by this patch.
