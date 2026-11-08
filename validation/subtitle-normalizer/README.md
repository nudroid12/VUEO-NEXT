# Subtitle normalizer regression

Compile these sources together in a standalone Kotlin/JVM module:
- shared/core/src/main/java/com/vueo/shared/core/player/SubtitleTextNormalizer.kt
- validation/subtitle-normalizer/LegacySubtitleTextNormalizer.kt
- validation/subtitle-normalizer/NormalizerCheck.kt

Run com.vueo.shared.core.player.NormalizerCheckKt with kotlin-stdlib on the classpath.

LegacySubtitleTextNormalizer is an unchanged test oracle from the supplied source, renamed to avoid collision. Do not place this validation directory in the Android app source set.

Cases: LF/CRLF, BOM/leading blank space, short timestamp forms, long hour fields, fractional padding, canonical unchanged-byte identity, VTT/ASS/ordinary-text passthrough, malformed timestamps, trailing settings, dialogue preservation, no final newline, invalid UTF-8, repeat-normalization identity, 3,000 varied cues compared byte-for-byte with legacy output and a 1 MB dialogue line.
