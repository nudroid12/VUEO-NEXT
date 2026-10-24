# Addon compatibility adaptation

Reference implementations reviewed:
- NuvioMedia/NuvioTV, commit 36a9ad0ca92028a0019bbf8b89f3a65963aeda8d (GPL-3.0).
- NuvioMedia/NuvioMobile, commit c114d6fb69fbed2bbed1f728bce7d752b5a19158 (GPL-3.0).

This VUEO adaptation follows their configured URL/query construction, resource-level type/ID-prefix matching and richer stream/subtitle payload handling. It does not replace VUEO's addon engine, storage, cache, player, subtitle discovery lifecycle or plugin runtime with Nuvio's implementation.

HTTP(S) is accepted through installation, addon resource requests, direct media selection, subtitle handoff and artwork. Android cleartext traffic is enabled in both applications; normal HTTPS certificate validation remains enabled. Configured query values and path case remain unchanged.

Inline stream subtitles and subtitle headers are now preserved through the addon adapter. Filename, videoHash, bingeGroup, notWebReady and proxy response-header metadata are retained in addonHints. Retaining these hints does not implement new autoplay, response-header rewriting, debrid or external resolver behaviour. ExternalUrl-only, ytId-only and clientResolve-only sources still require dedicated playback handlers and are outside this patch.

The existing timeout/retry policy, filtering below 720p, original-audio policy, installation identity, profile data, History/Delete and subtitle rollback are preserved.

Validation: 30 standalone JVM integration checks (including real OkHttp HTTP requests to a local server); 3 Media3 subtitle-header handoff checks with real Media3 classes and Android JVM fixtures; additional existing JUnit regressions described in README. No APK build or device playback verification was performed.
