VUEO TV Subtitle Control + Focus Reliability v125

Base: current v124 chain. The touched TV Player files were unchanged by v121-v124, so this is a clean overlay on the latest repo.
Scope: TV Player subtitles only. Mobile/shared untouched.
No Gradle build.

Fixes
1. Restore Subs control even when no subtitle tracks are currently available
   - TV Player chrome now always exposes the Subs action.
   - Opening it with zero tracks lands in the subtitle workspace with Off available.
   - Refresh remains reachable even when discovery returned nothing.
   - This restores the older expected behavior instead of hiding the entire subtitle entry point.

2. Harden Languages -> Subtitles D-pad focus
   - D-pad Right from every language row with tracks now uses an explicit focus handoff.
   - The handoff first activates that language, waits for its track column to be rebuilt, scrolls the first track into composition, then requests focus with a longer retry window.
   - No direct right FocusRequester is kept across a language change, avoiding the stale-requester race that intermittently trapped focus in Languages.
   - Repeated Right presses are given a request generation counter, so a failed handoff can be retried instead of becoming permanently stuck.

Unchanged
- Subtitle discovery/readiness logic
- SmartSubs/OpenSubtitles behavior
- Same-track retry/re-apply behavior from v115
- Subtitle Refresh behavior from v116
- Subtitle style controls
- Mobile Player
- shared/core
