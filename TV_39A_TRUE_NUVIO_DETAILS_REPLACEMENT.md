# TV 39A — True Nuvio Details Replacement

Presentation target: NuvioTV 0.8.6-beta Details screen, matching the supplied Nuvio TV references rather than restyling the old VUEO Details composition.

## Rebuilt Details presentation

- Large 540dp cinematic hero with Nuvio-style backdrop/scrims.
- Large title hierarchy and Nuvio action row.
- Ratings row and richer release/runtime/country/language/status metadata.
- Creator and Cast / More like this / Trailer switcher.
- Circular cast portraits and role labels.
- Production/network logo cards.
- Nuvio-sized 360x235 episode rail and season tabs.
- Source -> Details season/episode focus memory retained.

## VUEO boundaries retained

- VUEO runtime/meta/ratings/related data contracts.
- Watchlist/library/history and resume behavior.
- Details -> Source playback routing.
- Related-title routing.
- Optional insight support.
- Extra visual metadata/trailer is loaded separately for Details presentation only.

## Locks

- Source presentation is not modified by 39A.
- Player presentation/playback engine is not modified by 39A.
- Mobile is not modified by 39A.

## Validation

- Kotlin parser surface check: no parse/`expecting` diagnostics.
- Full Gradle compile could not run because `services.gradle.org` DNS resolution is unavailable in the build environment.
