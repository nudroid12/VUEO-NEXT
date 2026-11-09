# TV Video Fit mapping fix

Only TvPlayerScreen.kt changes. Fit maps to RESIZE_MODE_FIT, Fill maps to RESIZE_MODE_FILL (stretch), Zoom maps to RESIZE_MODE_ZOOM (aspect-preserving crop). Fill previously duplicated Zoom.

Apply CHANGES.diff from the repository root with `git apply --check CHANGES.diff` followed by `git apply CHANGES.diff`. Alternatively copy the included source file over baseline (64); use the diff if your player screen has later edits to preserve them.

No extra magnification or black-bar detection is introduced. Fit and Zoom can still look identical when source and display aspect ratios match. Encoded black bars remain part of the picture.

Validation: exact scoped replacement, all three mappings and factory/update resizeMode assignments checked; ZIP CRC checked. Android build and device playback not run.
