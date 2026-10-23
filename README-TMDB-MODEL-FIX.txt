VUEO — Fix missing MediaItem.tmdbId after deferred subtitles patch

Upload shared/ from this ZIP to the VUEO repository root and replace the matching file. Then run the existing build again. No provider change or workflow/YML change is needed.

The previous deferred-subtitle package carried an older MediaModels.kt which omitted MediaItem.tmdbId. Other current metadata/catalog/source/storage code still uses that property, causing the shared-core compilation errors in the supplied log.
This restores the exact String? = null property and documentation from the existing VUEO Search TMDB Identity patch, retaining the new SubtitleTrack.liveOverlay field. No other model changes.

Verified: model matches the original identity patch byte-for-byte after removing the intentionally added liveOverlay field. Production shared subtitle sources compile; constructor/property/copy identity check and all 17 deferred subtitle checks pass. Full release APK assembly was not run here; rebuild through the existing GitHub workflow.
