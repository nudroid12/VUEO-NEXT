# Minimal Temporal cleanup

Apply AFTER Native Position Fix and Active Cue Refresh Fix. TvSubtitlePlayerView.kt retains Active Cue Refresh Fix in full; TimelineLoader is not replaced.

Changes: replace a redundant Pair named adjusted with two direct aliases; delete unused TvSubtitleCollisionLayout.kt and its two obsolete validation files plus PATCH_TV_SUBTITLE_COLLISION.md. The helper has no callers in app source. Other historical notes are retained.

The repository ZIP workflow supports .vueo-delete-files.txt and removes the four exact paths automatically. Simply extracting the ZIP manually does NOT perform deletions. For manual Codespaces extraction, also run:

    rm -f -- tv/src/main/java/com/vueo/tv/ui/player/TvSubtitleCollisionLayout.kt validation/tv-subtitle-collision/GeometryCheck.kt validation/tv-subtitle-collision/README.md PATCH_TV_SUBTITLE_COLLISION.md

Checks performed: reconstructed baseline + working native-position patch + latest refresh patch; verified exactly the four specified files are deleted, only the Pair aliases change in the TV view, and no remaining Kotlin reference to the collision helper exists. All other source bytes are unchanged, including diagnostics, line handling, temporal role logic and refresh fixes.

No Android/Kotlin compile or device render test was run; Kotlin compiler/Android dependencies are unavailable in this environment. This is source/dependency verification, not a measured speed improvement.
