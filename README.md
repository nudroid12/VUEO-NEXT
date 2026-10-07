VUEO Next Episode Handoff Optimization v120

Base: v119 TV Diagnostics Focus + Tab Layout
Scope: Mobile + TV Player handoff only. No shared-core changes.
No Gradle build.

Goal
Reduce the burst of codec allocation, LibraryStore/PlaybackStore writes, and late subtitle MediaItem churn when an episode ends and the user/auto-play advances to the next episode.

Changes
1. Immediate old-player release for episode-to-episode handoff
   - Mobile and TV no longer keep the outgoing ExoPlayer/MediaCodec alive for the extra 64 ms during an episode switch.
   - The outgoing player is muted/paused and released immediately when the handoff disposes it.
   - The existing 64 ms deferred release is retained for normal Back/navigation (and Mobile source disposal), where it still helps the return surface render first.

2. Completion/progress persistence de-duplication
   - STATE_ENDED queues the completion snapshot once per video.
   - Pressing Play Next after STATE_ENDED reuses that queued completion instead of writing PlaybackStore + LibraryStore again.
   - Early-next during safe credits still queues completion once because STATE_ENDED has not happened yet.
   - Player disposal skips another progress/history write when the episode handoff already captured it.
   - Completed episodes also skip the redundant dispose write.
   - TV next/manual-episode persistence is now dispatched through the existing serialized IO queue instead of synchronous saveProgress() on the UI path.

3. Manual episode switches
   - Mobile and TV capture the current episode progress once before switching to a manually selected episode.
   - Disposal does not serialize the same cursor a second time.
   - If a TV switch is cancelled/failed and the old Player survives, normal persistence is re-armed.

4. Late subtitle registration is batched around decoder startup
   - Newly discovered subtitle choices still appear in the workspace immediately.
   - If prefetched/late subtitles arrive while the next episode is producing its first frame, MediaItem replacement waits for startup/handoff to settle, then applies once after a short settle delay.
   - This reduces replaceMediaItem()/seek/track-rebuild pressure during the decoder handoff.

Unchanged
- T-5 / T-3 / T-1 next-episode prefetch/validation policy
- Same-server-first source selection and fallback policy
- Subtitle discovery itself and SmartSubs/OpenSubtitles behavior
- Continue Watching / resume cursor rules
- Back-navigation deferred-release behavior
- shared/core
