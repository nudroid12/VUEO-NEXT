# TV 29C — Mobile Search Parity

29C ports the mature Mobile Search information architecture and search behavior into the rebuilt TV app. The UI hierarchy remains recognizably Mobile; only scale, focus and remote interaction are adapted for a 10-foot D-pad experience.

## Mobile parity carried to TV
- Search header and large rounded search field
- Discover mode when the query is empty
- Search Results mode when a query is present
- Title / Actor search mode
- Type filters: All / Movies / Series / Anime
- Discover sorting: Popular / Trending / Newest
- Dynamic genre filter
- Local cached title results first, followed by remote/partial search results
- Actor search through actor-capable addons plus optional TMDB filmography
- Mobile ranking, relevance filtering, dedupe and sort behavior
- Poster-first result cards with title and type/catalog metadata
- Mobile empty/error/status copy adapted for TV

## D-pad adaptation
- Search field is initial focus and uses the Android TV text-input path.
- UP from search field reveals contextual navigation and focuses Search.
- DOWN from navigation restores the search field.
- DOWN from the field reaches the Title/Actor control, then filters, then results.
- LEFT/RIGHT on Title/Actor selects the mode; OK toggles it.
- Filter row has deterministic LEFT/RIGHT movement and no horizontal escape at the ends.
- OK opens filter choices or the focused media once.
- First poster row UP returns to filters.
- Poster row edges do not wrap horizontally.
- Filter dialogs restore focus to the filter that opened them.

## Return-state lock
Search session state is owned by `VueoTvApp`, not by the route-local composable. Returning from Detail restores:
- query
- Title/Actor mode
- type/sort/genre filters
- current result/discover data
- grid scroll position
- exact focused media when it still exists

No Mobile or Shared Core source was changed in this patch.
