# TV 33A — Sidebar Rebuild

Baseline: TV 32C Home is locked. Backend, data, routes and root-screen contracts remain VUEO-owned.

## Rebuilt from blank

`tv/src/main/java/com/vueo/tv/ui/TvTopBar.kt` was fully replaced. The old rounded panel/pill presentation and item-card styling are not retained.

## Nuvio reference principles adapted

- selected route is the deterministic entry point from content
- focus entering navigation expands the drawer
- RIGHT/BACK returns focus to the screen-owned content target
- one stable requester exists for each destination
- labels appear only in expanded state
- movement inside the drawer is explicit and predictable

## VUEO visual direction

- 72dp collapsed clean icon rail
- 238dp expanded drawer
- no floating pill
- no rounded sidebar capsule
- no per-item pill/card background
- selected/focused state uses a slim accent indicator + icon treatment
- profile remains available at the bottom
- Home/Search/Library/Settings remain unchanged routes

## Regression boundary

33A does not modify Home 32C presentation, repositories, playback, provider logic, search/library/settings content or routing contracts.
