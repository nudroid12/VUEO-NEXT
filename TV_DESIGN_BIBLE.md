# VUEO TV Design Bible — Home v1

Status: **LOCKED for the clean TV rebuild (29A-R+)**

## Product DNA

VUEO TV is built around three qualities:

1. **Premium** — restrained chrome, deliberate spacing, quiet brand presence, no decorative clutter.
2. **Cinematic** — artwork owns the canvas; UI overlays support the content instead of boxing it in.
3. **Fluid** — remote input responds immediately while larger visual changes settle smoothly.

Performance rule: **60 FPS first.** Any visual effect that causes unstable frame pacing is removed or simplified.

## Home composition

Locked composition: **Hero + Peeking Row**.

- Full-canvas cinematic artwork.
- Small persistent VUEO brand anchor at the top-left.
- Profile anchor at the top-right.
- Hero is presentation only and never receives focus.
- The first content rail is visible in the initial viewport.
- Continue Watching is the initial focus target when available; otherwise the first available rail is used.

## Navigation

Locked navigation: **Top contextual navigation**.

Primary items: `Home`, `Search`, `Library`, `Settings`.

- Navigation labels visually recede during content browsing.
- UP from the first rail focuses Home and reveals the navigation.
- DOWN from navigation restores the exact previous content card.
- No hamburger menu and no persistent sidebar.
- Profile remains a separate small anchor.

## Home interaction

- Card focus is immediate.
- The focused card becomes the pending hero item.
- Hero artwork/text update after a 180 ms settle delay to avoid flashing during fast D-pad movement.
- Hero transition is a soft fade-through, not a slide or zoom.
- OK on a content card opens Details directly.
- No hero buttons.
- No hero carousel dots.
- No auto-rotating hero.
- No ornamental parallax.

## Motion language

Home uses only three primary motion ideas:

1. Shallow card focus depth/scale.
2. Smooth hero fade-through.
3. Contextual navigation reveal/recede.

Principle: **Input fast. Visuals smooth. Navigation obvious.**

## Focus language

- Focus uses a clean neutral white edge and small depth increase.
- Avoid loud neon outlines for ordinary browsing.
- Focus must remain unambiguous from normal TV viewing distance.
- Vertical movement should be deterministic and preserve horizontal intent where possible.

## Clean rebuild scope

29A-R cuts over the entire post-profile TV runtime to fresh code. Legacy Home, Search, Library, Settings, Details, Source and Player implementations are removed/tombstoned. Only the approved profile experience is intentionally preserved.

Mobile provides proven behaviour contracts through Shared Core; TV does not copy Mobile UI and does not depend on `:mobile`.


## 29B real-TV visual lock

29B is calibrated from the first real-TV screenshot of the clean rebuild. Home presentation now follows these concrete rules:

- First rail begins at roughly 62% of the viewport so the hero owns the upper canvas while the first row still peeks into view.
- Home navigation labels fully disappear while browsing content; VUEO and the profile anchor remain quiet anchors.
- Hero copy uses a wider breathing field, stronger title hierarchy and restrained three-line synopsis.
- Layered horizontal + vertical scrims merge artwork, copy and rails into one cinematic canvas.
- Landscape cards are larger (244dp), with titles below artwork instead of heavy text painted over every image.
- Focus is shallow scale + soft shadow + a 1dp neutral edge. No thick white frame.
- Hero still settles after 180ms and fades through; remote focus itself remains immediate.

## 29B.1 Remote Navigation Contract
- D-pad movement changes focus only; it never commits navigation.
- DPAD_CENTER / ENTER performs one action for the focused target.
- Do not stack independent focus targets for one visible control.
- Content -> UP -> contextual top navigation.
- Top navigation -> DOWN -> exact last focused content/settings target.
- Settings rows use Up/Down to move, Left/Right to adjust, OK to commit/toggle/open.
