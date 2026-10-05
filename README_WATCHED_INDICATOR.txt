VUEO Player Episode Watched Indicator v73
Base compatibility: VUEO-NEXT-main (38) + TV hidden-seek v68 + Mobile Phase 1-4 (v69-v72)

Changes:
- Mobile: Next Episode from ended/safe-credits state records current episode as completed.
- Mobile: Episodes workspace refreshes completed history deterministically; existing Watched label/check now receives reliable data.
- TV: Episodes panel now shows a Watched check marker and label for completed episodes.
- TV: contextual Next marks current episode completed before switching, matching auto-next completion behavior.
- Manual episode switching from the Episodes panel does NOT mark the current episode watched.
- Preserves v68 hidden-seek preview/commit behavior and Mobile Phase 1-4 logic.
