# TV-only subtitle collision guard

Apply ZIP contents at the VUEO repository root, then build/reinstall TV. Only two production files are included: TvSubtitlePlayerView.kt and new TvSubtitleCollisionLayout.kt. Based on the uploaded VUEO-NEXT-main (63) TV renderer; compatible with the earlier subtitle decoder, diagnostic and normalizer patches, whose files are not replaced.

Behavior:
- Keep existing lower dialogue grouping, parenthetical commentary classification, commentary toggle and size.
- When a default-position lower cue and an authored-position cue coexist, measure both layers with current font, typeface, wrapping width, line spacing and anchors.
- Use a Media3 Canvas SubtitlePainter-style bounds estimate with a small edge guard. Keep default-position cue boxes fixed. Reposition an authored cue only if its estimated box conflicts; choose the nearest visible free vertical slot, preferring above on ties.
- Preserve text, timing, horizontal placement and style metadata. Only the moved cue's vertical position/anchor is changed.
- Recalculate on viewport, style and bottom-padding changes, including paused frames.
- Frames containing only default-position cues use the existing inexpensive path.
- Performance diagnostics logs TV_SUBTITLE_COLLISION with moved count and viewport size, without subtitle text.

This is geometric avoidance, not semantic dialogue/info classification. The source-positioned cue is the movable one. Existing lower-source grouping still applies before this guard. Mobile, HiAnime, translation service, SmartSubs activation, network requests, video and timeouts remain unchanged. No YAML.

Limits:
- Bounds are conservative estimates of Canvas subtitle text, not measured GPU glyph rectangles. Ruby, unusual spans, OEM typography or differences in Media3 layout can affect accuracy.
- Bitmap, vertical and sheared cues are left unchanged. Groups larger than 32 cues bypass the extra measurement.
- If no visible free slot exists at the chosen font sizes, preserve source rather than hide text, shrink font or place it offscreen; overlap may remain in that case.
- No exact cue file or device rendering reproduction for the reported SNAFU S1E7 11:44 scene was available. No full APK build or on-device visual test has been completed.

Validation: pure geometry tests plus 5,000 generated cases; complete modified view/helper Kotlin compilation with real Android/Media3 APIs and compile-only app-service stubs; existing commentary policy unchanged; Mobile renderer unchanged. See validation/tv-subtitle-collision/README.md.

Device check: repeat 11:44 with English and Malay AI, then change font size, show/hide controls, pause/resume, and verify commentary toggle/size still behave as before. Existing working mobile image is the visual reference.
