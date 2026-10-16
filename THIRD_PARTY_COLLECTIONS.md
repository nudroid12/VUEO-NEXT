# Nuvio collections attribution

This patch adapts Nuvio's user-defined Collection -> CollectionFolder -> catalog
source model and collection ordering/editor/browser approach for VUEO.

Upstream sources inspected on 2026-10-16 UTC:

- NuvioMedia/NuvioTV, commit `6bda667f8c6756d98b06e1404081fad91f09a134`
  https://github.com/NuvioMedia/NuvioTV/tree/6bda667f8c6756d98b06e1404081fad91f09a134
  `app/src/main/java/com/nuvio/tv/domain/model/Collection.kt`
  `app/src/main/java/com/nuvio/tv/ui/screens/collection/FolderDetailViewModel.kt`
- NuvioMedia/NuvioMobile, commit `229c083cf3db7bab6fda284484f87439eb4e0eb0`
  https://github.com/NuvioMedia/NuvioMobile/tree/229c083cf3db7bab6fda284484f87439eb4e0eb0
  `composeApp/src/commonMain/kotlin/com/nuvio/app/features/collection/CollectionModels.kt`
  `CollectionRepository.kt` (collection upsert/delete/move)
  `CollectionCatalogResolver.kt`
  `CollectionManagementScreen.kt`, `CollectionEditorScreen.kt`, `FolderDetailScreen.kt`

Copyright belongs to the respective Nuvio contributors for upstream work.
New adapted files are marked SPDX GPL-3.0-only and the upstream license text is
included at `LICENSES/Nuvio-GPL-3.0.txt`. Preserve this attribution and license
when redistributing the adaptation. GPL source/distribution terms apply to
reused GPL code; this notice does not relicense unrelated third-party assets.

VUEO-specific changes:

- Use VUEO's shared media engine and exact installation IDs from Phase 14.
- No fallback to another addon just because its catalog ID matches.
- Profile-local atomic file persistence, with IO work outside the UI thread.
- Native VUEO versioned JSON, strict validation and merge preview.
- Existing Mobile/TV image caches supplied as image slots.
- Shared Android Compose editor/browser; TV Home uses existing remote focus rows.
- Addon catalog sources only; Nuvio TMDB/Trakt source builders, sync backend,
  website, GIF-on-focus and branding/assets were not copied.

This is an adaptation, not a byte-for-byte port of Nuvio or a promise of Nuvio
JSON compatibility.
