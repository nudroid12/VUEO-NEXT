# VUEO For You rebuild — TV and mobile

Apply this ZIP at the VUEO-NEXT app repository root. Existing app workflows stay in place; no YML is supplied.

## Result

For You uses up to 12 unique watched/Favorite seeds. Watch signals require existing meaningful-watch eligibility, repeated episodes use the strongest signal rather than adding up, Favorite adds 3, per-title signal is capped at 8, and watch influence decays with a 90-day half-life.
The active TMDB/Trakt engine expands those seeds into related candidates (18 per seed, maximum 216). Home catalogs supplement the candidate pool (maximum 360 local items). Engine requests have a 12-second budget and at most 3 concurrent jobs; cancellation also cancels metadata HTTP calls. Local ranking appears before remote discovery completes.
The aggregate feed cache is profile/engine/credential/input scoped, bounded to 12 entries, and retained for 15 minutes (90 seconds when all requests return no candidates).

## Score

Strong-profile starting weights: 55% personal similarity, 20% global taste, 15% vote-confidence-adjusted quality, 10% nearby exploration. Missing components are omitted and the remaining weights normalized. New profiles blend in general catalog/popularity ordering instead of hiding the row behind confidence or a fixed match threshold.
Personal similarity uses available synopsis TF-IDF cosine, canonical genres, people metadata, and direct related-result rank evidence. Synopsis matching is lexical; no semantic embedding or new AI service is introduced. Candidate cast/crew metadata is used when supplied; no per-candidate metadata fan-out is added.
Quality shrinks rating towards 6.8 using a 100-vote prior. Unknown vote counts do not receive fabricated confidence. These ratings are transient ranking fields, not new UI rating sources.
Watched/Favorite titles are excluded. Exact known title/year/type aliases reconcile some TMDB/IMDb duplicates; absent release years never use title-only matching. Diversity uses synopsis/genre/people similarity; no inferred title-prefix family penalty is used by the new ranking.
Scores are internal ranking values, not probabilities or displayed match percentages. User DNA workspace and its original score calculation are unchanged.

Because You Watch retains direct engine order for the latest meaningful watched title and reuses that seed's result. This patch does not guarantee nonempty TMDB results when credentials, toggles, ID resolution or upstream availability prevent discovery. More Like This remains direct provider order.

## Validation

Actual new Kotlin ranking, feed loader and Home policy compiled on JVM with boundary fixtures. Six JUnit behavior tests passed, plus engine/profile/cache/cancellation checks. Metadata transport compiled against OkHttp 5.4.0 and passed success/error/HTTPS/cancellation checks. Full Android/Compose build and live TMDB/Trakt results were not verified here.
