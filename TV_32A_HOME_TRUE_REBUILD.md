# TV 32A — Home True Presentation Rebuild

This checkpoint rejects 31A/31B as insufficiently clean rebuilds.

32A starts from the latest uploaded VUEO repo and replaces the entire Home Compose presentation boundary.

## Preserved

Only VUEO application contracts are preserved:

- `TvRuntime.homeRows(...)`
- shared `LibraryStore` Continue Watching and My List data
- media/detail/resume callbacks
- root route names Home / Search / Library / Settings
- profile callback

## Rebuilt from blank

- Home presentation hierarchy
- right-side hero media composition
- hero scrims and metadata placement
- row viewport and spacing
- Continue Watching cards
- portrait catalog cards
- per-row focus memory
- vertical row landing behavior
- Home sidebar presentation and focus behavior

Home no longer imports or invokes the legacy `TvSidebar` from `TvTopBar.kt`.

## Source-reference principles

The Nuvio source was used as an architectural reference, especially its Modern Home split:

- hero text approximately 42% width
- hero media approximately 72% width on the right
- independent rows viewport around 49–52% screen height
- focused item remembered per row
- hero updates after focus settles rather than on every rapid D-pad event
- one stable navigation focus tree instead of hidden duplicate sidebar targets

No Nuvio application code or data model is copied into VUEO. VUEO keeps its own data/runtime/navigation contracts.
