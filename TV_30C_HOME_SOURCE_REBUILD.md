# TV 30C — Source-referenced Home rebuild

Reference inspected: `NuvioTV-0.8.6-beta` supplied by the user.

Source files reviewed for behavior and layout principles:
- `ui/screens/home/ModernHomeContent.kt`
- `ui/screens/home/ModernHomeModels.kt`
- `ui/screens/home/ModernHomeRowsList.kt`
- `ui/screens/home/ModernHomeRows.kt`
- `ui/theme/ComponentTokens.kt`
- `ui/theme/LayoutMediaTokens.kt`
- `ModernSidebarBlurPanel.kt`
- `MainActivity.kt`

The implementation in VUEO is rewritten for VUEO's own data, routes and focus contracts. No Nuvio source file is copied into VUEO.

## Adopted principles

- Home rails occupy roughly the lower half of the viewport rather than sitting too low.
- Hero copy stays within a compact left reading column; artwork remains visually dominant on the right.
- Continue Watching uses a restrained landscape card; catalog rows use portrait cards.
- Focus scale is intentionally small (`1.02x`).
- Horizontal row focus remembers the last index per row.
- Up/down navigation targets the remembered index in the adjacent row rather than relying only on spatial guessing.
- The collapsed navigation state is a stable icon rail, not a delayed floating route pill.
- The expanded sidebar is created only while open/animating, so an invisible focusable panel cannot cover Home.

## VUEO 30C metrics

- Rows begin at `49%` of viewport height.
- Hero text width: `42%`.
- Content anchor: `92dp` from the left.
- Continue Watching card width: `252dp`, 16:9.
- Catalog poster width: `144dp`, 2:3.
- Card focus scale: `1.02x`.
- Collapsed rail: icon-only, centered vertically.

## Preserved VUEO behavior

- Continue Watching stays first when available.
- My List stays second when available.
- Remaining catalog order is unchanged.
- LEFT from the first item opens the sidebar.
- RIGHT from the expanded sidebar restores the exact last content focus target.
- VUEO themes/accent and VUEO data/runtime remain unchanged.
