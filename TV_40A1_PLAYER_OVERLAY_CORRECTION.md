# TV 40A1 — Player Overlay Correction

Based directly on the supplied NuvioTV 0.8.6 player overlays.

## Corrected

- Subtitle overlay moved to compact bottom-left Nuvio geometry.
- Audio overlay uses compact bottom-left track rail.
- Removed old-style `Choose subtitle/audio track` header treatment.
- Sources panel normalized to Nuvio 520dp right side panel.
- Episodes panel normalized to Nuvio 520dp right side panel.
- Episode rows use 130x90 thumbnails and Nuvio-style current indicator.
- Raw ISO episode timestamps are formatted for display.
- Base bottom player controls are hidden while Subtitle, Audio, Sources or Episodes modal overlays are open.
- MORE remains part of the bottom control row by design.

## Preserved

- VUEO ExoPlayer/playback engine.
- Subtitle selection and Auto/Off behavior.
- Audio track selection.
- Source switching.
- Episode routing.
- Resume/progress, skip, auto-next and playback settings.
- Home, Source screen, Details and mobile are untouched.

## Validation

- Only `TvPlayerNuvioPanels.kt` and `TvPlayerNuvioPresentation.kt` changed from 40A.
- Kotlin parse surface check returned no parse diagnostics.
- Full Gradle compile remains unavailable because `services.gradle.org` DNS resolution is blocked in this environment.
