# TV 29C.2 — Centered Floating Navigation

Scope is intentionally narrow: global TV navigation presentation only.

## Changed
- `TvTopBar` now renders Home / Search / Library / Settings inside one centered floating capsule.
- Profile stays outside the capsule at top-right.
- Current route has a quiet filled pill; focused route uses a slightly brighter fill/edge and ~1.025 scale.
- Capsule uses translucent theme surfaces and a subtle neutral border; no live blur is used, protecting 60fps-first behavior.
- Top spacing is slightly tighter so the control island sits naturally in the upper center of a TV viewport.
- Existing contextual collapse/reveal behavior is preserved for Home/Search.

## Not changed
- Route graph or route commit behavior.
- One-OK activation contract.
- Search 29C/29C.1 logic, state restoration, 8-up poster density, or filters.
- Home hero, rails, focus settle, or playback/library/settings behavior.
- Mobile or Shared Core.
- Update popup behavior (separate restoration task).

## Build note
Local Gradle compile could not start because the environment cannot resolve `services.gradle.org`; device/CI build remains the authoritative compile check.
