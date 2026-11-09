# TV AI initial position fix

Apply after Native Position Fix, Active Cue Refresh Fix and Temporal Minimal Cleanup. One production file: TvSubtitlePlayerView.kt. Does not restore the deleted collision helper or replace TimelineLoader.

AI while Temporal analysis is pending no longer uses the early raw-coordinate output branch. Automatic cues pass through the existing split/stack/padding path from their first displayed group. Negative automatic lines are not blanket-protected during this AI-only pending phase. Non-automatic decoded placements remain excluded by isAutomaticText. Native pending output, ready-state layout, temporal classification, font/commentary rules, timing, provider, translation and readiness remain unchanged. No waiting timer, hiding-until-ready or new network operation is introduced.

Limits: final temporal info/dialogue classification is still asynchronous. A multi-cue group assigned new roles after analysis may still be rearranged; this patch does not claim pixel-identical pre-analysis and post-analysis info layout. It targets the code path that paints automatic AI cues at raw coordinates before applying normal padding. There is no recording/log yet proving this is the user's exact observed frame sequence.

Source checks passed: unchanged split/commentary, height measurement, padding calculation and active-owner refresh; padding applied before normal output; native/ready paths preserved. ZIP integrity verified. Kotlin/Android compile and device/pixel tests NOT RUN because compiler/dependencies are unavailable.

Device acceptance: select AI with a single ordinary dialogue cue visible, including a negative automatic line; verify its first appearance uses Bottom Position and does not jump when TV_SUBTITLE_TIMELINE completes. Also test multi-cue info, commentary on/off, explicitly positioned captions, subtitle offset and normal English/Malay. If jumping occurs on every dialogue rather than initial selection, collect a recording and diagnostics rather than assuming this pending-analysis path is the cause.
