VUEO v93 — TV Episodes + Subtitle Handoff + Commentary Size

Base: cumulative v92 state.
No Gradle build was run by design.

1) TV Episodes resume/progress parity
- Load saved episode position + duration off the main thread.
- Show a lime progress strip on episode thumbnails.
- Show "Resume M:SS" for partially watched episodes.
- Keep Watched indication for completed episodes.
- Current episode uses live player position/duration while the panel is open.

2) TV Next/manual episode subtitle handoff
- Late discovered external subtitles are visible in the TV subtitle workspace immediately.
- Media3 late TrackGroup publication is reconciled for up to ~6 seconds after registration.
- Prefetched subtitle results are explicitly handed to the committed episode after source selection.
- Existing subtitle-language preference is restored when the matching track becomes available.
- Video playback does not wait for subtitle discovery.

3) TV Episodes DPAD behavior
- Left/Right on an episode card no longer changes the 50-episode range group.
- Down at the final card also no longer silently changes group.
- Range groups change only through the range chips.

4) Commentary font size
ARCHITECTURE CHANGE: shared subtitle settings now store a separate commentary font size.
- TV + Mobile use the same saved commentary size preference.
- New control: Commentary Size, 12–40sp in 2sp steps.
- Main subtitle Font Size remains independent.
- Existing commentary gap/position behavior is preserved.
- Existing users default commentary size to their current main subtitle size until changed.

Changed implementation areas:
- shared/core SettingsStore subtitle preference
- TV episode player panel/presentation
- TV episode-switch subtitle handoff
- TV late subtitle registration/selection
- TV + Mobile subtitle style UI/rendering

Validation:
- delimiter/lexical scan on all changed Kotlin files: clean
- kotlinc parse pass: no syntax/"expecting" errors (Android/Compose symbols intentionally unresolved without Android classpath)
- git diff --check: clean
- no Gradle build run
