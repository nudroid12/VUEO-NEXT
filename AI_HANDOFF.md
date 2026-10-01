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
- Mobile Content Manager presentation now lives in `ui/content/VueoContentManager.kt`; do not copy it back into `VueoApp.kt`.
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

## TV 29C.4 — VUEO TV presentation direction
The current VUEO TV presentation direction is cinematic, remote-first and reference-driven. Preserve the established composition, density, focus grammar, overlays, motion and D-pad behavior while VUEO continues to own routes, data/runtime, Shared Core contracts, profile behavior and theme.

The first implementation is the global sidebar: `TvSidebar` now follows the current VUEO floating-sidebar pattern rather than 29C.3's permanent slim rail. Collapsed state is a floating current-route pill (hidden on Search, label can collapse after idle); expanded state is an inset rounded overlay panel with Profile at top and Home/Search/Library/Settings centered. LEFT enters navigation, RIGHT restores last content focus, focus never routes, one OK commits once.

## TV 29D Library current direction

Do not rebuild Library from the old TV horizontal rails. Mobile VUEO is canonical for Library feature/data behavior; TV keeps the established VUEO visual/focus grammar. Current Mobile Library = `LibraryStore.watchlist()` / My List, Cloud placeholder, Grid/List toggle persisted under `vueo_library_ui` → `grid_view`. Continue Watching and History are not visible Library sections, though their Shared Core data remains valid elsewhere.

`TvLibraryScreen.kt` now follows that contract with a responsive poster canvas (target width, not fixed 8 columns), compact list mode, VUEO 1.02 focus scale / 180ms motion, 29C.4 sidebar entry/return, and TV-only ephemeral last-item/scroll restoration after Detail. Do not create a second Library data store. Do not treat Search's old 8-up density as a global rule.

## TV 29E Detail current direction

`TvDetailScreen.kt` is now the functional Detail baseline. Behaviour/data comes from Mobile + Shared Core; TV presentation/focus follows the established VUEO detail grammar. Keep resume-aware Watch/Play labels, My List, season/episode state, progress, facts/ratings/DNA, credits, Overview, Cast, companies, More Like This and manual VUEO Insight where configured. Cast/company remain informational because VUEO Mobile has no cast/company navigation contract. Source and Player are still the next functional rebuilds. Do not spend a separate polish pass on Library/Detail yet; the maintainer wants a final whole-TV polish after the functional flow is complete.

## TV 29F Source Selection current direction

`TvSourceScreen.kt` is now the functional Source baseline. VUEO Mobile's Source Picker is canonical for ranking/filtering/recommendation/diagnostic behavior; TV composition/focus follows the established VUEO source-selection grammar. TV now shows shared cached results immediately, publishes addon/plugin results progressively, preserves provider order, exposes Engine Details, marks the VUEO-recommended source, obeys `showSourceTechnicalDetails`, and restores the last source/provider when returning from Player where possible.

`TvRuntime.discover(...)` gained an optional `onUpdate(TvSourceDiscoverySnapshot)` callback but still returns the same final `TvSourceBundle`. Do not replace Shared Core discovery/ranking/cache with TV-only logic. Source is still a functional baseline; exact visual calibration waits for the final whole-TV polish after Player.

## TV 30A Home polish
Do not reintroduce top navigation. Home keeps the 29C.4 floating `TvSidebar`. Preserve the ~50% hero reading zone, medium 224dp Continue Watching landscape cards, 128dp 2:3 My List/catalog posters, restrained ~1.028 focus scale and the existing exact sidebar return-to-content behavior unless a later explicit product decision supersedes it.

## TV 30C Home source-referenced rebuild

- Home/sidebar were rewritten around the current VUEO TV composition and D-pad grammar.
- VUEO keeps its own data/routes; only layout and focus principles were adapted.
- Collapsed TV navigation is now a stable icon-only rail; no floating route pill.
- Home rows begin at ~49% viewport height, hero copy is ~42% width, CW is landscape and catalogs remain portrait.
- Per-row focus memory and deterministic UP/DOWN row transfer are part of the Home contract.

## TV 32A Home true rebuild

31A/31B are rejected as Home rebuild baselines. TV 32A is the new Home presentation baseline.

- `TvHomeScreen.kt` is now only the Home data boundary.
- Home visual composition lives in `TvHomePresentation.kt`.
- Home navigation/sidebar lives in `TvHomeNavigation.kt`.
- Home must not import or invoke the legacy `TvSidebar` from `TvTopBar.kt`.
- Preserve the established VUEO structural ratios and focus model described above unless a later explicit product decision replaces them.

## TV 44B subtitle workspace correction

- Preserve the existing three-column VUEO subtitle workspace composition.
- Its overlay is deliberately transparent so the video and subtitle remain visible at their real positions while sync is adjusted.
- Do not add a Float mode and do not relocate the rendered subtitle while this workspace is open.
- Opening the workspace must transfer focus directly into its selected language row. If initial focus is missed, the next D-pad direction or OK/Enter must recover focus into the panel.

## TV 45 motion polish

- TV motion uses short fade-throughs, shallow scale and no spring or bounce.
- Focus scale timing is 120ms in and 90ms out with the shared `TvMotion.EaseOut` curve.
- General screen transitions are 250ms in and 130ms out. Player route transitions are fade-only at 180ms in and 90ms out so video never zooms.
- Player chrome, scrim and workspaces animate independently from the video. The transparent subtitle workspace remains fade-only and does not move the rendered subtitle.
- Hero and backdrop transitions are capped near 270ms. Do not restore the previous 420ms to 500ms fades.
- This revision was reviewed statically only. No local Gradle build was run, as requested by the maintainer.

## TV Player — hidden seek feedback + directional chrome

- LEFT/RIGHT while player controls are hidden seeks immediately on KeyDown, including accelerated held-key repeats, without revealing full controls or moving focus away from the root.
- Hidden seek feedback is a display-only progress rail with no focus target. It fades out after 1.5 seconds without another seek; opening controls or a workspace clears it.
- Hidden seeks already applied on KeyDown must not be applied again on KeyUp. Visible-rail seeks retain release-to-commit and the missing-release fallback.
- Main player chrome now has independent anchored motion: top enters/exits through the top edge, bottom enters/exits through the bottom edge. Video, subtitle rendering and workspace animations keep their existing behavior.
- This explicitly supersedes the main chrome's previous fade-only presentation. Full TV compilation and real-remote motion verification remain required; the patch was reviewed statically in the handoff environment.

## TV Player — Skip & Auto-next calibration

- Intro/Recap prompts use bottom-left; Ending/Credits and Next use bottom-right. Prompts use 14sp text, a 44dp minimum height and 320dp maximum width. Same-side prompts stack with real layout spacing, not fixed overlapping offsets.
- Prompt inset adapts to chrome visibility and the configured subtitle baseline, reserving three subtitle lines plus spacing. Unusually tall custom cues still require real-TV calibration. Prompts are hidden while workspaces or playback errors are open.
- Prompt focus persists across changing countdown labels. Removed focused prompts restore progress/root focus; new skip prompts do not steal focus.
- `PlayerSkipPolicy` validates intervals against the actual video duration. An ending within 1,000ms of video end permits an early 8-second auto-next countdown. A longer tail, unknown duration or invalid interval waits for actual playback end. This is an explicitly approved duration heuristic, not provider confirmation that no post-credit scene exists.
- Skip Credits only seeks to the segment endpoint; it never invokes next-episode navigation directly. Pending seek/fallback state is cleared before Skip, Restart and episode navigation.
- Early countdown resets on seeking, pause/buffer, workspace, recovery or error. Back cancels automatic next for the current episode. Navigation dispatch is guarded against repeats and early auto-next records the episode as complete.
- Empty skip cache expires after 30 seconds; ready results after 10 minutes. TV retries empty results at most twice, 31 seconds apart. No movie skip support was added, no Mobile UI changed, and the prior hidden-seek/directional-chrome patch is retained.
- Verification: static source/signature/delimiter and ZIP checks only. Policy regression tests are included for CI but were not executed locally. Do not run a local build; the maintainer uses GitHub Actions.

## TV Player — prompt position correction

- Skip/Next prompts now sit 12dp above the progress rail, based on the measured bottom-controls height. Hidden-mode prompts use the existing feedback rail position (22dp bottom inset plus 3dp rail height).
- Subtitle clearance no longer raises prompt positions. Intro/Recap remain left; Ending/Next remain right; prompt sizes, focus, seek and auto-next behavior are unchanged.
- This patch follows the Skip & Auto-next patch. Static checks only; do not run a local build.

## TV Player — next card, remaining time and hidden-input correction

- Next Episode now uses a dark, rounded thumbnail card with episode metadata and a Play/countdown pill. The entire card is one focus target. Missing episode artwork falls back to the media background through the existing image loader.
- Countdown/card focus does not reveal full player chrome. Hardware OK activates a focused hidden-mode card; hidden LEFT/RIGHT still seek. Existing ending/duration/post-credit heuristics remain unchanged.
- The right-hand progress timestamp shows negative remaining time (`-23:45`), clamped at zero. Unknown duration displays `--:--`.
- `onIsPlayingChanged(false)` must not reveal controls while `playWhenReady` is true: this includes seek-induced buffering. Real pause may reveal controls.
- Player root focus remains attached while controls are visible. `hideControls()` cancels pending chrome focus and moves focus directly to root. Chrome retained during exit has input/focus/focused styling disabled through `LocalPlayerChromeInteractive`.
- Focus retries now accept an eligibility callback and stop when their target is no longer available. The release of an OK press that merely reveals controls is consumed so it cannot accidentally toggle playback after focus transfer.
- Keep the earlier 12dp progress-relative prompt positioning and skip/auto-next safety behavior. Apply this patch after the previous Skip & Auto-next / prompt-position patches. Static checks only; no local build or TV runtime validation was performed.
