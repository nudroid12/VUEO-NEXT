# TV 31A — Clean Home Rebuild

This checkpoint replaces the previous VUEO TV Home/sidebar implementation instead of layering another corrective patch on top of it.

## Source-reference decisions

The uploaded NuvioTV 0.8.6-beta source was used only as a technical reference for proven TV interaction/layout principles:

- modern Home keeps the row viewport around half of screen height,
- hero copy occupies a restrained left-side column,
- backdrop remains visually dominant on the right,
- Continue Watching uses landscape cards while normal catalogs use portrait posters,
- focus is remembered per row/item,
- sidebar/content focus transfer is explicit and D-pad first.

VUEO code is independently implemented around VUEO's existing Shared Core, `TvRuntime`, library store, catalog rows, routes, player flow and profile flow.

## VUEO 31A Home contract

- Home rows begin at 49% of the viewport.
- Hero copy uses 42% of width and does not exceed the upper half of the screen.
- Continue Watching cards: 246 x 138 dp.
- Catalog/My List posters: 136 x 204 dp.
- Focus scale: 1.022.
- Continue Watching, My List and provider catalog data are unchanged.
- Hero changes only after a short focus settle delay to avoid backdrop flicker.
- Last focused item and per-row item index are retained in-process.

## Sidebar contract

The old floating/hidden dual-shell sidebar is retired.

There is now one set of focus targets:

- content focus -> quiet icon rail,
- DPAD LEFT -> the same rail expands with labels,
- DPAD RIGHT -> return to exact Home content focus,
- no collapsed top-left pill,
- no duplicate hidden focus targets.

## Superseded Home UI work

For Home/sidebar presentation, this checkpoint supersedes 29C.x and 30A/30B/30C implementations. Those historical notes remain history only and must not be used as the active Home architecture.
