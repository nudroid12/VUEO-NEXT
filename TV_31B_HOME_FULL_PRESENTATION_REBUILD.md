# TV 31B — Home Full Presentation Rebuild

31B discards the previous Home Compose presentation instead of continuing the 29C/30A/30B/30C/31A visual patch chain.

Kept from VUEO:
- `TvRuntime.homeRows()` data contract
- profile-scoped Continue Watching and My List
- existing Home -> Detail and Home -> Source/Resume routes
- Home/Search/Library/Settings navigation labels

Rebuilt from blank:
- Home presentation shell
- hero/backdrop composition
- hero metadata hierarchy
- rows viewport
- Continue Watching cards
- poster catalog cards
- per-row focus memory and vertical focus transfer
- Home sidebar rail

Source-reference principles taken from the supplied NuvioTV source:
- hero text around 42% width
- hero media around 72% on the right
- rows occupy roughly the lower half of the viewport
- stable per-row item focus memory
- subtle 1.02 focus scale
- compact TV-safe icon rail

The code is VUEO-specific and reconnects to VUEO's existing data and routes rather than importing Nuvio application code.
