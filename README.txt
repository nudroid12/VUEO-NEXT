VUEO TV Episode IMDb + bottom safe spacing + MAL

Apply after VUEO_TV_Details_Hero_Episodes_Patch.zip. Replace the seven files at their matching paths. This ZIP includes the latest D-pad and MAL changes, including the runtime MAL filter fix, so the separate D-pad/MAL and MAL runtime ZIPs are not required first.

Episode ratings: background bulk lookup from SeriesGraph's public season-ratings endpoint, using its explicit imdb_rating values. This follows the episode-rating service shape used by NuvioTV 0.8.3-beta's repository/API fallback. TMDB season/episode mapping takes priority, to match the episode cards for anime where IMDb numbering differs. No overall-series rating is copied to episodes. No TMDB vote_average is labelled as IMDb. Valid range (0,10]; missing values are omitted.

For IMDb IDs the existing TMDB key/resolver is needed to identify the TMDB show ID. Numeric/tmdb IDs can be used directly. No title-only guessed matching. The page is released before rating fetch; metadata HTTP transport runs off the main thread. Bounded 40-title cache, success TTL 30 minutes, empty/failure TTL 1 minute. Cancellation propagates. EPISODE_RATINGS_READY / FAILED / SKIPPED events appear in Performance Diagnostic.

More Like This: focus reveal keeps the full card column, including title/year, at least 32dp above the viewport bottom; existing bottom content padding remains. Current hero layout is retained.

MAL: parser, label/badge and runtime allowlist included. Requires global MDBList ratings enabled/key and an actual returned MAL value. Existing rating source toggles remain unchanged.

Validation: inspected Nuvio tagged repository/API model; verified a real SeriesGraph sample payload and season/episode IMDb mapping; static data-flow/cancellation/MAL gate checks, delimiter balance and ZIP integrity. No Kotlin build or device test.

On TV: open a series, allow ratings to arrive, Down to season and episodes, inspect per-episode IMDb score, then browse to More Like This and check title/year spacing across Left/Right. If ratings are missing, copy the EPISODE_RATINGS diagnostic events.
