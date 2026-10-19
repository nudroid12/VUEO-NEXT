# VUEO Search Anime Identity v2 — Mobile and TV

Cumulative replacement for VUEO-Search-TMDB-Identity-Patch-No-YML.zip. Based on VUEO-NEXT-main (55).zip. Copy mobile/ and shared/ into the repository root, replace matching files, and add AnimeIdResolver.kt. Build/install Mobile or TV normally. This is a source patch, not an APK. No provider, manifest or YML changes. SHA-256 hashes in PATCH_FILES.json allow checking the baseline before replacement; manually merge if your working files differ.

## Confirmed failure

The supplied Performance ZIP records rawId=mal:62076, metadataTmdb=none, route=UNSUPPORTED_ID, keyConfigured=true. The prior patch handled IMDb/TMDB identity but missed MyAnimeList identity. API-key storage was not the cause.

## Resolution

Retain the prior explicit TMDB metadata and persistence fixes, cache-before-key order and useful skip reasons. Add one shared source-discovery bridge for mal:/myanimelist:, anilist: and kitsu: numeric IDs. Keep addon IDs and video requests unchanged. Anime identity is not added to background enrichment: that could overwrite local episode metadata with a different TMDB episode's data.

For movies: one ARM exact source-ID lookup, verify source ID and movie type, then use the explicit TMDB ID. For series: verify the same identity plus the explicitly mapped TMDB season; obtain the selected anime episode's published air date from AniZip and compare with TMDB season episodes. Only a single date match within one day is accepted. Missing/ambiguous data stops with a specific reason; local episode numbers are never substituted blindly. Existing conflicting TMDB hints are rejected.

An initial uncached anime series resolution uses up to three requests: ARM mapping, AniZip episode dataset, and the mapped TMDB season. The successful JSON responses are held in a bounded 64-entry, one-hour in-memory cache; repeated scans and later episodes in the same datasets reuse them. Cold series verification requires the user's configured TMDB key. No title searches, title-specific ID tables, retry loops or fallback service chains are added.

ARM and AniZip responses were fetched for MAL 62076 and 58514. MAL 62076 explicitly maps to TV 296286, season 1. MAL 58514 illustrates why local anime numbering cannot be sent straight through: its ARM TMDB season is 1 while the associated TVDB season is 2. Production discovery uses the verified result only for plugin season/episode arguments. Addon playback/subtitle requests retain their existing IDs.

## Validation and limits

49 executable checks passed: 30 prior metadata/identity regression cases and 19 anime identity/episode/integration cases. Kotlin compilation included the production resolver, anime bridge, models, catalogue codec, extracted production addon parser and merge functions, and the production discoverPlugins function with test boundaries for Android services. The latter verifies mapped episode arguments reach the plugin engine without rewriting the addon identity.

Live ARM/AniZip responses were used as fixtures. TMDB season responses in the tests are controlled fixtures, including an offset example and ambiguous same-day episodes; the complete live TMDB/provider flow and Android APK build were not validated here. Automatic approval review rejected a separate live TMDB probe because it attempted to use an embedded provider API key; it was not retried. No credential is included in this patch or documentation.

After installation, reopen Smoking Behind the Supermarket with You from Search and select S1E1. The TMDB_IDENTITY diagnostic should show rawId=mal:62076, route=ANIME_ID, resolved=296286, pluginEpisode=1:1 on a successful match. If mapping fails, animeReason gives the stage/reason. This patch fixes discovery identity; a separate video playback failure after discovery is outside its scope.
