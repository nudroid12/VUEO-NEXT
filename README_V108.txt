VUEO v108 — Crash Diagnostics Log Cleanup
Baseline: v107 (v105 + v106 FIXED + v107)

Scope
- Mobile + TV Crash Diagnostics output cleanup.
- Shared RuntimeDiagnostics core updated so both platforms receive identical Summary/Raw semantics.
- No Gradle build was run.

Changes
1. Summary is now signal-first instead of metadata-first:
   - Diagnostics ON/OFF
   - current screen when known
   - crash signals
   - native crashes
   - stalls
   - QuickJS failures
   - provider failures
   - source-scan/playback errors only when non-zero
   - active work only when present
   - up to six recent signals to inspect only when problems exist
2. Empty sections ending in "- none" were removed.
3. Package/version/Android/device/memory/logging/tombstone details were removed from the visible Summary/Raw views.
4. Technical metadata is retained and expanded in metadata.txt inside Save ZIP.
5. Raw view is now concise: RAW CRASH LOG + event count + chronological evidence.
6. Clear no longer injects DIAGNOSTICS_CLEARED into the visible raw log.
7. Current screen is cached in memory so clearing the log does not immediately degrade Summary to "Unavailable".
8. Crash Diagnostics header subtitle shortened on Mobile + TV to avoid truncation.

Not changed
- Crash/native/QuickJS/stall collection semantics.
- Save ZIP structure (summary.txt, raw.log, metadata.txt, crash report/tombstone when available).
- Performance Diagnostics UI/output.
- Provider/source/player runtime behavior.
