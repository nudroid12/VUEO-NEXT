# VUEO Project Context

> Canonical handoff document for humans and AI coding agents.
> Read this file before changing VUEO.
>
> Checkpoint date: 2026-09-05
> Repository: VUEO-NEXT
GitHub: https://github.com/nudroid12/VUEO-NEXT

## 1. What VUEO is

VUEO is a Kotlin/Jetpack Compose Android media application maintained as one monorepo with:

- Android Mobile application
- Android TV application
- Shared Core used by both applications

The project goal is feature parity between Mobile and TV while keeping platform presentation different where necessary.

Mobile is the mature product reference for behaviour and visual language. TV should reuse shared domain/runtime logic and adapt presentation for a 10-foot, D-pad-first experience.

## 2. Repository modules

```text
VUEO-NEXT/
├── mobile/         Android Mobile app
├── tv/             Android TV app
└── shared/core/    Shared domain, runtime, storage and integration logic
```

Gradle modules:

```text
:mobile
:tv
:shared:core
```

Locked application IDs:

| Product | Application ID |
| --- | --- |
| VUEO Mobile | `com.vueo.app` |
| VUEO TV | `com.vueo.tv` |

Do not casually change these package/application IDs.

## 3. Architecture rule

The dependency direction is:

```text
                 shared/core
                 /         \
             mobile         tv
```

Rules:

1. `mobile` may depend on `shared:core`.
2. `tv` may depend on `shared:core`.
3. `shared:core` must never depend on `mobile` or `tv`.
4. Reusable business logic belongs in Shared Core.
5. Mobile and TV keep platform-specific UI, navigation and input handling in their own modules.
6. Do not create a TV-only copy of logic that already exists in Shared Core.
7. Do not create a new Shared Core implementation without checking whether the mature Mobile implementation has already been migrated or exposed through a compatibility alias.

## 4. Mobile compatibility files are often aliases

Several files under `mobile/src/main/java/com/vueo/app/core/...` intentionally preserve old Mobile package names while delegating to Shared Core.

Examples include:

- `core/extensions/UnifiedMediaEngine.kt`
- `core/extensions/CatalogDiscoveryCache.kt`
- `core/stremio/StremioAddonProvider.kt`
- `core/storage/ProfileStore.kt`
- `core/storage/SettingsStore.kt`
- `core/enrichment/TmdbEnhancementClient.kt`
- `core/dna/UserDnaEngine.kt`

Many of these are `typealias` or compatibility facades. Inspect the file before assuming it is duplicate production logic.

`mobile/core/storage/VueoBackupManager.kt` is intentionally an empty migration marker after Shared Core migration. Do not restore the old Mobile backup implementation there.

## 5. Shared Core source of truth

The following areas currently live in `shared/core` and are intended to be consumed by both Mobile and TV.

### Discovery and catalog

- `UnifiedMediaEngine`
- `MediaExtension`
- `StremioAddonExtension`
- `CatalogDiscoveryCache`
- extension/media contracts and models
- Stremio manifest/catalog/search/meta integration

TV discovery adapters should remain thin. TV must not reintroduce hardcoded Cinemeta HTTP/catalog parsing when Shared Core already handles enabled addons and catalog discovery.

### Source system

- source contracts/models
- source discovery cache
- source policy
- source ranking
- source selector
- player source policy

Source ranking and recovery are mature areas. Avoid changing them while doing visual/UI work unless a real source bug requires it.

### Provider/plugin runtime

- plugin repository manager/client
- plugin runtime and runtime cache
- provider code store
- plugin health store
- compatibility bridges
- TMDB resolver used by providers

The official UI/product name for addon/provider management is **Content Manager**.

Do not rename it to "Content & Discovery", "Extensions", or another generic label.

### Enrichment

- TMDB enhancement
- MDBList ratings
- Rich Details
- Gemini client/insight
- metadata enhancement engine
- Content Warning repository

### User data and settings

- profiles
- library/media library
- playback progress/playback state
- SettingsStore
- user data core
- backup/restore/reset

### Personalisation

- User DNA engine
- User DNA preferences

### Player support

- skip segment repository
- shared player/source policy

## 6. Shared Core cleanup checkpoint

The latest Shared Core cleanup prepared in this source tree is **18A Shared Core Final Cleanup**.

It centralises the last major duplicated areas:

### Content Warning

- Shared source of truth: `shared/core/.../enrichment/ContentWarningRepository.kt`
- Mobile warning fetch/parser duplication was removed.
- TV already consumes the Shared repository.
- Content Warning setting is migrated to Shared `SettingsStore`.
- TV warning presentation intentionally follows Mobile behaviour 1:1.

### Backup / Restore / Reset

- Shared source of truth: `shared/core/.../storage/VueoBackupManager.kt`
- Mobile and TV use the Shared manager.
- Shared backup schema is intended to support user-data interchange between Mobile and TV where the data type is common.
- Shared reset clears the relevant shared/mobile/TV user-data preferences and caches.

At this checkpoint, 18A has passed static sanity checks in the handoff environment. A full GitHub Actions build should still be treated as the canonical build confirmation if it has not yet been run.

## 6A. Mobile checkpoint after patches 26 to 28F

Mobile is now in a stabilisation phase. Avoid adding new Mobile features unless a real product requirement is identified. The current source includes:

- three dark application themes: Charcoal, Midnight and Deep Teal
- a neutral translucent profile hero in Settings
- neutral My List, Watched and DNA stats without a special DNA accent
- grouped compact Settings and polished Settings subpages
- Content Manager terminology that avoids third-party platform branding in user-facing copy
- per-catalog enable/disable state persisted independently from the parent addon
- provider diagnostics that keep request, failure, error, HTTP, timing, result and raw evidence while hiding debug noise by default
- a startup destination gate that resolves Who's Watching before Home is allowed to render
- navigation-bar safe spacing on Mobile profile management screens

Architecture cleanup started in 28F. The Content Manager UI family was extracted from the oversized `VueoApp.kt` into:

- `mobile/src/main/java/com/vueo/app/ui/VueoContentManager.kt`

This extraction is presentation-only. Content Manager data models, stores, provider runtime and Shared Core contracts were not rewritten. Continue reducing `VueoApp.kt` only in small, behaviour-preserving slices. Do not move startup/profile/player logic during unrelated cleanup.

## 7. Mobile is the visual/behaviour reference where explicitly locked

### Player

Current direction after 17A:

**Mobile Player UI is canonical.**

The previous custom TV Player layout is superseded. TV should use the Mobile Player composition and visual hierarchy, adapted for TV scale, D-pad and focus.

Current TV adaptation includes the Mobile-style structure:

- top title/actions
- Next
- Lock
- More
- Back
- central rewind 10s / Play-Pause / forward 10s
- lower seekbar and time
- `Subs`, `Audio`, `Sources`, `Episodes` pills
- Mobile-style modal/workspace presentation for related player controls

Do not restore the old TV-only layout with title top-left, Restart/Next top-right and the older bottom button row. That design was superseded by 17A.

Player behaviour from the earlier TV behaviour work remains important underneath the Mobile UI adaptation, including:

- D-pad-safe focus flow
- quick seek
- progress persistence
- resume/start over
- auto-next and cancellation
- source recovery
- playback problem handling
- source switching while retaining playback position when possible
- audio/subtitle preference reapplication
- playback speed
- Fit/Fill/Zoom
- sleep timer
- subtitle styling/sync
- provider filter

TV must remain fully usable without a pointer.

TV PiP is intentionally not part of the product direction.

### Content Warning presentation

After 17C, TV follows Mobile warning behaviour/visuals 1:1 as closely as practical:

- IMDb lookup with TMDB fallback
- show once when playback is actually playing
- lime vertical line
- label and severity without a large background card
- progressive item animation
- roughly 5-second hold
- Mobile-style position and timing

Keep repository/data logic shared even when presentation is platform-specific.

### Settings

Current direction after 17B:

**Mobile Settings UI hierarchy is canonical.**

The old 2-column TV Settings grid is superseded.

TV Settings should use:

- Mobile-style one-column navigation cards
- profile card at the top
- icon box
- title
- subtitle/status
- trailing arrow/value/toggle where appropriate
- Mobile-style subpage hierarchy
- larger typography/row height for 10-foot viewing
- D-pad focus treatment

Settings still opens from the TV Profile icon.

Do not rebuild a separate TV-only Settings information architecture unless a TV constraint genuinely requires it.

## 8. TV interaction rules

TV is a 10-foot interface.

Locked interaction principles:

- 100% D-pad operable
- no cursor required
- focus must always be obvious
- white + scale is the primary focus language
- accent colour must not replace focus clarity
- no focus traps
- Back should unwind the nearest overlay/panel before leaving the parent screen
- restore focus when returning from Detail, Player, Search, Library or Settings where practical
- avoid unexpected horizontal wrapping at the end of a row
- preserve scroll/focus state during normal navigation

Typography must be readable from a sofa. Avoid shrinking text just to fit more items on one screen.

## 9. TV top navigation

Current TV top navigation direction:

```text
Search icon | Library icon | Home | Movie | Series | Anime | Clock | Profile icon
```

- Search and Library are icons.
- Home, Movie, Series and Anime are text tabs.
- Clock is display-only and should not become a focus target.
- Profile opens Settings/profile UI.
- Settings is not a standalone top-nav tab.

## 10. Home behaviour

The Home behaviour work established these expectations:

- restore focus and scroll state
- D-pad Up/Down should try to preserve the same column
- horizontal rows should not wrap unexpectedly
- Hero supports Play/Resume and More Info
- Hero can rotate only when idle and should stop while the user is interacting
- Continue Watching uses real playback progress
- Home should not force a full network refresh every time the user returns from Detail or Player
- loading/error states should not destroy the entire Home experience when only one row fails
- root Back uses an exit confirmation rather than accidental immediate exit

Discovery content must come through Shared Core/Content Manager state rather than reintroduced hardcoded catalog endpoints.

## 11. Discovery, Browse and Search

The corrective architecture established:

- Shared `UnifiedMediaEngine` is the source of truth.
- Stremio addon discovery lives in Shared Core.
- enabled/disabled addon state matters.
- catalog ordering matters.
- TV adapters map Shared results to TV presentation.

Feature parity work also added/targets:

- Movie / Series / Anime browsing
- Actor Search
- Anime filter
- Genre filter
- Popular / Trending / Newest sorting
- relevance-aware search ordering

Do not reintroduce a TV-specific direct HTTP discovery engine.

## 12. Detail screen

TV Detail parity direction includes:

- metadata loaded through Shared Core
- no hardcoded Cinemeta detail endpoint
- TMDB Recommendations
- TMDB Similar Titles
- More Like This
- local VUEO similarity fallback
- rich cast with images/character names
- network/production branding when available
- Gemini Insight on demand
- related-title back-stack behaviour

Keep enrichment logic in Shared Core and the 10-foot presentation in TV.

## 13. Content Manager

Official product name: **Content Manager**.

User-facing UI should use VUEO-neutral terms such as addons, repositories, providers and catalogs. Internal protocol/runtime class names may remain technical where required by the implementation.

It manages:

- addons
- provider plugins
- repositories
- provider health and diagnostics
- catalog ordering
- per-catalog visibility

Current Mobile behaviour includes:

- refresh/remove addon
- refresh/remove repository
- provider health summary
- compact expandable provider diagnostics
- sanitized raw diagnostic evidence
- persistent per-catalog enable/disable state
- catalog order preserved while a catalog is hidden

Content Manager state should feed Shared Core discovery and playback behaviour. Avoid separate hidden state stores that drift between screens.

## 14. Profiles, Library and Appearance

TV parity includes:

### Profiles

- Add profile
- Edit profile
- Delete profile
- avatar selection
- Kids Profile toggle
- Manage Profiles entry
- profile PIN/security support

### Library

- My List
- watched/history/progress state
- Grid/List view
- remember selected Library view

### Appearance

Accent options include the current Mobile/TV parity set such as:

- White
- Lime
- Ocean
- Violet
- Amber
- Coral

Accent applies to product accents/progress/status. White + scale focus remains the focus language on TV.

## 14A. Current important Mobile files

High-value Mobile UI entry points now include:

- `mobile/src/main/java/com/vueo/app/ui/VueoApp.kt`
- `mobile/src/main/java/com/vueo/app/ui/VueoContentManager.kt`
- `mobile/src/main/java/com/vueo/app/ui/VueoSettings.kt`
- `mobile/src/main/java/com/vueo/app/ui/VueoProfiles.kt`
- `mobile/src/main/java/com/vueo/app/ui/VueoDesign.kt`

`VueoApp.kt` is still large. Extract only coherent screen families, keep navigation contracts stable, and validate regression-sensitive flows after each extraction.

## 15. Current important TV files

Key TV entry points include:

- `tv/src/main/java/com/vueo/tv/VueoTvApp.kt`
- `tv/src/main/java/com/vueo/tv/data/TvUnifiedDiscovery.kt`
- `tv/src/main/java/com/vueo/tv/detail/TvDetailScreen.kt`
- `tv/src/main/java/com/vueo/tv/search/TvSearchScreen.kt`
- `tv/src/main/java/com/vueo/tv/content/TvContentManagerScreen.kt`
- `tv/src/main/java/com/vueo/tv/player/TvPlayerScreen.kt`
- `tv/src/main/java/com/vueo/tv/player/TvSourceEngine.kt`
- `tv/src/main/java/com/vueo/tv/player/TvSourcePickerScreen.kt`
- `tv/src/main/java/com/vueo/tv/library/TvLibraryScreen.kt`
- `tv/src/main/java/com/vueo/tv/profile/TvFunctionalSettings.kt`
- `tv/src/main/java/com/vueo/tv/profile/TvProfileDnaPanel.kt`
- `tv/src/main/java/com/vueo/tv/profile/TvProfilePickerScreen.kt`
- `tv/src/main/java/com/vueo/tv/profile/TvUserHubScreen.kt`
- `tv/src/main/java/com/vueo/tv/ui/focus/TvFocus.kt`
- `tv/src/main/java/com/vueo/tv/ui/theme/TvAppearance.kt`

Inspect the current implementation before editing. This document describes design intent and architecture, but source code is the authority for exact APIs and signatures.

## 16. Current important Shared Core files

Key Shared Core areas include:

```text
shared/core/src/main/java/com/vueo/shared/core/
├── dna/
├── enrichment/
├── extensions/
├── media/
├── player/
├── plugin/
├── source/
├── storage/
└── stremio/
```

High-value files:

- `extensions/UnifiedMediaEngine.kt`
- `extensions/StremioAddonExtension.kt`
- `extensions/CatalogDiscoveryCache.kt`
- `enrichment/ContentWarningRepository.kt`
- `enrichment/MetadataEnhancementEngine.kt`
- `enrichment/TmdbEnhancementClient.kt`
- `plugin/PluginRuntime.kt`
- `plugin/PluginStore.kt`
- `plugin/ProviderCodeStore.kt`
- `source/SourceRanker.kt`
- `source/SourceSelector.kt`
- `storage/ProfileStore.kt`
- `storage/LibraryStore.kt`
- `storage/PlaybackStore.kt`
- `storage/SettingsStore.kt`
- `storage/VueoBackupManager.kt`

## 17. Current milestone history

Use this only as a handoff guide. The current source is more important than old patch names.

Recent milestones:

- 14A: TV Player UI redesign, later superseded visually by 17A
- 14B: complete TV Player behaviour foundation, behaviour retained where compatible
- 15A: Home complete behaviour
- 15B: Settings visual polish, later superseded visually by 17B
- 16A: Unified Discovery, then corrective Shared Core architecture
- 16B: Detail parity through Shared Core
- 16C: Player feature parity
- 16D: Content Manager + Search parity
- 16E: Profile, Library and Appearance parity
- 17A: Mobile Player UI adapted to TV, canonical current Player visual direction
- 17B: Mobile Settings UI adapted to TV, canonical current Settings visual direction
- 17C: Content Warning presentation matched to Mobile 1:1
- 18A: Shared Core cleanup for Content Warning and Backup/Restore/Reset
- 26A-27B: Mobile Settings hierarchy, themes, profile surface and Settings subpage polish
- 28A: Mobile Content Manager presentation rebuild
- 28B: Mobile Who's Watching startup destination gate restored
- 28C-28E: Provider diagnostics upgraded, then compacted to debug-first evidence
- 28D: Mobile profile safe-area fix and persistent per-catalog enable/disable state
- 28F: Mobile housekeeping and first safe extraction from `VueoApp.kt` into `VueoContentManager.kt`

Do not revert to a superseded visual milestone just because an older patch or conversation mentions it.

## 18. Build and validation

Canonical local command:

```bash
./gradlew :mobile:assembleDebug :tv:assembleDebug
```

GitHub Actions is the authoritative full build when local/network constraints prevent Gradle dependency resolution.

A previous handoff environment could not resolve `services.gradle.org`, so static Kotlin syntax checks and ZIP integrity checks were used there. Do not interpret inability to reach Gradle servers as a source-code failure.

When changing Shared Core, validate both applications because both depend on it.

## 19. Patch workflow rules

The project has often been updated with replacement ZIP patches.

When producing a patch for the maintainer:

1. Include only actual replacement/new source files unless explicitly asked for workflows or raw patches.
2. Preserve repository-relative paths.
3. Prefer one ZIP for a coherent phase.
4. Do not include generated build outputs.
5. Avoid unnecessary workflow modifications.
6. Full GitHub Actions build result is the final compile signal.

### Important repository-root ZIP pitfall

A previous patch contained only a top-level `tv/` directory. The patch workflow mistook `tv/` for the repository root because it contained its own `build.gradle.kts`.

For replacement ZIPs that could trigger this ambiguity, include the repository root `build.gradle.kts` as a root marker so extraction/apply logic resolves the real repo root correctly.

Do not overwrite the root build file with `tv/build.gradle.kts`.

## 20. Distribution/update assumption

The TV app is currently distributed as an APK outside the Play Store workflow, including direct sharing such as Telegram. Do not design release/update logic around mandatory Play Store publication unless the maintainer explicitly changes this direction.

## 21. Rules for future AI agents

Before modifying anything:

1. Read this entire file.
2. Inspect `settings.gradle.kts`, module build files and the exact current source files involved.
3. Search Shared Core before implementing reusable logic in Mobile or TV.
4. Search Mobile before inventing a new TV behaviour when the maintainer asks for Mobile parity.
5. Treat Mobile Player and Mobile Settings as the current visual reference for their TV equivalents.
6. Preserve TV D-pad/focus requirements.
7. Do not rewrite stable Source Engine/ranking logic during unrelated UI work.
8. Do not rename Content Manager.
9. Do not reintroduce hardcoded Cinemeta discovery/detail paths where Shared Core is already responsible.
10. Do not assume compatibility files in Mobile are duplicate logic. Many are aliases.
11. Keep changes scoped. Avoid large architecture rewrites unless the task genuinely requires them.
12. If source and this document disagree, inspect recent code/history and update this document with the confirmed current truth.

## 22. Handoff checklist after every substantial phase

Future agents should update this file when a substantial architecture or product direction changes.

At minimum record:

- what became canonical
- what was superseded
- Shared Core migrations
- new known limitations
- build status
- next recommended phase

This file exists specifically so the project can be continued safely from another ChatGPT account, another coding model, or another development environment without relying on prior chat history.
