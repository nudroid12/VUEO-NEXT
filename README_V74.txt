VUEO Player Skip + Commentary Subtitle Fix v74
Base: cumulative state after VUEO-NEXT-main (38) + v68-v73 patches

Changes:
1) TV Skip prompt no longer triggers the cinematic gradient/scrim by itself.
   Full controls, panels and playback errors keep their normal scrim.
   Next Episode countdown keeps its scrim only when no Skip prompt is active.
2) TV Skip focus can escape when chrome is hidden:
   - DPAD_UP reveals controls and focuses Restart when there is no Next Episode card above it.
   - DPAD_DOWN reveals controls and focuses the progress rail.
   - Existing Skip -> Next Episode focus relationship is preserved when that card is present.
3) TV + Mobile commentary subtitle now stays in the lower subtitle area.
   - Commentary appears one blank line above the normal subtitle.
   - Commentary + normal subtitle share the same Bottom Position anchor.
   - Commentary toggle remains supported.

No provider/source/discovery/watch-history/seek logic changed.
