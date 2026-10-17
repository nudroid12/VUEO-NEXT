# VUEO video decoder recovery — TV and Mobile

Base: VUEO-NEXT-main (54).zip. Extract at repository root; paths start with mobile/, tv/, shared/ and validation/. No YML included.

Changes:
- Enable Media3 decoder initialization fallback on both existing renderer factories.
- On a video decoder error, try another supported video track from the same source, preferring H.264 and full-HD-or-smaller formats. This runs only after an error; normal successful selection remains unchanged.
- Exclude failed video configurations and cap track recovery at three attempts per source/player session.
- Keep the current media item, request headers, audio/subtitle settings, playback position and play/pause intent during track recovery.
- Clear the video override when TV switches source. Do not start decoder recovery while backgrounded or exiting.
- If track recovery cannot proceed, retain the existing automatic-source-recovery setting and pass a decoder-specific error into that flow.
- Add VIDEO_DECODER_RECOVERY and VIDEO_DECODER_EXHAUSTED events to Performance diagnostics.

Validation:
- Actual pure Kotlin policy compiled with Kotlin 2.2.21; 16 assertions passed.
- Android build attempted for shared core, mobile and TV. Blocked before source compilation: com.android.application:9.1.1 could not be resolved from configured repositories. No APK build or device playback result is claimed.

Device check:
1. Retry the logged VidLink DASH source on the affected phone.
2. Test an HEVC source with a smaller track or H.264 alternative; recovery should preserve position.
3. Test a source with only the failing HEVC format; it should reach the existing next-source flow or show a useful decoder message, without looping on that configuration.
4. Verify subtitle selection and resume after recovery, source switching, and TV playback.
5. Export Performance after any remaining failure.

Limits: This patch cannot add a missing system/software HEVC decoder or transcode video. Same-source recovery requires an alternative track exposed by the stream. The earlier provider validation timeout is outside this patch.
