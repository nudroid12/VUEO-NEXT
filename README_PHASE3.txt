VUEO Mobile Player Phase 3 - Playback / Status UI Parity
Base: VUEO-NEXT-main (38) with Phase 1 v69 + Phase 2 v70 applied

Changes:
- Normal LOADING/BUFFERING text removed from the top control row.
- Loading/buffering now uses a centered spinner only.
- Spinner has a 500 ms anti-flash delay so brief buffering does not flicker.
- Source recovery keeps the useful "TRYING NEXT SOURCE" status.
- Added compact "Translating…" pill with spinner in the visible top control row.
- Translation pill is hidden together with controls and inherits the existing smooth control transition.
- No next-episode prefetch changes in this phase.

Changed paths:
mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayer.kt
mobile/src/main/java/com/vueo/mobile/ui/player/MobilePlayerStatusIndicators.kt
