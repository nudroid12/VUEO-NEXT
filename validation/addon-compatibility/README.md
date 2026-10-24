# Standalone validation

AddonCompatibilityCheck.kt: 30 checks compiled against production addon models, parser, adapter, transport, installation registry, source conversion and Mobile deep-link normalizer. Uses Kotlin 2.2.0, coroutines 1.10.2, org.json, OkHttp 5.4.0 and Okio 3.16.4. The TmdbResolver fixture isolates unchanged metadata enrichment; it is not an application replacement.

SubtitleHeaderCheck.kt: 3 checks compiled with production SubtitleSessionDataSource, ProviderHlsDataSource, ProviderHlsCodec and SubtitleTextNormalizer against Media3 1.11.0. Android Uri/Base64/Util JVM fixtures were used. It verifies that subtitle-specific headers reach upstream and do not leak into the next media request. This is a JVM test, not an Android player test.

Existing regression suites exercised: EpisodeArtworkPolicyTest (updated to allow HTTP artwork), StreamSubtitlePolicyTest and BoundedStremioResponseTest. No full Gradle APK build or device test was performed.

These standalone sources belong in validation/, never src/main/.
