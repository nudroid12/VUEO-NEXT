# TV 29E — Detail Functional Rebuild: Mobile Behaviour + Nuvio TV Reference

Baseline: TV 29D Library functional baseline on top of 29C.4 Nuvio-reference navigation.

## Direction

29E rebuilds **Detail only**. This is a functional baseline, not the final polish pass.

- VUEO Mobile Detail is the canonical behaviour/data source.
- Shared Core remains the runtime/data boundary.
- Supplied `NuvioTV-0.8.6-beta` is the TV composition/focus reference.
- No Nuvio data model, repository, tracking service or code dependency is imported.
- Library/Home/Search/sidebar visuals are intentionally not re-polished in this patch.

## Mobile behaviour audited

The Mobile `MediaDetailsScreen` carries the following Detail semantics that 29E keeps/adapts:

- enriched metadata shell from the existing media engine/enrichment pipeline
- My List add/remove state through `LibraryStore`
- movie/series primary action semantics
- resume awareness from shared playback/history state
- series season selection and selected episode state
- episode playback progress
- release/season/runtime/certification facts
- IMDb/TMDB/supplemental ratings plus optional VUEO DNA match
- creator/director/writer credits
- Overview
- Cast
- Network / Production companies
- More Like This
- optional Gemini/VUEO title insight when enabled and configured

## Nuvio TV patterns adapted

Reviewed supplied Nuvio Detail sources, especially:

- `ui/screens/detail/MetaDetailsScreen.kt`
- `ui/screens/detail/HeroSection.kt`
- `ui/screens/detail/EpisodesSection.kt`
- `ui/screens/detail/CastSection.kt`
- `ui/screens/detail/MoreLikeThisSection.kt`

TV presentation patterns adapted:

- sticky cinematic full-screen backdrop with strong left/bottom readability scrims
- hero-owned Play/Resume + Library actions
- initial focus on the hero primary action
- shallow ~1.02 focus motion instead of glow/bounce
- horizontal season tabs followed by a landscape episode row
- episode progress on the episode artwork
- supporting Cast / company / More Like This rows below the hero
- landscape More Like This cards in the Nuvio TV proportion family
- KeyUp-only OK/Enter activation for custom TV controls

## 29E implementation

`tv/src/main/java/com/vueo/tv/detail/TvDetailScreen.kt` is rebuilt while keeping its existing route signature so Source/Player work can continue later without a root navigation rewrite.

- Metadata, ratings and related titles still come from `TvRuntime` / Shared Core.
- Base IMDb/TMDB values carried on `MediaItem` are merged with supplemental ratings like Mobile.
- Series default selection prefers the latest resumable episode in shared history, then falls back to the first valid season/episode.
- Primary action labels are `Resume`, `Resume Sx Ex`, `Play Sx Ex`, `Select an Episode`, or `Watch` according to current media/playback state.
- Player resume remains backed by the existing `PlaybackStore` fallback used by `TvPlayerScreen`; 29E does not fork playback position storage.
- My List toggles the existing shared `LibraryStore` and triggers the existing library refresh callback.
- Overview has a compact TV preview and an OK-expand/collapse surface so long Mobile metadata is not discarded.
- Cast is informational because current VUEO Mobile has no cast-detail navigation contract. It is not given a fake TV-only route.
- Network/Production remains informational, matching current Mobile behaviour.
- More Like This keeps the existing `onOpenRelated` route contract.
- VUEO Insight is explicit/manual: when Gemini insights are enabled and configured, OK generates through the existing `TvRuntime.geminiInsight()` path rather than introducing another client.

## Explicitly not changed

- Mobile source/UI
- Shared Core media/storage/enrichment/source logic
- Home
- Search
- Library 29D functional baseline
- 29C.4 sidebar
- Source
- Player
- updater

## Validation status

Static delimiter/path checks pass. Local Gradle compilation remains unavailable in this environment because the wrapper distribution cannot resolve `services.gradle.org`; GitHub Actions/device build remains authoritative.

Final TV-wide density, spacing, typography, sidebar alignment and motion calibration is deferred to the requested final polish pass after Detail → Source → Player functional rebuilds are complete.
