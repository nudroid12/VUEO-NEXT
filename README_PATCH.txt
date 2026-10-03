VUEO ANR diagnostic evidence patch
Extract at repository root, replacing the included files. Shared diagnostics applies to TV and mobile.

- Android ANR trace after restart, up to 48000 characters; explicitly reports unavailable/error/truncation.
- Foreground watchdog independent of main looper: heartbeat every second, captures at >=2 seconds, max three main-thread snapshots per stall, max 80 frames. Covers screens outside source scans too. Background monitoring suppressed.
- Persistent previous-session evidence: screen, last 48 events (700 characters each), most recent live stall stack (16000 characters). Session/time checked against selected process exit. Atomic evidence writes on diagnostic writer; previous evidence rotated at startup.
- Existing provider/scan/navigation events retained. No claim that an active provider caused an ANR.
- Report bounded at 110000 characters with explicit truncation. Popup preview limited to 12000 characters for UI cost; Copy Log keeps complete stored report on TV/mobile.
- Existing URL/header sanitization retained. No marker changes. Does not fix the underlying ANR.

Validation: source review and ZIP integrity only. No local build or device test.
Device check: foreground navigation and source discovery; reproduce stall, restart, Copy Log and verify session/version, timeline, UI_STALL_LIVE stack, Android trace status. Background app should not emit watchdog snapshots. Android trace availability depends on system retention; snapshots may miss a specific operation.
