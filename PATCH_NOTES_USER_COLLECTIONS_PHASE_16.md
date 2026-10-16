# Phase 16 — user collections, adapted from Nuvio

Base: user-supplied `VUEO-NEXT-main (52).zip`. This patch adds a tool for users
to compose their own collections. No predefined Netflix/Anime/country lists
are installed. Existing Phase 14/15 files are retained except explicit Home/
navigation additions listed in the patch.

## Apply

Extract the ZIP at the repository root and overwrite matching paths. Keep
`shared/collections-ui/` as a sibling of `shared/core/`. Includes settings and
app Gradle dependency updates for the new shared UI module. Contains no YML.
Build Mobile and TV through the existing GitHub Actions workflow.

## Use

1. Settings -> Content Manager -> Collections -> Create.
2. Name the collection (e.g. Genres), choose Tabs or Rows, then Add folder.
3. Name the folder (e.g. Anime), optionally enter a direct HTTPS cover image URL
   and emoji, then Add catalog source. Search installed enabled catalog addons.
4. Add one or more catalogs; select a supplied genre when needed. Reorder or
   remove sources, save the folder, then save changes to the collection.
5. Return to Home. Each enabled nonempty collection becomes a row of folder
   cards. Open a folder to browse its sources; open a title for normal Details
   and playback. Back returns to the folder; Back again returns to Home.

Collection, folder and source order can be changed. Whole collections can be
turned off without deleting them. Collections are stored per local profile.
Copy JSON and Import JSON from clipboard provide a manual transfer/backup path.
Imports show a merge confirmation and update matching collection IDs while
keeping other collections. Only VUEO version-1 exports are supported.

This is the addon-catalog collection tool, not all Nuvio collection features:
no direct TMDB/Trakt source builder, collection cloud sync, local image upload,
GIF/video covers or independent folder poster-shape controls are included.
Covers use direct HTTPS URLs with the host image loader's existing formats.

## Loading and identity

- Collection covers do not fetch their source catalogs on Home.
- Mobile collections are separate lazy Home items, not one large eager column.
- TV cards enter the existing Home row focus/scroll system, with stable keys.
- TV folder cards skip media-artwork metadata lookup and title action menus.
- Initial folder requests run on IO, at most two concurrently. Switching tabs
  cancels obsolete initial requests. Errors/Retry are per source.
- Tabs (including optional All) or rows; Load more is offered when skip is
  advertised. Deduplication uses media type + ID; provider IDs are preserved.
- Pagination follows raw page counts, even if Kids filtering removes all items.
  Repeated pages stop after no new raw IDs; a provider ignoring skip cannot
  append the same content indefinitely.
- Disabled or missing addons show an error. Installation identity is exact:
  same manifest ID/catalog ID from another installation is not substituted.
- Kids filtering uses the same shared engine policy as other browsing screens.
- Saving does not install/enable addons, change Content Manager toggles, touch
  playback, change SmartSubs, or add Supabase/Cloudflare requests.
- Catalog Order continues to control normal Home catalog rows independently;
  a catalog hidden from normal Home rows can still be used inside a collection.

## Validation

18 JVM tests execute the actual new models, JSON codec, store and catalog
resolver. Android Context/AtomicFile and the host engine are narrow test doubles;
actual shared descriptor/media/extension definitions are used. Tests cover JSON
round trip, malformed imports/URLs, duplicate IDs, stable order, Off persistence,
profile isolation, concurrent saves, non-destructive import, corrupt-file
preservation, exact same-manifest installation lookup, disabled/missing addons,
genre/skip extras, Kids filtering and cancellation.

The new CollectionScreens.kt successfully compiles with the Kotlin 2.3.21 Compose
compiler and real Compose BOM 2026.06.01 libraries (UI/Foundation 1.11.4,
Material3 1.4.0), against the tested core API and an engine test shell.
All 15 changed/new production Kotlin files pass PSI syntax checks. App route
and callback wiring was reviewed against the supplied repo. No Android SDK is
installed here: full Gradle/APK builds, device rendering, remote behavior and
live addon calls have NOT been validated. GitHub Actions/device verification
is still needed, especially first focus, Back from Details and small screens.

Attribution and GPL-3.0 license text are included; see THIRD_PARTY_COLLECTIONS.md.
