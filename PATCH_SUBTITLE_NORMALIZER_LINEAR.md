# Linear subtitle timestamp normalization — Mobile + TV

Apply contents at the VUEO repository root, then build and reinstall. This patch changes one production file: shared/core/src/main/java/com/vueo/shared/core/player/SubtitleTextNormalizer.kt. It is compatible with the earlier First Preparation and Timing Diagnostics patches and does not replace their other files.

Measured on device in the supplied 12:17 diagnostics:
- Episode 824 Malay: download 534 ms, decrypt 336 ms, normalize 9,221 ms.
- Episode 824 English: 395 ms, 294 ms, normalize 10,872 ms.
- Episode 825 English: 295 ms, 272 ms, normalize 11,072 ms.
- Episode 825 Malay: 61 ms, 278 ms, normalize 9,812 ms.

Replace the whole-file multiline regex replacement with a linear line scanner and bounded timestamp parser. Only numbered SRT timestamp lines are candidates. Keep the SRT-only first-cue gate, strict UTF-8 validation, fractional second padding and comma output. Preserve dialogue, cue index lines, trailing whitespace and line endings. VTT/ASS/other documents pass through. Return the original byte array when no correction is needed. Construct output lazily only for actual changes.

No change to AES, Cloudbank, URLs, timeout, retry, cache policy, source selection, video, prewarm or SmartSubs activation. Generic shared implementation applies to both Mobile and TV. No YAML.

Validation:
- Kotlin/JVM 17 production normalizer compilation passed.
- 14 fixture cases matched the previous implementation byte-for-byte.
- 3,000 varied cues (~268 KB) matched legacy output byte-for-byte.
- Invalid UTF-8, canonical identity/idempotence and 1 MB dialogue preservation passed.
- A single new-normalizer pass over the 3,000-cue fixture measured 13 ms on the development JVM. This is not an Android/device benchmark and does not predict exact TV latency.
- No full Android APK build or post-patch device playback verified.

After installation, repeat English/Malay first selection and export diagnostics. Existing stage=normalize timing will show whether the device delay has been removed.
