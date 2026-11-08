# Source-placement policy regression

Run extract_policy.py REPO_ROOT TEMP_OUTPUT_DIR. This extracts the actual production stacking and layer-selection methods, not a separately copied implementation.

Compile the extracted Kotlin sources with stubs/*.kt, PlacementCheck.kt and CommentaryCheck.kt using Kotlin/JVM plus kotlin-stdlib. Run:
- com.vueo.tv.player.PlacementCheckKt
- com.vueo.tv.player.CommentaryCheckKt

The small Cue/text substitutes validate policy deterministically without loading Android natives. They do not validate Android drawing.

Separately, the complete modified TvSubtitlePlayerView and collision helper were compiled against real Android API 34 and Media3 common/UI/exoplayer 1.11.0, with the real style/stacking methods and compile-only substitutes for unrelated app services. Actual Cue runtime tests without Android cannot run because Android SystemProperties requires native runtime; no device rendering success is claimed.

Cases cover cue order reversal, authored fractional and numbered positions/anchors/size, ordinary unpositioned block stacking, legacy commentary positioning, commentary on/off and size flag, partial-parentheses dialogue and removal of last-cue priority. Existing 5,000-case geometry checks also pass.
