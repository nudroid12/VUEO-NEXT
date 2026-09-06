# TV 32B — Home Presentation Rewrite (Nuvio reference)

Scope: Home presentation only.

## Kept from VUEO
- `TvRuntime.homeRows()` loading/refresh behavior
- `LibraryStore.continueWatching()` and `watchlist()`
- media/open/resume callbacks
- VUEO routes in `VueoTvApp`
- current shared sidebar (`TvSidebar`) is intentionally **held unchanged** for the later sidebar pass

## Deleted from the Home presentation
The old one-file Home presentation and its manual per-card D-pad routing are not reused. The rewrite does not contain the previous `HomeBackdrop`, `HomeScrim`, `HomeHeroCopy`, `HomeMediaRow`, `HomeMediaCard`, `tvPremiumFocus`, or `onPreviewKeyEvent` Home implementation.

## New presentation structure
- `TvHomeModels.kt` — presentation-only models + focus memory
- `TvHomePresentation.kt` — screen composition and hero focus settling
- `TvHomeHero.kt` — independent hero media / gradient / metadata layer
- `TvHomeRows.kt` — independent rows, focus restoration, bring-into-view, cards
- `TvHomeScreen.kt` — VUEO data/routing boundary only, plus the held shared sidebar shell

## Nuvio source references used
From uploaded `NuvioTV-0.8.6-beta.zip`:
- `ModernHomeContent.kt`
- `ModernHomeHero.kt`
- `ModernHomeRows.kt`
- `ModernHomeRowsList.kt`
- `ModernHomeModels.kt`
- `SizeTokens.kt`
- `LayoutMediaTokens.kt`

Adapted presentation principles:
- hero text width: 42%
- hero media width: 72%
- rows viewport: 52% (portrait-poster mode)
- hero height derived from `screenHeight - rowsViewport + row title allowance`
- row horizontal padding: 52dp
- modern poster card: ~114x172dp from Nuvio's 126x189 base and Modern Home scale
- Continue Watching card: ~210x119dp from Nuvio's modern CARD formula
- 450ms hero focus settle
- focus restoration at content -> row -> item levels
- custom vertical/horizontal bring-into-view behavior instead of manual D-pad routing on every card
- hero artwork isolated to the upper/right scene with two-axis black fade into the rows

This is an independent VUEO implementation using Nuvio as UX/architecture reference; Nuvio application code is not copied wholesale.
