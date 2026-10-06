VUEO v101 - Dual Subtitle Parenthetical Commentary Classification

Base: cumulative v100 state.
Scope: TV + Mobile subtitle renderer only. No shared-core change.

Behavior:
- Existing authored commentary metadata remains supported.
- New heuristic is enabled only when at least two lower subtitle cues are active simultaneously.
- A cue is treated as commentary when its entire trimmed text is enclosed by one outer pair of parentheses.
  Examples:
    (Pintu ditutup) -> commentary
    (Mengeluh) Saya penat hari ini. -> normal subtitle
    Saya penat hari ini. (Mengeluh) -> normal subtitle
- Parenthetical commentary uses Commentary Size and the existing measured dual-layer gap.
- If Commentary is disabled, the classified commentary cue is hidden while the main cue remains.
- Generic v98 dual-layer spacing remains the fallback when neither cue is commentary.

Files:
- tv/src/main/java/com/vueo/tv/ui/player/TvSubtitlePlayerView.kt
- mobile/src/main/java/com/vueo/mobile/ui/player/MobileSubtitlePlayerView.kt

Validation:
- No Gradle build performed by request.
- Static delimiter check: PASS
- diff --check: PASS
- Parenthetical rule cases: PASS
