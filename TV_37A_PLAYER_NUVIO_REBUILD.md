# TV 37A — Player Nuvio Presentation Rebuild

Baseline: TV 36A Details Redo. TV 35A Source and TV 36A Details are locked and unchanged.

## Scope
- Rebuilt `TvPlayerScreen` presentation and TV focus interaction using NuvioTV 0.8.6 player behavior as the reference.
- Preserved VUEO playback/runtime contracts, Media3 ExoPlayer setup, stream switching, subtitle/audio selection, resume/progress persistence, skip segments, source recovery, autoplay-next, video fit, playback speed and routing callbacks.

## Presentation changes
- Removed the legacy bottom capsule control strip and top-right Next/More controls.
- Added Nuvio-style cinematic top/bottom player gradients.
- Added bottom metadata hierarchy: content title, episode line, paused stream/source line.
- Added a focusable seek rail: LEFT/RIGHT seeks 10 seconds; DOWN enters controls; UP hides chrome.
- Added a clean circular control row: Play/Pause, Next, Subtitles, Audio, Sources, Episodes and More.
- Focus uses white circular inversion without scale animation or pill containers.
- Subtitles/Audio/More use a left overlay treatment inspired by Nuvio player overlays.
- Sources/Episodes use a 520dp elevated right-side panel inspired by Nuvio side panels.
- Option rows were rewritten with a restrained selected accent rail and focus border; legacy radio/capsule treatment was removed.
- Skip and next-episode prompts remain contextual and restore into the rebuilt control row.

## Remote behavior
- Controls hidden: CENTER toggles playback; LEFT/RIGHT seek; DOWN opens controls; UP opens contextual Next/Skip/More path.
- Controls visible: UP from a control enters seek rail; DOWN hides controls; CENTER activates focused action.
- BACK: closes active panel, otherwise hides controls, then exits player on the next BACK.
- Auto-hide remains 4.5 seconds while playing and no panel is active.

## Nuvio references
- `ui/screens/player/PlayerScreen.kt` (`PlayerControlsOverlay`, `ProgressBar`, `ControlButton`)
- `ui/screens/player/PlayerOverlayScaffold.kt`
- `ui/screens/player/StreamSourcesSidePanel.kt`
- `ui/screens/player/EpisodesSidePanel.kt`
- `ui/screens/player/SubtitleSelectionOverlay.kt`
- `ui/screens/player/AudioSelectionOverlay.kt`

## Validation
- Legacy presentation symbols removed: `TvPlayerCapsuleAction`, `TvPlayerTopAction`, `TvPlayerContextAction`, `TvPlayerSidePanel`, `TvPlayerOptionRow`.
- VUEO playback/backend symbols retained.
- Kotlin parser pass via `kotlinc` reported no syntax/`expecting` errors; Android/Compose symbols are unresolved outside Gradle as expected.
- Full Gradle compile could not start because the environment cannot resolve `services.gradle.org`.
