VUEO subtitle dialogue sync / TV diagnostics patch

Apply these repository-relative files over the current cumulative source tree.

Changes:
- Escape the closing brace in subtitle override cleanup for Android regex compatibility. The cleanup runs for every cue, even plain SRT/VTT.
- Subtitle failure diagnostics include the first VUEO code location, without subtitle text or URL tokens.
- TV Performance & Crash Diagnostics: initial focus on log, D-pad Up/Down scroll, OK moves to Copy Log, Down at bottom moves to Copy Log, Up from footer returns to log. Full export remains copied; preview is limited to recent 24,000 characters.
- Added SRT override cleanup regression test source.

Validation: static source review and ZIP integrity only. No local build or JUnit/device execution.
Device check: retry the same subtitle on mobile and TV; expect SUBTITLE_SYNC_READY with cue count. On TV, open diagnostics and scroll both ways, move to footer, return with Up, and copy full log.
