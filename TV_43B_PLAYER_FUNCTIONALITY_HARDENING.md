# TV 43B Player Functionality Hardening

Base: 43A TV Player UI Rebuild.

## Purpose

43A rebuilt the TV player chrome, but the previous TV subtitle and audio selectors were not equivalent to the mobile player workspaces. This patch hardens the actual playback functions instead of treating the old generic panels as complete.

## Subtitle repairs

- Replaces the generic subtitle list with a TV subtitle workspace that exposes language groups, exact selectable tracks, provider or built-in source labels, sync and style controls.
- Detects Media3 text tracks from `player.currentTracks`, so embedded subtitles can make the Subs control available even when the provider returned no external subtitle list.
- External subtitle MediaItems receive stable VUEO selection IDs so the displayed row can be mapped back to the exact Media3 track.
- Manual selection now uses `TrackSelectionOverride` for the exact selected track instead of only setting a preferred language.
- Persists content-specific subtitle selection and the global preferred subtitle language marker through Shared Core SettingsStore.
- Restores saved subtitle selection after the active source has produced its own track groups.
- Adds per-content subtitle delay from -60 seconds to +60 seconds using a Media3 text renderer offset, in 250 ms UI steps.
- Subtitle size, bold, text colour, text opacity, outline, outline colour and bottom position update the live PlayerView and Shared Core settings.

## Audio repairs

- Audio options are built from exact Media3 audio tracks instead of collapsing rows by language.
- Manual audio selection uses `TrackSelectionOverride`, so multiple tracks with the same language can remain distinct.
- The UI shows track metadata such as channel layout, codec and variant when Media3 exposes it.
- Stream default clears the audio override and returns selection to Media3 automatic track choice.
- Audio selection is persisted and restored through Shared Core SettingsStore.

## Playback continuity

The following existing flows are intentionally preserved:

- Left and Right 10 second seek behavior and progress rail Play or Pause interaction.
- In-bundle source switching with current playback position retained.
- Episode selection and Next Episode continue to use the existing TV source-discovery route for the target episode. This patch does not claim seamless next-episode source resolution inside the player.
- Resume still uses the existing saved playback position. A lightweight position checkpoint is additionally written every 10 seconds while the player session is alive, reducing progress loss if playback ends unexpectedly.
- Existing skip segment, source recovery, speed, video fit and hardware media-key behavior are left intact.

## Validation status

Confirmed by source inspection:

- Generic language-only manual subtitle and audio selection paths were removed.
- Exact Media3 `TrackSelectionOverride` is used for both subtitle and audio manual selection.
- Embedded and external text tracks feed the subtitle workspace.
- Existing source, episode, next episode, seek and resume code paths remain present.
- Kotlin parser surface check shows no syntax diagnostics in the changed files.

Not confirmed in this environment:

- Full Android Gradle compilation, because the project wrapper attempts to resolve `services.gradle.org` and network resolution is unavailable here.
- Runtime behavior on a physical Android TV, including provider-specific subtitle loading and D-pad behavior across every Media3 track combination.
