VUEO v96 - TV Source Refresh Focus + Episode Playing Progress

TV-only patch. Apply after v95.

1. Source page Refresh remains focusable while discovery is active.
   - Left from All/provider tabs can focus Refresh during loading.
   - OK while discovery is active stops the current source scan.
   - OK after stop starts Refresh again.
   - Explicit discovery-running state prevents a stopped pre-snapshot scan from looking active.

2. Player Episodes status priority:
   - Current episode: Playing
   - Completed non-current episode: Watched
   - Partially watched non-current episode: Resume M:SS

3. Episode thumbnail progress:
   - 6dp high for TV viewing distance.
   - Dark neutral unplayed track.
   - White/light-neutral played fill, no lime/mobile accent.
   - Current episode follows live player progress.
   - Watched non-current episodes render full progress.

No Mobile/shared-core changes.
No Gradle build performed.
Static delimiter, diff, whitespace and overlay-path checks passed.
