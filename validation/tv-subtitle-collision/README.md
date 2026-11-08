# TV subtitle collision checks

Compile TvSubtitleCollisionLayout.kt with GeometryCheck.kt using Kotlin/JVM and kotlin-stdlib, then run com.vueo.tv.player.GeometryCheckKt.

Tests cover moving a three-line-like information box above a fixed dialogue box, unchanged already-spaced layout, protection of an additional commentary box, horizontally separate boxes, insufficient-space preservation and 5,000 generated viewport/box cases.

Additional implementation checks performed during this task:
- Compiled the complete modified TvSubtitlePlayerView.kt plus the geometry helper against Android API 34 (Robolectric android-all compile jar), real Media3 common/ui/exoplayer 1.11.0 jars, AndroidX annotations and Guava. Existing TV style/stacking code was extracted verbatim from TvPlayerTracks.kt; surrounding app services were replaced with compile-only signatures.
- Compared commentary splitting/classification/visibility methods byte-for-byte with VUEO-NEXT-main (63).zip.
- Confirmed MobileSubtitlePlayerView.kt remains byte-identical to the uploaded ZIP.

This validates geometry policy and API/type compatibility. It is not an Android visual/instrumentation test or an APK build. Geometry fixtures approximate the screenshot relationship; the actual HiAnime episode 7 cue file was not obtained. Device layout and exact 11:44 scene still need verification.
