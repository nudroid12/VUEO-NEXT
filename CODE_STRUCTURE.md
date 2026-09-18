# VUEO Code Structure

This repository is split by responsibility so UI screens, player workspaces, and shared runtime code are easier to find without changing app behavior.

## Mobile

`mobile/src/main/java/com/vueo/mobile/`

- `core/` — Android/mobile aliases and platform-facing core helpers.
- `ui/VueoApp.kt` — app-level navigation/state orchestration only.
- `ui/home/` — Home, Continue Watching, catalog rows and media actions.
- `ui/search/` — Search UI and search-specific sorting/filtering.
- `ui/library/` — Library / My List / History presentation.
- `ui/detail/` — Details page, people/company UI, episode selection and entity results.
- `ui/source/` — source/provider picker UI.
- `ui/player/` — player orchestration, overlays, tracks and media helpers.
- `ui/settings/` — Settings pages and reusable settings components.
- `ui/content/` — addons, plugins and provider diagnostics.
- `ui/components/` — reusable visual components.
- `ui/theme/` — palette/theme definitions.

The nested mobile UI files intentionally retain the existing `com.vueo.mobile.ui` Kotlin package in this cleanup. This keeps the change structural only and avoids navigation/behavior churn while the repository paths become easier to browse.

## TV

`tv/src/main/java/com/vueo/tv/`

- `home/` — TV Home presentation.
- `detail/` — TV Details presentation/state.
- `player/` — player orchestration plus separate Sources/Episodes, Audio, Subtitles and More workspace files.
- `search/` — TV Search.
- `library/` — TV Library.
- `settings/` — Settings shell and shared entries.
  - `settings/profile/` — profile settings.
  - `settings/content/` — content manager and enhancement settings.
  - `settings/player/` — playback/subtitle/source/appearance settings.
  - `settings/system/` — data, updates and about.
- `source/` — TV source presentation.
- `ui/` — shared TV chrome/sidebar/design helpers.

## Shared Core

`shared/core/src/main/java/com/vueo/shared/core/`

Shared core stays grouped by domain (`storage`, `plugin`, `source`, `search`, `recommendation`, `enrichment`, `player`, etc.). This cleanup separates safe support code without rewriting behavior-critical engines:

- `plugin/PluginRuntime.kt` — provider execution engine.
- `plugin/PluginRuntimeModels.kt` — discovery/diagnostic models.
- `plugin/PluginRuntimeParsing.kt` — provider result/failure parsing.
- `plugin/PluginSourceResolver.kt` — shared source resolver adapter.
- `extensions/CatalogDiscoveryCache.kt` — catalog cache/ranking engine.
- `extensions/CatalogDiscoveryJson.kt` — catalog JSON persistence helpers.

## Rule for future patches

Put new code in the domain file/folder that owns it. Avoid growing `VueoApp.kt`, `TvPlayerScreen.kt`, or other orchestration files with screen-specific UI when a dedicated file already exists.
