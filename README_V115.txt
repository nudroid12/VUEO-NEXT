VUEO Subtitle Reliability + Live Style v115
===========================================
Baseline: v114
Scope: Mobile + TV subtitle presentation and selection reliability
Shared core: CHANGED (SubtitleReadinessProbe force-network retry support)
Gradle build: NOT run (per VUEO project rule)

User-visible fixes
------------------
1. Commentary spacing
   - Commentary / normal subtitle vertical gap reduced from 20dp to 12dp.
   - Applied consistently to Mobile and TV.

2. Subtitle Style Workspace applies immediately
   - Active cues are explicitly rebound after presentation changes so current subtitle
     text redraws immediately instead of waiting for the next cue/update.
   - Covers font, background, outline, colour, opacity and size presentation changes.
   - Rapid left/right style input now derives from the latest in-workspace style state,
     preventing repeated remote/tap input from applying an older Compose snapshot.

3. Slow external / translated subtitle selection no longer bounces back
   - The user's requested selection remains the visible intent if a slow subtitle is not
     ready/attached within the current attempt.
   - Manual selection waits up to 60 seconds for a late Media3 text TrackGroup after the
     subtitle endpoint reports ready (auto-restore timing is intentionally unchanged).
   - A failed/late attempt clears only the active preparation state, not the requested
     track intent, so the row remains selected and can be explicitly retried.

4. Reselecting the same subtitle is a retry, never Off
   - TV no longer toggles an already-selected subtitle to Off when pressed again.
   - Mobile already routed selected rows through selection; both platforms now use the
     same re-request/re-apply behavior.
   - Off/None remains the only explicit action that disables subtitles.
   - If the same track already has an in-flight request, another tap does not create a
     duplicate concurrent request.

5. SmartSubs / generated subtitle manual retry
   - Reselecting an active external subtitle bypasses the readiness cache and performs a
     fresh readiness GET.
   - If that succeeds, the text renderer is briefly detached/reselected so Media3 reopens
     the freshly warmed subtitle session cache rather than merely keeping the stale track.
   - This makes a selected translated subtitle row useful as a real delivery retry after
     translation has finished but attachment/rendering was previously idle or delayed.

Shared-core change
------------------
SubtitleReadinessProbe.awaitReady() now accepts forceNetwork=false by default.
Normal callers preserve the existing 15-minute ready cache behavior. Explicit same-track
manual retries pass forceNetwork=true so the provider/generation endpoint is contacted
again before re-applying the text track.

Not changed
-----------
- Video source selection / refresh logic.
- Audio track selection.
- Playback position / resume / Continue Watching rules.
- Next Episode prefetch checkpoints and stale cutoff.
- Subtitle discovery/refresh semantics.
- SmartSubs translation logic itself.
- Existing commentary classification rule.

Static validation
-----------------
- Kotlin lexical/delimiter balance: PASS on all 7 changed Kotlin files.
- Duplicate imports: PASS.
- Trailing whitespace/diff hygiene: PASS.
- Patch overlay simulation over v114: PASS.
- Patch root/layout check: PASS (mobile/ + tv/ + shared/ + README, no wrapper folder).
- Only the 7 intended files differ from v114.
- No Gradle build executed.
