# TV 29B.2 — Complete Settings Implementation

This patch replaces the temporary 29B.1 settings panel with the canonical VUEO Settings hierarchy adapted for a TV remote.

## Settings hierarchy

- Personalization
- Content Manager
  - Addons
  - Providers
  - Catalog Order
- Enhancements
- Playback
- Subtitles
- Sources
- Appearance
- Data & Storage
- Updates
- About VUEO

## Remote grammar

Settings use one focus plane. DPAD Up/Down moves rows, Left/Right changes adjustable values, OK activates the focused row once, and Back returns to the parent page. DPAD Up from the first enabled row moves to the contextual top navigation; DPAD Down from the top navigation restores the last focused Settings row.

## Runtime wiring

This patch deliberately avoids exposing fake preferences. Settings now have TV consumers where appropriate:

- Personalization gates User DNA match display and Home recommendation reranking.
- Content Manager changes the active addon/provider/catalog configuration and refreshes discovery data.
- TMDB enrichment changes metadata/artwork and related-title surfaces.
- MDBList controls external ratings shown on Details.
- Gemini controls optional title insight on Details.
- Playback settings drive resume, quality ranking, playback speed, video fit, content warnings, skip segments, next-episode autoplay and source recovery.
- Subtitle preferences drive language selection, default state and TV subtitle rendering.
- Technical Source Details changes Source Picker presentation.
- Appearance applies theme/accent immediately to TV surfaces.
- Data & Storage performs real backup/restore/cache/history/reset actions through Shared Core.
- Updates performs rate-limited checks, APK validation and Android install handoff.

## Preservation rule

No legacy TV Settings implementation is restored. Shared Core owns preference/data behavior; TV owns the 10-foot interaction and presentation.
