# VUEO TV Rebuild Lock

Baseline: **29A-R clean rebuild**.

## Product DNA

- Premium
- Cinematic
- Fluid
- 60fps-first

A screen is not accepted only because it looks good in a screenshot. It must feel immediate and predictable under a remote.

## Architecture lock

- TV Kotlin lives normally in `tv/src/main/java`.
- Legacy TV implementations were removed/overwritten; old paths that cannot be deleted by ZIP overlay are inert tombstones only.
- New TV must never depend on `:mobile`.
- Mobile is a behavioural reference. Shared Core is the reusable logic layer.
- Do not bring legacy TV repositories, root navigation, focus memory, TV-only library/playback stores or source wrappers into the rebuild.

## Explicit preservation exception

Keep the approved existing experience for:

- Who's Watching
- Manage Profiles
- Add Profile / Edit Profile
- Profile PIN/editor behaviour

Everything after profile selection belongs to the rebuilt TV runtime.

## Home lock

- Top contextual navigation: Home / Search / Library / Settings.
- Small persistent VUEO brand anchor.
- Hero + Peeking Row composition.
- Hero is presentation-only and never receives focus.
- Initial focus: first Continue Watching item; fallback first item in the first available row.
- Focused card drives the hero after a short ~180 ms settle delay.
- UP from first row reveals top navigation.
- DOWN restores the last content focus.
- OK on a card opens Details directly.
- No hero auto-rotation.
- No carousel dots.
- No excessive parallax or decorative motion.

## Motion lock

Home motion is intentionally small:

1. Immediate card focus with shallow scale/depth.
2. Smooth hero fade-through after focus settles.
3. Contextual navigation reveal/recede.

**Input fast. Visuals smooth. Navigation obvious.**

## Data/runtime direction

The new TV runtime adapts Mobile-proven behaviour using Shared Core:

- startup/profile gate via `ProfileStore`
- Home discovery via `UnifiedMediaEngine`
- catalog order + per-catalog enabled state using Mobile `AddonStore` semantics
- My List / Continue Watching via `LibraryStore`
- playback position via `PlaybackStore`
- settings via `SettingsStore`
- addon + provider source discovery through Shared Core runtime
- source cleanup/ranking through Shared Core

Do not redesign these behaviours merely because the TV UI is new.


## 29B Home calibration lock

Validated against the first real-TV Home screenshot after the clean rebuild:

- Home rail origin: ~62% viewport height.
- Contextual nav labels: alpha 0 while content has focus, reveal on UP.
- Home brand anchor: compact 18sp; profile anchor: compact 30dp.
- Home landscape card width: 244dp, 16:9, title outside artwork.
- Focus: ~1.045 scale, soft shadow, 1dp neutral edge.
- Hero fade-through: 420ms in / 220ms out after the locked 180ms focus settle.

Do not shrink the hero back to the 29A-R prototype proportions or restore persistent top-nav labels.

## 29B.1 Settings + D-pad lock
- Top navigation must require only one OK press to open the focused destination.
- Focus movement alone must never auto-open a destination.
- TV Settings is a clean TV-native surface backed by Shared Core; do not restore the legacy TV Settings implementation.
- The first TV Settings activation covers profile/startup, playback, subtitle and source preferences whose behavior already exists in Shared Core.

## 29B.2 Settings parity lock
The temporary 29B.1 flat Settings surface is replaced by the canonical VUEO hierarchy adapted to D-pad TV. Legacy TV Settings must not return. Settings that imply behavior must have a real TV consumer. One focused navigation/settings item requires one OK activation only.
