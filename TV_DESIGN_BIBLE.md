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
- No persistent VUEO wordmark in post-profile page chrome; branding stays in startup/profile surfaces.
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
- Home navigation labels fully disappear while browsing content; the profile anchor remains the quiet persistent anchor.
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

## Settings interaction grammar
Settings is a calm single-column 10-foot workspace. Up/Down changes focus, Left/Right changes inline values, OK commits/opens once, and Back returns one level. The contextual top navigation remains the same global navigation plane used by Home; Down restores the exact last Settings row. Disabled informational rows are never used as the only focus target.

## 29C Search lock
- Mobile Search UI hierarchy is the visual/behavior reference for TV Search.
- Keep Search -> field -> Discover/Search Results -> Title/Actor -> Type/Sort/Genre -> poster grid.
- TV adaptation is limited to 10-foot sizing, neutral-white focus treatment and deterministic D-pad behavior.
- Poster results remain 2:3 artwork with title and metadata below; do not regress to landscape developer cards.
- Search returns from Detail with query, filters, grid position and exact focus restored where possible.


## 29C.1 compact Search composition
- Search uses the approved mockup as a composition/density reference, not as a new navigation architecture.
- Keep contextual top navigation; do not introduce the mockup's permanent left sidebar.
- Remove the VUEO wordmark from regular post-profile page chrome.
- Search title, field and controls are deliberately smaller than the first 29C real-TV pass.
- Search field occupies roughly three quarters of the content width rather than spanning edge-to-edge.
- Type / Sort / Genre / Title-Actor live on a single calm control line.
- Results are 2:3 poster cards with **8 columns** visible per row, title + quiet metadata below.
- Focus motion is purposeful only: ~1.035 scale, short 110-145ms timing, low shadow, 1dp neutral edge.

## 29C.2 centered floating navigation
- Global post-profile navigation is a **centered floating capsule**, not a full-width toolbar and not a sidebar.
- Capsule contents: Home / Search / Library / Settings. Profile remains outside the capsule at top-right.
- Use a quiet translucent charcoal/glass-like surface with a subtle 1dp neutral edge; do not use expensive live blur.
- Selected route gets a restrained filled pill. Focus gets a slightly stronger fill/edge and only ~1.025 scale.
- Navigation motion stays short and functional (roughly 95–145ms), with no bounce, glow, or ornamental transitions.
- Focus movement does not route. OK/Enter commits once. Down returns to the screen's existing content target.
- Home/Search may collapse labels while content owns focus, preserving the 29B contextual-navigation lock.

## 29C.3 collapsible sidebar navigation — supersedes earlier top-nav locks
- The 29B/29C.1/29C.2 top-navigation rules above are historical and are superseded for the current TV shell.
- Global post-profile navigation is now a **slim collapsible left sidebar**.
- Collapsed rail: 66dp. Focused/expanded rail: 202dp.
- Order: Home / Search / Library / Settings, with Profile at the bottom.
- LEFT from the first logical content column enters the current destination. RIGHT restores exact last content focus where available.
- UP/DOWN explores the rail; OK/Enter commits once; focus movement alone never routes.
- Search preserves normal cursor-left behavior while query text exists; an empty search field may enter the sidebar directly with LEFT.
- The rail overlays the cinematic canvas and uses a quiet translucent charcoal surface with a subtle neutral edge. No live blur, glow, bounce, or decorative motion.
- Normal app pages remain free of the VUEO wordmark.

## 29C.4 Nuvio-reference visual system
The supplied Nuvio TV source is the current design reference for the VUEO TV rebuild. Use it as a visual and interaction benchmark, not as a code/runtime architecture dependency.

Relevant source references in the supplied project:
- `app/src/main/java/com/nuvio/tv/MainActivity.kt` — `ModernSidebarScaffold`, collapsed route pill, focus transfer and expand/collapse choreography.
- `app/src/main/java/com/nuvio/tv/ModernSidebarBlurPanel.kt` — rounded floating panel, Profile-at-top composition, centered primary destinations, circular icon wells and pill navigation rows.
- `app/src/main/java/com/nuvio/tv/ui/components/SidebarNavigation.kt` — selected/focused item treatment.
- `app/src/main/java/com/nuvio/tv/ui/theme/ComponentTokens.kt` and `MotionFocusTokens.kt` — sidebar sizing/motion proportions.

VUEO adaptation rules:
- Keep VUEO routes, Shared Core behavior and current theme system.
- Prefer floating/inset overlays over a persistent full-height rail that permanently steals canvas width.
- Normal content should return to its pre-29C.3 horizontal breathing space; the navigation panel overlays the cinematic canvas when open.
- Collapsed navigation is a current-route pill rather than a visible vertical rail. It may reduce to icon-only after idle; Settings may retain its label; Search may omit the pill.
- Expanded panel target is roughly 262dp wide with ~30dp corner radius, inset from overscan edges.
- Navigation row target is roughly 52dp high with a ~34dp circular leading visual and rounded-full selected/focus surface.
- Keep neutral white, low-noise focus and no bounce/neon treatment.

## 29D Library composition — Mobile parity, Nuvio TV grammar
- Library feature/data behavior follows VUEO Mobile; Nuvio is a TV presentation/focus reference only.
- Visible Library structure: **Library title → My List / Cloud controls + Grid/List control → content**.
- Continue Watching and History are not dedicated Library sections.
- Grid/List view is a real user preference and must remain remembered using the Mobile preference contract.
- Poster density is target-width responsive; fixed 8-up is not a global TV design rule.
- Poster proportions remain 2:3. Nuvio reference proportions guide the current target width (~126dp), 12dp radius, 2dp focused edge, ~1.02 scale and ~180ms focus motion.
- List mode is compact and content-first: poster thumbnail, title, release/type metadata, restrained focus surface.
- Preserve exact last-item/scroll focus return after Detail where possible.
- Keep Library header below the 29C.4 collapsed route pill rather than adding a permanent left rail gutter.

## 29E Detail baseline — Nuvio-reference composition, Mobile behaviour

Detail uses a sticky full-screen backdrop with strong left and bottom scrims, a bottom-weighted hero text/action block, then TV-native horizontal sections. Primary action receives initial focus; custom controls activate on KeyUp once; focus motion remains shallow (~1.02–1.025) with neutral white treatment. Series uses season tabs plus 16:9 episode cards with playback progress. More Like This uses restrained landscape cards. Overview must remain accessible even when hero copy is clamped.

This is not a final visual lock. Preserve the functional composition and Mobile parity, then calibrate exact density/spacing/type/motion during the final whole-TV polish pass.

## 29F Source Selection baseline — Nuvio stream grammar, Mobile source semantics

Source uses the supplied Nuvio `StreamScreen.kt` as the 10-foot composition reference: cinematic full-screen backdrop, quiet information plane on the left, provider chips plus a rounded translucent stream workspace on the right. Source rows do not scale on focus; use a restrained 2dp focus edge/surface contrast instead. Keep the workspace dense enough for several rows at once and avoid oversized mobile cards.

Functional presentation must still expose VUEO Mobile semantics: live/ready discovery state, optional Engine Details, provider filtering, recommended source, source metadata/technical-detail preference and progressive results. D-pad rules: initial source focus when safe, UP from the first row to the active chip, chip DOWN to results, LEFT/RIGHT source-row provider cycling, and KeyUp-only OK activation. Exact dimensions/type/motion remain subject to the final whole-TV polish pass.
