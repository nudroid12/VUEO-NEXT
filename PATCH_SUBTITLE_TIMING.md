# Subtitle timing diagnostics — Mobile and TV

Based on VUEO-NEXT-main (63).zip with the previous First Preparation patch. Contains that decoder/cache optimization plus additive diagnostics. Apply contents at the VUEO repository root; build and reinstall the app. No Cloudbank changes or YAML.

Only when Provider or Performance diagnostics is collecting, SUBTITLE_PREP events record:
- user_request: manual language request marker.
- await_ready / await_ready_forced: total readiness time, including per-URL lock/retries and cache outcome.
- readiness_download / media3_download: connection and full body time before preparation, plus bytes and outcome.
- decrypt / plaintext, normalize: separate processing durations.
- media3_cache: hit/miss; media3_bytes_ready: bytes handed to the native parser path.
- apply_track_request: track override request marker.

Each event carries a short SHA-256 URL fingerprint and span ID. No raw URL, headers, tokens, subtitle text or key/IV is included. Existing PLAYER_TEXT_TRACK and SUBTITLE_CUES_DELIVERED events provide the final selection/cue timestamps.

Interpretation: parser execution is not intercepted. The interval after bytes-ready/preparation until track application and cue delivery is a combined player/parse/attach interval, not a pure parser benchmark. Cue timing can also depend on subtitle content. Automatic selection lacks a manual user_request marker but uses await_ready/apply events. A cache timestamp hit is distinguished from prepared-byte cache hits. Cancellation/failure may leave an unmatched start marker. Timing instrumentation is synchronous but bounded to stage boundaries, not per cue; no zero-overhead claim.

Timeouts, retry policy, fetching order, video buffering, prewarm and SmartSubs activation are unchanged. No new network request is added.

Validation:
- Compiled complete production cache/data-source, readiness and timing helper with Kotlin/JVM 17, coroutines and minimal Android/Media3/diagnostic stubs.
- Local HTTP fixture: full subtitle download/decrypt/normalize, second readiness reuses cache without another HTTP request.
- Media3 data-source fixture: download then prepared cache reopen; logging off emits no timing events.
- Confirmed events omit fixture URL, subtitle text and key.
- Previous real decoder/cache regression checks remain passed.
- Android UI marker changes were reviewed statically. Full Android APK build and device playback are not verified.

Fixture: validation/subtitle-timing contains TimingCheck.kt and test-only substitutes. Compile these separately from real diagnostic/media model/Android classes; use real SubtitleCipherDecoder plus the existing Base64 stub. Add kotlin-stdlib and kotlinx-coroutines-core to the classpath; run TimingCheckKt with JDK module jdk.httpserver. These are JVM validation fixtures, not app source-set files.

Device capture:
1. Enable Performance and Provider diagnostics.
2. Reopen app, play an episode, wait for the first subtitle.
3. Switch English/Malay once each, then switch back.
4. Open another episode and repeat.
5. Export both diagnostic ZIPs.
