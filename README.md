# VUEO Motion Cross-Transition v117 Patch

Baseline: v116
Scope: Mobile + TV motion/navigation only.
Shared core: unchanged.
Gradle build: NOT run.

## Why
The previous full-screen motion used a near-sequential fade-through: the outgoing
screen became faint before the incoming screen started drawing. On heavier pages
this could expose the black/background surface for a frame and read as a flicker.

## v117 motion model
- Incoming full-screen content starts immediately; there is no incoming dead-time.
- Outgoing content is held opaque briefly, then fades while the incoming page is
  already rendering. This overlap prevents the app background from appearing
  between screens.
- Only incoming full-screen content receives a tiny scale/depth cue. The outgoing
  full-screen tree is not scaled, reducing GPU work and visual hitching.
- Forward and Back transitions have distinct depth direction.
- Home/Search/Library/Settings tab transitions are shorter and shallower than
  page pushes.
- Player route transitions remain fade-only so video never zooms.

## Mobile
- Replaced old delayed root/surface fade-through with overlapping forward/back
  transitions.
- Removed the special delayed Home return transition. All root tabs now use the
  same lightweight tab handoff.
- Details -> Sources uses forward depth; Sources -> Details uses reverse depth.
- Player route uses dedicated fade-only handoff.
- Settings Personalization/DNA and Profile Manage/Edit now distinguish forward
  and reverse navigation.

## TV
- Replaced delayed screen fade-through with overlapping screen handoff.
- Home/Search/Library/Settings use a lighter tab transition.
- DETAIL/DNA/PROFILE/ENTITY_RESULTS/SOURCE returns use reverse motion where the
  route relationship is known.
- Player route remains fade-only.
- Manage Profiles / Profile Editor use reverse motion on Back.

## Static validation
- Kotlin delimiter/lexical scan: PASS
- Duplicate import scan: PASS
- Trailing-whitespace/tab scan: PASS
- Overlay/apply simulation over v116: PASS
- Patch root layout: PASS
