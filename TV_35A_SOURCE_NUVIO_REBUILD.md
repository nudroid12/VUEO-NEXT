# TV 35A — Source Selection Nuvio Rebuild

Baseline: `VUEO-NEXT-main (17).zip` (includes green 32C Home, 33A Sidebar and 34A Details).

## Boundary kept VUEO

`TvSourceScreen.kt` now owns only VUEO source semantics:
- progressive `runtime.discover(...)`
- cache/fresh discovery updates
- direct-play gating
- shared `PlayerSourcePolicy` ranking and recommendation
- provider ordering/filtering
- source technical-detail setting
- VUEO bundle + playback callback

No provider, routing, resolver or playback backend was replaced.

## Presentation rebuilt

The old 29F Source Compose presentation was removed and rebuilt as:
- `TvSourcePresentation.kt`
- `TvSourceModels.kt`

The supplied NuvioTV 0.8.6 `StreamScreen.kt` is the layout/focus reference:
- full-screen backdrop
- Nuvio-style symmetric horizontal gradient stops (0/15/30/50/70/85/100)
- 40/60 identity/results split
- centered title/episode metadata on the left
- filter row above a translucent source panel
- progressive source list with stable per-source focus requesters
- first-result focus assignment
- focus memory restoration
- UP from first source returns to the active filter
- LEFT/RIGHT while in the source list cycles provider filters
- 112ms repeat throttle for provider cycling
- no focus scale on source cards; focus uses a clean ring
- skeleton list while discovery is still empty

VUEO-specific additions kept in the new presentation:
- Refresh action
- All/provider filters
- Details toggle for discovery diagnostics
- RECOMMENDED marker from VUEO source policy
- quality/file-size and optional technical detail display

## Regression boundary

35A does not change:
- Home 32C
- Sidebar 33A
- Details 34A
- `VueoTvApp` routes
- `TvRuntime`
- source engines/providers/resolvers
- player screen
