# Addon installation identity and toggle preservation — Phase 14, TV + Mobile

Apply after Phase 13. Extract into the repository root and overwrite matching files. No YML/workflow files are included. Based on the supplied VUEO-NEXT-main (5)(1).zip. App roots, Player, Entity Results and sync serialization are not replaced.

## Problem
The engine previously indexed Stremio installations by the manifest's provider ID. Two configuration URLs sharing that ID could replace one another. TV refresh loads manifests concurrently, so completion order could change which installation remained. Mobile's coordinator rejected one conflicting ID rather than allowing both configurations to coexist. Add/Install also overwrote an existing URL's saved enabled preference with true.

This mechanism is confirmed in the code, but the user's particular addon manifests were not fetched to establish whether they share an ID. This patch addresses that conflict class and the confirmed Add/Install toggle reset.

## Installation identity
- ExtensionDescriptor.id remains the provider's original manifest ID.
- A separate installationId is derived from SHA-256 of the trimmed, exact Stremio manifest URL. Configuration path/query differences remain distinct. The internal key does not contain the URL or embedded credentials.
- Refreshing the same URL keeps its identity even when the manifest version or provider ID changes. Different URLs with the same manifest ID coexist.
- Non-Stremio extension identity remains its original ID.
- The registry atomically publishes a replacement and its enabled state, preserving other installations. Same-URL replacement retains the installation's position; the addon list is ordered by URL for deterministic presentation.
- Stremio catalog rows, Home/catalog preferences, browse keys, source planning, metadata-origin references, addon UI keys, toggling and deletion use installation identity.
- Stremio transport still receives the original manifest ID. Existing stream/subtitle provider IDs, names and subtitle display IDs are not rewritten.
- Legacy saved media containing a provider ID can resolve it only when exactly one matching installation exists. Ambiguous legacy origins cannot be inferred reliably; fresh catalog results carry the precise installation identity.

## Toggle preservation
- Add is idempotent for an existing manifest URL: an explicit Off remains Off. A genuinely new URL starts On.
- TV/Mobile Add, manual refresh and configured reload install using the persisted enabled preference at commit time.
- Deleting an installation removes only that configuration. Explicit removal followed by adding again starts On, as a new installation.
- URLs remain exact identities after trimming. This patch does not guess whether changed configuration URLs or redirect aliases represent the same user choice.

## Catalog preference migration
Before publishing a configured installation, copy matching legacy catalog order/Off keys into the new installation namespace once. Each installation has its own migration marker, so a later user change is not overwritten on subsequent refreshes. Legacy preferences remain available for configuration URLs which have not loaded yet. Two previously indistinguishable installations inherit the same legacy catalog Off choice initially; they can then be controlled independently.

Legacy Home cache rows cannot identify which duplicate configuration supplied them. They are filtered out rather than being treated as current scoped rows; fresh scoped catalog rows repopulate the cache. A first-upgrade catalog reload may therefore be visible. Existing favorites/history are not deleted.

Sync still transfers the existing addon/repository configuration data. Local addon/repo/provider toggle preferences and these migration markers are not added to the sync payload. Existing search/catalog work limits are unchanged; there is no new installation-count limit.

## Validation
17 JUnit JVM tests passed using the production identity, descriptor/model, registry, preference-store and Mobile reload-coordinator code. Android Context/SharedPreferences and the full engine shell are stand-ins; registry behavior is the actual production implementation.

Coverage: distinct configuration hashes, secret-free internal keys, same-ID coexistence, repeated refresh and independent Off state, precise deletion, safe legacy lookup, unchanged non-Stremio identity, distinct catalog keys, idempotent Add across store instances, new/remove-and-add behavior, one-time order/Off migration, independent post-migration catalog choices, incomplete-manifest preference preservation, atomic disabled installation under concurrent reads, four concurrent configuration refreshes, actual Mobile coordinator duplicate-ID acceptance, refresh failure retention and failed preference-read atomicity.

All 16 production Kotlin files passed PSI syntax checks. Static integration checks cover scoped engine/UI/catalog call sites, persisted-toggle install wiring and preserved original Stremio transport IDs. Full engine transport, TVRuntime and Compose UI were not compiled end-to-end. No Android build or phone/TV test was run because this workspace lacks an Android SDK and the supplied repository lacks its Gradle wrapper JAR.

## Device checks
1. On TV and Mobile, keep the four catalog addons installed and refresh repeatedly. All successfully loaded configurations should remain listed, including those sharing a provider ID.
2. Set one addon Off, refresh and install its same URL again. Verify it stays Off and the other configuration is unchanged. Restart/update the app and verify the saved choice persists.
3. Toggle and delete only one configuration; verify its sibling remains available.
4. Verify independent catalogs, catalog order/Off migration, Home rows and See All after the first fresh reload.
5. Open a title from each configuration; verify metadata routing. Also test favorites/history created before this patch.
6. Check streams/subtitles and their provider/display IDs, and sync with both platforms updated. Local toggles should remain local.
7. Test a temporary manifest failure: a working descriptor already loaded in that runtime should remain rather than being removed by a failed refresh.
