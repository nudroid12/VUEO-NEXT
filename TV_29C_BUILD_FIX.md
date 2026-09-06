# TV 29C — Build Fix

This follow-up fixes the Kotlin compile errors reported by the first 29C CI build without changing Search behavior or visual design.

Fixes in `TvSearchScreen.kt`:
- use the correct Compose `graphicsLayer` import
- import LazyGrid `itemsIndexed` as `gridItemsIndexed`
- keep `TvSearchScreen` internal so it does not expose the internal `TvSearchSession` type

The index/type/composable errors around the result grid were cascading from the missing LazyGrid `itemsIndexed` import. The graphics-layer property errors were cascading from the wrong extension import.

No Mobile or Shared Core source is changed.
