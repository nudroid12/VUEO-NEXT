# TV 29G Player D-pad Native Rebuild

Date: 2026-09-06

## Scope

TV Player only. Mobile and Shared Core behavior are unchanged.

## Locked presentation

- Player controls are bottom anchored, not centred over the video.
- The top area contains title/provider context only.
- There is no on-screen Back button.
- There is no on-screen Lock button.
- Playback/backend logic stays on the existing ExoPlayer + Shared Core path.

## Remote interaction contract

- The full player surface is no longer treated as one giant OK = Play/Pause target while controls are visible.
- When controls are hidden:
  - OK/Enter reveals controls and focuses Play/Pause.
  - Left/Right performs a 10 second quick seek.
  - Up/Down reveals controls. Up prefers an active Skip or Next contextual action when present.
- When controls are visible:
  - Left/Right moves deterministic focus through the bottom control row.
  - OK/Enter activates the focused control.
  - D-pad focus is visually explicit.
- Hardware media Play/Pause, Play, Pause, Rewind and Fast Forward keys remain supported.
- Remote Back is layered:
  1. close the active player side panel,
  2. hide controls,
  3. save progress and exit the player.

## Bottom controls

- Play/Pause
- Rewind 10 seconds
- Forward 10 seconds
- Next episode
- Subtitles
- Audio
- Sources
- Episodes
- More

Disabled controls remain focusable so the horizontal focus graph stays predictable, but activation is ignored and the control is visually dimmed.

## D-pad side panels

All side panels are remote-first and trap horizontal focus. Back returns to the button that opened the panel.

### Subtitles

- Off
- Auto using stored preferred subtitle languages
- External subtitle tracks from the current source bundle

### Audio

- Reads audio languages exposed by Media3 current tracks.
- Selecting a language updates Media3 track selection preferences.
- If the stream exposes no alternate language metadata, the panel shows a non-actionable stream-default state.

### Sources

- Shows direct-playable sources only.
- Switching source preserves the current playback position.
- Existing auto-recovery behavior is retained.

### Episodes

- Lists episodes in season/episode order.
- Current episode is marked active.
- Selecting another episode saves progress first and uses the existing episode navigation callback.

### More

- Playback speed: 0.5x, 0.75x, 1x, 1.25x, 1.5x, 2x.
- Video fit: Fit, Fill, Zoom.
- Changes persist through SettingsStore.

## Contextual actions

- Skip Intro / Recap / Ending remains contextual and is now focusable by remote.
- Auto-next countdown remains and is also directly activatable.

## Auto-hide

- Player controls auto-hide after remote inactivity while playback is actively playing.
- Controls remain visible while paused.
- Side panels do not auto-hide while open.

## Validation status

- Source-level Kotlin syntax check found no parser-level `expecting`, `unexpected`, or `syntax` diagnostics.
- Full Android/Gradle compile could not run in this environment because Gradle 9.3.1 is not cached and `services.gradle.org` cannot be resolved from the container.
- The relevant Compose FocusRequester `Cancel` API and Media3 track-selection APIs were cross-checked against current Android Developers reference documentation.

## Real-TV checks required

1. Confirm initial focus lands on Play/Pause.
2. Confirm Left/Right stays inside the bottom row while controls are visible.
3. Confirm hidden-state Left/Right performs 10 second seek.
4. Confirm every side panel receives focus on open and Back restores the originating button.
5. Confirm long Sources/Episodes lists scroll naturally with D-pad.
6. Confirm pause prevents auto-hide and resume restarts the idle timer.
7. Confirm subtitle/audio changes take effect on representative HLS/DASH/direct streams.
8. Confirm source switch resumes close to the pre-switch timestamp.
9. Confirm Back requires the intended layered sequence before exiting.
