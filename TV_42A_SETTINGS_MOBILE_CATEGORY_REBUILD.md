# TV 42A - Settings Mobile Category Rebuild

Base: latest uploaded `VUEO-NEXT-main (25).zip`.

## Goal

Use the current Mobile Settings grouping as the TV Settings category model without changing Shared Core behavior.

## Root hierarchy

- VUEO
  - active Profile
  - Personalization
  - Content Manager
  - Enhancements
- PLAYBACK
  - Playback
  - Subtitles
  - Sources
- APP
  - Appearance
  - Data & Storage
  - Updates
  - About VUEO

## Presentation

- The left category rail uses VUEO / PLAYBACK / APP instead of the previous generic TV split.
- The right pane uses one grouped Mobile-style card with icon wells, dividers, compact status text and neutral white focus.
- Existing child settings pages remain one-column TV screens.
- Global TV sidebar navigation and D-pad commit behavior are unchanged.

## Runtime

No preference or data implementation was duplicated. Existing `TvRuntime`, `SettingsStore`, profile, Content Manager, update and Shared Core wiring remain authoritative.

## Validation

Static source checks are required locally. Full Gradle compilation may require a network-accessible Gradle bootstrap if the wrapper distribution is not already cached.
