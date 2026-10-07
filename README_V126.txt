VUEO Stremio Open-With + Shared Addon Categories v126

Base: current v125 chain reconstructed from VUEO-NEXT-main (41) + v122 + v123 + v124 + v125 overlays.
Scope: Mobile Android deep-link install flow + shared addon category policy + TV category helper parity.
No Gradle build.

1. VUEO now registers for Stremio addon links on Android
- Mobile MainActivity advertises ACTION_VIEW + DEFAULT + BROWSABLE for the stremio:// scheme.
- MainActivity uses singleTop so an incoming addon link can be delivered to the existing VUEO activity through onNewIntent when applicable.
- stremio://host/path is normalized to https://host/path before it reaches the existing addon installer.
- Plain HTTPS manifest URLs are also accepted by the normalizer; insecure HTTP links are rejected.
- Invalid/empty deep links are ignored instead of being installed.

2. Deep links use the existing VUEO confirmation flow
- VUEO does NOT silently install an addon from an external link.
- After startup/profile resolution, an incoming Stremio link routes to Settings > Content Manager > Addons.
- The Install Addon dialog opens pre-filled with the normalized HTTPS manifest URL.
- The user still presses Install, after which the existing ExtensionInstaller/AddOnStore flow is used.
- Consumed intents are cleared so the same dialog does not reopen from the same Activity intent.

3. Multi-purpose category removed
- AddonCategory.MULTI_PURPOSE has been removed from shared/core.
- One addon now receives exactly one primary category using the TV priority rule:
  stream -> Streams
  subtitles -> Subtitles
  catalog/meta -> Catalog & Metadata
  otherwise -> Other
- Therefore an addon that exposes stream + catalog/meta is categorized as Streams.
- Mobile Content Manager no longer renders or references Multi-purpose.

4. TV and Mobile now share the same category policy
- TV previously implemented the stream/subtitle/catalog priority locally.
- TV now calls the shared primaryAddonCategory() helper.
- This is a shared-core refactor used by both Mobile and TV.
- TV visible behavior is intentionally unchanged.

Unchanged
- No hard addon-count limit was added.
- Addon enable/disable/install/remove behavior is unchanged.
- Home catalog concurrency is unchanged.
- Source discovery, Player and subtitle behavior are unchanged.
- TV Content Manager layout/category order is unchanged.
