# TV 39A1 — Build Fix

Fixes the compile failure reported by GitHub Actions after applying 39A over the prior 38A tree.

## Root causes fixed

1. 38A source files remained in the repository and were still compiled alongside 39A.
   - This duplicated `detailPlaybackEntry`, `detailCanResume`, `detailPrimaryActionLabel`, and related helpers.
   - The old 38A presentation files are now compatibility tombstones so only the 39A Nuvio-first presentation compiles.
2. `TvDetailNuvioHero.kt` directly imported OkHttp although the TV module does not expose OkHttp on its compile classpath.
   - The logo now uses VUEO's existing `TvNetworkImage`, removing the direct OkHttp dependency.

## Scope

Only TV Details build compatibility is changed. Source, Player, Home, Sidebar, mobile, routing and playback contracts are untouched.
