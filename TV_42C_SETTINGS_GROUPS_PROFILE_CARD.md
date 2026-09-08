# TV 42C Settings groups and Profile card

Baseline: 42A full repository with 42B applied.

## Changes

- Keeps the 42B direct master-detail Settings architecture.
- Profile remains the first Settings category.
- Adds Mobile-style visual grouping in the TV category rail:
  - VUEO: Personalization, Content Manager, Enhancements
  - PLAYBACK: Playback, Subtitles, Sources
  - APP: Appearance, Data & Storage, Updates, About VUEO
- Group labels are non-focusable. Only real Settings categories receive D-pad focus.
- Group rows share a rounded container with separators, instead of looking like independent top-level group pages.
- Increases Settings category typography slightly for TV viewing distance.
- Replaces the old single-row Profile panel with a Mobile-parity profile summary card:
  - avatar and active profile name
  - VUEO viewing class and DNA class
  - My List count
  - watched-title count
  - DNA confidence
  - top three genre affinities
  - Switch Profiles action
- Selecting the profile summary opens the existing full Your DNA TV page.
- Switch Profiles remains inside the Settings right panel and returns to the Profile panel after selection.

## Focus behavior

- Left from any Settings category returns to global TV navigation.
- Right or OK from a Settings category enters the right panel.
- Profile summary is the first focus target in the Profile panel.
- Down from the profile summary moves to Switch Profiles.
- Left from Profile panel actions returns to the Profile category.
- Back behavior from 42B is preserved.

## Verification

- Only the two TV Settings Kotlin files are replaced.
- Kotlin delimiter balance was checked for both modified files.
- A standalone Kotlin parser pass reported no syntax-pattern errors such as `expecting` or `unclosed` before dependency-resolution errors.
- Full Gradle compile could not run because the environment could not resolve `services.gradle.org` to obtain the configured Gradle distribution.
