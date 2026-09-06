# TV 38A — Details Clean Rebuild

Baseline: TV 37A. TV 35A Source and TV 37A Player are locked and unchanged.

38A supersedes the 34A/36A Details presentation implementation.

## What changed

- Old Details presentation files are replaced by tombstones:
  - `TvDetailPresentation.kt`
  - `TvDetailHero.kt`
  - `TvDetailEpisodes.kt`
  - `TvDetailSupporting.kt`
- New presentation tree written from scratch:
  - `TvDetailView.kt`
  - `TvDetailHeroSection.kt`
  - `TvDetailEpisodeSection.kt`
  - `TvDetailDiscoverySection.kt`
- `TvDetailScreen.kt` rewritten as a runtime/data/navigation boundary only.
- `TvDetailContract.kt` holds the minimal UI contract and pure Details helpers.

## Nuvio 0.8.6 reference retained

- 540dp bottom-anchored cinematic hero.
- Full-screen backdrop with left/bottom scrims and scroll fade.
- Primary Play/Resume + compact library action.
- Rounded season tabs with no focus scaling.
- 360x235 episode cards with full-bleed art, floor scrim, progress and 2dp focus ring.
- Cast and company/network shelves.
- 260x146 More Like This cards with restrained 1.02 focus scale.
- Source -> Details season/episode focus restoration.

## VUEO contracts preserved

- `TvRuntime.loadMeta`, ratings, related titles and DNA match.
- Watchlist/library semantics.
- Playback history and resume semantics.
- Details -> Source routing.
- Related-title routing.
- Optional VUEO Insight.
- No fake Trailer action because `MediaItem` does not expose a trailer target.

## Regression lock

- Home 32C unchanged.
- Sidebar 33A unchanged.
- Source 35A unchanged.
- Player 37A unchanged.
- Mobile unchanged.
