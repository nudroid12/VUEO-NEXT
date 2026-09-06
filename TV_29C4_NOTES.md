# TV 29C.4 — Nuvio-Reference Sidebar Rebuild

Baseline: 29C.3a (including 29C.2a update popup restore).

## Why
The 29C.3 custom slim rail did not match the desired TV feel. The supplied `NuvioTV-0.8.6-beta` project is now the explicit UI/interaction reference for the TV rebuild.

## Nuvio source reviewed
- `app/src/main/java/com/nuvio/tv/MainActivity.kt`
  - `ModernSidebarScaffold`
  - `CollapsedSidebarPill`
  - focus transfer / expand-collapse choreography
- `app/src/main/java/com/nuvio/tv/ModernSidebarBlurPanel.kt`
  - inset rounded panel
  - Profile at top
  - vertically centered navigation
  - circular icon wells
  - full-pill selected/focus rows
- `app/src/main/java/com/nuvio/tv/ui/components/SidebarNavigation.kt`
- `app/src/main/java/com/nuvio/tv/ui/theme/ComponentTokens.kt`
- `app/src/main/java/com/nuvio/tv/ui/theme/MotionFocusTokens.kt`

## VUEO implementation
- Replaces the visible 66dp collapsed rail / 202dp expanded rail visual.
- Collapsed navigation becomes a floating current-route pill near top-left.
- Pill label reduces to icon-only after 4 seconds of idle; Settings keeps its label; Search hides the collapsed pill.
- Expanded navigation becomes an inset rounded floating overlay panel, ~262dp target width / 30dp radius.
- Profile moves to the top of the panel.
- Home / Search / Library / Settings are vertically centered.
- Navigation rows are ~52dp high with ~34dp circular icon wells and rounded-full focus/selection surfaces.
- Panel enter/exit and label timings are adapted from the supplied Nuvio motion proportions.
- No Haze/live-blur dependency was imported; VUEO uses its existing theme colors with a layered translucent gradient to stay lightweight.
- Home/Search/Library/Settings left padding added only for the old rail is reverted to the pre-rail content width.

## Behavior preserved
- LEFT from logical content edge enters current destination.
- RIGHT restores exact last content focus where supported.
- UP/DOWN explores navigation.
- Focus movement never routes.
- OK/Enter commits once on KeyUp.
- Search cursor-left remains normal while query text exists; empty field can enter navigation.
- Search 8-up poster density remains unchanged.
- Home hero logic remains unchanged.
- 29C.2a update popup remains unchanged.
- Mobile and Shared Core remain untouched.

## Build status
Local Gradle cannot start because the environment cannot resolve `services.gradle.org`, including with `--offline`; CI/device build remains canonical. Static delimiter/reference checks pass.
