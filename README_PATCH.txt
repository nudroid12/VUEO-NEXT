VUEO Live subtitle sync - mobile and TV

Apply repository-relative files over latest cumulative source including previous subtitle regex/cache fixes. Keep those shared parser fixes.

Changes: right-half full-height Live Sync panel; no background dim; opening with external subtitle resumes playback; choose a line at spoken start to immediately replace absolute delay; list stays open. TV captures on OK key down, consuming repeats and release. No Undo, staged confirmation, pause or seek. Close retains delay and leaves playback running. Focus gray, last applied line white when unfocused, other lines dark. Mobile controls hidden on opening.

Existing 60-second delay limit and bounded loading/cache/diagnostics retained.

Validation: static checks and ZIP integrity only. No local build or device tests.

Device checks: open during playback and while paused; select at spoken start and verify alignment; choose another line to replace delay; close/reopen to verify persistence; TV hold OK to verify no repeated capture; scroll both ways and reach Close; check landscape/portrait bounds and video/subtitle visibility. Portrait intentionally stays half width as requested.
