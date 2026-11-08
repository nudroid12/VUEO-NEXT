# TV subtitle temporal layout patch

Apply the ZIP paths over the VUEO project root. This replaces the v1 audit view;
no YAML or provider update is required. Rebuild the TV application.

## Changes
- The old authored-cue collision relocation code is removed from the TV view.
- Explicit fractional/positive positions, horizontal placement, vertical writing,
  bitmap cues and sheared text stay outside the automatic bottom layout.
- Original WebVTT timing-line settings (line, position, size, vertical) are inspected
  before interpreting negative numbers. Explicit line:-4 is protected too.
  Protection is conservative by caption text within the selected file.
- Negative auto-generated line indices are no longer limited to three lines.
- A background analysis reads already-prepared subtitle bytes from session cache.
  It initiates no download, decrypt, translation, media preparation or video wait.
  This does add one separate timeline parse for the selected subtitle, plus parsing
  explicitly positioned VTT blocks to preserve their decoded text identity.
- Evidence requires at least two persistent cue texts with matching reconstructed
  start/end times, lasting >=6 seconds, and at least two distinct non-overlapping
  shorter cues contained within that interval. A long duration alone is insufficient.
  Persistent parser slices are joined by text before analysis. Work is bounded.
- Confident info is shown above dialogue in original active-cue order, with stable
  spacing reserved for the region's dialogue and the TV control lift. Dialogue
  follows user bottom padding and controls. Info does not follow panel visibility.
- Full-parentheses commentary classification/toggle/size remain on their existing
  path. A candidate group containing such commentary does not use temporal roles.
- The media clock includes the user's subtitle offset. Work is cancelled and stale
  results discarded when the player/content/selected subtitle changes.
- SmartSubs only gains a read-only selected-track accessor. Neither Mobile output
  nor AI generation/prompt, AES/cache implementation, provider or video logic changes.

## Limits — do not interpret as perfect semantic detection
- This is a timing heuristic, not an understanding of spoken dialogue.
  Persistent multi-line lyrics or overlapping long dialogue can resemble info.
- A single persistent cue, or a block overlapping only one dialogue, does not meet
  the confidence rule. These keep the fallback layout, which can follow controls.
- Native external tracks need a matching selected subtitle configuration ID and
  prepared bytes in session cache. Embedded/uncached files have no full timeline;
  their positioned cues are preserved rather than assigned guessed semantic roles.
- ASS/TTML use decoded positions; full script/style preservation is not implemented
  by this patch and does not replace a dedicated libass renderer.
- Explicit positioned source text is not moved to avoid collisions. If authors
  deliberately overlap such text, this patch preserves that placement.
- While analysis runs captions render with their source coordinates, so a brief
  initial unsorted/overlapping frame is possible; the video is never gated on it.
- No original HiAnime VTT for the reported scene was available. Episode success
  has not been verified; no claim that the exact scene is fixed on-device.

## Validation
- 16 Kotlin/JVM policy checks passed (see validation/tv-temporal/TemporalCheck.kt).
  They cover reconstructed intervals, reordered parser output, ordinary dialogue,
  insufficient evidence, simultaneous speakers, simple lyrics, negative auto lines,
  explicit position detection, horizontal positions and vertical writing.
- TV view, loader, diagnostics and extracted actual stack implementation compile
  against Android 14 / Media3 1.11.0 and coroutines 1.8.1; app services were stubs.
- Full Gradle/APK and Android rendering tests were not run. Attempting the parser
  fixture in a plain JVM stops at missing Android native SystemProperties support;
  the parser/painter require an Android test runtime. Policy tests do not validate
  rendered pixels or all commentary/AI integration paths.

## Device verification
Use English first at the reported episode/time, then Malay AI. Check info order,
dialogue bottom placement and opening/hiding controls. Include a positioned sign,
commentary off/on and a normal dialogue-only scene. Existing diagnostics retain
CUE_LAYOUT; upper-layer records now include info=true/false. Performance records
TV_SUBTITLE_TIMELINE regions/protected/parsed counters without readable captions.
