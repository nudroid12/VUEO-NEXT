VUEO v107 - Performance Diagnostics log presentation cleanup
Baseline: VUEO v105 Performance Diagnostics. Compatible after v106 Crash Diagnostics FIXED because v106 does not modify PerformanceDiagnostics shared core.

SCOPE
- Shared PerformanceDiagnostics output cleanup for BOTH Mobile and TV.
- No Performance Diagnostics UI layout changes in this patch.
- No Crash Diagnostics changes.
- No collection/instrumentation behavior changes.
- No ON/OFF lifecycle changes.

SUMMARY
- Empty state is now intentionally minimal:
  PERFORMANCE SUMMARY — <TAB>
  Diagnostics: OFF/ON
  No performance data recorded yet.
- Removes Selected events / Total buffered / Window: no events noise from the visible summary.
- With data, keeps only actionable information:
  Events, dropped count when non-zero, session window, current screen,
  jank/worst frame, UI stalls/worst stall, source scans, providers,
  QuickJS, playback issues and signals to inspect.
- Active counters and issue counters are shown only when non-zero.
- Recent signals are shown only when they exist and are reduced to the latest 5.
- Recent signal timestamps are compacted to time-of-day instead of full date.

RAW
- Raw header reduced to:
  RAW PERFORMANCE LOG — <TAB>
  ON/OFF • Events: N [• Dropped: N]
- Removes implementation-detail lines such as sampling interval, current screen,
  active counters and "Copy/Save retain complete raw log" from the visible Raw output.
- Empty Raw state is a single useful message.
- Preview only says "Showing latest X of Y events" when the UI is actually truncating events.
- Full Copy/Save Raw still contains the full retained event sequence.

EXPORT / METADATA
- Technical environment metadata is NOT removed from saved diagnostics.
- metadata.txt still contains package/version, Android/API, device, enabled/recording,
  event/drop counts, active counters, current screen, sampling/PSS intervals and jank threshold.
- Saved ZIP still includes full.log, per-tab raw logs and per-tab summaries.

SHARED CORE NOTE
- PerformanceDiagnostics is shared by Mobile and TV. This patch changes that shared core directly,
  so the cleaner Summary/Raw output appears on both platforms without duplicate UI-specific logic.

VALIDATION
- No Gradle build run.
- Static/diff/package-layout checks only.
