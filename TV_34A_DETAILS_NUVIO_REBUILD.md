# TV 34A — Details Nuvio Presentation Rebuild

## Scope

34A removes the old TV 29E Details Compose presentation and rebuilds the visual/focus layer from a blank file while keeping VUEO data and navigation contracts intact.

### Kept from VUEO
- `TvRuntime.loadMeta`, ratings, related titles and DNA data
- Library/watchlist and playback history semantics
- Series season/episode selection and resume target semantics
- Detail -> Source routing and related-title routing
- Optional VUEO Insight data path

### Rewritten
- Entire Details hero composition
- Sticky cinematic backdrop and scrims
- Primary Play/Resume and My List actions
- Ratings/credits/synopsis hierarchy
- Season tabs
- Horizontal episode cards and playback progress
- Cast presentation
- Network/production presentation
- More Like This row
- Detail focus memory and row restoration

## Nuvio source reference

The supplied NuvioTV 0.8.6-beta source was used as an architecture/UX reference, especially:
- `ui/screens/detail/MetaDetailsScreen.kt`
- `ui/screens/detail/HeroSection.kt`
- `ui/screens/detail/EpisodesSection.kt`
- `ui/screens/detail/CastSection.kt`
- `ui/screens/detail/MoreLikeThisSection.kt`

VUEO independently implements the same TV principles: a fixed-height hero over a sticky backdrop, hero-owned actions/metadata, pill season selectors, landscape episode rows, ~1.02 focus scale, focus restoration, and horizontal supporting rows.

No Nuvio backend, models, routing or source code are copied into VUEO.

## Important contract note

VUEO's current `MediaItem` model has no trailer field/route. 34A therefore does **not** fabricate a dead Trailer button. A trailer action can be added later when the VUEO data contract exposes a real trailer target.

## Regression lock
- Home 32C untouched
- Sidebar 33A untouched
- Source/Player untouched
- Mobile untouched
