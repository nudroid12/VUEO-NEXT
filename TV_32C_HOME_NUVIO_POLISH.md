# TV 32C — Home Nuvio Polish

Base: TV 32B (green GitHub Actions).

Scope: Home presentation/behavior polish only. Backend, data, routing, and the shared sidebar stay unchanged.

## What changed
- Hero gradient now lives inside the hero-media bounds instead of dimming the entire Home surface.
- Horizontal hero fade follows Nuvio Modern Home's ~45% leading fade profile.
- Bottom hero fade starts late (~82% of hero-media height) for a cleaner image-to-rows handoff.
- Hero media crossfade tightened to 300ms; metadata remains on the settled 450ms focus model.
- Vertical row bring-into-view now uses Nuvio's 40dp row-header anchor.
- Removed the extra per-row `animateScrollToItem()` that could fight Compose bring-into-view during D-pad vertical movement.
- Focus restoration remains content -> row -> saved item.
- Card focus gets a restrained 1.022x compositor-only scale plus the existing 2dp white focus border.
- Focused cards use z-order lift so scaling does not visually tuck under neighboring cards.
- Continue Watching progress/remaining/episode metadata behavior is preserved.

## Intentionally unchanged
- `TvRuntime`, Home loading/refresh, watchlist and Continue Watching data
- VUEO open/resume callbacks and routes
- shared `TvSidebar` (held for the dedicated sidebar rebuild)
- 32B Home row/card dimensions and 52% rows viewport

Reference: uploaded NuvioTV 0.8.6 Modern Home (`ModernHomeContent.kt`, `ModernHomeHero.kt`, `ModernHomeRows.kt`, `ModernHomeRowsList.kt`).
