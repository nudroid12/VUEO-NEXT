# VUEO Code Structure

Repository source is grouped first by **core** vs **ui**, then by feature. This is a structural organisation only; Kotlin package names are intentionally unchanged in this phase to avoid behavior/navigation churn.

## Mobile

`mobile/src/main/java/com/vueo/mobile/`

- `core/` — non-visual mobile runtime, storage aliases, player/source policies, plugin/runtime helpers and platform-facing core adapters.
- `ui/app/` — app shell/orchestration (`VueoApp`).
- `ui/home/` — Home, catalog rows, Continue Watching and media actions.
- `ui/detail/` — Details, episode selectors and entity results.
- `ui/player/` — player orchestration plus Audio/Episodes/More/Sources/Subtitles workspaces and skip controls.
- `ui/search/` — Search presentation.
- `ui/library/` — Library/My List/History presentation.
- `ui/source/` — source picker UI.
- `ui/settings/` — settings shell/pages/components.
- `ui/content/` — Content Manager, addons, providers and diagnostics.
- `ui/profile/` — profiles and User DNA presentation.
- `ui/components/` — reusable visual components.
- `ui/theme/` — theme/design/motion.
- `MainActivity.kt` — Android entry point.

## TV

`tv/src/main/java/com/vueo/tv/`

- `core/runtime/` — TV runtime/orchestration helpers.
- `core/preferences/` — non-visual persisted TV preferences.
- `core/detail/` — non-visual detail upstream adapter.
- `core/update/` — update manager.
- `ui/app/` — TV app shell (`VueoTvApp`).
- `ui/home/` — Home presentation.
- `ui/detail/` — Details presentation/state.
- `ui/player/` — player orchestration and all TV player workspaces.
- `ui/search/` — Search/entity results.
- `ui/library/` — Library.
- `ui/source/` — source picker presentation.
- `ui/settings/` — settings shell plus profile/content/player/system pages.
- `ui/profile/` — profile picker/PIN/User DNA UI.
- `ui/sidebar/` — sidebar presentation and sidebar-specific preference helper.
- `ui/components/` — reusable TV visual components.
- `ui/theme/` — design and motion tokens.
- `ui/update/` — update prompt UI.
- `MainActivity.kt` — Android entry point.

## Shared Core

`shared/core/src/main/java/com/vueo/shared/core/` stays the canonical cross-platform logic layer. Mobile/TV must reuse it rather than duplicating shared behavior.

## Replacement ZIP note

The current repository patch workflow overlays files and cannot physically delete tracked paths. Old moved Kotlin paths are therefore replaced with inert **structural tombstones**. They contain no declarations and are not part of the runtime; the live implementation is only at the new `core/` or `ui/` path written inside each tombstone.

## Rule for future patches

New non-visual app-specific code goes under `core/<domain>/`. New screens/components/workspaces go under `ui/<domain>/`. Avoid adding feature UI back to module-root folders or growing app-shell files with screen-specific code.
