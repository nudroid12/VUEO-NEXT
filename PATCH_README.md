# VUEO TV English VTT position classification fix

Baseline: commit 32fdffa (the user's confirmed baseline).

Scope:
- One file only: TvSubtitleTimelineLoader.kt
- Changes how VTT cues are added to protectedText.
- Negative numbered line cues that satisfy the existing automatic-placement policy
  (for example line:-1 through line:-4) are no longer blanket-protected merely
  because the WebVTT cue has a line setting.
- Authored/non-automatic placements remain protected, including non-central
  horizontal placement, non-default size, vertical writing, bitmap cues, and
  unsupported line placement.
- This allows ordinary English VTT cues to enter the existing TV lower/upper
  subtitle layer policy. SmartSubs/Malay AI code is not modified.

No provider, video, decryptor, timing, commentary, Mobile, or SmartSubs renderer
changes are included.

Important:
- This is a narrow source patch, not a full APK/build.
- It relies on the existing automaticPlacement() policy and temporal layer logic.
- Verify on the same scene: English and Malay AI should have matching layer
  behavior, while authored sign translations retain their source positions.
