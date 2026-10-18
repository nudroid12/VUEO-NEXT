# VUEO Search TMDB identity patch

Based on VUEO-NEXT-main (55).zip. Copy the mobile/ and shared/ directories into the matching repository root and replace the ten listed Kotlin files, then build Mobile/TV normally. This is a source patch, not an APK. No YML/workflow files are included. Baseline and replacement SHA-256 hashes are listed in PATCH_FILES.json; compare or merge manually if your files have changed since this baseline.

## Change

Preserve explicit TMDB identity from addon metadata (tmdbId, tmdb_id, moviedb_id, ids.tmdb, or a tmdb-prefixed ID), including catalogue/library persistence. Keep the addon ID for its normal addon requests. Preserve a hint during detail merging only for matching identity and media type. Share successful enrichment mappings with the source resolver.

Resolution uses a direct TMDB ID, explicit metadata mapping, then cached mapping before considering API-key availability. With a configured key, retain official TMDB IMDb find, requiring exactly one result of the requested type. Without a key, an uncached valid IMDb ID gets one exact-ID Cinemeta metadata request, requiring the same IMDb and media type plus an explicit TMDB mapping. Successful mappings are cached. No title search, title-specific IDs, movie-to-series substitution, or season guessing.

Source discovery reports the actual skip reason. Performance diagnostics include TMDB_IDENTITY with the resolution route and whether a key is configured; they do not print the key.

## Validation

Kotlin compilation and 30 executable identity/resolver checks passed. Includes the production addon parser, metadata merge functions, media models and catalogue JSON codec; covers metadata/cache without a key, exact-ID fallback, malformed/conflicting mapping, type mismatch, cancellation, official lookup ambiguity, and catalogue persistence. Android-dependent integration files were reviewed; no full Android APK build or device playback validation was performed in this environment.

## Device check

After building/installing, reopen the title from Search and select S1E1. Existing cached items without a TMDB hint can use the exact IMDb fallback. For the supplied Smoking Behind the Supermarket with You example (IMDb tt37614297), the verified mapping is TV 296286; this mapping is not hardcoded in the app. Confirm providers now execute and inspect TMDB_IDENTITY in performance diagnostics if still skipped.

This patch addresses discovery identity. It does not change provider extraction or repair a separate video failure after playback starts. A movie catalogue item remains a movie even if its title resembles a series.
