# Mobile image/cache audit — phase 1

Apply these files at the VUEO-NEXT repository root, replacing matching files.
This patch contains no workflow/YML files and no APK.

## Behavior

- Mobile NetworkImage reads the measured physical-pixel bounds and rounds them to 128px size buckets (maximum target dimension 2048px). Bitmap bounds are decoded first, then a power-of-two sample is selected with a 2-million-pixel decoded budget. Full-resolution source images are no longer decoded for small posters.
- RAM cache keys include URL and target size. Cache budget is the smaller of one eighth of the Java heap and 24 MiB. System memory callbacks trim or clear cached references; displayed bitmaps are never recycled manually.
- At most three image jobs download/decode concurrently. Sixty-four fixed mutex stripes replace the URL-keyed lock map. Weak previews are bounded to 512 URL entries and allow a previous decode to paint while layout establishes a new size.
- Same-URL image state remains visible while a new size loads or fails. Changing URLs resets that state so recycled cards do not display the previous title.
- Existing 96 MiB disk cache and URL hashes are reused. Pruning runs after every successful download and on cache access at most once per minute. Reads/publication/pruning share a lock; active temporary downloads are excluded. Orphan temporary files from previous process runs are removed before this process starts downloading.
- Existing HTTPS acceptance, 16 MiB encoded-response limit, timeout values and retry count remain. Cancellation is checked while reading and before decoding/publishing. A currently blocked HttpURLConnection operation can still wait until its network timeout; it is not instantly interrupted.

## Performance diagnostics (shared: Mobile + TV)

- Application Activity start/stop callbacks gate the frame probe and system sampler. The first callback after returning to the foreground establishes a fresh frame baseline. A gap spanning background time cannot become FRAME_JANK.
- Real long callback gaps within an uninterrupted foreground interval are retained; no arbitrary long-gap filter is applied.
- APP_VISIBILITY logs foreground/background changes and cumulative activeElapsed for the recording session. This is Activity-visible time, which includes a paused but still started Activity in multi-window.
- System sampler CPU/PSS baselines restart on foreground entry. An older sampler cannot publish a late result after a lifecycle restart.
- Other diagnostic breadcrumbs can still be recorded in background. The existing session-window summary is wall-clock time; use visibility markers for active time. Callback cadence remains a proxy, not rendered FPS/GPU frame time.

## Validation completed

- Production MobileImageDecodePolicy compiled with Kotlin 2.3.21 and executed on the JVM: 429 checks passed, including large/malformed dimensions, sample powers, target rounding and the pixel budget.
- All three production Kotlin files passed Kotlin PSI syntax parsing.
- Reviewed cache access/maintenance locking, bounded preview/lock structures, cancellation propagation, and install-before-Activity-start integration in both MainActivity implementations.
- Full Android build, real BitmapFactory decode, Compose behavior and device performance were NOT tested: this workspace has no Android SDK and the supplied source has no Gradle wrapper JAR.

The standalone verification source is in validation/mobile-image-cache/DecodePolicyCheck.kt. To rerun with an installed Kotlin compiler:

```sh
kotlinc mobile/src/main/java/com/vueo/mobile/ui/components/MobileImageDecodePolicy.kt validation/mobile-image-cache/DecodePolicyCheck.kt -include-runtime -d image-policy-check.jar
java -jar image-policy-check.jar
```

## Device verification after building

1. Browse Home, Search, Details and Library, including fast scrolling and 20 repeated navigation loops. Verify posters/backdrops render and no wrong-title images appear.
2. Rotate/change window size while an image is visible. Verify it remains visible while the new decode loads; repeat offline using disk-cached images.
3. Leave diagnostics enabled, background/lock for several minutes, then return. Confirm APP_VISIBILITY markers and no FRAME_JANK covering the background interval. Repeat on TV, where the diagnostics file is shared.
4. Compare Java heap/PSS after repeated browsing under the same workload; confirm a plateau before drawing conclusions about improvement. No measured speedup or memory reduction is claimed by this patch alone.
5. Exercise image failures and memory pressure. Confirm navigation remains usable and fallback images/text work.
