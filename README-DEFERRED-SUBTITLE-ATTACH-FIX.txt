VUEO — Keep deferred subtitle tracks out of video configuration

Upload mobile/ and shared/ from this ZIP to the VUEO repository root, replacing matching files. Rebuild/install the APK through your existing workflow. Cloudbank VidLove v2.5.0 stays unchanged. No YML.

Fixes:
1. Mobile buildPlayerMediaItem excluded only AI SmartSubs tracks. It now excludes every LiveSmartSubs.isOverlay track, including deferred provider captions. A later external-subtitle update can no longer attach those overlay captions as Media3 child sources.
2. Shared playback subtitle deduplication prefers the liveOverlay descriptor when the same URL arrives again as a regular discovery track, regardless of ordering. The overlay flag and its provider metadata are retained.
TV already uses LiveSmartSubs.videoSubtitles in its MediaItem builder. It receives the shared deduplication fix.

Evidence: supplied log shows 66 VidLove overlay tracks then a subsequent SUBTITLE_HANDOFF configured=66. The old Mobile helper filtered isSmart rather than isOverlay, which allowed this path. This fix removes that defect; network/server/decoder buffering cannot be diagnosed from the supplied provider log alone.

Validation: production shared Kotlin compilation, 20 deferred-subtitle checks plus restored TMDB constructor/property/copy check, and Kotlin syntax parsing for the Mobile media helper. The new checks cover regular duplicates arriving before/after overlay descriptors and ensure merged late tracks remain excluded from video configuration. Full APK assembly/device playback still requires the existing build and a device test.
