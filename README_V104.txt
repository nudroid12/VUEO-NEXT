VUEO v104 - Mobile Performance Diagnostics Full-screen UI

Scope: Mobile UI only. No TV files and no shared-core/diagnostics engine changes.

Changes:
- Replaces the cramped Performance Diagnostics AlertDialog with a full-screen Mobile diagnostics page.
- Compact header with Back and recorder state.
- Recorder card with ON/OFF switch and full-width Start/Stop Recording action.
- Horizontal diagnostic tabs remain: Home, Search, Details, Player, Sources, Episodes, Library, Settings, Provider, System, Other, Full.
- Adds log search/filter on the visible preview.
- Log viewer takes the remaining screen space.
- Fixed bottom actions: Copy, Save, Clear, Close.
- Keeps the existing 30k recent preview guard; Copy/Save still use the complete selected log.
- Performance recorder semantics stay unchanged: OFF has no sampler/frame probe/event buffering; enabled is only armed until Start Recording.

Validation:
- Static/diff checks only.
- No Gradle build run.
