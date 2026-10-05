VUEO Mobile Subtitle Parity - Phase 2 (v70)
Base: VUEO-NEXT-main (38), intended after Phase 1 v69

Changes
- Mobile stacks simultaneous lower subtitle cues instead of overlapping them.
- Mobile detects tagged commentary subtitles, places them at the top, and provides a Commentary ON/OFF toggle.
- Commentary preference is shared/preserved using the existing subtitle commentary setting key.
- Mobile subtitle bottom padding rises to at least 18% while full player controls are visible, then returns to the user's saved Bottom Position when controls hide.
- Mobile Bottom Position adjustment step changed from 5% to 2%, matching TV.
- Subtitle Delay remains 250 ms per step on Mobile.
- TV Subtitle Delay changed from 50 ms to 250 ms per step, matching Mobile.
- Existing font/style/subtitle timing logic remains intact.

Files
mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
mobile/src/main/java/com/vueo/mobile/ui/player/PlayerSubtitleWorkspace.kt
mobile/src/main/java/com/vueo/mobile/ui/player/MobileSubtitlePlayerView.kt (new)
tv/src/main/java/com/vueo/tv/ui/player/TvPlayerSubtitleWorkspace.kt
shared/core/src/main/java/com/vueo/shared/core/storage/SettingsStore.kt

Build note
Full Gradle compile could not run in the offline environment because the project wrapper tries to download Gradle from services.gradle.org. Static diff/path checks were performed.
