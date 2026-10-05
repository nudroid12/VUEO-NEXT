VUEO Mobile Player Phase 1 Parity v69
Base: VUEO-NEXT-main (38)

Changed files:
- mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
- mobile/src/main/java/com/vueo/mobile/ui/player/PlayerSkipControl.kt

Scope:
1. Mobile uses shared PlayerSkipPolicy for active/valid skip segments and safe targets.
2. Skip Credits remains available even when a next episode exists.
3. Next Episode card is allowed early only when ENDING safely terminates at video end (same post-credit safety policy as TV).
4. 8-second auto-next countdown now has pause/seek/buffer/panel/recovery/error guards and rechecks the real player before dispatch.
5. Old 95% / 60-second fallback is removed to avoid bypassing post-credit safety.
6. Progress time display is current time + negative remaining time.
7. ENDING label is now "Skip Credits".

Not included:
- Subtitle renderer/commentary parity (Phase 2)
- Buffering/translation UI parity (Phase 3)
- Next-episode source/subtitle prefetch (Phase 4)
- TV changes

Validation:
- Static diff/syntax structure checked.
- Full Gradle compile could not run because the wrapper tried to download Gradle from services.gradle.org in an offline environment.
