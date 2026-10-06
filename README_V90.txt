VUEO TV Responsiveness Targeted Fix v90
========================================

Scope: TV-only. No shared-core or Mobile changes.
No Gradle build performed by design.

Fixes
-----
1. Poster -> Details perceived delay
   - Initial Details composition no longer parses watchlist/history/continue-watching JSON on Main.
   - Library state hydrates on Dispatchers.IO after the instant Details shell is present.
   - Episode resume selection consumes the hydrated playback snapshot instead of synchronously reopening storage.

2. Player Back responsiveness / lingering audio
   - Back now mutes + pauses immediately.
   - Progress/history persistence runs on a process-lived IO scope so navigation does not wait for JSON serialization.
   - ExoPlayer release is deferred 64 ms after disposal, allowing the return route to present first while audio is already silent.

3. Player startup audio-before-video
   - Audio is muted before prepare/play for each source.
   - Volume restores only from onRenderedFirstFrame().
   - Retry path re-arms the same first-frame audio gate.

4. Player remote responsiveness
   - Watched-episode history parsing moved out of composition and onto Dispatchers.IO.
   - Background progress saves now move PlaybackStore + LibraryStore persistence off Main when requested.

Files
-----
tv/src/main/java/com/vueo/tv/ui/detail/TvDetailScreen.kt
tv/src/main/java/com/vueo/tv/ui/player/TvPlayerScreen.kt
README_V90.txt

Checks
------
- Kotlin delimiter/static structure check: OK
- Diff/whitespace check: OK
- Overlay path check: OK
- No Gradle build executed
