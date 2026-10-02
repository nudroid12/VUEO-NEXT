Extract at repository root and overwrite the matching file.
Fixes missing TvPlayerOption.providerName used by TvPlayerVueoPanels provider tabs and populates it from StreamSource.providerName. Other option types retain null default. Based on VUEO-NEXT-main (34); preserves HLS MIME and playback diagnostics.
Static checks and ZIP integrity passed. No local build or tests run. Re-run CI.
