VUEO Mobile Responsiveness Adaptation v111
==========================================

Base: v110
Scope: Mobile-only adaptation of the still-useful TV v90 responsiveness ideas.
TV and shared core are unchanged.
No Gradle build performed by design.

Changes
-------
1. Player Back responsiveness / lingering audio
   - Final Player exit now mutes + pauses ExoPlayer immediately before orientation/navigation work.
   - Resume/progress + Library history persistence is queued off Main and serialized.
   - The top Back button no longer performs a duplicate synchronous save before exit.

2. Deferred ExoPlayer release
   - Player disposal mutes + pauses immediately.
   - ExoPlayer release is deferred 64 ms, allowing the return/new-source composition a frame first.
   - Source-switch disposal still captures progress, but persistence is asynchronous.

3. First-frame audio gate
   - Each newly created Player starts muted before prepare/play.
   - A listener registered before prepare restores volume only when the first video frame renders.
   - Manual Retry re-arms the same gate before prepare/play.

4. Player persistence off Main
   - PlaybackStore + LibraryStore progress/history writes use a process-lived IO scope guarded by a shared Mutex.
   - Episode completion writes and clear-position operations use the same serialized queue.
   - Periodic resume-point writes are moved off Main.
   - Transient isPlaying=false states caused by buffering/seek no longer trigger expensive Library JSON saves; intentional pause still does.

5. Episode history hydration
   - libraryStore.history() is removed from Player composition.
   - Watched/progress history now hydrates on Dispatchers.IO and waits behind the same persistence Mutex for consistent reads.

Already covered by v110
-----------------------
- Poster -> Details progressive hydration was already adapted in v110, so v111 does not touch Mobile Details again.

Files
-----
mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
README_V111.txt

Validation
----------
- No Gradle build executed.
- Kotlin parser smoke check: no parser/syntax errors detected (dependency resolution intentionally unavailable outside Gradle).
- git diff --check: PASS.
- Patch overlay simulation against v110: PASS.
- TV tree unchanged: PASS.
- Shared tree unchanged: PASS.
