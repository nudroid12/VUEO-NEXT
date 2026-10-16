# Mobile Player resource cleanup — Phase 12

Apply after Phase 11: extract into the repository root and overwrite matching paths. No YML files are included. Root navigation and Search are not replaced. TV and SmartSubs are unchanged.

## Evidence and scope
The 15:43:44 performance export shows rising process memory after leaving Player, eventually PSS 668 MB and native heap 191 MB. It does not prove a Player or bitmap leak. The user was seeking repeatedly, so the short buffering transitions are not treated as a playback defect.

The existing Player already calls release(). This patch hardens two cleanup paths and adds evidence for the next session; it does not claim that the full memory increase is resolved.

## Changes
- AndroidView now explicitly unbinds PlayerView on permanent Compose disposal. Clear the Player reference, managed subtitle cues and subtitle observer/listener ownership, and turn off keepScreenOn.
- Keep ordinary window detach/reattach behavior separate from permanent disposal.
- Clear the outgoing player's video surface during disposal.
- Snapshot exit position/duration before releasing anything. Preserve existing progress persistence rules and the Phase 2 background lifecycle behavior.
- A finally path queues/releases the player even if progress cleanup fails. Trakt cleanup failure cannot skip release. Duplicate release requests are ignored by a resource lease.
- Keep immediate release for next-episode handoff and the existing 64 ms Main-thread defer for ordinary disposal. Media3 release is not moved to a background thread.
- Trakt listener disposal is best effort so a stop failure cannot skip listener removal.

## New diagnostics
PLAYER_RESOURCE logs created, dispose_requested, cleanup_started, release_started and released, with a player ID and outstanding count. Failures log the exception class only, without URLs or exception messages. A failed release remains outstanding rather than being counted as freed. These counters represent instrumented Player ownership, not measured native allocations; they span the process and may include players created before recording started.

PLAYER_VIEW_RESOURCE logs whether listener cleanup and PlayerView unbinding succeeded.

IMAGE_CACHE_RESOURCE samples cache KiB, configured limit, entries, active loads and hit/miss/eviction counts opportunistically, at most once per three seconds while performance recording is active. No extra timer, image request or bitmap copy is introduced. These values cover the decoded LRU cache only; they do not measure all displayed bitmaps, GPU textures, WebViews or codecs. Active loads are a sampled value and may be nonzero in the final log line. The prior image memory/disk limits and decode policy remain in place.

## Validation
Eight JUnit JVM tests passed against the production release guard: successful ordering/accounting, cleanup failure, release failure accounting, repeated and concurrent disposal, diagnostic callback failure, fatal cleanup finally behavior and independent player ownership.

All four production Kotlin files passed PSI syntax checks. Static integration checks verified AndroidView disposal wiring, the finally release path, preserved Phase 2 lifecycle markers and prior image cache limits. These tests use callbacks in place of Media3; they do not prove codec or Surface release on a device. No full Android build or device test was run: this workspace has no Android SDK and the supplied repository lacks its Gradle wrapper JAR.

## Phone validation
1. Start a fresh diagnostics session and play a title. Seeking is allowed.
2. Exit to Details/Home, then repeat playback and exit three times. Include one next-episode switch if available.
3. Switch Home/Search/Library/Settings, then leave one page idle for 20–30 seconds before exporting the full performance log.
4. Check that disposed players have matching released events, PlayerView unbinding succeeds and no release_failed event appears. Compare process memory trends with decoded cache ownership; high PSS alone does not establish a leak.
5. Verify video, subtitles, Continue Watching progress and next-episode transitions on the phone.

Settings and Details jank still require investigation; this patch does not claim to fix them.
