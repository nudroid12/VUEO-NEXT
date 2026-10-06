VUEO v106 FIXED - Crash Diagnostics full-screen UI parity
Baseline: VUEO v105 (VUEO-NEXT-main (40) with v105 Performance Diagnostics patch applied).

Scope verified:
- Mobile Crash Diagnostics: full-screen workspace matching the v105 Performance Diagnostics visual shell.
- TV Crash Diagnostics: full-screen workspace matching the v105 Performance Diagnostics visual shell and D-pad navigation behavior.
- One ON/OFF toggle only. ON enables RuntimeDiagnostics collection; OFF stops collection while retaining recorded evidence.
- Summary / Raw segmented modes.
- Compact search field.
- Large log viewport.
- Fixed Copy / Save / Clear / Close action row.
- Copy uses a fresh full export of the selected Summary/Raw mode, not the filtered/preview text.
- Save keeps existing RuntimeDiagnostics full ZIP bundle behavior.
- Clear deletes RuntimeDiagnostics evidence and refreshes the visible log.
- While ON, the visible diagnostics refresh periodically (Mobile 1.25s, TV 1.5s).
- TV D-pad path: toggle -> Summary/Raw -> Search -> Log -> actions; log Up/Down scroll is explicitly handled.
- RuntimeDiagnostics shared-core implementation is NOT modified by this patch.
- No Gradle build was run. Static lexical/delimiter, duplicate-import, diff-scope and patch-layout checks only.

Packaging correction:
- Patch files are at repository-relative ZIP root (mobile/... and tv/...), compatible with the repository's Apply ZIP Patch workflow.
