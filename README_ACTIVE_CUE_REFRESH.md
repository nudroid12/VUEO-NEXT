# VUEO TV active subtitle cue refresh fix

Apply over the confirmed 32fdffa baseline plus the working Native Position patch. One production file is replaced: tv/src/main/java/com/vueo/tv/ui/player/TvSubtitlePlayerView.kt. This ZIP does NOT replace TimelineLoader, so the working native position fix remains installed.

Changes:
- Refresh reads current SmartSubs cues when SmartSubs owns the track, instead of falling back to native player.currentCues.
- Retains raw input cues while asynchronous Temporal analysis is pending. The completion refresh still clears the equality guard before rebuilding the current group.
- Commentary visibility changes use the same active-owner cue resolver.

Unchanged: layout classification, upper/lower split, bottom positioning, font size, commentary rules, subtitle timestamps, translation/readiness, provider, normalizer, native TimelineLoader and Mobile.

Code failure addressed:
Pending analysis previously displayed a cue but discarded lastInputCues. Analysis completion cleared subtitle views and could read empty native cues while AI was selected. SmartSubs suppresses repeated identical cues, so that cleared display could remain blank until the next cue change. The fixed refresh consults the current active AI timeline directly.

Validation:
- Source guards passed for pending input retention, refresh invalidation, active-owner resolution and commentary refresh.
- Byte comparison confirms split/position/style/temporal-role code unchanged from baseline apart from the documented refresh changes.
- Included validation/active-cue-refresh/check.py extracts the production resolver for Kotlin checks. Kotlin execution NOT RUN here because kotlinc is unavailable.
- Full Android build and TV rendering/latency tests NOT RUN. The reported post-translation delay has NOT been proven to come from this code path.

Device acceptance:
1. Select a ready AI subtitle while a long cue is active. Its text should remain visible across analysis completion, including when paused.
2. Change Bottom Position/font/commentary visibility during that same cue; AI should refresh immediately without waiting for the next dialogue.
3. Switch AI to normal English/Malay, then back; check the working native position behavior remains intact and old cues do not leak across tracks.
4. Translation completion to first rendered text still needs fresh diagnostics or a timed recording if a delay persists.

To run the standalone resolver checks where Kotlin CLI exists:
  python3 validation/active-cue-refresh/check.py .
These checks do not execute Android SubtitleView or measure screen latency.
