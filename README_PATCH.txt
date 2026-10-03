Extract at repository root; replace included files.
TV pause backdrop waits 8 seconds without interaction (previously 5). Existing interaction-token reset and playback eligibility checks retained.
Adds current position / duration and remaining minutes (rounded up). Unknown duration displays --:-- and omits remaining time; negative position clamped and known-duration position clamped to duration. Existing title, episode and OK hint retained.
Includes current cumulative TvPlayerScreen.kt; apply to the current session repository. No marker changes or synopsis added.
Validation: source checks and ZIP integrity passed. No local build or device test.
Device checks: pause without interaction => backdrop at 8 seconds; remote interaction restarts timer; resume hides backdrop; opening panels prevents backdrop; verify position/duration and remaining minutes.
