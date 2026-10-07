VUEO Resume Cursor Race Fix v112
================================
Baseline: v111
Scope: Mobile + TV + Shared Core
Gradle build: NOT run (per project rule)

Problem fixed
-------------
A title/episode could reopen at the position from which the previous player
session originally started instead of the newest position reached before Back.
The issue was timing-sensitive because Player progress is persisted in the
background while Details can be reopened immediately.

Example fixed:
- Open EP5 at 15:00
- Watch until 20:00
- Back out of Player
- Reopen EP5 quickly
- v112 resumes from the newest captured cursor (~20:00), not stale 15:00.

Shared-core changes
-------------------
1. PlaybackStore now captures an immediate process-local playback cursor before IO.
2. Every captured cursor gets a strictly increasing playback-event timestamp.
3. Async PlaybackStore persistence rejects an older event that finishes after a
   newer event.
4. Resume selection can read the newest process-local cursor while disk/library
   persistence is still in flight.
5. Completion/clear writes keep a timestamp tombstone so an old queued save cannot
   resurrect a completed resume position.
6. LibraryStore.recordPlayback accepts the captured playback-event timestamp.
7. Library history rejects stale same-episode writes.
8. Continue Watching title cursor keeps the newest playback event even when an
   older episode write completes later.
9. Library playback writes use a class-wide lock because Mobile/TV screens may
   own different LibraryStore instances pointing at the same preferences.

Mobile
------
- Back/periodic progress captures the latest cursor synchronously in memory, then
  persists it through the existing serialized IO queue.
- Player startup prefers that live-session cursor over a stale Details handoff.
- Deliberate rewinds are preserved. v112 does NOT use max(position).
- Player-start history writes use a captured event timestamp so completion order
  cannot redefine recency.
- Removes the accidental duplicate notifyLibrary named argument present in the
  v111 working source.

TV
--
- Adds serialized background player persistence.
- Back captures the newest cursor before navigation.
- Player startup prefers the live-session cursor over stale Details state.
- Periodic, exit, pause and completed-state persistence use captured snapshots.
- Library/UI refresh for background saves happens after persistence completes.
- Lifecycle stop persistence stays off Main.

Behavior intentionally preserved
--------------------------------
- Continue Watching still follows the LAST actively watched episode, not the
  highest episode number.
- A newer rewind is authoritative. Example: 40:00 -> rewind -> exit at 25:00
  resumes at 25:00.
- Existing completion threshold remains unchanged.
- Existing Mobile v111 responsiveness work remains intact.
- Existing TV responsiveness/player behavior remains intact outside resume
  persistence ordering.
- No Next Episode prefetch rules, source discovery, subtitle refresh, audio,
  transition, or UI design behavior changed.

Static validation
-----------------
- Kotlin delimiter/lexical balance: PASS on all changed Kotlin files.
- Duplicate imports: PASS.
- Duplicate notifyLibrary named argument: removed.
- git diff --check: no whitespace errors.
- Patch root/apply layout simulation: PASS.
- No Gradle build executed.
