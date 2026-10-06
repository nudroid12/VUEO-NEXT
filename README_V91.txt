VUEO v91 - Commentary Subtitle Spacing

Scope
- TV + Mobile player subtitle presentation only.
- No shared-core changes.
- No changes to seek, skip, source discovery, next-episode, playback timing, subtitle delay, font settings, or Bottom Position controls.

Changes
1. Commentary and normal subtitles now render in two separate SubtitleView layers.
2. Commentary remains above normal subtitles at the lower screen position.
3. Explicit 14dp visual gap is added above the normal subtitle block.
4. Commentary vertical offset also accounts for the current number of normal subtitle lines, so two-line dialogue gets more room than one-line dialogue.
5. Both layers still use the same font, text/background/outline style, and Bottom Position base.
6. When controls raise the subtitle base, both layers move together.
7. When commentary appears without a normal subtitle, it uses the normal Bottom Position without an unnecessary extra offset.
8. Commentary ON/OFF remains live without seeking/restarting playback.

Validation
- Gradle build intentionally not run.
- Static delimiter/balance checks passed.
- Diff scope checked: only TvSubtitlePlayerView.kt and MobileSubtitlePlayerView.kt plus this README.
