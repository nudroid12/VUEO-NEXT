# TV 29C.3 — Premium Collapsible Sidebar

29C.3 replaces the 29C.2 centered floating top capsule with the final TV navigation shell direction: a slim, collapsible left sidebar.

## Locked interaction grammar

- Content focus keeps the sidebar collapsed at 66dp.
- Entering the sidebar expands it to 202dp with a short 170ms width transition and 145ms label fade.
- DPAD_UP / DPAD_DOWN explores Home, Search, Library, Settings, then Profile.
- Focus movement never changes route.
- DPAD_CENTER / ENTER commits exactly once.
- DPAD_RIGHT returns to the exact last content focus when that surface has a focus target.
- DPAD_LEFT is consumed while the sidebar owns focus.
- From content, DPAD_LEFT on the first logical column enters the current destination in the sidebar.
- Search keeps normal cursor-left behavior when query text exists; an empty focused search field can enter the sidebar with DPAD_LEFT.

## Visual lock

- No VUEO wordmark on normal app pages.
- Sidebar is an overlay, not a content-resizing Android drawer.
- Translucent charcoal/black surface, subtle neutral-white edge, no neon accent and no live blur.
- Selected route uses a quiet fill; focused route uses a slightly stronger neutral-white fill/edge.
- Profile lives at the bottom of the sidebar rather than as a floating top-right anchor.
- Home/Search/Library/Settings content receives extra left breathing room so the collapsed rail never covers important content.

## Scope

Changed only TV presentation/focus plumbing:

- `tv/ui/TvTopBar.kt` now hosts `TvSidebar` and the shared primary destination list.
- Home left-edge focus entry + content offset.
- Search left-edge focus entry, exact local focus restore, content offset, and top-chrome removal spacing.
- Library deterministic content focus memory + sidebar entry/return.
- Settings sidebar entry/return while preserving LEFT/RIGHT value adjustment for adjustable rows.

No Mobile or Shared Core behavior changed. 29C Search behavior, 29C.1 eight-poster density, Home hero behavior, and 29C.2a updater restore remain intact.
