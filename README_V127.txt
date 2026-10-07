VUEO Android TV Watch Next Integration v127

Base: reconstructed latest v126 (repo 41 + v122-v126 overlays)
Scope: TV only. No Mobile/shared-core behavior changes.
No Gradle build.

Goal
Publish VUEO Continue Watching into the Android TV system Watch Next / Play Next row and let launcher cards reopen VUEO at the correct movie/episode cursor.

Changes
1. Android TV Watch Next publisher
   - Added androidx.tvprovider:tvprovider:1.1.0 to the TV module.
   - VUEO mirrors the active profile's resolved LibraryStore.continueWatching() list into the system Watch Next provider.
   - Unfinished movies/episodes publish as WATCH_NEXT_TYPE_CONTINUE with duration + playback position.
   - A released next episode resolved by ContinueWatchingPolicy publishes as WATCH_NEXT_TYPE_NEXT.
   - Cards include title, description, poster, series season/episode metadata and last engagement time.
   - Existing VUEO rows are updated instead of blindly duplicated.
   - Stale VUEO rows are removed when Continue Watching is cleared, an item is completed/caught-up/marked watched, or the active profile changes.
   - Publisher owns only internal IDs prefixed vueo:watch-next: and does not touch other apps or unrelated future VUEO rows.

2. Low-overhead synchronization
   - A content fingerprint prevents redundant TV-provider writes when Library changes do not affect Continue Watching.
   - Publisher syncs after TV playback/detail/settings/profile Library notifications.
   - MainActivity also performs a background sync on pause so poster-menu actions such as Remove from Continue Watching are reflected before returning to the Android TV launcher.
   - Process-wide synchronization prevents two publisher instances racing each other.

3. Launcher deep link + resume
   - TV MainActivity now handles vueo://watch-next links.
   - A launcher card resolves the matching active-profile LibraryPlaybackEntry.
   - Movie progress and exact series season/episode are restored.
   - VUEO enters source discovery and automatically chooses the best currently available direct source using the existing PlayerSourcePolicy.
   - Player Back returns to Home for launcher-started playback.
   - If the app is cold-started, source discovery waits for configured Stremio addons to finish preparing so launcher resume does not accidentally scan an incomplete addon set.
   - If no playable source is found, the normal Sources screen remains available instead of failing silently.

4. Profile / completion behavior
   - Startup sync uses the active profile.
   - Selecting/changing a profile resyncs system Watch Next and removes stale rows from the previous profile.
   - Completing a movie removes it from Watch Next after normal VUEO persistence.
   - Completing a series episode can advance the system card to the next released unwatched episode, matching VUEO's existing ContinueWatchingPolicy.

Unchanged
- VUEO Home Continue Watching UI and selection policy
- Mobile app
- source ranking policy
- subtitle behavior
- Next Episode handoff logic
- v126 addon categories / Stremio Open With
