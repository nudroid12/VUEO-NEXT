VUEO TV Hidden Long-Seek Progress + Thumb v92
Apply after v91.

Scope: TV only. No shared-core or Mobile changes.

Changes
- Hidden + single Left/Right tap keeps the existing immediate seek and shows no chrome.
- Hidden + held Left/Right (or media rewind/fast-forward repeat) shows only the progress rail.
- The hidden long-seek rail reuses the exact full-controls bottom layout, preserving the same rail position.
- Time labels and action pills keep their layout space during hidden long-seek but are fully invisible/non-interactive.
- Hidden long-seek suppresses contextual Skip/Next prompts while the rail preview is active, so the rail is the only seek chrome shown.
- Release commits the pending seek once and immediately hides the hidden-only progress rail.
- Added a circular progress thumb to the rail in both full controls and hidden long-seek preview.
- During hidden long-seek the rail/thumb uses the focused/emphasized size for clear visibility.
- Existing hybrid seek behavior and long-press acceleration are preserved.

Files
- tv/src/main/java/com/vueo/tv/ui/player/TvPlayerScreen.kt
- tv/src/main/java/com/vueo/tv/ui/player/TvPlayerVueoPresentation.kt
- tv/src/main/java/com/vueo/tv/ui/player/TvPlayerVueoComponents.kt

Checks
- No Gradle build run, per project instruction.
- Delimiter/syntax-structure sanity checks passed.
- git diff whitespace checks passed.
- Overlay contains only the three intended TV source files plus this README.
