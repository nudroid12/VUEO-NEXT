# Delete these obsolete files manually

**After overlaying this ZIP, delete these files from your VUEO repository before building.** Files already absent can be skipped. This is a plain list, not a deletion script.

The first 11 entries remove compiled trial code and LibVLC-only resources/rules. Leaving the old VLC Kotlin classes in place after removing their dependency can prevent compilation. The remaining entries are obsolete validation files and experiment notes.

- `shared/core/src/main/java/com/vueo/shared/core/player/SoftwarePlaybackActivity.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/SoftwarePlaybackPolicy.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/ExternalVlcActivity.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/ExternalVlcPolicy.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/CompatibleVideoRenderersFactory.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/VideoDecoderSession.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/VideoCodecPolicy.kt`
- `shared/core/src/main/java/com/vueo/shared/core/player/VideoQueueingPolicy.kt`
- `shared/core/consumer-rules.pro`
- `shared/core/src/main/assets/licenses/LibVLC-LGPL-2.1.txt`
- `shared/core/src/main/assets/licenses/LibVLC-NOTICE.txt`
- `validation/software-playback/SoftwarePlaybackPolicyCheck.kt`
- `validation/external-vlc/ExternalVlcPolicyCheck.kt`
- `validation/video-decoder-recovery/VideoCodecPolicyCheck.kt`
- `validation/video-decoder-recovery/VideoQueueingPolicyCheck.kt`
- `PATCH_NOTES_SOFTWARE_PLAYBACK_PHASE_21.md`
- `PATCH_NOTES_EXTERNAL_VLC_PHASE_22.md`
- `PATCH_NOTES_VIDEO_DECODER_RECOVERY_PHASE_19.md`
- `PATCH_NOTES_VIDEO_QUEUEING_PHASE_20.md`

Keep VideoDecoderRecovery.kt, VideoFallbackPolicy.kt and the replacement validation/video-decoder-recovery files in this ZIP. Keep the existing ProviderBrowserAccessActivity implementation.
