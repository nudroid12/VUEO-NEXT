# TV 29F — Source Selection functional baseline

## Scope
29F rebuilds the TV Source Selection surface only. It is a functional baseline, not the final whole-TV polish pass.

## Product rule
- VUEO Mobile Source Picker remains canonical for source behavior/data.
- Supplied Nuvio TV source is the presentation, layout and D-pad reference.
- Shared Core remains the owner of addon/plugin discovery, source cleanup/ranking, source policy and the short-lived discovery cache.
- No `:mobile` dependency and no legacy TV source engine is introduced.

## Mobile parity carried into TV
- direct-playable gating stays HTTPS-only through the existing `StreamSource.isDirectPlayable` contract;
- source ordering uses the same Shared Core `SourceCleaner` / `PlayerSourcePolicy` semantics and preferred-quality/original-language context;
- cached results can appear immediately while a fresh discovery continues;
- addon and plugin results publish progressively instead of waiting for every provider to finish;
- first-result timing, raw-result count, duplicate-removal summary and plugin-provider diagnostics are available through Engine Details;
- VUEO recommendation is retained and marked on the best eligible source;
- provider filtering follows first-discovery/provider order;
- the existing `showSourceTechnicalDetails` setting controls the extra raw source-name/technical line;
- selecting a source keeps the existing `TvSourceBundle` handoff into Player.

## Nuvio-reference TV composition
Reference inspected from the supplied project:
- `ui/screens/stream/StreamScreen.kt`
- `RightStreamSection`
- `AddonFilterChips`
- `StreamsList`
- `StreamCard`

VUEO adapts the same broad grammar:
- full-screen cinematic artwork/scrim;
- identity + engine-state pane on the left;
- provider chips and the source list in a rounded translucent workspace on the right;
- no focus zoom on source rows; focus is primarily edge/surface contrast;
- first source gets initial focus when results arrive if the user has not already interacted;
- UP from the first result returns to the active provider chip;
- LEFT/RIGHT while a source row owns focus cycles provider filters, matching the Nuvio stream-list shortcut;
- failure state exposes a D-pad reachable Retry action, with UP returning to Engine Details;
- OK/Enter activates once on KeyUp.

## Return-state behavior
A small TV-only in-memory UI state remembers the selected provider, Engine Details state and exact source key. Returning from Player to Source uses the shared short-lived source cache and restores the last focused source when it is still available. This does not create a second source-data store.

## Runtime change
`TvRuntime.discover(...)` now exposes optional progressive `TvSourceDiscoverySnapshot` updates while preserving its existing final `TvSourceBundle` return type. The underlying discovery engines and Shared Core contracts are unchanged.

## Not part of 29F
- Player UI/behavior rebuild;
- final spacing/type/motion calibration across all TV screens;
- torrent/debrid/external-player features not already supported by current VUEO Mobile/Shared Core contracts.
