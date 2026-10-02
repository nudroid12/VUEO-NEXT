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

## TV Player — compact next card and single-Back dismissal

- Next Episode card width is now 360dp, with 10dp padding/spacing, a 96x54dp thumbnail and a 76dp minimum height. Long episode titles retain two-line ellipsis. The whole card remains one remote focus target.
- Only the Play row has a small pill outline; countdown appears below it, outside the pill. Existing progress-relative positioning is retained.
- Back closes an open workspace first. Otherwise, when controls or the next countdown are visible, one Back cancels any active countdown and hides controls with the existing direct-root focus handoff. It no longer reveals progress controls just to cancel the countdown. Back with both hidden retains player exit behavior.
- Apply after VUEO_TV_NextCard_Seek_Focus_Patch.zip. Seek, remaining-time, slide motion and post-credit safety policy are unchanged. Static source and archive checks only; no local build or TV runtime test was performed.

## TV Player — root Back capture and inline Play countdown

- Root preview captures hardware Back down/repeats/up when no workspace is open. It consumes both halves, invokes the shared player Back action once on an uncancelled release, and leaves workspace events to existing handlers. System BackHandler uses the same action.
- Ended controls dismissed by Back are guarded in both the playback-ended listener and ended-focus effect. The guard resets after leaving ended playback. Active countdown cancellation and immediate root-focus handoff remain in the shared Back action.
- Countdown is now inline inside the small Play pill (`Play • 5s`), keeping the 360dp card and a single focus target.
- Apply after VUEO_TV_Compact_NextCard_Back_Fix.zip. Static checks only; no local build or real-remote verification.

## TV Player — episode switching inside the player route

- Next button, automatic countdown and Episodes selection now call in-player episode discovery. PLAYER route and the old playable bundle remain mounted during discovery; source discovery updates for the target episode are held separately until commit.
- Automatic selection uses Shared Core PlayerSourcePolicy and the same preferred-quality/original-audio eligibility as Mobile. Below-policy fallback is considered only after discovery completes. New bundle/source/episode are committed together; a keyed TV playback session releases the previous player and initializes the target episode without navigating to SOURCE.
- A remote-focused overlay remains until the target playback renders its first frame. Discovery/no-source/startup failures show Retry and Sources inside the player. Manual source selection recreates the pending playback session. The existing startup timeout and automatic source recovery remain active.
- Back cancels pending discovery or dismisses preparation/failure, keeping the currently mounted player. Generation guards reject cancelled/old discovery results; post-commit late results only enrich the matching active bundle. Duplicate episode requests are rejected during a switch.
- Overlay owns focus while switching. Player focus retries, auto-next and listener-triggered chrome reveals are suppressed until completion/cancellation. Existing skip/post-credit heuristics and root Back capture are retained.
- Mobile and Shared Core source files are unchanged. Apply after VUEO_TV_Back_Capture_Play_Countdown_Fix.zip. Static source/signature/control-flow/archive checks only; no local build or actual TV/network test was performed.

## TV Home — progressive loading and cache freshness

- Startup still reads only local boot/profile state before showing Home. Disk cache restoration runs before addon preparation; it logs cache readiness and refreshes the retained Home state. Provider sync/update checks remain separate background tasks.
- Manifest results are installed and signalled as each finishes, before the aggregate awaitAll completes. Home watches addon revisions and schedules each available addon once, with a shared four-request catalog gate across all addon jobs. Completion is read before taking the addon snapshot to avoid skipping a manifest installed during the final scheduling pass.
- Shared UnifiedMediaEngine supports addon-scoped catalog loads without overwriting the complete Home cache. Completed nonempty rows publish immediately, serialized under the row lock rather than batched in groups of four. Default callers still cache their complete result.
- Fresh catalog cache keeps the existing 10-minute TTL and is accepted when the persisted TV configuration fingerprint matches manifests/enabled state/catalog order/disabled rows. Startup no longer invalidates fresh catalog cache after manifest preparation. Old installations establish the fingerprint on their first completed refresh. Home re-entry checks expiry even when its refresh token did not change. Explicit/configuration refresh paths retain cache invalidation.
- Progressive fresh rows replace matching cached rows while untouched cached rows remain visible. Final cache is persisted only after discovery completes; cache fallback is retained on empty/error results. Recommendation scoring follows immediate local Continue Watching/cache rows, keeping existing recommendation targets until recomputation completes.
- Shared Stremio HTTP uses suspendCancellableCoroutine and cancels the underlying OkHttp Call when timeout/navigation cancels the coroutine. Response bodies are closed even after cancellation. This fixes the effectiveness of existing eight-second catalog timeouts and also applies to Mobile Stremio transport; Mobile UI is unchanged.
- TV Home images start only for cards intersecting both visible row/card viewports (or focused cards). Hero/focus requests can use the reserved third image slot; ordinary image work is capped at two within the existing global maximum of three. Existing loaded images and disk/memory cache are retained.
- Logcat tag VUEO_HOME records cache_restored, manifest_ready, manifests_finished, fresh_cache_used, first_fresh_row, first_rows_presented and catalogs_finished/fallback, with elapsed milliseconds/counts and no addon URLs. first_rows_presented is a Compose frame opportunity marker, not GPU-render timing or a promise that artwork is ready.
- Four Shared Core regression tests cover immediate first/second-row publication despite a blocked remainder, scoped-load cache preservation, aggregate catalog concurrency and cancelled-slot release. Included for CI; not run locally. Static source/signature/control-flow/archive checks only, no local build or TV/network measurement. Keep the prior player patches; apply this patch after VUEO_TV_InPlayer_Episode_Switch_Patch.zip.

## About — update action placed last on Mobile and TV

- About order is now VUEO/version, Privacy, TMDB Attribution, Check for updates on both Mobile and TV. Existing update callbacks/status/install logic are unchanged. Explicit maintainer authorization permits this Mobile UI ordering adjustment.
- TV keeps the update entry key and activation callback, with the existing settings-list focus handling. Static ordering/block-preservation/archive checks only; no local build.

## TV Home — vertical row motion correction

- Vertical Home focus alignment is now owned by one cancellable row-scroll job, using a 180ms EaseOut tween and a 2px settle tolerance. Row headers align at viewport top, retaining the approximate 40dp poster/header inset. Incoming focus on a different row retargets from the current scroll position; same-row left/right focus does not restart vertical motion.
- The outer BringIntoViewSpec returns zero to prevent competing automatic vertical relocation. Do not attempt to tune the deprecated scrollAnimationSpec property: Compose stopped supporting that customization in 1.8.0. Horizontal BringIntoViewSpec and card activation/focus/image behavior remain unchanged.
- Target distance uses visible layout offsets or measured row heights plus spacing for a beyond-viewport focus target. One frame permits lazy target measurement before calculating the distance. Layout estimates are used only for unmeasured rows. The job is cancelled on disposal or floating-navigation preview, preserving the existing preview restoration path.
- Outer LazyColumn uses a 232dp ahead/behind LazyLayoutCacheWindow (roughly one poster row) and row-kind content types for composition reuse. Inner LazyRow behavior is unchanged. Home image viewport gates remain in place, so cached offscreen composition does not start image downloads.
- API references: https://developer.android.com/reference/kotlin/androidx/compose/foundation/gestures/BringIntoViewSpec and https://developer.android.com/reference/kotlin/androidx/compose/foundation/lazy/rememberLazyListState.composable . Cache-window state API was added in Compose Foundation 1.9.0; the project uses its existing Compose BOM without dependency changes.
- Static delimiter/named-call/unchanged-horizontal/archive checks only; no local build, frame profiling or TV-remote test. Apply after the Home Progressive Loading patch; other player/About patches remain intact. Real-TV smoothness still requires maintainer validation.

## Provider loading memory/cancellation patch (2026-10-02)

- Shared Core only; both Mobile aliases and TV use the changes. No local build or tests run, per user instruction. Static source/archive checks only.
- PluginHttp bounds script/manifest downloads to 8 MiB (including unknown length bodies), and keeps an OkHttp call plus active response attached to coroutine cancellation until decoding finishes. Existing 4 MiB provider fetch response cap remains.
- ProviderCodeStore bounds cached script reads; readiness counters now use file metadata rather than copying script contents during settings composition. Files written here are required to be nonblank and <=8 MiB. Legacy corrupt files may need repository refresh.
- ProviderCodeSyncManager uses one process-wide three-download gate across startup/settings/preflight instances. Fixed striped locks serialize writes to the same provider/version file; missing-code callers recheck readiness after waiting. Work runs on IO and cancellation propagates.
- Console log retention is bounded to the latest 24 entries during execution via a synchronized deque, replacing unbounded copy-on-write accumulation. Two JUnit regression checks included for CI, not run locally.
- WebView renderer termination destroys the affected view and reports a provider failure. Cleanup attempts destroy even if preceding cleanup fails. Injected HTML reads have a 4 MiB cap.
- Provider execution concurrency, source ranking, progressive publication, scan budgets and UI remain unchanged. Actual crash cause and real-device behavior still require diagnostics/device validation; native runtime or OS process termination is not proven resolved.

## TV Settings category spacing patch (2026-10-02)

- TvSettingsComponents: category column replaces fixed 242 dp with measured longest-label width at focused 16 sp Semibold, measured chevron plus 30 dp side padding, 12 dp label/arrow gap and 4 dp reserve. All menu boxes share this width, adapting to font scale; Content Manager is the reference longest label in the current menu.
- Category list spacing increases 6 -> 14 dp; noninitial section header top padding increases 9 -> 14 dp. Row heights, panel content, focus requesters and D-pad callbacks are unchanged.
- Static checks and ZIP integrity only. No local build/test execution or real-TV visual validation, per user instruction.

## TV Settings pill/contrast patch (2026-10-02)

- Settings master/detail shell uses an opaque neutral charcoal #272B32 backdrop, replacing the near-black low-alpha surface, scoped to Settings only.
- Category menus use 50% rounded pill shapes: normal #11151B, selected #343A44, focused #4C535F with the existing 2 dp white outline. Inactive labels/chevrons have higher white contrast. Existing measured label width, 14 dp spacing, row heights and D-pad callbacks are retained.
- Panel contents and global theme are unchanged. Static source/archive checks only; no local build or tests, and real-TV contrast still requires user review.

## TV Settings dark backdrop / brighter surfaces revision (2026-10-02)

- Supersedes prior pill contrast palette: roll back the master/detail outer surface to original dark Surface at 18% opacity. Keep pill shapes, measured widths, 14 dp category spacing and D-pad behaviors.
- Brighter opaque neutral menu fills: normal #30353F, selected #414956, focused #596170. Shared Settings cards/metrics/profile tiles now #2B3039 with focused #596170 and disabled #1C2129; profile container #1D222B. Existing focused white outlines and switch-profile focused white/black text remain.
- Remove only rendered left category group headers (VUEO/PLAYBACK/APP), leaving original category order and destination data intact. Right-side content sections are retained.
- Static checks and ZIP verification only; no build or tests executed locally. Real-TV visual review pending.

## TV Settings neutral grey palette revision (2026-10-02)

- Replace blue-tinted Settings surface palette only: normal pill and content card #303030, selected pill #404040, focused pill/card #555555, profile container #202020, disabled card #242424. Matches the user's reply-input reference approximately; exact pixel colour not sampled.
- Dark backdrop, pill shape, measured widths, row spacing, omitted left group headers, white outlines and all navigation/behavior unchanged. Static palette/archive checks only; no local build or tests.

## TV Player hidden OK / neutral skip prompt patch (2026-10-02)

- Hidden chrome OK/Enter/Numpad Enter captures the press and toggles playback once on uncancelled KeyUp. Held repeats are consumed. It does not reveal chrome; Up/Down retain existing reveal behavior. Playback toggle uses playWhenReady so it can pause during buffering.
- onIsPlayingChanged saves progress but only restores control focus for a real pause when chrome is already visible; hidden pauses remain hidden. Existing error reveal and visible control activation paths remain.
- Focused Skip/Next prompts retain their own OK activation even with chrome hidden. Episode transition, workspace and canceled-release guards retained.
- Skip Intro/Recap/Credits prompt fill #303030, focused #555555 with existing white outline and now white focused text. Geometry, timestamps and skip policy unchanged; next-episode card styling untouched.
- Static checks/archive validation only; no build or local test execution. Real remote/device validation pending.

## TV Sources loading focus patch (2026-10-02)

- User explicitly excluded retaining empty/completed provider tabs: TvSourceScreen visibleProviders and fallback-to-All policy untouched.
- First-result auto-focus now includes userInteracted in its cancellation key and guard, preventing updates from stealing focus after remote navigation. Explicit source-row provider cycling retains its one-time first-source handoff; newer user interaction clears that handoff.
- Provider filter row intercepts horizontal intent and tracks focused/pending chip identity. LazyRow uses an explicit list state; offscreen destination scrolls into composition before a bounded four-frame focus retry. New inputs cancel the old job with a generation check; removed targets abort, and disposal cancels pending work. Long-press moves from pending identity instead of repeatedly targeting the same offscreen neighbor.
- Refresh/error visuals, provider discovery, source ranking, provider tab policy, source playback and source-card behavior unchanged. Static source/archive checks only, no local build/test execution. Actual crash cause remains unconfirmed without a device log.

## TV player status indicators — 2026-10-02

- Hidden-control playback toggles emit a display-only play/pause indicator for 800ms using playWhenReady intent.
- Buffering shows a centre spinner after 500ms, takes priority over playback feedback, and clears on readiness/error.
- Subtitle translation status uses translatingSubtitleSelectionId; display a lower-centre spinner and label while actual translation is pending. Hide overlays during panels, errors and episode switching.
- All indicator foregrounds use TvDesign.Accent, exactly like the filled progress rail. No focus targets or control-reveal paths added. Mobile unchanged.
- Static source and ZIP checks only; no local build or tests executed. Device verification remains required.

## TV player status placement revision — 2026-10-02

- Removed chrome's top-right BUFFERING text and its now-unused buffering parameter.
- Centre buffering spinner now has its status label directly underneath; both follow TvDesign.Accent and the existing 500ms anti-flash gate.
- Translating subtitles moved to top-right, below top actions while chrome is visible and at the top inset while hidden. It remains bound to actual translation state.
- Existing episode/source-discovery overlay now stacks its loading label below the spinner and uses the progress accent. No discovery, focus, cancellation or playback logic changed.
- Static source/archive checks only; no local builds/tests or TV device validation.

## TV pause backdrop — 2026-10-02

- After 5 seconds paused without remote input, hide chrome and fade in current media.background with title/episode metadata. Transparent image fallback preserves the paused frame underneath a dark scrim. Uses existing bounded/cached TvNetworkImage path.
- Timer depends on playWhenReady intent and READY state, first rendered frame, inactivity, foreground, no buffering/recovery/error/end/episode switch/panel/pending seek/countdown. No artwork screen for buffering or lifecycle pauses.
- Player root keeps focus. Capture full remote dismissal press and repeats; OK resumes on uncancelled release, other keys only dismiss while paused. Software Back also dismisses first. Any normal KeyDown restarts inactivity.
- No new playback/discovery policy or mobile changes. Static source/archive validation only; no local builds or tests run. TV verification needed for 5-second timing, held keys, foreground/background and image fallback.

## Continue Watching series continuity — 2026-10-02

- Shared ContinueWatchingPolicy keeps short/zero-position episode cursors, advances completed episodes to the next released unwatched regular episode, and retains caught-up series in storage for future metadata refresh. Movie inclusion stays >5 seconds; existing 95%/20-second completion criteria preserved.
- Dedicated per-title cursors now merge legacy history/cache and survive the 150-entry history limit. Refresh updates episode metadata without changing watched timestamps or restoring manually dismissed/marked-watched titles.
- TV/mobile Home show all Continue Watching items locally first. Background metadata refresh uses 2 concurrent titles, 15-second per-title budget, 15-minute attempt cooldown and active-profile checks; unavailable/failed metadata keeps cached progress. Release dates prevent known future episodes from being offered. Metadata does not guarantee playable streams.
- Detail selection/resume policy shared on both platforms; series positions >0 can resume, movies >5 seconds. Mobile stable snapshot guards preserved with series-specific threshold. TV saves the series cursor at first frame.
- No local build/tests run. Static source/archive checks and CI-ready pure policy regression tests included; device/CI verification still required.

## Provider timeout progress diagnostic patch (2026-10-02)
- Per-run progress collector is owned by runProvider outside withTimeoutOrNull; timeout/cancellation no longer discards already captured console/progress evidence.
- Shared bounded snapshots preserve the latest 24 console messages, 10 elapsed timeline events, last observed stage and up to four pending requests plus an overflow count (40 stored lines maximum). Pending tracking is bounded to 32 labelled requests; extra requests retain a count. No request bodies/headers/query strings are added to diagnostic evidence.
- Observe discovery-context/HTTP/WebView start, completion or ordinary failure; deadline cancellation leaves unfinished requests pending. WebView slot acquisition, provider trace stages, HTML operations, delay and result parsing also record progress.
- Mobile and TV diagnostics and Copy Debug Log display Last stage, Pending requests and Timeline. Older saved records do not invent progress. Pending is evidence of no observed completion, not a proven timeout cause; timeline is recent bounded history, not every event.
- Provider deadline values, concurrency limits, cancellation propagation, source extraction/ranking and discovery rules remain unchanged. Existing console ERROR prefixes and 24-message retention remain intact for health classification.
- Validation: static source/patch checks only; no build or tests executed by user request. Five JUnit regression cases included for CI (timeout evidence, completion/failure, bounds, concurrent access, legacy records). Device verification still required. Reproduce a fresh timeout to capture the new evidence; old diagnostic records cannot be reconstructed.

## Previous-crash popup (Mobile + TV, 2026-10-02)
- Both manifests now use shared VueoDiagnosticApplication; RuntimeDiagnostics installs before Activity startup and keeps existing MainActivity install calls idempotent.
- CrashReportStore writes bounded, sanitized Java/Kotlin exception/root location, version/device/thread and recent activity evidence synchronously to an AtomicFile in noBackupFilesDir. Prior uncaught handler remains responsible for termination; fallback kills/exits if no handler exists. Capture is best effort, especially under OOM or hard termination.
- API 30+ reads the latest main-process ApplicationExitInfo since the previously tracked launch, off the UI thread: Java/native crash, ANR and foreground low-memory/excessive-resource/unknown exits. Normal exit, force-stop, updates and background memory cleanup do not generate crash popups. First install does not replay pre-feature OS history. Older Android only has captured JVM crashes; absence of evidence never fabricates a crash.
- ActivityManager process-state summary stores a bounded last observed screen/event for system exits. Breadcrumbs are observations, not attribution of fault. No source extraction/ranking, network deadlines or player behavior changed.
- Mobile popup waits for startup destination, boot and profile selection. TV waits until outside STARTUP/PROFILE; pending crash takes priority over update prompt. Details, Copy Log and Close are provided; TV Close receives initial focus. Android Back/outside dismissal follow Close. Dismissal watermark is committed on IO before UI removal, suppressing the same OS/local crash across relaunches; read reports remain until successfully dismissed.
- OS details do not claim an exact root cause/code location and binary native tombstones are not rendered as text. Local reports prefer richer JVM details over matching OS records; a newer distinct exit replaces stale evidence.
- Static validation only, no build or tests run per user instruction. Five pure JUnit cases supplied for CI (dismissal/session cutoff, OS/JVM dedupe, newer native selection, older record protection, redaction). Device checks pending: force Java crash then relaunch, copy/details/close and relaunch twice; TV D-pad/Back focus; profile gate; native/ANR API 30+; normal exit/force-stop must not show popup.
- Android reference consulted: https://developer.android.com/reference/android/app/ApplicationExitInfo and https://developer.android.com/reference/android/app/ActivityManager .

## Crash popup compile import fix (2026-10-02)
- Uploaded CI logs_100287535140.zip reports mobile VueoApp.kt unresolved withContext and Dispatchers at line 352. Added the two missing coroutine imports. No behavioral changes. Static import and ZIP checks only; no local build or tests run.

## Player source server labels (Mobile + TV, 2026-10-02)
- Shared PlayerSourceDisplay presents serverName first, falling back to source name then provider/Source. Bullet-separated addon tags stay in secondary details; raw standalone URLs are not used as server labels.
- Mobile player source cards show server identity with the existing quality badge, then provider, HLS/MP4 when known, and language/codec/audio/size/addon tags. TV player Sources and in-player episode-switch manual source picker use the same labels with quality in the title.
- Provider tabs/filtering, selected/playing/recommended/failed states, source keys/order/switching/recovery/extraction/ranking are unchanged. Changes are limited to presentation; standalone source discovery screens are unchanged.
- Static source/ZIP checks only. No build or tests run by user request. Device visual/D-pad validation pending.

## TV player Sources provider tabs (2026-10-03)
- Player source options retain full providerName for filtering; Sources panel now shows All plus distinct provider tabs and uses current player bundle updates. Provider display labels shorten the final slash segment while filter keys retain full names.
- Initial All list focuses the playing enabled source (existing selected-row behavior). Focusing a tab filters it without stealing focus back into the list. Down enters filtered list; Up from first enabled row returns to its active provider tab. Source/status labels remain and playing source explicitly says Playing.
- Source discovery remains active after Play until completion/timeout/cancellation as before. No extra discovery, ranking, cancellation or source-switch behavior introduced. Existing single-Back panel dismissal is unchanged.
- Static source and ZIP checks only; no build or tests executed. Device checks pending: open/close/reopen; provider navigation, Down/Up, sources arriving while tabs are focused, playing source highlight and one-Back dismissal.
