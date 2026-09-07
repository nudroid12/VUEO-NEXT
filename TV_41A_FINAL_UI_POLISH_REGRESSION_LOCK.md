# TV 41A — Final UI Polish + Regression Lock

Base: latest uploaded VUEO-NEXT `(18)`, including 39A/39A1 Details. This overlay also supersedes the first 40A player presentation files if they are already applied.
Reference: supplied NuvioTV 0.8.6-beta.

## Final polish

- Neutral white focus is now the TV-wide focus language. User accent remains for semantic progress/selection accents.
- Shared premium focus scale reduced from 1.055 to 1.025 to remove oversized card jump and keep a restrained Nuvio-like depth response.
- Details episode release dates are formatted instead of showing raw ISO timestamps.

## Player final correction

- Keeps the VUEO Media3/backend/controller contracts, but replaces the visible player chrome with the Nuvio-first presentation tree.
- Player controls do not remain visibly stacked under an open panel.
- Subtitle/Audio/More overlay is compact and bottom-left weighted rather than covering half the TV canvas.
- Sources panel is 520dp right-side.
- Episodes panel is 520dp right-side, matching the Nuvio reference width.
- Episode rows use 130x90 thumbnails, current-state indicator, title, overview and formatted release date.
- Season chips are compact, no oversized focus scale.
- Bottom player chrome keeps the Nuvio 150dp top and ~200dp bottom cinematic gradient proportions.
- D-pad seek remains ±10 seconds on the focused progress rail.
- Back contract remains panel -> controls -> player exit.

## Locked / unchanged

- Home composition/routing unchanged.
- Source composition/routing unchanged.
- Details 39A composition remains unchanged apart from release-date formatting.
- Mobile and Shared Core/backend unchanged.

## Validation

- Kotlin parser/static structural pass found no parse, duplicate-argument, missing-parameter, or redeclaration errors in changed UI files.
- Home and Source directories verified byte-for-byte unchanged against `(18)`.
- Full Gradle compile could not start because the environment cannot resolve `services.gradle.org` for the wrapper download.
