VUEO Mobile Player Phase 4 — Next Episode Prefetch Parity (v72)
Base: VUEO-NEXT-main (38), after Phase 1/2/3 patches

Scope
- Start next-episode prefetch when playback enters the last 5 minutes.
- Restrict the standby source scan to the current provider first.
- Prefer the exact same server via shared NextEpisodeSourcePolicy.
- Stop source scanning once the matching server is found while subtitle discovery continues independently.
- Reuse the prefetched source/subtitles when Next Episode is selected.
- Merge late subtitle results into the active next episode.
- If same-server prefetch is unavailable/failed, fall back to normal full discovery.
- During fallback, wait for the current provider before selecting unrelated provider results, matching TV behavior.
- Cancel stale prefetch when the active server changes or player/detail exits.
- Avoid duplicate prefetch dispatch for the same video/source.

Files
- mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
- mobile/src/main/java/com/vueo/mobile/ui/detail/MobileDetails.kt
- mobile/src/main/java/com/vueo/mobile/ui/detail/MobileEpisodePrefetch.kt (new)

Notes
- Phase 1 post-credit safety remains intact.
- Phase 2 subtitle rendering/settings remain intact.
- Phase 3 buffering/status UI remains intact in MobilePlayer.kt.
- No provider timeout values were changed.
- No build artifacts are included.
- Full Gradle compile could not run because this environment cannot resolve services.gradle.org.
  A Kotlin parser/static syntax pass found no token/bracket syntax errors in the changed Kotlin files.
