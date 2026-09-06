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
