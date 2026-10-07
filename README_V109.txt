VUEO v109 — Next Episode Prefetch Hardening + In-Player Subtitle Refresh

Baseline
- Apply after the current v108 state (v104 repo + v105 + v106 FIXED + v107 + v108).
- Patch paths start at repo root. There is no wrapper directory.

1) Next Episode prefetch hardening — Mobile + TV
- Existing T-5:00 next-episode prefetch remains the owner of discovery.
- Current server/provider selected by the user remains the preferred target.
- New T-3:00 checkpoint validates the already-prefetched playable URL.
- New T-1:00 checkpoint validates it again.
- VALID: keep the exact prefetched server/source on standby; no discovery rerun.
- INVALID (401/403/404/410) or UNKNOWN after one lightweight retry: cancel stale standby and rerun the SAME filtered prefetch path, still prioritising the current server/provider.
- Validation uses a tiny HTTP range probe and immediately closes the response. It does not start playback or download the stream.
- Checkpoints are based on remaining playback time and only dispatch while playback is actually playing. Pausing does not create a wall-clock validation loop.
- Existing next-episode fallback policy remains intact: other source/server fallback happens only through the existing transition discovery when the current server cannot provide a playable match.
- Existing 10-minute absolute reusable/stale cutoff is unchanged.
- Subtitle results already obtained by the T-5 prefetch are carried into a source refresh so URL revalidation does not gratuitously rerun subtitle discovery.

2) Refresh Subtitles inside Player — Mobile + TV
- Adds Refresh directly to the subtitle workspace.
- Refresh reruns the real subtitle-addon discovery pipeline for the current video/episode only.
- It does NOT refresh video sources, reload playback, switch server, change position, or alter audio selection.
- New tracks merge into the existing list and dedupe by URL.
- Existing subtitle tracks remain if refresh fails.
- Current subtitle selection is preserved; if subtitles are Off/None, newly discovered tracks are not auto-selected.
- Refresh is single-flight in the player UI and reports: refreshing, new-count/no-new, or failure while keeping existing tracks.
- TV adds explicit D-pad focus access to Refresh from the first language row and back down to the language list.

Shared-core changes
- NEW NextEpisodePrefetchValidator: common lightweight prefetched-URL validation for Mobile + TV.
- SourceDiscoveryEngine gains discoverSubtitlesOnly(): common subtitle-only discovery entry point used by both platforms.

Validation performed
- No Gradle build was run.
- Static delimiter/lexical scan: PASS.
- Duplicate-import scan: PASS.
- Trailing-whitespace scan on changed Kotlin files: PASS.
- Patch scope/diff check: PASS.
- Patch overlay/apply simulation against the reconstructed v108 baseline: PASS.
