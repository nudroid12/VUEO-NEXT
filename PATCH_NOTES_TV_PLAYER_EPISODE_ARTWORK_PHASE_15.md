# Player episode artwork — Phase 15, TV

Apply after Phase 14: extract into the repository root and overwrite matching paths. No YML/workflow files are included. The Player Episodes panel is the TV surface shown in the user's photo; Mobile episode UI is not modified.

## Behavior
- Try the episode thumbnail first. If it is missing, invalid or reports a load failure, try an available TMDB episode still, then the show's backdrop and poster.
- Keep failure state per episode row so a failed URL is not repeatedly retried under different fallback labels. Duplicate URLs are tried once. When a TMDB still arrives later, it can replace the show backdrop without retrying the failed primary URL.
- If every available image fails, the existing episode-code placeholder remains. No image can be guaranteed when both metadata and artwork endpoints lack a usable image.
- The fallback map is presentation-only: original EpisodeItem objects, episode IDs, seasons, episode selection and playback source are unchanged. Artwork arrival does not request another source or restart the video.

## TMDB requests
Only the selected season is checked when at least one episode lacks a usable primary thumbnail or a visible row reports failure. This is one completed lookup attempt per selected season during that panel's composition, not one TMDB metadata request per card. It reuses the existing resolved-ID and season-metadata caches. Returning to a cancelled lookup may start it again; reopening a disposed panel resets its local attempt state, while existing TMDB caches still apply.

The additional lookup requires the existing TMDB API key and enabled TMDB artwork enrichment. Without them, existing episode/show artwork is still used. A lookup may resolve an uncached TMDB ID before loading a season; a cache hit may require no network request. No new backend or key is added.

Network/season parsing runs on IO and is cancelled when the panel/selected season changes. The existing season metadata function now propagates coroutine cancellation instead of caching cancellation as an empty success. Other pre-existing TMDB error/cache behavior is unchanged.

A tv_player_episode_artwork timing event is available in Performance diagnostics under Player. It includes network waiting and is not blocked UI time. URLs and API keys are not added to logs.

## Validation
Eight JUnit JVM tests passed against the production EpisodeArtworkPolicy: ordered primary/TMDB/backdrop/poster selection, failure progression, no-key show-art fallback, URL deduplication, late TMDB arrival, malformed/insecure URL rejection, scheme/whitespace normalization without changing signed query data and isolation of failed-image sets between episodes.

All four production Kotlin files passed PSI syntax checks. Static checks verify the presentation/panel wiring, one season-level TMDB call site, completed-attempt guard, existing cache reuse and cancellation propagation.

The fallback policy tests do not execute Compose, live TMDB, image decoding or actual HTTP failures. Full Android compilation and TV remote/device tests were not run: this workspace lacks an Android SDK and the supplied repository lacks its Gradle wrapper JAR. The specific Hell's Kitchen episode URLs in the photo were not retrieved, so the original missing-image cause remains unconfirmed.

## TV checks
1. Open the Player Episodes panel for the affected season. Confirm episode stills appear when supplied by the addon or TMDB.
2. Test missing/broken episode thumbnails; verify a TMDB still or show backdrop/poster replaces the empty image area.
3. Switch seasons quickly, close/reopen the panel and navigate by remote. Verify the correct season's cards and unchanged focus/episode selection.
4. Test with no TMDB key or artwork enrichment Off; existing show artwork should still be used without an extra TMDB lookup.
5. Confirm artwork arrival does not interrupt playback, seek, change the source or alter subtitle selection.
