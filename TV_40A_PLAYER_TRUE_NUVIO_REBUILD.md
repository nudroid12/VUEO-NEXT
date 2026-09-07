# TV 40A — Player True Nuvio Rebuild

Base: latest VUEO-NEXT `(18)`.
Reference: supplied NuvioTV 0.8.6-beta player presentation.

## What changed

- Removed the previous VUEO/37A player presentation composables from `TvPlayerScreen.kt`.
- `TvPlayerScreen.kt` is now primarily the VUEO playback/controller boundary: Media3 player setup, stream recovery, subtitles/audio track selection, progress persistence, skip segments, autoplay-next, speed/fit settings, and routing.
- Added a separate Nuvio-first presentation tree:
  - `TvPlayerNuvioPresentation.kt`
  - `TvPlayerNuvioComponents.kt`
  - `TvPlayerNuvioPanels.kt`

## Nuvio presentation mapping

- Nuvio-style 150dp top gradient and ~220dp bottom cinematic gradient.
- Bottom metadata hierarchy: title, episode line, year/source context.
- Full-width focusable progress rail with D-pad seek.
- Circular player control row with white focused state.
- More actions expand inline around the control row instead of using the old VUEO capsule/panel composition.
- Subtitle/audio use left-weighted overlays.
- Sources use a 520dp right side panel.
- Episodes use a dedicated right side panel with season chips, thumbnails, and current-episode state.
- Back behavior stays: close panel -> hide controls -> exit.
- Auto-hide remains 4.5 seconds while playing.

## VUEO playback contracts retained

- Media3 / ExoPlayer backend.
- Source switching and auto recovery.
- Subtitle and audio selection.
- Resume/progress/library persistence.
- Skip intro/recap/ending.
- Autoplay next episode.
- Playback speed and video fit.
- Existing Details -> Source -> Player routing.

## Locks

Unchanged from `(18)`:

- Home
- Source
- 39A Details
- Mobile
- Shared/backend

## Validation

- Kotlin parser/static structural check: no parse, duplicate-argument, missing-parameter, or redeclaration diagnostics in the changed player files.
- Verified old player presentation symbols are absent.
- Verified only `tv/player` changed against `(18)`.
- Full Gradle compile could not start because the environment cannot resolve `services.gradle.org` for the Gradle wrapper.
