# Home vertical row entry viewport
Based on repo65. Only TvHomeRows.kt changes. Up/Down keeps the horizontal viewport when its saved target poster is fully visible. BringIntoView uses minimal reveal during vertical entry instead of left anchoring. Explicit Left/Right resets to existing leading-edge behavior. Covered-route return handling remains prioritized.

Both pre-focus scroll and post-focus BringIntoView are addressed. No image URL/cache, catalog order or hero timing change. Selected saved card remains the target; this does not force every row to its first card. Offscreen targets may still require horizontal scrolling.

Apply CHANGES.diff via git apply (recommended if later Home edits exist), or copy included source file over repo65. Static checks, patch applicability and ZIP CRC only. Android build and device DPAD verification not run. Check down/up into visible saved card, offscreen saved card, Left/Right, and return from details.
