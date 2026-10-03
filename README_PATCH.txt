Extract at repository root, replacing RuntimeDiagnostics.kt. Apply after the ANR diagnostics patch.
Restores STALL_THRESHOLD_MS = 350L used by provider UI_STALL_RISK. Live watchdog threshold remains 2000ms.
Uploaded build log has one unique compiler error: unresolved STALL_THRESHOLD_MS at RuntimeDiagnostics.kt:214. Shared core failed before downstream mobile/TV compilation, so this log cannot confirm those compile.
Validation: constant definition/reference checked and ZIP integrity passed. No local build run per user instruction. Re-run CI to verify remaining compilation.
