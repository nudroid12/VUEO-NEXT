Apply by extracting at repository root and overwrite matching paths.

Adds provider MIME metadata and preserves it through source adapters/merge. Both players set MediaItem MIME on initial playback and subtitle refresh. Legacy explicit HLS labels support extensionless URLs; plain VOE names do not force HLS. Existing headers and recovery behavior remain.

Performance Diagnostic: PLAYBACK_ERROR + PLAYBACK_CAUSE show server/provider, host, MIME, error code, position, state and bounded cause chain. URL paths, query strings and fragments are omitted to protect signed URLs.

Static path and call-site checks and ZIP integrity passed. Six policy regression tests included for CI; no build or tests run locally. Test VOE on mobile and TV and copy Performance Diagnostic if it still fails.
