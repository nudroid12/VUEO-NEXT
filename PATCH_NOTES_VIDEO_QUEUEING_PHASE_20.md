# VUEO Phase 20 — generic bounded queueing recovery (Mobile + TV)

Overwrite the included files at the app repository root and rebuild/install the APK. This cumulative decoder patch includes Phase 19 Mobile/TV wiring and adds the Phase 20 queueing policy. Baseline: VUEO-NEXT-main (55) plus Phase 17 startup continuity and Phase 18 live SmartSubs. No YML or deletion script.

## Behavior

There are no movie titles, TMDB IDs, device models, manufacturer checks, named-decoder whitelists or 4K bans in the recovery decision. Input formats and candidate decoder names come from Media3 at runtime.

Keep native initialization fallback. For a later runtime decoding failure, first try another decoder reported as supporting the exact input configuration, within the existing two-retry limit. If none remains, permit one explicit synchronous-mode experiment per source, only on the decoder that actually initialized that clear video. This handles conservative capability reports without selecting an unopened unsupported decoder. Auto mode may itself already be synchronous on some platforms; this is one bounded compatibility experiment, not a guarantee of a different implementation.

The renderer queries only Media3's original candidates, including its secure/tunneling and Dolby Vision base-layer decisions. A queueing retry pins the observed decoder within that original list and uses Media3's forceDisableAsynchronous adapter factory for video only. Audio and subtitle renderers keep their existing factories. No video bytes, Dolby Vision metadata, MIME or profile are rewritten. Encrypted/DRM formats, network errors, decoder initialization failures and unobserved decoders do not trigger this experiment. A failed synchronous attempt cannot repeat until a manual Retry or source change resets the session.

Stop/release the failed renderer, seek to the saved position and prepare the same MediaItem. Preserve playWhenReady, request headers and track parameters. After exhaustion, use existing track/source fallback, respecting Auto Source Recovery. 4K sources remain selectable.

## Evidence added

- VIDEO_CODEC_INIT_FAILED: the actual decoder that could not initialize, native diagnostic code and cause class. This reveals why the hardware candidate is skipped before a software decoder is selected.
- VIDEO_QUEUEING_RETRY: the observed decoder, explicit synchronous mode and its reported capability result.
- VIDEO_CODEC_ADAPTER: mode requested for the adapter that was created.
- VIDEO_DECODER_RECOVERY route=queueing: resume position for the bounded mode retry.

Source URLs/headers are not logged. Diagnostic strings are bounded and URL-redacted. Both clients use the same shared recovery implementation.

## Validation and limits

81 checks passed: 23 codec-policy, 15 queueing-policy, 16 existing track-policy and 27 recovery-control-flow checks. Tests cover generic names, conservative zero-supported snapshots, never forcing an unopened decoder, DRM exclusion, one mode experiment, stop/seek/prepare order, pause/play preservation, manual reset, source isolation, initialization-failure logs and exhaustion. Device capabilities are simulated; no physical codec is exercised.

The shared production implementation and actual extracted Mobile/TV subtitle factories compiled with Kotlin 2.3.21 against real Media3 1.11.0 binaries; only diagnostics capture is substituted. Full APK/Gradle build is unavailable here because there is no Android SDK. The previous deprecated subtitle callback warning remains.

Rebuild and retry the failing 4K sources, then the working 4K source. In the new performance log, look for VIDEO_CODEC_INIT_FAILED and VIDEO_QUEUEING_RETRY followed by VIDEO_CODEC_ADAPTER mode=synchronous. Playback success is not guaranteed: unsupported formats or decoder/bitstream failures may still need another source. Share the log to distinguish the outcomes.

Movies4u speed work is a separate Cloudbank v1.20.67/provider v2.1.0 patch. It uses the existing generic vueoMetadataCache bridge from the previously supplied catalogue-cache/AnimePahe browser-access app updates; this decoder patch does not overwrite PluginRuntime or add provider-specific logic to VUEO.
