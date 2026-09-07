# TV 39A — True Nuvio Details Replacement

## Why this patch exists
38A removed old presentation files but still reproduced too much of the previous VUEO visual composition. 39A changes the source-of-truth direction: Nuvio Details presentation first, VUEO runtime/data contracts second.

## Nuvio source used directly as presentation reference
- `MetaDetailsScreen.kt`: sticky backdrop + one scrolling Details column + section order.
- `HeroSection.kt`: fixed 540dp hero and hero content order.
- `EpisodesSection.kt`: season tabs, 360x235 episode cards, full-bleed art, floor-fade scrim, no episode focus scale.
- `MetaDetailsScreen.kt` `PeopleSectionTabs`: `Cast | More Like This` tab switch on focus.
- `CastSection.kt`: 100dp circular cast portraits inside a 150dp item lane.
- `MoreLikeThisSection.kt`: 260x146 landscape recommendations, restrained 1.02 focus scale.
- `CompanyLogosSection.kt`: network/production logo rows.

## 39A presentation differences
- Hero order is now Nuvio order: title -> actions -> resume progress -> credits -> ratings -> synopsis -> meta row.
- Play action is a rounded white Nuvio-style button; My List is a circular action.
- Backdrop uses Nuvio's long left fade and bottom fade beginning around 38% of the screen.
- Removed separate `Seasons` and `Episodes` headings from the series flow.
- Season change follows Nuvio focus behavior with a short 150ms settle delay.
- Episode cards use Nuvio 360x235 composition with episode badge + title + metadata inside the full-bleed card.
- When both exist, Cast and More Like This are no longer stacked as two independent sections: they are switched through Nuvio's `Cast | More Like This` tabs.
- TV titles can now expose both Network and Production rows, matching Nuvio's Details section ordering instead of collapsing them into one VUEO company row.

## VUEO behavior intentionally preserved
- `TvRuntime.loadMeta`, ratings, related titles and DNA.
- My List/watchlist state and callback.
- resume-aware primary action.
- season/episode selection and Source handoff.
- playback/history progress.
- related-title routing.
- optional manual VUEO Insight.
- process-local Details focus memory.

## Active route validation
The tested TV entry path in `VUEO-NEXT-main (17)` is:
`com.vueo.tv.MainActivity` -> `com.vueo.tv.VueoTvApp` -> `com.vueo.tv.detail.TvDetailScreen` -> `TvDetailView`.
The legacy `com.vueotv.app.detail` package is not referenced by the active `com.vueo.tv` application path.

## Regression boundary
39A changes only `com/vueo/tv/detail/*` and this note. 35A Source and 37A Player are not modified.

## Validation
- No `TvDetail38*` / `Detail38*` presentation symbols remain in the active Details package.
- Active route resolves to the new 39A presentation symbols.
- Kotlin parser scan found no syntax/`expecting` errors.
- Full Gradle compile cannot start in this environment because the repository wrapper downloads Gradle from `services.gradle.org`, and DNS is unavailable.

## Deliberate contract limits
Nuvio can render title logos and trailers because its `Meta` contract exposes them. VUEO `MediaItem` in the supplied source has no logo/trailer field, so 39A keeps the Nuvio composition with the text title and does not invent a TV-only logo/trailer backend.
