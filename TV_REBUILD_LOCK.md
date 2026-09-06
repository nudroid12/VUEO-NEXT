# VUEO TV Rebuild Lock

Baseline: **29A-R clean rebuild**.

## Product DNA

- Premium
- Cinematic
- Fluid
- 60fps-first

A screen is not accepted only because it looks good in a screenshot. It must feel immediate and predictable under a remote.

## Architecture lock

- TV Kotlin lives normally in `tv/src/main/java`.
- Legacy TV implementations were removed/overwritten; old paths that cannot be deleted by ZIP overlay are inert tombstones only.
- New TV must never depend on `:mobile`.
- Mobile is a behavioural reference. Shared Core is the reusable logic layer.
- Do not bring legacy TV repositories, root navigation, focus memory, TV-only library/playback stores or source wrappers into the rebuild.

## Explicit preservation exception

Keep the approved existing experience for:

- Who's Watching
- Manage Profiles
- Add Profile / Edit Profile
- Profile PIN/editor behaviour

Everything after profile selection belongs to the rebuilt TV runtime.

## Home lock

- Top contextual navigation: Home / Search / Library / Settings.
- No persistent VUEO wordmark in post-profile page chrome.
- Hero + Peeking Row composition.
- Hero is presentation-only and never receives focus.
- Initial focus: first Continue Watching item; fallback first item in the first available row.
- Focused card drives the hero after a short ~180 ms settle delay.
- UP from first row reveals top navigation.
- DOWN restores the last content focus.
- OK on a card opens Details directly.
- No hero auto-rotation.
- No carousel dots.
- No excessive parallax or decorative motion.

## Motion lock

Home motion is intentionally small:

1. Immediate card focus with shallow scale/depth.
2. Smooth hero fade-through after focus settles.
3. Contextual navigation reveal/recede.

**Input fast. Visuals smooth. Navigation obvious.**

## Data/runtime direction

The new TV runtime adapts Mobile-proven behaviour using Shared Core:

- startup/profile gate via `ProfileStore`
- Home discovery via `UnifiedMediaEngine`
- catalog order + per-catalog enabled state using Mobile `AddonStore` semantics
- My List / Continue Watching via `LibraryStore`
- playback position via `PlaybackStore`
- settings via `SettingsStore`
- addon + provider source discovery through Shared Core runtime
- source cleanup/ranking through Shared Core

Do not redesign these behaviours merely because the TV UI is new.


## 29B Home calibration lock

Validated against the first real-TV Home screenshot after the clean rebuild:

- Home rail origin: ~62% viewport height.
- Contextual nav labels: alpha 0 while content has focus, reveal on UP.
- Home wordmark is removed from page chrome; profile anchor remains compact at 30dp.
- Home landscape card width: 244dp, 16:9, title outside artwork.
- Focus: ~1.045 scale, soft shadow, 1dp neutral edge.
- Hero fade-through: 420ms in / 220ms out after the locked 180ms focus settle.

Do not shrink the hero back to the 29A-R prototype proportions or restore persistent top-nav labels.

## 29B.1 Settings + D-pad lock
- Top navigation must require only one OK press to open the focused destination.
- Focus movement alone must never auto-open a destination.
- TV Settings is a clean TV-native surface backed by Shared Core; do not restore the legacy TV Settings implementation.
- The first TV Settings activation covers profile/startup, playback, subtitle and source preferences whose behavior already exists in Shared Core.

## 29B.2 Settings parity lock
The temporary 29B.1 flat Settings surface is replaced by the canonical VUEO hierarchy adapted to D-pad TV. Legacy TV Settings must not return. Settings that imply behavior must have a real TV consumer. One focused navigation/settings item requires one OK activation only.

## 29C Search parity lock
- TV Search follows Mobile Search capability and information architecture: Discover, Title/Actor search, All/Movies/Series/Anime, Popular/Trending/Newest and dynamic Genre.
- Reuse Shared Core discovery/search/TMDB behavior; do not build a separate TV search engine.
- D-pad movement changes focus; OK commits once.
- Returning from Detail must preserve Search session state and focused result.


## 29C.1 Search TV calibration lock
- Remove the persistent VUEO wordmark from post-profile application page chrome, including Home, Search, Library and Settings. Startup/profile-selection branding is not part of this change.
- Search follows the approved compact cinematic mockup composition while retaining the existing contextual top-navigation architecture; do not add a persistent sidebar.
- Search field is compact and intentionally narrower than the viewport.
- Type / Sort / Genre / Title-Actor controls share one compact control row.
- Search/Discover poster grid is fixed at **8 visible 2:3 posters per row** for the TV viewport.
- Search focus motion stays shallow (~1.035 scale), neutral-white and low-shadow; no bounce, glow or decorative motion.
- 29C Search logic, Shared Core behavior, return-state restoration and Home hero/focus logic remain unchanged.

## 29C.2 floating navigation lock
- Use the centered floating capsule for Home / Search / Library / Settings across normal post-profile TV pages.
- Keep Profile as a separate top-right circular anchor and keep the VUEO wordmark absent from normal page chrome.
- Do not add a sidebar or restore a left-aligned full-width nav strip.
- Selected destination may use a soft filled pill; focused destination uses shallow neutral-white depth only.
- D-pad focus movement never changes route; one OK/Enter activation commits once.
- Preserve Home/Search contextual nav collapse/reveal, exact Down-to-content restore behavior, and all existing route logic.

## 29C.2a startup update popup lock
- Automatic TV update checks must surface a newer `TvUpdateRelease` instead of discarding the check result.
- Keep the existing `TvUpdateManager` feed/download/SHA/package validation/installer flow; do not create a second updater.
- The update prompt is modal and D-pad-contained: Later / Update, one OK activation, Back behaves as Later when not downloading.
- `Later` dismisses only the current session; it must not erase cached release metadata or disable automatic checks.
- Do not force installation. Android retains final installer confirmation and unknown-source permission handling.

## 29C.3 sidebar navigation lock

The 29C.2 centered top capsule is superseded by the 29C.3 slim collapsible left sidebar. Normal TV destinations use Home / Search / Library / Settings with Profile at the bottom of the rail. The rail is collapsed while content owns focus and expands only when the rail owns focus. DPAD_LEFT from a logical first content column enters the current destination; DPAD_RIGHT returns to the exact last content focus where available. UP/DOWN explores the rail; OK commits once; focus alone never routes. Do not restore persistent top navigation, VUEO page wordmarks, auto-route-on-focus, or stacked focus targets.

## 29C.4 Nuvio-reference TV UI direction — supersedes 29C.3 sidebar visuals
- The supplied `NuvioTV-0.8.6-beta` source is now the primary **TV presentation and remote-interaction reference** for rebuild work after the profile gate.
- Reference Nuvio composition, density, focus grammar, overlay/panel treatment and motion before inventing new TV chrome. Do not copy Nuvio runtime/data architecture into VUEO.
- VUEO behavior remains owned by Shared Core/Mobile-proven semantics, and VUEO branding/routes/theme remain VUEO-specific.
- For the global sidebar specifically, use the Nuvio modern pattern: a quiet floating current-route pill while content owns focus; a rounded floating overlay panel when navigation owns focus; Profile at the top; Home / Search / Library / Settings centered vertically; circular icon wells; full-pill selected/focused rows.
- The previous permanent 66dp collapsed rail / 202dp expanded rail visual is retired.
- LEFT from logical content edge opens the current destination, RIGHT restores exact last content focus where supported, UP/DOWN explores navigation, OK commits once, and focus alone never routes.
- Search may hide the collapsed route pill to keep its header clear, matching the supplied Nuvio modern-sidebar pattern.
- No post-profile VUEO wordmark returns to normal page chrome.

## 29D Library parity lock — supersedes old TV Library composition

Library behavior is sourced from canonical VUEO Mobile, while Nuvio is only the TV presentation/focus reference. The visible Library screen is **My List + Cloud placeholder + Grid/List view**. Continue Watching and History must not be reintroduced as dedicated Library sections unless Mobile changes first. Their Shared Core data remains valid for other surfaces.

Grid/List uses the same `vueo_library_ui` / `grid_view` preference contract as Mobile. Library poster density is responsive to a target card width and is **not** locked to 8 columns. LEFT from the first logical content edge enters the current 29C.4 sidebar; sidebar RIGHT restores the last Library control/item where practical; OK/Enter commits once on KeyUp; focus movement never routes. Returning from Detail should restore the last Library item/scroll position without creating a second library-data store.

## 29E Detail functional baseline — Mobile behaviour + Nuvio TV composition

Detail is now a functional rebuild, not a final visual lock. VUEO Mobile/Shared Core remain canonical for Detail data/actions; supplied Nuvio Detail sources are the TV composition/focus reference only. Preserve My List, resume-aware primary action state, season/episode selection, episode progress, facts/ratings/DNA, credits, Overview, Cast, Network/Production, More Like This and optional manual VUEO Insight where supported by current Mobile/runtime behaviour. Do not create TV-only cast/company routes or duplicate playback/library stores.

Use the Nuvio-style sticky cinematic backdrop + hero actions + season tabs/landscape episode row + supporting horizontal sections, with shallow neutral focus and KeyUp-only activation. This 29E screen is intentionally subject to the later whole-TV final polish pass.

## 29F Source Selection lock — functional baseline

Source behavior/data is sourced from VUEO Mobile + Shared Core; supplied Nuvio `StreamScreen.kt` is only the TV composition/D-pad reference. Preserve shared `SourceCleaner`/`PlayerSourcePolicy` ranking, preferred-quality/original-language context, direct-play gating, provider filters, recommendation, source technical-details preference, provider diagnostics and the short-lived shared source-discovery cache behavior. Do not create a TV-only resolver/ranker/store.

The current TV composition is backdrop + left identity/engine context + right provider-chip/source-list workspace. Cached/fresh results may appear progressively. Focus alone never plays a source; OK/Enter commits once on KeyUp. First result receives initial focus only if the user has not already interacted; UP from the first result returns to the active provider chip; LEFT/RIGHT on a source row may cycle provider filters. Returning from Player should restore the last provider/source when available. This is not the final visual polish lock.
