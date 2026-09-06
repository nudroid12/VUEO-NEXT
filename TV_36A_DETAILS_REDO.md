# TV 36A — Details Rebuild Redo

Baseline: latest VUEO branch after TV 35A Source Selection. TV 35A is locked and unchanged.

36A supersedes the failed 34A Details presentation.

## Rebuilt again from a clean presentation layer

- `TvDetailPresentation.kt` replaced with a small composition shell.
- Hero moved to fresh `TvDetailHero.kt`.
- Season/episode UI moved to fresh `TvDetailEpisodes.kt`.
- Cast, company/network, More Like This and Insight moved to fresh `TvDetailSupporting.kt`.
- `TvDetailScreen.kt` remains the VUEO data/routing boundary but now preserves season/episode focus memory when returning from Source.

## Nuvio 0.8.6 reference used

- 540dp bottom-anchored hero composition.
- Full-screen backdrop that fades down after scrolling past the hero.
- Strong left/bottom cinematic scrims.
- Primary Play/Resume + compact library action.
- Season tabs: rounded selectors, focused scale stays 1.0.
- Episode cards: ~360x235 landscape, full-bleed image, floor-fade text scrim, 2dp focus ring, focused scale stays 1.0.
- More Like This: 260x146 landscape cards with restrained 1.02 focus scale.
- Focus restore returns to the remembered season/episode after Source -> Details.

## VUEO contracts preserved

- `TvRuntime.loadMeta`, ratings, related titles and DNA match.
- Library/watchlist and playback history semantics.
- Detail -> Source and related-title routing.
- Optional VUEO Insight path.
- Existing `MediaItem` model has no trailer target, so 36A does not create a dead Trailer button.

## Regression lock

- Home 32C unchanged.
- Sidebar 33A unchanged.
- Source 35A unchanged.
- Player unchanged.
- Mobile unchanged.
