# AI Handoff

**Read `PROJECT_CONTEXT.md` before modifying this repository.**

VUEO is a Mobile + Android TV monorepo with `:shared:core` as the reusable logic layer.

Critical rules:

- Search Shared Core before adding reusable logic to Mobile or TV.
- Mobile Player UI is the current canonical Player visual reference. TV adapts it for D-pad and 10-foot use.
- Mobile Settings hierarchy is the current canonical Settings visual reference. TV adapts it for D-pad and 10-foot use.
- `Content Manager` is the official name for addon/provider management.
- Do not reintroduce TV-only hardcoded Cinemeta discovery when Shared Core already provides discovery/meta.
- Do not casually touch stable source ranking/recovery during unrelated UI work.
- Many `mobile/core/...` files are compatibility aliases to Shared Core, not duplicate implementations.
- TV must remain fully D-pad operable with obvious white + scale focus.
- Preserve repository-relative paths in replacement ZIP patches.
- For patch ZIPs containing only module paths such as `tv/`, include root `build.gradle.kts` as a repository-root marker to avoid extraction ambiguity.

Source code is authoritative for exact signatures. `PROJECT_CONTEXT.md` is authoritative for current architecture and locked product direction.
Current Mobile stabilisation rules:

- Do not let Home render before the startup destination gate resolves Who's Watching.
- Mobile Content Manager presentation now lives in `ui/VueoContentManager.kt`; do not copy it back into `VueoApp.kt`.
- Per-catalog enable/disable is real persisted state and must continue filtering Home discovery/cache results.
- Provider diagnostics should be compact in the list but must preserve useful request/failure/error/HTTP/timing/result/raw evidence when expanded.
- User-facing Content Manager copy should avoid third-party platform branding. Internal protocol/runtime names may remain in implementation code.
- Continue `VueoApp.kt` cleanup only as small behaviour-preserving extractions. Keep startup, profile and player flows isolated from unrelated refactors.
28G Mobile lock:

- Read `MOBILE_REGRESSION_LOCK.md` before modifying Mobile.
- Mobile v1 is feature-locked during the TV rebuild.
- `Ask on startup` must show Who's Watching whenever the setting is enabled, even if only one profile currently exists.
- Do not reintroduce the old `profiles.size > 1` gate in either the setting control or `ProfileStore.shouldShowPickerOnStartup()`.

29A-R2 TV clean rebuild:

- Read `TV_REBUILD_LOCK.md` before modifying TV.
- Do not resurrect tombstoned legacy TV repositories, stores, root navigation or UI screens.
- Preserve the approved Who's Watching / Manage Profiles / Add/Edit Profile experience.
- Everything after profile selection uses the new TV runtime and fresh TV UI.
- Behaviour comes from Mobile-proven semantics implemented through Shared Core; TV must not depend on `:mobile`.
- Home remains locked to contextual top navigation + Hero/Peeking Row + card-driven hero with ~180 ms settle.



## TV 29B — Premium Cinematic Home

- Built on the green 29A-R + build-fix baseline.
- Home only: larger hero breathing area, first rail at ~62% viewport, fully contextual nav labels, layered cinematic scrims, 244dp landscape cards, external captions, shallow neutral focus depth.
- Preserves 180ms card-driven hero settle and all clean-rebuild runtime/data contracts.
- Profile flow, Search, Library, Settings, Details, Source and Player behaviour are intentionally unchanged by 29B.

## TV 29B.1 lock
Top navigation now commits with a single OK/Enter press; never reintroduce stacked `focusable + clickable` focus targets for one nav control. TV Settings is active as a clean TV-native Shared-Core-backed surface. Home rail title-to-card spacing is 19dp while the 29B rail vertical anchor remains unchanged.

### TV 29B.2 Settings lock
- Settings hierarchy: Personalization, Content Manager, Enhancements, Playback, Subtitles, Sources, Appearance, Data & Storage, Updates, About VUEO.
- Keep TV UI remote-native; reuse Shared Core/Mobile behavior rather than legacy TV Settings code.
- Settings row grammar: Up/Down focus, Left/Right adjust, one OK to activate, Back to parent; Up from first enabled row enters contextual nav and Down restores last row.
- Do not expose a toggle/value unless TV has a real runtime consumer or it is explicitly informational.
- Playback/subtitle/source/appearance/data/update consumers introduced by 29B.2 are regression-protected.

## TV 29C Search lock
TV Search is now Mobile Search parity adapted only for TV scale/focus/D-pad. Preserve Discover + Title/Actor + Type/Sort/Genre + poster grid and the root-owned `TvSearchSession` return-state behavior. Do not restore the old minimal title-only TV Search.


## TV 29C.1 calibration lock
Latest TV Search calibration removes the persistent VUEO wordmark from post-profile page chrome and compacts Search to the approved cinematic TV composition. Search uses an 8-column 2:3 poster grid, a ~76% width search field, one compact filter/mode row and shallow 1.035 focus scale. Keep 29C search logic/return-state behavior and 29B Home hero logic unchanged. Startup/profile branding remains.

## TV 29C.2 floating navigation lock
Global TV navigation now uses a centered floating capsule: Home / Search / Library / Settings. Profile stays separate at top-right; no VUEO wordmark returns to normal app pages. The capsule is a translucent charcoal surface with a restrained selected pill and shallow focus treatment. D-pad movement remains focus-only, OK commits once, and Home/Search keep the contextual collapse/reveal grammar from 29B.

## TV 29C.2a update popup lock
Startup automatic update checks now surface the existing TV updater result through `TvUpdatePrompt`. Preserve the single `TvUpdateManager` engine and modal D-pad behavior. The exact pre-rebuild prompt source is not available in the current repo (legacy updater UI is tombstoned), so do not claim pixel-identical restoration; behavior is restored on the surviving updater engine.

## 29C.3 current TV navigation

The centered 29C.2 top navigation capsule is superseded. Use `TvSidebar` from `tv/ui/TvTopBar.kt`: 66dp collapsed / 202dp expanded, Home → Search → Library → Settings, Profile at bottom. LEFT from first logical content column enters current destination; RIGHT restores exact last content focus when possible; UP/DOWN explores; OK commits once; focus never routes. Keep Search 8-up poster density and 29C.2a update popup restore intact.

## TV 29C.4 — Nuvio-reference direction
The maintainer explicitly chose the supplied `NuvioTV-0.8.6-beta` source as the UI reference for subsequent TV rebuild work. Before designing a TV screen/chrome from scratch, inspect the relevant Nuvio source first. Treat Nuvio as the presentation/D-pad reference only; VUEO still owns its routes, data/runtime, Shared Core contracts, profile behavior and theme.

The first implementation is the global sidebar: `TvSidebar` now follows Nuvio's modern floating-sidebar pattern rather than 29C.3's permanent slim rail. Collapsed state is a floating current-route pill (hidden on Search, label can collapse after idle); expanded state is an inset rounded overlay panel with Profile at top and Home/Search/Library/Settings centered. LEFT enters navigation, RIGHT restores last content focus, focus never routes, one OK commits once.

## TV 29D Library current direction

Do not rebuild Library from the old TV horizontal rails. Mobile VUEO is canonical for Library feature/data behavior; the supplied Nuvio project is the TV visual/focus reference only. Current Mobile Library = `LibraryStore.watchlist()` / My List, Cloud placeholder, Grid/List toggle persisted under `vueo_library_ui` → `grid_view`. Continue Watching and History are not visible Library sections, though their Shared Core data remains valid elsewhere.

`TvLibraryScreen.kt` now follows that contract with a responsive poster canvas (target width, not fixed 8 columns), compact list mode, Nuvio-proportioned 1.02 focus scale / 180ms motion, 29C.4 sidebar entry/return, and TV-only ephemeral last-item/scroll restoration after Detail. Do not create a second Library data store. Do not treat Search's old 8-up density as a global rule.

## TV 29E Detail current direction

`TvDetailScreen.kt` is now the functional Detail baseline. Behaviour/data comes from Mobile + Shared Core; supplied Nuvio Detail source is the presentation/focus reference. Keep resume-aware Watch/Play labels, My List, season/episode state, progress, facts/ratings/DNA, credits, Overview, Cast, companies, More Like This and manual VUEO Insight where configured. Cast/company remain informational because VUEO Mobile has no cast/company navigation contract. Source and Player are still the next functional rebuilds. Do not spend a separate polish pass on Library/Detail yet; the maintainer wants a final whole-TV polish after the functional flow is complete.

## TV 29F Source Selection current direction

`TvSourceScreen.kt` is now the functional Source baseline. VUEO Mobile's Source Picker is canonical for ranking/filtering/recommendation/diagnostic behavior; supplied Nuvio `StreamScreen.kt` is the TV composition/focus reference. TV now shows shared cached results immediately, publishes addon/plugin results progressively, preserves provider order, exposes Engine Details, marks the VUEO-recommended source, obeys `showSourceTechnicalDetails`, and restores the last source/provider when returning from Player where possible.

`TvRuntime.discover(...)` gained an optional `onUpdate(TvSourceDiscoverySnapshot)` callback but still returns the same final `TvSourceBundle`. Do not replace Shared Core discovery/ranking/cache with TV-only logic. Source is still a functional baseline; exact visual calibration waits for the final whole-TV polish after Player.

## TV 30A Home polish
Do not reintroduce top navigation. Home keeps the 29C.4 floating `TvSidebar`. Preserve the ~50% hero reading zone, medium 224dp Continue Watching landscape cards, 128dp 2:3 My List/catalog posters, restrained ~1.028 focus scale and the existing exact sidebar return-to-content behavior unless a later explicit product decision supersedes it.
