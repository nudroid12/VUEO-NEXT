VUEO v133 — TV Continue Watching Same-First-Presentation

Scope
- TV Home startup presentation
- Shared LibraryStore lightweight Continue Watching startup cache
- No Mobile UI behavior changes
- No Player / Sources / subtitle changes
- No GitHub workflow files

What changed
1. TV Home retained state now reads a tiny persisted Continue Watching cache at construction time.
2. Cached catalog rows and cached Continue Watching are passed into buildTvHomeRows() together, so both can appear in the same first Home presentation.
3. Full LibraryStore history reconciliation still runs in the background and corrects the row after first paint when needed.
4. LibraryStore maintains a lightweight per-profile Home CW cache on playback/library mutations.
5. Completed playback performs exact history-backed reconciliation so next-episode policy remains correct.
6. Cache payload intentionally keeps only lightweight media/playback fields needed for Home startup.
7. Existing v132 Home focus / deterministic vertical navigation is preserved.

Migration
- On the first launch after upgrading from a pre-v133 build, the new cache may not exist yet. It is seeded once from the existing dedicated Continue Watching cursors, then future launches use the lightweight persisted cache directly.

Diagnostics
- HOME_CW_STARTUP_PUBLISHED
- HOME_CW_FULL_BEGIN
- HOME_CW_FULL_PUBLISHED
- HOME_CW_FIRST_FRAME

Validation
- Static delimiter/import/whitespace checks: PASS
- Kotlin parser smoke: PASS (no Android/project classpath; unresolved symbols expected)
- Startup-cache behavior assertions: PASS
- No .github/workflows content in patch
- Gradle build: NOT RUN
